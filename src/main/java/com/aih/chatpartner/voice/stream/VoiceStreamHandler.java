package com.aih.chatpartner.voice.stream;

import com.aih.chatpartner.ai.AiService;
import com.aih.chatpartner.ai.AiServiceFactory;
import com.aih.chatpartner.config.QiniuConfig;
import com.aih.chatpartner.service.voice.AsrService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * /ws/voice-chat WebSocket 端点
 * 最小可运行版本：
 *   - 客户端发送 start {groupId, voiceType, speedRatio}
 *   - 客户端发送 user_text {text}
 *   - 服务端：LLM 流式 token -> llm_partial
 *   - 将切好的句子分段发送到七牛 TTS WS，收到 base64 音频块 -> tts_chunk
 */
@Slf4j
@Component
public class VoiceStreamHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AiServiceFactory aiServiceFactory;
    private final QiniuConfig qiniuConfig;
    private final AsrService asrService;

    private final Map<String, SessionCtx> sessions = new ConcurrentHashMap<>();

    public VoiceStreamHandler(ObjectMapper objectMapper,
                              AiServiceFactory aiServiceFactory,
                              QiniuConfig qiniuConfig,
                              AsrService asrService) {
        this.objectMapper = objectMapper;
        this.aiServiceFactory = aiServiceFactory;
        this.qiniuConfig = qiniuConfig;
        this.asrService = asrService;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        SessionCtx ctx = new SessionCtx(session);
        sessions.put(session.getId(), ctx);
        log.info("WS connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("WS recv text: {}", payload);
        Map<String, Object> req = objectMapper.readValue(payload, new TypeReference<>() {});
        String type = (String) req.getOrDefault("type", "");
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null) {
            return;
        }
        switch (type) {
            case "start" -> {
                ctx.groupId = asLong(req.get("groupId"), 0L);
                ctx.voiceType = asStr(req.get("voiceType"), "qiniu_zh_female_tmjxxy");
                ctx.speedRatio = asDouble(req.get("speedRatio"), 1.0);
                ctx.audioFormat = asStr(req.get("audioFormat"), "raw");
                ctx.chunker = new TextChunker(64);
                ctx.ttsClient = new QiniuTtsWsClient(qiniuConfig, objectMapper);
                ctx.streamingService = aiServiceFactory.getAiService(ctx.groupId);
                ctx.asrService = this.asrService;
                // 初始化 ASR WS（实时识别）
                ctx.startAsr(session);
                sendJson(session, Map.of("type", "started"));
            }
            case "user_text" -> {
                String text = asStr(req.get("text"), "");
                if (text.isEmpty()) return;
                if (ctx.streaming) {
                    sendJson(session, Map.of("type", "warn", "message", "already streaming"));
                    return;
                }
                startLlmStreaming(session, ctx, text);
            }
            case "stop" -> {
                // 如果累积了音频，先做一次 ASR -> LLM -> TTS 的链路
                byte[] audio = ctx.drainAudio();
                if (audio != null && audio.length > 0) {
                    // 进行 ASR 识别（当前 AsrService 需要对象存储 URL，暂以演示模式返回）
                    String userText = ctx.asrService.speechToText(audio, ctx.audioFormat);
                    safeSendJson(session, Map.of("type", "asr_final", "text", userText));
                    startLlmStreaming(session, ctx, userText);
                } else {
                    // 没有音频则直接关闭
                    // 关闭 ASR WS
                    ctx.closeAsr();
                    closeSession(session);
                }
            }
            default -> sendJson(session, Map.of("type", "error", "message", "unknown type"));
        }
    }

    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession session, @NonNull BinaryMessage message) {
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null) return;
        java.nio.ByteBuffer buf = message.getPayload();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        // 优先走 ASR WS 实时流
        if (ctx.asrWsClient != null && ctx.asrWsClient.isOpened()) {
            ctx.asrWsClient.sendAudio(data);
        } else {
            // 兜底：本地累积，stop 时走 REST ASR（演示）
            ctx.appendAudio(data);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("WS closed: {} {}", session.getId(), status);
        closeSession(session);
    }

    private void closeSession(WebSocketSession session) {
        try { session.close(); } catch (Exception ignore) {}
        SessionCtx ctx = sessions.remove(session.getId());
        if (ctx != null) {
            ctx.dispose();
        }
    }

    private void sendJson(WebSocketSession session, Map<String, ?> payload) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private void safeSendJson(WebSocketSession session, Map<String, ?> payload) {
        try { sendJson(session, payload); } catch (Exception ignore) {}
    }

    private static String asStr(Object o, String dft) { return o == null ? dft : String.valueOf(o); }
    private static long asLong(Object o, long dft) { try { return o == null ? dft : Long.parseLong(String.valueOf(o)); } catch (Exception e) { return dft; } }
    private static double asDouble(Object o, double dft) { try { return o == null ? dft : Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return dft; } }

    private void startLlmStreaming(WebSocketSession session, SessionCtx ctx, String text) {
        if (text == null || text.isBlank()) return;
        if (ctx.streaming) {
            // 正在流式，排队
            ctx.enqueueUserText(text);
            return;
        }
        ctx.streaming = true;
        ctx.streamingService.chatStreamInXiYangYangRole(text)
                .doOnError(err -> {
                    log.error("LLM stream error", err);
                    safeSendJson(session, Map.of("type", "error", "message", "llm error: " + err.getMessage()));
                    ctx.streaming = false;
                    // 继续下一个
                    ctx.drainLlm(session);
                })
                .doOnComplete(() -> {
                    String rest = ctx.chunker.flushRemainder();
                    if (rest != null && !rest.isEmpty()) {
                        ctx.enqueueTts(rest);
                    }
                    ctx.streaming = false;
                    // 继续下一个
                    ctx.drainLlm(session);
                })
                .subscribe(token -> {
                    safeSendJson(session, Map.of("type", "llm_partial", "text", token));
                    for (String sentence : ctx.chunker.appendAndExtract(token)) {
                        ctx.enqueueTts(sentence);
                    }
                });
    }

    @Data
    class SessionCtx {
        final WebSocketSession session;
        Long groupId = 0L;
        String voiceType = "qiniu_zh_female_tmjxxy";
        Double speedRatio = 1.0;
        volatile boolean streaming = false;

        transient AiService streamingService;
        transient TextChunker chunker;
        transient QiniuTtsWsClient ttsClient;
        transient AsrService asrService;
        transient QiniuAsrWsClient asrWsClient;

        String audioFormat = "raw";
        final ByteArrayOutputStream audioBuf = new ByteArrayOutputStream(32 * 1024);

        final Queue<String> ttsQueue = new ConcurrentLinkedQueue<>();
        final AtomicBoolean ttsBusy = new AtomicBoolean(false);
        final Queue<String> llmQueue = new ConcurrentLinkedQueue<>();

        SessionCtx(WebSocketSession session) { this.session = session; }

        void enqueueTts(String sentence) {
            if (sentence == null || sentence.isBlank()) return;
            ttsQueue.add(sentence.trim());
            drainTts();
        }

        void drainTts() {
            if (ttsBusy.get()) return;
            String next = ttsQueue.peek();
            if (next == null) return;
            if (!ttsBusy.compareAndSet(false, true)) return;

            // 推送 tts 开始
            safeSendJson(session, Map.of("type", "tts_start", "text", next));

            ttsClient.synthesizeStream(next, voiceType, "mp3", speedRatio,
                    base64 -> safeSendJson(session, Map.of("type", "tts_chunk", "data", base64)),
                    () -> {
                        // 完成一个句子的 TTS
                        ttsQueue.poll();
                        ttsBusy.set(false);
                        safeSendJson(session, Map.of("type", "tts_done"));
                        // 继续下一句
                        drainTts();
                    },
                    err -> {
                        ttsQueue.poll();
                        ttsBusy.set(false);
                        safeSendJson(session, Map.of("type", "error", "message", "tts error: " + err.getMessage()));
                        drainTts();
                    });
        }

        void dispose() {
            // 若后续加入 ASR/LLM 订阅句柄，可在此处取消
        }

        private void safeSendJson(WebSocketSession session, Map<String, ?> payload) {
            try {
                session.sendMessage(new TextMessage(new ObjectMapper().writeValueAsString(payload)));
            } catch (Exception ignore) {
            }
        }

        void appendAudio(byte[] data) {
            try {
                audioBuf.write(data);
            } catch (IOException ignore) {
            }
        }

        byte[] drainAudio() {
            byte[] out = audioBuf.toByteArray();
            audioBuf.reset();
            return out;
        }

        void enqueueUserText(String text) {
            if (text == null || text.isBlank()) return;
            llmQueue.add(text.trim());
            drainLlm(session);
        }

        void drainLlm(WebSocketSession session) {
            if (streaming) return;
            String next = llmQueue.poll();
            if (next == null) return;
            // 调用外部处理以继续流式
            VoiceStreamHandler.this.startLlmStreaming(session, this, next);
        }

        void startAsr(WebSocketSession session) {
            try {
                if (asrWsClient != null && asrWsClient.isOpened()) return;
                asrWsClient = new QiniuAsrWsClient(qiniuConfig, objectMapper);
                int sr = 16000;
                int ch = 1;
                String fmt = audioFormat != null ? audioFormat : "raw";
                String model = "paraformer-realtime-v2";
                try {
                    // 尝试从配置中读取
                    sr = qiniuConfig.getAsr().getSampleRate();
                    ch = qiniuConfig.getAsr().getChannels();
                    if (qiniuConfig.getAsr().getFormat() != null) fmt = qiniuConfig.getAsr().getFormat();
                    if (qiniuConfig.getAsr().getModel() != null) model = qiniuConfig.getAsr().getModel();
                } catch (Exception ignore) {}

                asrWsClient.connect(
                        fmt,
                        sr,
                        ch,
                        model,
                        true,
                        partial -> safeSendJson(session, java.util.Map.of("type", "asr_partial", "text", partial)),
                        fin -> {
                            safeSendJson(session, java.util.Map.of("type", "asr_final", "text", fin));
                            // 触发 LLM 流式
                            VoiceStreamHandler.this.startLlmStreaming(session, this, fin);
                        },
                        err -> safeSendJson(session, java.util.Map.of("type", "error", "message", "asr error: " + err.getMessage())),
                        () -> safeSendJson(session, java.util.Map.of("type", "asr_closed"))
                );
            } catch (Exception e) {
                safeSendJson(session, java.util.Map.of("type", "error", "message", "asr start error: " + e.getMessage()));
            }
        }

        void closeAsr() {
            try {
                if (asrWsClient != null) asrWsClient.close();
            } catch (Exception ignore) {
            } finally {
                asrWsClient = null;
            }
        }
    }
}
