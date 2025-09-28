package com.aih.chatpartner.voice.stream;

import com.aih.chatpartner.ai.AiService;
import com.aih.chatpartner.ai.AiServiceFactory;
import com.aih.chatpartner.config.QiniuConfig;
import com.aih.chatpartner.model.entity.ChatGroup;
import com.aih.chatpartner.service.ChatGroupService;
import com.aih.chatpartner.service.ChatHistoryService;
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
import reactor.core.Disposable;

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
    private final ChatHistoryService chatHistoryService;
    private final ChatGroupService chatGroupService;

    private final Map<String, SessionCtx> sessions = new ConcurrentHashMap<>();

    public VoiceStreamHandler(ObjectMapper objectMapper,
                              AiServiceFactory aiServiceFactory,
                              QiniuConfig qiniuConfig,
                              AsrService asrService,
                              ChatHistoryService chatHistoryService,
                              ChatGroupService chatGroupService) {
        this.objectMapper = objectMapper;
        this.aiServiceFactory = aiServiceFactory;
        this.qiniuConfig = qiniuConfig;
        this.asrService = asrService;
        this.chatHistoryService = chatHistoryService;
        this.chatGroupService = chatGroupService;
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
                ctx.autoReply = asBool(req.get("autoReply"), true);
                ctx.chunker = new TextChunker(24);
                ctx.ttsClient = new QiniuTtsWsClient(qiniuConfig, objectMapper);
                ctx.streamingService = aiServiceFactory.getAiService(ctx.groupId);
                ctx.asrService = this.asrService;
                // 通过 groupId 反查 userId，用于持久化
                try {
                    if (ctx.groupId != null && ctx.groupId > 0) {
                        ChatGroup grp = chatGroupService.getById(ctx.groupId);
                        if (grp != null) ctx.userId = grp.getUserId();
                    }
                } catch (Exception ignore) {}
                // 只在首次start时初始化 ASR WS
                if (ctx.asrWsClient == null || !ctx.asrWsClient.isOpened()) {
                    ctx.startAsr(session);
                }
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
            case "config" -> {
                // 动态配置：当前仅支持 autoReply 切换
                Boolean ar = (req.get("autoReply") instanceof Boolean) ? (Boolean) req.get("autoReply") : null;
                if (ar != null) {
                    ctx.autoReply = ar;
                    safeSendJson(session, Map.of("type", "config_ack", "autoReply", ctx.autoReply));
                }
            }
            case "stop" -> {
                // 关闭ASR连接
                ctx.closeAsr();
                // 如果累积了音频，先做一次 ASR -> LLM -> TTS 的链路
                byte[] audio = ctx.drainAudio();
                if (audio != null && audio.length > 0) {
                    // 进行 ASR 识别（当前 AsrService 需要对象存储 URL，暂以演示模式返回）
                    String userText = ctx.asrService.speechToText(audio, ctx.audioFormat);
                    safeSendJson(session, Map.of("type", "asr_final", "text", userText));
                    // 保存用户消息
                    try {
                        if (ctx.groupId != null && ctx.groupId > 0 && ctx.userId != null && userText != null && !userText.isBlank()) {
                            chatHistoryService.saveUserMessage(ctx.groupId, ctx.userId, userText);
                        }
                    } catch (Exception e) {
                        log.warn("saveUserMessage failed groupId={}, userId={}, err={}", ctx.groupId, ctx.userId, e.getMessage());
                    }
                    if (ctx.autoReply) startLlmStreaming(session, ctx, userText);
                }
                // 等待LLM完成后再关闭会话
                // 不立即关闭，让客户端通过 asr_closed 消息决定何时关闭
                safeSendJson(session, Map.of("type", "asr_closed"));
            }
            default -> sendJson(session, Map.of("type", "error", "message", "unknown type"));
        }
    }

    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession session, @NonNull BinaryMessage message) {
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null) {
            log.warn("Binary message received but no session context found: {}", session.getId());
            return;
        }
        java.nio.ByteBuffer buf = message.getPayload();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        log.debug("Received binary audio data: {} bytes from session {}", data.length, session.getId());
        // 若检测到语音活动且当前正在流式或播放 TTS，进行打断（问题5）
        boolean active = ctx.isVoiceActive(data);
        if (active && (ctx.ttsBusy.get() || ctx.streaming)) {
            ctx.vadActiveFrames = active ? (ctx.vadActiveFrames + 1) : 0;
            long now = System.currentTimeMillis();
            if (ctx.vadActiveFrames >= ctx.vadRequiredFrames
                    && (now - ctx.lastBargeInAtMs) >= ctx.bargeInCooldownMs) {
                ctx.lastBargeInAtMs = now;
                ctx.handleUserInterruption();
                log.info("User interruption triggered during TTS/LLM streaming");
            }
        } else if (!active) {
            ctx.vadActiveFrames = 0;
        }
        // 优先走 ASR WS 实时流
        if (ctx.asrWsClient != null && ctx.asrWsClient.isOpened()) {
            try {
                ctx.asrWsClient.sendAudio(data);
            } catch (Exception e) {
                log.warn("Failed to send audio to ASR, will reconnect: {}", e.getMessage());
                // 尝试重新连接ASR
                ctx.closeAsr();
                ctx.startAsr(session);
                // 同时累积音频以备后用
                ctx.appendAudio(data);
            }
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
    private static boolean asBool(Object o, boolean dft) { try { return o == null ? dft : (o instanceof Boolean ? (Boolean) o : Boolean.parseBoolean(String.valueOf(o))); } catch (Exception e) { return dft; } }

    private void startLlmStreaming(WebSocketSession session, SessionCtx ctx, String text) {
        if (text == null || text.isBlank()) return;
        if (ctx.streaming) {
            // 正在流式，排队
            log.info("Already streaming, enqueue text: {}", text);
            ctx.enqueueUserText(text);
            return;
        }
        
        // 先保存用户消息（问题1：及时保存）
        try {
            if (ctx.groupId != null && ctx.groupId > 0 && ctx.userId != null && !text.isBlank()) {
                chatHistoryService.saveUserMessage(ctx.groupId, ctx.userId, text);
                log.info("User message saved immediately: {}", text);
            }
        } catch (Exception e) {
            log.warn("saveUserMessage failed in startLlmStreaming: {}", e.getMessage());
        }
        
        ctx.streaming = true;
        // 新一轮 AI 累积缓冲
        ctx.aiBuf = new StringBuilder(1024);
        // 若已有订阅，先取消
        try { if (ctx.llmSub != null && !ctx.llmSub.isDisposed()) ctx.llmSub.dispose(); } catch (Exception ignore) {}
        
        log.info("Starting LLM streaming for text: {}", text);
        ctx.llmSub = ctx.streamingService.chatStreamInXiYangYangRole(text)
                .doOnError(err -> {
                    log.error("LLM stream error", err);
                    safeSendJson(session, Map.of("type", "error", "message", "llm error: " + err.getMessage()));
                    // 将已生成的部分内容入库（符合"截断也入库"的要求）
                    ctx.saveAiIfBufferExists();
                    ctx.streaming = false;
                    // 清理当前订阅
                    try { if (ctx.llmSub != null && !ctx.llmSub.isDisposed()) ctx.llmSub.dispose(); } catch (Exception ignore) {}
                    ctx.llmSub = null;
                    // 继续下一个
                    ctx.drainLlm(session);
                })
                .doOnComplete(() -> {
                    log.info("LLM streaming completed");
                    String rest = ctx.chunker.flushRemainder();
                    if (rest != null && !rest.isEmpty()) {
                        ctx.enqueueTts(rest);
                        // 同步追加到 AI 累积
                        if (ctx.aiBuf != null) ctx.aiBuf.append(rest);
                    }
                    // 本轮完整生成完成，立即持久化 AI 消息（问题1：及时保存）
                    ctx.saveAiIfBufferExists();
                    ctx.streaming = false;
                    // 清理当前订阅
                    try { if (ctx.llmSub != null && !ctx.llmSub.isDisposed()) ctx.llmSub.dispose(); } catch (Exception ignore) {}
                    ctx.llmSub = null;
                    // 继续下一个
                    ctx.drainLlm(session);
                })
                .subscribe(token -> {
                    safeSendJson(session, Map.of("type", "llm_partial", "text", token));
                    if (ctx.aiBuf != null) ctx.aiBuf.append(token);
                    for (String sentence : ctx.chunker.appendAndExtract(token)) {
                        ctx.enqueueTts(sentence);
                    }
                });
    }

    @Data
    class SessionCtx {
        final WebSocketSession session;
        Long groupId = 0L;
        Long userId = null;
        String voiceType = "qiniu_zh_female_tmjxxy";
        Double speedRatio = 1.0;
        volatile boolean streaming = false;
        boolean autoReply = true;

        transient AiService streamingService;
        transient TextChunker chunker;
        transient QiniuTtsWsClient ttsClient;
        transient AsrService asrService;
        transient QiniuAsrWsClient asrWsClient;
        transient AutoCloseable ttsCancelHandle;
        transient Disposable llmSub;
        transient StringBuilder aiBuf;
        // VAD 与打断控制
        transient int vadActiveFrames = 0;                 // 连续被判定为“有声”的帧计数
        transient final int vadRequiredFrames = 2;         // 达到多少连续帧才认为用户开始说话
        transient long lastBargeInAtMs = 0;                // 上次触发打断的时间
        transient final long bargeInCooldownMs = 700;      // 连续打断之间的冷却期
        transient final int vadAbsThreshold = 600;         // 平均绝对幅度阈值（0~32767）

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

            this.ttsCancelHandle = ttsClient.synthesizeStream(next, voiceType, "mp3", speedRatio,
                    base64 -> safeSendJson(session, Map.of("type", "tts_chunk", "data", base64)),
                    () -> {
                        // 完成一个句子的 TTS
                        ttsQueue.poll();
                        ttsBusy.set(false);
                        this.ttsCancelHandle = null;
                        safeSendJson(session, Map.of("type", "tts_done"));
                        // 继续下一句
                        drainTts();
                    },
                    err -> {
                        ttsQueue.poll();
                        ttsBusy.set(false);
                        this.ttsCancelHandle = null;
                        safeSendJson(session, Map.of("type", "error", "message", "tts error: " + err.getMessage()));
                        drainTts();
                    });
        }

        void interruptTts(String reason) {
            try {
                if (ttsCancelHandle != null) {
                    ttsCancelHandle.close();
                }
            } catch (Exception ignore) {
            } finally {
                ttsCancelHandle = null;
            }
            ttsQueue.clear();
            ttsBusy.set(false);
            safeSendJson(session, Map.of("type", "tts_interrupted", "reason", reason));
            // 注意：不取消 LLM；若随后会话关闭，在 dispose 时会将已生成内容入库
        }
        
        // 用户打断时的完整处理（问题5）
        void handleUserInterruption() {
            log.info("User interruption detected");
            // 1. 停止TTS播放
            interruptTts("user_interruption");
            
            // 2. 保存当前已生成的AI内容
            saveAiIfBufferExists();
            
            // 3. 停止LLM流
            try {
                if (llmSub != null && !llmSub.isDisposed()) {
                    llmSub.dispose();
                }
            } catch (Exception ignore) {}
            llmSub = null;
            
            // 4. 重置状态
            streaming = false;
            
            // 5. 清空队列，准备接收新输入
            llmQueue.clear();
            
            // 6. 重置切句器
            chunker = new TextChunker(64);
        }

        void dispose() {
            // 停止 LLM 流
            try {
                if (llmSub != null && !llmSub.isDisposed()) {
                    llmSub.dispose();
                }
            } catch (Exception ignore) {}
            llmSub = null;
            // 在关闭会话前，将已生成但未保存的 AI 内容入库
            saveAiIfBufferExists();

            // 停止 TTS
            try {
                if (ttsCancelHandle != null) {
                    ttsCancelHandle.close();
                }
            } catch (Exception ignore) {}
            ttsCancelHandle = null;
            ttsQueue.clear();
            ttsBusy.set(false);

            // 关闭 ASR WS
            closeAsr();
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
                            // 保存用户消息（立即保存，问题1）
                            try {
                                if (groupId != null && groupId > 0 && userId != null && fin != null && !fin.isBlank()) {
                                    chatHistoryService.saveUserMessage(groupId, userId, fin);
                                    log.info("ASR final message saved: {}", fin);
                                }
                            } catch (Exception e) {
                                log.warn("saveUserMessage failed groupId={}, userId={}, err={}", groupId, userId, e.getMessage());
                            }
                            // 触发 LLM 流式（受 autoReply 控制）
                            // 防止重复触发：增强检查逻辑（问题2、3）
                            if (autoReply && !streaming) {
                                // 额外检查：如果LLM订阅存在且活跃，不要重复触发
                                if (llmSub == null || llmSub.isDisposed()) {
                                    log.info("Triggering LLM for ASR final: {}", fin);
                                    VoiceStreamHandler.this.startLlmStreaming(session, SessionCtx.this, fin);
                                } else {
                                    log.warn("Skipping LLM trigger, already active for: {}", fin);
                                }
                            }
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

        // 计算当前帧是否存在语音活动（简单能量阈值）
        boolean isVoiceActive(byte[] pcm) {
            if (pcm == null || pcm.length < 4) return false;
            int samples = pcm.length / 2;
            long sumAbs = 0;
            for (int i = 0; i + 1 < pcm.length; i += 2) {
                int lo = pcm[i] & 0xff;
                int hi = pcm[i + 1]; // already signed
                int val = (hi << 8) | lo; // little-endian to signed 16
                sumAbs += Math.abs(val);
            }
            int avgAbs = (int) (sumAbs / Math.max(1, samples));
            return avgAbs >= vadAbsThreshold;
        }

        // 将当前累积的 AI 输出入库（若存在且上下文可用）
        void saveAiIfBufferExists() {
            try {
                if (aiBuf != null && aiBuf.length() > 0 && groupId != null && groupId > 0 && userId != null) {
                    chatHistoryService.saveAiMessage(groupId, userId, aiBuf.toString());
                }
            } catch (Exception e) {
                log.warn("saveAiMessage failed groupId={}, userId={}, err={}", groupId, userId, e.getMessage());
            } finally {
                if (aiBuf != null) aiBuf.setLength(0);
            }
        }
    }
}
