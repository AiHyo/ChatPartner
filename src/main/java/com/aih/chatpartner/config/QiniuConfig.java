package com.aih.chatpartner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 七牛云配置类
 *
 * @author AiHyo
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "qiniu")
public class QiniuConfig {
    
    /**
     * API密钥
     */
    private String apiKey;
    
    /**
     * API基础URL
     */
    private String baseUrl = "https://openai.qiniu.com/v1";
    
    /**
     * WebSocket URL for ASR
     */
    private String asrWsUrl = "wss://openai.qiniu.com/v1/voice/asr";
    
    /**
     * WebSocket URL for TTS
     */
    private String ttsWsUrl = "wss://openai.qiniu.com/v1/voice/tts";
    
    /**
     * ASR默认配置
     */
    private AsrConfig asr = new AsrConfig();
    
    /**
     * TTS默认配置
     */
    private TtsConfig tts = new TtsConfig();
    
    @Data
    public static class AsrConfig {
        /**
         * 采样率
         */
        private Integer sampleRate = 16000;
        
        /**
         * 声道数
         */
        private Integer channels = 1;
        
        /**
         * 音频格式
         */
        private String format = "raw";
        
        /**
         * 是否分离扬声器
         */
        private Boolean speakerDiarization = false;
        
        /**
         * 模型
         */
        private String model = "paraformer-realtime-v2";
        
        /**
         * 延迟策略
         */
        private String latency = "normal";
        
        /**
         * 默认提示词
         */
        private String prompt = "";
    }
    
    @Data
    public static class TtsConfig {
        /**
         * 默认音色
         */
        private String voiceType = "female-tianmei";
        
        /**
         * 音频编码
         */
        private String encoding = "pcm";
        
        /**
         * 采样率
         */
        private Integer sampleRate = 16000;
        
        /**
         * 语速比例
         */
        private Double speedRatio = 1.0;
    }
}
