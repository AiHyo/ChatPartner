package com.aih.chatpartner.voice.stream;

import com.aih.chatpartner.config.QiniuConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.Closeable;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 七牛云 TTS WebSocket 客户端（流式）
 */
@Slf4j
public class QiniuTtsWsClient {

    private final QiniuConfig qiniuConfig;
    private final ObjectMapper objectMapper;

    public QiniuTtsWsClient(QiniuConfig qiniuConfig, ObjectMapper objectMapper) {
        this.qiniuConfig = qiniuConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 以流式方式将一段文本合成为音频，并通过回调返回 base64 音频分片
     * @return 返回可用于取消操作的 Closeable 对象
     */
    public Closeable synthesizeStream(String text,
                                      String voiceType,
                                      String encoding,
                                      double speedRatio,
                                      Consumer<String> onAudioBase64Chunk,
                                      Runnable onDone,
                                      Consumer<Throwable> onError) {
        try {
            String url = qiniuConfig.getTtsWsUrl();
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + qiniuConfig.getApiKey());
            // 某些实现需要在握手头中附带 VoiceType，参考官方示例
            headers.put("VoiceType", voiceType);

            AtomicBoolean finished = new AtomicBoolean(false);
            // 追踪握手是否成功与是否收到任何音频数据
            AtomicBoolean connected = new AtomicBoolean(false);
            AtomicBoolean receivedAnyData = new AtomicBoolean(false);

            WebSocketClient client = new WebSocketClient(new URI(url), new Draft_6455(), headers, 10000) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("TTS WS connected");
                    connected.set(true);
                    try {
                        Map<String, Object> root = new HashMap<>();
                        Map<String, Object> audio = new HashMap<>();
                        audio.put("voice_type", voiceType);
                        audio.put("encoding", encoding);
                        audio.put("speed_ratio", speedRatio);
                        root.put("audio", audio);
                        Map<String, Object> request = new HashMap<>();
                        request.put("text", text);
                        root.put("request", request);

                        String payload = objectMapper.writeValueAsString(root);
                        send(payload.getBytes(StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        log.error("Send TTS request error", e);
                        tryClose();
                        if (onError != null) onError.accept(e);
                    }
                }

                private void handleJsonMessage(String message) {
                    try {
                        JsonNode node = objectMapper.readTree(message);
                        if (node.has("data")) {
                            String base64 = node.get("data").asText("");
                            if (!base64.isEmpty() && onAudioBase64Chunk != null) {
                                receivedAnyData.set(true);
                                onAudioBase64Chunk.accept(base64);
                            }
                        }
                        if (node.has("sequence")) {
                            int seq = node.get("sequence").asInt(0);
                            if (seq < 0) {
                                if (finished.compareAndSet(false, true)) {
                                    if (onDone != null) onDone.run();
                                }
                                tryClose();
                            }
                        }
                    } catch (Exception e) {
                        log.error("Parse TTS message error", e);
                        if (onError != null) onError.accept(e);
                    }
                }

                @Override
                public void onMessage(String message) { handleJsonMessage(message); }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    // Some servers may send JSON in binary frames
                    String message = StandardCharsets.UTF_8.decode(bytes).toString();
                    handleJsonMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("TTS WS closed: {} {} remote={} ", code, reason, remote);
                    // 区分握手失败/无数据 与 正常结束
                    if (!connected.get()) {
                        if (onError != null) onError.accept(new RuntimeException("TTS WS handshake failed: " + code + " " + reason));
                        return;
                    }
                    if (!receivedAnyData.get()) {
                        if (onError != null) onError.accept(new RuntimeException("TTS WS closed without data: " + code + " " + reason));
                        return;
                    }
                    // 若正常结束但未给出 sequence<0，兜底触发完成
                    if (finished.compareAndSet(false, true)) {
                        if (onDone != null) onDone.run();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    log.error("TTS WS error", ex);
                    if (onError != null) onError.accept(ex);
                    // Also mark finished to avoid blocking the queue
                    finished.compareAndSet(false, true);
                }

                private void tryClose() {
                    try {
                        close();
                    } catch (Exception ignore) {
                    }
                }
            };
            client.connect();
            // 返回一个 Closeable 包装器，用于取消 TTS 操作
            return () -> {
                try {
                    if (client.isOpen()) {
                        client.close();
                    }
                } catch (Exception ignore) {
                    log.debug("Error closing TTS client", ignore);
                }
            };
        } catch (Exception e) {
            log.error("Create TTS WS error", e);
            if (onError != null) onError.accept(e);
            return () -> {}; // 返回空的 Closeable
        }
    }
}
