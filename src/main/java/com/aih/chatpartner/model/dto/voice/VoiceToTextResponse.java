package com.aih.chatpartner.model.dto.voice;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 语音转文本响应
 *
 * @author AiHyo
 */
@Data
@Schema(description = "语音转文本响应")
public class VoiceToTextResponse {
    
    @Schema(description = "识别出的文本", example = "你好，我需要帮助")
    private String text;
}
