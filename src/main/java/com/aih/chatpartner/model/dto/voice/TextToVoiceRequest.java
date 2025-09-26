package com.aih.chatpartner.model.dto.voice;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文本转语音请求
 *
 * @author AiHyo
 */
@Data
@Schema(description = "文本转语音请求")
public class TextToVoiceRequest {
    
    @Schema(description = "要转换的文本", required = true, example = "你好，我是AI助手")
    private String text;
    
    @Schema(description = "音色类型", example = "female-tianmei")
    private String voiceType;
    
    @Schema(description = "语速比例(0.5-2.0)", example = "1.0")
    private Double speedRatio;
}
