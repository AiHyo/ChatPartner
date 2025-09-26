package com.aih.chatpartner.service.voice;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.aih.chatpartner.config.QiniuConfig;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 文本转语音服务（TTS - Text To Speech）
 * 基于七牛云 REST API 实现
 *
 * @author AiHyo
 */
@Slf4j
@Service
public class TtsService {
    
    @Resource
    private QiniuConfig qiniuConfig;
    
    @Resource
    private ObjectMapper objectMapper;
    
    /**
     * 获取可用的音色列表
     *
     * @return 音色列表
     */
    public List<VoiceInfo> getVoiceList() {
        try {
            // 构建请求URL
            String apiUrl = qiniuConfig.getBaseUrl() + "/voice/list";
            
            // 发送HTTP请求
            HttpResponse response = HttpRequest.get(apiUrl)
                    .header("Authorization", "Bearer " + qiniuConfig.getApiKey())
                    .timeout(10000) // 10秒超时
                    .execute();
            
            if (!response.isOk()) {
                log.error("获取音色列表失败，状态码: {}, 响应: {}", response.getStatus(), response.body());
                // 返回默认音色列表
                return getDefaultVoiceList();
            }
            
            // 解析响应
            String responseBody = response.body();
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            List<VoiceInfo> voices = new ArrayList<>();
            if (jsonResponse.isArray()) {
                for (JsonNode voiceNode : jsonResponse) {
                    String voiceType = voiceNode.has("voice_type") ? voiceNode.get("voice_type").asText() : "";
                    String voiceName = voiceNode.has("voice_name") ? voiceNode.get("voice_name").asText() : "";
                    String category = voiceNode.has("category") ? voiceNode.get("category").asText() : "";
                    String url = voiceNode.has("url") ? voiceNode.get("url").asText() : "";
                    
                    voices.add(new VoiceInfo(voiceType, voiceName, category, url));
                }
            }
            
            log.info("成功获取{}个音色", voices.size());
            return voices.isEmpty() ? getDefaultVoiceList() : voices;
            
        } catch (Exception e) {
            log.error("获取音色列表异常", e);
            // 返回默认音色列表
            return getDefaultVoiceList();
        }
    }
    
    /**
     * 获取默认音色列表
     */
    private List<VoiceInfo> getDefaultVoiceList() {
        List<VoiceInfo> voices = new ArrayList<>();
        
        // 根据文档添加默认音色
        voices.add(new VoiceInfo("qiniu_zh_female_tmjxxy", "甜美教学小源", "传统音色", ""));
        voices.add(new VoiceInfo("qiniu_zh_female_wwxkjx", "温柔小客教学", "传统音色", ""));
        voices.add(new VoiceInfo("qiniu_zh_male_ywxdkf", "阳光小达开放", "传统音色", ""));
        voices.add(new VoiceInfo("qiniu_zh_male_cgxksc", "沉稳小康市场", "传统音色", ""));
        
        return voices;
    }
    
    /**
     * 将文本转换为语音（使用默认音色）
     *
     * @param text 要转换的文本
     * @return 音频数据（Base64编码的MP3格式）
     */
    public byte[] textToSpeech(String text) {
        return textToSpeech(text, null, null, "mp3");
    }
    
    /**
     * 将文本转换为语音
     *
     * @param text 要转换的文本
     * @param voiceType 音色类型（可选）
     * @param speedRatio 语速比例（可选，0.5-2.0）
     * @param encoding 音频编码格式（mp3, wav等）
     * @return 音频数据
     */
    public byte[] textToSpeech(String text, String voiceType, Double speedRatio, String encoding) {
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文本不能为空");
        }
        
        // 使用默认值
        if (voiceType == null || voiceType.isEmpty()) {
            voiceType = "qiniu_zh_female_tmjxxy"; // 使用七牛云格式的音色ID
        }
        if (speedRatio == null) {
            speedRatio = qiniuConfig.getTts().getSpeedRatio();
        }
        if (encoding == null || encoding.isEmpty()) {
            encoding = "mp3"; // 默认使用mp3格式
        }
        
        // 验证语速范围
        if (speedRatio < 0.5 || speedRatio > 2.0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "语速比例必须在0.5-2.0之间");
        }
        
        try {
            // 构建请求URL
            String apiUrl = qiniuConfig.getBaseUrl() + "/voice/tts";
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            
            Map<String, Object> audio = new HashMap<>();
            audio.put("voice_type", voiceType);
            audio.put("encoding", encoding);
            audio.put("speed_ratio", speedRatio);
            requestBody.put("audio", audio);
            
            Map<String, String> request = new HashMap<>();
            request.put("text", text);
            requestBody.put("request", request);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("TTS请求体: {}", jsonBody);
            
            // 发送HTTP请求
            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Authorization", "Bearer " + qiniuConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .timeout(60000) // 60秒超时
                    .execute();
            
            if (!response.isOk()) {
                log.error("TTS请求失败，状态码: {}, 响应: {}", response.getStatus(), response.body());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文本转语音失败: HTTP " + response.getStatus());
            }
            
            // 解析响应
            String responseBody = response.body();
            log.debug("TTS响应: {}", responseBody);
            
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            // 提取音频数据（Base64编码）
            if (jsonResponse.has("data")) {
                String audioBase64 = jsonResponse.get("data").asText();
                
                // 解码Base64
                byte[] audioData = Base64.decode(audioBase64);
                
                // 获取音频时长信息（如果有）
                if (jsonResponse.has("addition") && 
                    jsonResponse.get("addition").has("duration")) {
                    String duration = jsonResponse.get("addition").get("duration").asText();
                    log.info("TTS转换成功，音频时长: {}ms, 大小: {} bytes", duration, audioData.length);
                } else {
                    log.info("TTS转换成功，音频大小: {} bytes", audioData.length);
                }
                
                return audioData;
            }
            
            log.error("TTS响应格式错误: {}", responseBody);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文本转语音失败: 响应格式错误");
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文本转语音异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文本转语音失败: " + e.getMessage());
        }
    }
    
    /**
     * 将文本转换为语音（使用默认参数）
     */
    public byte[] textToSpeech(String text, String voiceType, Double speedRatio) {
        return textToSpeech(text, voiceType, speedRatio, "mp3");
    }
    
    /**
     * TTS响应数据结构
     */
    @Data
    public static class TtsResponse {
        private String reqid;
        private String operation;
        private Integer sequence;
        private String data;
        private TtsAddition addition;
    }
    
    @Data
    public static class TtsAddition {
        private String duration;
    }
    
    /**
     * 音色信息
     */
    @Data
    public static class VoiceInfo {
        private String voiceType;  // 音色ID
        private String voiceName;  // 音色名称
        private String category;   // 音色分类
        private String url;        // 试听URL
        
        public VoiceInfo(String voiceType, String voiceName, String category, String url) {
            this.voiceType = voiceType;
            this.voiceName = voiceName;
            this.category = category;
            this.url = url;
        }
    }
}
