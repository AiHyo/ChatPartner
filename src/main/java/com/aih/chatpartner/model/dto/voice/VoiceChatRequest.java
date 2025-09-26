package com.aih.chatpartner.model.dto.voice;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 语音聊天请求（Base64格式）
 *
 * @author AiHyo
 */
@Data
@Schema(description = "语音聊天请求")
public class VoiceChatRequest {
    
    @Schema(description = "音频数据(Base64编码)", required = true)
    private String audioBase64;
    
    @Schema(description = "聊天分组ID", required = true)
    private Long groupId;
    
    @Schema(description = "TTS音色类型", example = "female-tianmei")
    private String voiceType;
    
    @Schema(description = "TTS语速比例(0.5-2.0)", example = "1.0")
    private Double speedRatio;
    
    @Schema(description = "音频格式(mp3, wav, ogg, raw等)", example = "mp3")
    private String audioFormat;
}
