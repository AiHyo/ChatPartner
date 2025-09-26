package com.aih.chatpartner.service.voice;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
 

/**
 * 语音识别服务（ASR - Automatic Speech Recognition）
 * 基于七牛云 REST API 实现
 *
 * @author AiHyo
 */
@Slf4j
@Service
public class AsrService {
    
    @Resource
    private QiniuConfig qiniuConfig;
    
    @Resource
    private ObjectMapper objectMapper;
    
    /**
     * 七牛云对象存储配置（需要在生产环境中配置）
     * TODO: 在生产环境中配置七牛云对象存储，用于上传音频文件获取公网URL
     */
    
    /**
     * 将音频转换为文本（通过URL）
     *
     * @param audioUrl 音频文件的公网URL
     * @param format 音频格式（mp3, wav, ogg, raw等）
     * @return 识别出的文本
     */
    public String speechToTextByUrl(String audioUrl, String format) {
        try {
            // 构建请求URL
            String apiUrl = qiniuConfig.getBaseUrl() + "/voice/asr";
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "asr");
            
            Map<String, String> audio = new HashMap<>();
            audio.put("format", format);
            audio.put("url", audioUrl);
            requestBody.put("audio", audio);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("ASR请求体: {}", jsonBody);
            
            // 发送HTTP请求
            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Authorization", "Bearer " + qiniuConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .timeout(30000) // 30秒超时
                    .execute();
            
            if (!response.isOk()) {
                log.error("ASR请求失败，状态码: {}, 响应: {}", response.getStatus(), response.body());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "语音识别失败: HTTP " + response.getStatus());
            }
            
            // 解析响应
            String responseBody = response.body();
            log.debug("ASR响应: {}", responseBody);
            
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            // 提取识别文本
            if (jsonResponse.has("data") && 
                jsonResponse.get("data").has("result") && 
                jsonResponse.get("data").get("result").has("text")) {
                
                String text = jsonResponse.get("data").get("result").get("text").asText();
                log.info("ASR识别成功: {}", text);
                return text;
            }
            
            log.error("ASR响应格式错误: {}", responseBody);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "语音识别失败: 响应格式错误");
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("语音识别异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "语音识别失败: " + e.getMessage());
        }
    }
    
    /**
     * 将音频数据转换为文本
     * 由于七牛云ASR API需要音频URL，所以需要先上传音频文件
     *
     * @param audioData 音频数据
     * @param format 音频格式（mp3, wav, ogg, raw等）
     * @return 识别出的文本
     */
    public String speechToText(byte[] audioData, String format) {
        if (audioData == null || audioData.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "音频数据不能为空");
        }
        
        // 在生产环境中，应该上传到对象存储服务获取公网URL
        // 目前作为临时解决方案，仅用于演示
        
        // 创建临时文件
        File tempFile = null;
        try {
            // 生成临时文件
            tempFile = File.createTempFile("asr_", "." + format);
            
            // 写入音频数据
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(audioData);
            }
            
            // TODO: 生产环境中，这里应该上传到七牛云存储并获取公网URL
            // 示例代码：String audioUrl = uploadToQiniu(tempFile);
            // return speechToTextByUrl(audioUrl, format);
            
            log.warn("ASR功能需要音频文件的公网URL，请先配置七牛云对象存储服务");
            
            // 目前仅为演示，返回模拟结果
            return "【演示模式】ASR功能需要配置七牛云存储以获取音频URL";
            
        } catch (Exception e) {
            log.error("处理音频文件失败", e);
            // 暂时返回模拟结果以便演示
            return "【演示模式】处理音频文件时出现错误";
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * 将音频数据转换为文本（使用默认格式PCM）
     *
     * @param audioData 音频数据
     * @return 识别出的文本
     */
    public String speechToText(byte[] audioData) {
        return speechToText(audioData, "raw");
    }
    
    /**
     * 将音频文件转换为文本
     *
     * @param audioFile 音频文件
     * @return 识别出的文本
     */
    public String speechToText(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "音频文件不能为空");
        }
        
        try {
            // 获取文件格式
            String originalFilename = audioFile.getOriginalFilename();
            String format = "raw";
            if (originalFilename != null && originalFilename.contains(".")) {
                format = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            }
            
            // 转换为文本
            return speechToText(audioFile.getBytes(), format);
            
        } catch (IOException e) {
            log.error("读取音频文件失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取音频文件失败: " + e.getMessage());
        }
    }
    
    /**
     * ASR响应数据结构
     */
    @Data
    public static class AsrResponse {
        private String reqid;
        private String operation;
        private AsrData data;
    }
    
    @Data
    public static class AsrData {
        private AudioInfo audioInfo;
        private AsrResult result;
    }
    
    @Data
    public static class AudioInfo {
        private Integer duration;
    }
    
    @Data
    public static class AsrResult {
        private Map<String, String> additions;
        private String text;
    }
}
