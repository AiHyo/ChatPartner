package com.aih.chatpartner.service;

import com.aih.chatpartner.ai.AiService;
import com.aih.chatpartner.ai.AiServiceFactory;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.service.voice.AsrService;
import com.aih.chatpartner.service.voice.TtsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 语音聊天服务
 * 整合语音识别(ASR)、文本转语音(TTS)和LLM对话能力
 *
 * @author AiHyo
 */
@Slf4j
@Service
public class VoiceChatService {
    
    @Resource
    private AsrService asrService;
    
    @Resource
    private TtsService ttsService;
    
    @Resource
    private AiServiceFactory aiServiceFactory;
    
    @Resource
    private ChatHistoryService chatHistoryService;
    
    @Resource
    private ChatGroupService chatGroupService;
    
    /**
     * 处理语音聊天
     *
     * @param audioData 用户的语音数据
     * @param audioFormat 音频格式（mp3, wav, ogg, raw等）
     * @param groupId 聊天分组ID
     * @param userId 用户ID
     * @return 响应的语音数据（MP3格式）
     */
    public byte[] processVoiceChat(byte[] audioData, String audioFormat, Long groupId, Long userId) {
        return processVoiceChat(audioData, audioFormat, groupId, userId, null, null);
    }
    
    /**
     * 处理语音聊天（带参数）
     *
     * @param audioData 用户的语音数据
     * @param audioFormat 音频格式（mp3, wav, ogg, raw等）
     * @param groupId 聊天分组ID
     * @param userId 用户ID
     * @param voiceType TTS音色类型（可选）
     * @param speedRatio TTS语速比例（可选）
     * @return 响应的语音数据（MP3格式）
     */
    public byte[] processVoiceChat(byte[] audioData, String audioFormat, Long groupId, Long userId, 
                                   String voiceType, Double speedRatio) {
        try {
            // 1. 语音识别：将用户的语音转换为文本
            log.info("开始语音识别，groupId: {}, userId: {}, format: {}", groupId, userId, audioFormat);
            String userText = asrService.speechToText(audioData, audioFormat);
            
            if (userText == null || userText.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别语音内容");
            }
            log.info("语音识别完成，识别文本: {}", userText);
            
            // 2. 保存用户消息
            chatHistoryService.saveUserMessage(groupId, userId, userText);
            
            // 3. 获取AI服务并生成回复
            log.info("开始AI对话处理");
            AiService aiService = aiServiceFactory.getAiService(groupId);
            String aiResponse = aiService.chatInXiYangYangRole(userText);
            log.info("AI回复生成: {}", aiResponse);
            
            // 4. 保存AI回复
            chatHistoryService.saveAiMessage(groupId, userId, aiResponse);
            
            // 5. 文本转语音：将AI的回复转换为语音（使用MP3格式）
            log.info("开始文本转语音");
            byte[] responseAudio = ttsService.textToSpeech(aiResponse, voiceType, speedRatio, "mp3");
            log.info("语音合成完成，音频大小: {} bytes", responseAudio.length);
            
            return responseAudio;
            
        } catch (BusinessException e) {
            log.error("语音聊天处理失败: {}", e.getMessage());
            // 保存错误消息
            chatHistoryService.saveAiErrorMessage(groupId, userId, e.getMessage());
            // 将错误信息转为语音
            String errorMessage = "抱歉，" + e.getMessage();
            return ttsService.textToSpeech(errorMessage, voiceType, speedRatio);
            
        } catch (Exception e) {
            log.error("语音聊天处理异常", e);
            // 保存错误消息
            chatHistoryService.saveAiErrorMessage(groupId, userId, "系统错误");
            // 返回通用错误语音
            String errorMessage = "抱歉，系统暂时无法处理您的请求";
            return ttsService.textToSpeech(errorMessage, voiceType, speedRatio);
        }
    }
    
    /**
     * 文本消息转语音
     * 用于将文本消息（包括历史消息）转换为语音
     *
     * @param text 文本内容
     * @param voiceType 音色类型（可选）
     * @param speedRatio 语速比例（可选）
     * @return 语音数据（MP3格式）
     */
    public byte[] textToVoice(String text, String voiceType, Double speedRatio) {
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文本内容不能为空");
        }
        
        try {
            return ttsService.textToSpeech(text, voiceType, speedRatio, "mp3");
        } catch (Exception e) {
            log.error("文本转语音失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文本转语音失败");
        }
    }
    
    /**
     * 语音转文本
     * 用于将语音消息转换为文本（不进行对话处理）
     *
     * @param audioData 语音数据
     * @param audioFormat 音频格式（mp3, wav, ogg, raw等）
     * @return 识别出的文本
     */
    public String voiceToText(byte[] audioData, String audioFormat) {
        if (audioData == null || audioData.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "语音数据不能为空");
        }
        
        if (audioFormat == null || audioFormat.isEmpty()) {
            audioFormat = "raw"; // 默认格式
        }
        
        try {
            String text = asrService.speechToText(audioData, audioFormat);
            if (text == null || text.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法识别语音内容");
            }
            return text;
        } catch (Exception e) {
            log.error("语音识别失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "语音识别失败");
        }
    }
}
