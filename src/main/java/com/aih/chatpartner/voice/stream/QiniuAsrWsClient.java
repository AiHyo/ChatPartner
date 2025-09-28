package com.aih.chatpartner.voice.stream;

import com.aih.chatpartner.config.QiniuConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Qiniu ASR WebSocket client for real-time speech recognition.
 */
@Slf4j
public class QiniuAsrWsClient {

    private final QiniuConfig qiniuConfig;
    private final ObjectMapper objectMapper;

    private WebSocketClient client;
    private volatile boolean opened = false;
    private int seq = 1;
    private final String uid = UUID.randomUUID().toString();
    
    // 回调函数
    private Consumer<String> onPartial;
    private Consumer<String> onFinal;
    private Consumer<Throwable> onError;
    private Runnable onClosed;
    
    // 去重相关
    private String lastPartialText = "";
    private String lastFinalText = "";
    // 更稳健的最终结果去重：记录最后一个 definite=true 的 utterance 的 end_time 与 log_id
    private long lastFinalEndTime = -1L;
    private String lastFinalLogId = "";

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
            // 保存回调函数
            this.onPartial = onPartial;
            this.onFinal = onFinal;
            this.onError = onError;
            this.onClosed = onClosed;
            
            // 重置去重状态
            this.lastPartialText = "";
            this.lastFinalText = "";
            this.lastFinalEndTime = -1L;
            this.lastFinalLogId = "";
            
            String url = qiniuConfig.getAsrWsUrl();
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + qiniuConfig.getApiKey());

            client = new WebSocketClient(new URI(url), new Draft_6455(), headers, 10000) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    opened = true;
                    log.info("ASR WS connected, handshake: {}", handshakedata.getHttpStatus());
                    try {
                        // 按照七牛云协议发送配置信息
                        sendAsrConfig(format, sampleRate, channels, modelName, enablePunc);
                        log.info("ASR config sent successfully");
                    } catch (Exception e) {
                        log.error("Send ASR config error", e);
                        if (onError != null) onError.accept(e);
                    }
                }

                @Override
                public void onMessage(String message) {
                    // 七牛云ASR使用二进制协议，不应该收到文本消息
                    log.warn("Unexpected text message from ASR: {}", message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    try {
                        // Safely copy the readable segment instead of using bytes.array()
                        byte[] arr;
                        if (bytes.hasArray() && bytes.arrayOffset() == 0 && bytes.remaining() == bytes.array().length) {
                            // zero-copy path only when the visible range equals the underlying array length
                            arr = bytes.array();
                        } else {
                            ByteBuffer dup = bytes.slice();
                            arr = new byte[dup.remaining()];
                            dup.get(arr);
                        }
                        parseAsrResponse(arr);
                    } catch (Exception e) {
                        log.error("Parse ASR binary response error", e);
                        if (onError != null) onError.accept(e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    opened = false;
                    log.info("ASR WS closed: {} {} remote={}", code, reason, remote);
                    if (QiniuAsrWsClient.this.onClosed != null) QiniuAsrWsClient.this.onClosed.run();
                }

                @Override
                public void onError(Exception ex) {
                    log.error("ASR WS error", ex);
                    if (QiniuAsrWsClient.this.onError != null) QiniuAsrWsClient.this.onError.accept(ex);
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
                // 按照七牛云协议发送音频数据
                sendAudioChunk(data);
                log.debug("ASR sent audio data: {} bytes", data.length);
            } else {
                log.warn("ASR sendAudio skipped: client={}, opened={}, data length={}",
                    client != null, opened, data != null ? data.length : 0);
            }
        } catch (Exception e) {
            log.warn("ASR sendAudio error", e);
        }
    }

    public void end() { tryClose(); }

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

    /**
     * 生成协议头
     */
    private byte[] generateHeader(int messageType, int flags, int serialMethod, int compression) {
        byte[] header = new byte[4];
        header[0] = (byte) ((0x01 << 4) | 0x01); // protocol version + header size
        header[1] = (byte) ((messageType << 4) | flags);
        header[2] = (byte) ((serialMethod << 4) | compression);
        header[3] = 0x00; // reserved
        return header;
    }

    /**
     * 生成序列号部分
     */
    private byte[] generateSequence(int sequence) {
        byte[] seqBytes = new byte[4];
        seqBytes[0] = (byte) (sequence >>> 24);
        seqBytes[1] = (byte) (sequence >>> 16);
        seqBytes[2] = (byte) (sequence >>> 8);
        seqBytes[3] = (byte) sequence;
        return seqBytes;
    }

    /**
     * GZIP压缩
     */
    private byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data);
        }
        return baos.toByteArray();
    }

    /**
     * GZIP解压缩
     */
    private byte[] gzipDecompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzipIn = new GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }

    /**
     * 发送ASR配置信息
     */
    private void sendAsrConfig(String format, int sampleRate, int channels, String modelName, boolean enablePunc) throws Exception {
        // 构建配置JSON
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        config.put("user", user);
        
        Map<String, Object> audio = new HashMap<>();
        audio.put("format", "pcm");
        audio.put("sample_rate", sampleRate);
        audio.put("bits", 16);
        audio.put("channel", channels);
        audio.put("codec", "raw");
        config.put("audio", audio);
        
        Map<String, Object> request = new HashMap<>();
        // 按传入参数设置模型名称（例如 paraformer-realtime-v2）
        request.put("model_name", modelName);
        request.put("enable_punc", enablePunc);
        config.put("request", request);
        
        String jsonPayload = objectMapper.writeValueAsString(config);
        log.info("ASR config payload: {}", jsonPayload);
        
        // 压缩payload
        byte[] compressedPayload = gzipCompress(jsonPayload.getBytes("UTF-8"));
        
        // 构建完整消息
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 协议头：FULL_CLIENT_REQUEST (0x01) + POS_SEQUENCE (0x01) + JSON (0x01) + GZIP (0x01)
        baos.write(generateHeader(0x01, 0x01, 0x01, 0x01));
        // 序列号
        baos.write(generateSequence(seq));
        // payload长度
        byte[] lengthBytes = new byte[4];
        int payloadLength = compressedPayload.length;
        lengthBytes[0] = (byte) (payloadLength >>> 24);
        lengthBytes[1] = (byte) (payloadLength >>> 16);
        lengthBytes[2] = (byte) (payloadLength >>> 8);
        lengthBytes[3] = (byte) payloadLength;
        baos.write(lengthBytes);
        // payload
        baos.write(compressedPayload);
        
        client.send(baos.toByteArray());
        log.info("ASR config sent, total bytes: {}, payload bytes: {}", baos.size(), compressedPayload.length);
    }

    /**
     * 发送音频数据块
     */
    private void sendAudioChunk(byte[] audioData) throws Exception {
        seq++;
        
        // 压缩音频数据
        byte[] compressedAudio = gzipCompress(audioData);
        
        // 构建完整消息
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 协议头：AUDIO_ONLY_REQUEST (0x02) + POS_SEQUENCE (0x01) + JSON (0x01) + GZIP (0x01)
        baos.write(generateHeader(0x02, 0x01, 0x01, 0x01));
        // 序列号
        baos.write(generateSequence(seq));
        // payload长度
        byte[] lengthBytes = new byte[4];
        int payloadLength = compressedAudio.length;
        lengthBytes[0] = (byte) (payloadLength >>> 24);
        lengthBytes[1] = (byte) (payloadLength >>> 16);
        lengthBytes[2] = (byte) (payloadLength >>> 8);
        lengthBytes[3] = (byte) payloadLength;
        baos.write(lengthBytes);
        // payload
        baos.write(compressedAudio);
        
        client.send(baos.toByteArray());
    }

    /**
     * 解析ASR响应
     */
    private void parseAsrResponse(byte[] data) throws Exception {
        if (data.length < 4) {
            log.warn("ASR response too short: {} bytes", data.length);
            return;
        }
        
        // 解析协议头
        int headerSize = data[0] & 0x0f;
        int messageType = (data[1] >>> 4) & 0x0f;
        int flags = data[1] & 0x0f;
        int serialMethod = (data[2] >>> 4) & 0x0f;
        int compression = data[2] & 0x0f;
        
        log.debug("ASR response: headerSize={}, messageType={}, flags={}, serialMethod={}, compression={}", 
                headerSize, messageType, flags, serialMethod, compression);
        
        // 跳过协议头
        int offset = headerSize * 4;
        
        // 如果有序列号，跳过4字节
        if ((flags & 0x01) != 0) {
            offset += 4;
        }
        
        // 读取payload
        byte[] payload = null;
        if (messageType == 0x09) { // FULL_SERVER_RESPONSE
            if (offset + 4 <= data.length) {
                // 读取payload长度
                int payloadLength = ((data[offset] & 0xff) << 24) |
                                  ((data[offset + 1] & 0xff) << 16) |
                                  ((data[offset + 2] & 0xff) << 8) |
                                  (data[offset + 3] & 0xff);
                offset += 4;
                
                if (offset + payloadLength <= data.length) {
                    payload = new byte[payloadLength];
                    System.arraycopy(data, offset, payload, 0, payloadLength);
                }
            }
        } else {
            // 其他消息类型，读取剩余所有数据作为payload
            if (offset < data.length) {
                int remaining = data.length - offset;
                payload = new byte[remaining];
                System.arraycopy(data, offset, payload, 0, remaining);
            }
        }
        
        if (payload == null || payload.length == 0) {
            log.debug("ASR response has no payload");
            return;
        }
        
        // 解压缩
        if (compression == 0x01) { // GZIP
            try {
                payload = gzipDecompress(payload);
            } catch (Exception e) {
                log.warn("Failed to decompress ASR payload", e);
                return;
            }
        }
        
        // 反序列化
        if (serialMethod == 0x01) { // JSON
            try {
                String jsonStr = new String(payload, "UTF-8");
                log.debug("ASR response JSON: {}", jsonStr);

                JsonNode node = objectMapper.readTree(jsonStr);
                JsonNode resultNode = node.has("result") ? node.get("result") : null;
                String text = (resultNode != null && resultNode.has("text")) ? resultNode.get("text").asText("") : null;

                if (text != null && !text.isEmpty()) {
                    log.debug("ASR recognized text: '{}'", text);

                    // 解析 log_id（若存在）
                    String logId = "";
                    if (resultNode != null && resultNode.has("additions") && resultNode.get("additions").has("log_id")) {
                        logId = resultNode.get("additions").get("log_id").asText("");
                    }

                    // 解析 utterances，寻找 definite=true 的最大 end_time
                    long maxFinalEnd = -1L;
                    boolean hasUtterances = false;
                    if (resultNode != null && resultNode.has("utterances") && resultNode.get("utterances").isArray()) {
                        hasUtterances = resultNode.get("utterances").size() > 0;
                        for (JsonNode u : resultNode.get("utterances")) {
                            boolean definite = u.has("definite") && u.get("definite").asBoolean(false);
                            if (definite) {
                                long endTime = u.has("end_time") ? u.get("end_time").asLong(-1L) : -1L;
                                if (endTime > maxFinalEnd) maxFinalEnd = endTime;
                            }
                        }
                    }

                    boolean hasIsFinalFlag = (resultNode != null && resultNode.has("is_final")) && resultNode.get("is_final").asBoolean(false);
                    boolean hasTypeFinal = (resultNode != null && resultNode.has("type")) && "final".equals(resultNode.get("type").asText(""));

                    // 仅在没有任何机器信号时才使用“标点兜底”
                    boolean isFinal = (maxFinalEnd >= 0) || hasIsFinalFlag || hasTypeFinal;
                    if (!isFinal && !hasUtterances && !hasIsFinalFlag && !hasTypeFinal) {
                        isFinal = text.endsWith("。") || text.endsWith("！") || text.endsWith("？") ||
                                text.endsWith(".") || text.endsWith("!") || text.endsWith("?");
                    }

                    // 根据结果类型调用相应的回调，并进行去重
                    if (isFinal) {
                        // 更稳健的去重逻辑：同一 logId 情况下，若 end_time 增大，视为新的最终话语
                        boolean sameLog = !logId.isEmpty() && logId.equals(this.lastFinalLogId);
                        boolean newerByEndTime = (maxFinalEnd >= 0) && (maxFinalEnd > this.lastFinalEndTime);

                        if (sameLog && !newerByEndTime) {
                            // 同一 logId 且没有更大的 end_time，当作重复确认
                            this.lastFinalEndTime = Math.max(this.lastFinalEndTime, maxFinalEnd);
                            log.debug("Duplicate ASR final ignored by logId: '{}' (logId={}, endTime={})", text, logId, maxFinalEnd);
                        } else if (!sameLog && logId.isEmpty() && text.equals(this.lastFinalText) && !newerByEndTime) {
                            // 无 logId 时退化到文本去重，同时参考 end_time
                            this.lastFinalEndTime = Math.max(this.lastFinalEndTime, maxFinalEnd);
                            log.debug("Duplicate ASR final ignored by text: '{}' (endTime={})", text, maxFinalEnd);
                        } else {
                            log.info("ASR final result: '{}' (logId={}, endTime={})", text, logId, maxFinalEnd);
                            this.lastFinalText = text;
                            this.lastFinalEndTime = (maxFinalEnd >= 0) ? maxFinalEnd : this.lastFinalEndTime;
                            this.lastFinalLogId = logId;
                            if (this.onFinal != null) this.onFinal.accept(text);
                        }
                    } else {
                        // 部分结果：按文本去重
                        if (!text.equals(this.lastPartialText)) {
                            log.info("ASR partial result: '{}'", text);
                            this.lastPartialText = text;
                            if (this.onPartial != null) this.onPartial.accept(text);
                        } else {
                            log.debug("Duplicate ASR partial ignored: '{}'", text);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse ASR JSON response", e);
            }
        } else {
            String textResponse = new String(payload, "UTF-8");
            log.info("ASR text response: {}", textResponse);
        }
    }
}
