package com.aih.chatpartner.voice.stream;

import com.aih.chatpartner.config.QiniuConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Qiniu ASR WebSocket client for real-time speech recognition.
 */
@Slf4j
public class QiniuAsrWsClient {

    private final QiniuConfig qiniuConfig;
    private final ObjectMapper objectMapper;

    private WebSocketClient client;
    private volatile boolean opened = false;

    public QiniuAsrWsClient(QiniuConfig qiniuConfig, ObjectMapper objectMapper) {
        this.qiniuConfig = qiniuConfig;
        this.objectMapper = objectMapper;
    }

    public void connect(String format,
                        int sampleRate,
                        int channels,
                        String modelName,
                        boolean enablePunc,
                        Consumer<String> onPartial,
                        Consumer<String> onFinal,
                        Consumer<Throwable> onError,
                        Runnable onClosed) {
        try {
            String url = qiniuConfig.getAsrWsUrl();
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + qiniuConfig.getApiKey());

            client = new WebSocketClient(new URI(url), new Draft_6455(), headers, 10000) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    opened = true;
                    log.info("ASR WS connected");
                    try {
                        Map<String, Object> root = new HashMap<>();
                        Map<String, Object> audio = new HashMap<>();
                        audio.put("format", format);
                        audio.put("sample_rate", sampleRate);
                        audio.put("channels", channels);
                        root.put("audio", audio);
                        Map<String, Object> request = new HashMap<>();
                        request.put("model_name", modelName);
                        request.put("enable_punc", enablePunc);
                        root.put("request", request);
                        String payload = objectMapper.writeValueAsString(root);
                        send(payload.getBytes(StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        log.error("Send ASR config error", e);
                        if (onError != null) onError.accept(e);
                    }
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode node = objectMapper.readTree(message);
                        // Try to extract recognized text
                        String text = null;
                        if (node.has("data") && node.get("data").has("result") && node.get("data").get("result").has("text")) {
                            text = node.get("data").get("result").get("text").asText("");
                        }
                        boolean isEnd = false;
                        if (node.has("operation")) {
                            String op = node.get("operation").asText("");
                            if ("end".equalsIgnoreCase(op)) {
                                isEnd = true;
                            }
                        }
                        if (text != null && !text.isEmpty()) {
                            if (isEnd) {
                                if (onFinal != null) onFinal.accept(text);
                            } else {
                                if (onPartial != null) onPartial.accept(text);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Parse ASR message error", e);
                        if (onError != null) onError.accept(e);
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    // Some providers may send binary frames; ignore or log
                    log.debug("ASR WS binary frame: {} bytes", bytes.remaining());
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    opened = false;
                    log.info("ASR WS closed: {} {} remote={}", code, reason, remote);
                    if (onClosed != null) onClosed.run();
                }

                @Override
                public void onError(Exception ex) {
                    log.error("ASR WS error", ex);
                    if (onError != null) onError.accept(ex);
                }
            };
            client.connect();
        } catch (Exception e) {
            log.error("Create ASR WS error", e);
            if (onError != null) onError.accept(e);
        }
    }

    public boolean isOpened() { return opened; }

    public void sendAudio(byte[] data) {
        try {
            if (client != null && opened && data != null && data.length > 0) {
                client.send(data);
            }
        } catch (Exception e) {
            log.warn("ASR sendAudio error", e);
        }
    }

    public void end() {
        tryClose();
    }

    public void close() {
        tryClose();
    }

    private void tryClose() {
        try {
            if (client != null) client.close();
        } catch (Exception ignore) {
        }
        opened = false;
    }
}
