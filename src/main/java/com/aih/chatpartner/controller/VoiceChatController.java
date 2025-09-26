package com.aih.chatpartner.controller;

import com.aih.chatpartner.common.BaseResponse;
import com.aih.chatpartner.common.ResultUtils;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.model.dto.voice.TextToVoiceRequest;
import com.aih.chatpartner.model.dto.voice.VoiceChatRequest;
import com.aih.chatpartner.model.dto.voice.VoiceToTextResponse;
import com.aih.chatpartner.model.entity.ChatGroup;
import com.aih.chatpartner.model.entity.User;
import com.aih.chatpartner.service.ChatGroupService;
import com.aih.chatpartner.service.UserService;
import com.aih.chatpartner.service.VoiceChatService;
import com.aih.chatpartner.service.voice.TtsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

/**
 * 语音聊天控制器
 *
 * @author AiHyo
 */
@RestController
@RequestMapping("/voice")
@Tag(name = "语音聊天", description = "语音聊天相关接口")
@Slf4j
public class VoiceChatController {
    
    @Resource
    private VoiceChatService voiceChatService;
    
    @Resource
    private TtsService ttsService;
    
    @Resource
    private UserService userService;
    
    @Resource
    private ChatGroupService chatGroupService;
    
    /**
     * 语音聊天（上传音频文件）
     */
    @PostMapping("/chat")
    @Operation(summary = "语音聊天", description = "上传语音进行对话，返回语音响应")
    public ResponseEntity<byte[]> voiceChat(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam("groupId") Long groupId,
            @RequestParam(value = "voiceType", required = false) String voiceType,
            @RequestParam(value = "speedRatio", required = false) Double speedRatio,
            HttpServletRequest request) {
        
        try {
            // 验证用户
            User loginUser = userService.getLoginUser(request);
            
            // 验证分组权限
            validateGroupAccess(groupId, loginUser.getId());
            
            // 获取音频数据
            byte[] audioData = audioFile.getBytes();
            
            // 获取音频格式
            String originalFilename = audioFile.getOriginalFilename();
            String audioFormat = "raw";
            if (originalFilename != null && originalFilename.contains(".")) {
                audioFormat = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            }
            
            // 处理语音聊天
            byte[] responseAudio = voiceChatService.processVoiceChat(
                    audioData, audioFormat, groupId, loginUser.getId(), voiceType, speedRatio);
            
            // 返回音频响应
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("audio", "pcm"));
            headers.setContentLength(responseAudio.length);
            
            return new ResponseEntity<>(responseAudio, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("语音聊天失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "语音聊天失败: " + e.getMessage());
        }
    }
    
    /**
     * 语音聊天（Base64编码）
     */
    @PostMapping("/chat/base64")
    @Operation(summary = "语音聊天(Base64)", description = "发送Base64编码的语音进行对话")
    public BaseResponse<String> voiceChatBase64(
            @RequestBody VoiceChatRequest request,
            HttpServletRequest httpRequest) {
        
        // 验证参数
        if (request == null || request.getAudioBase64() == null || request.getGroupId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        // 验证用户
        User loginUser = userService.getLoginUser(httpRequest);
        
        // 验证分组权限
        validateGroupAccess(request.getGroupId(), loginUser.getId());
        
        try {
            // 解码音频数据
            byte[] audioData = Base64.getDecoder().decode(request.getAudioBase64());
            
            // 获取音频格式，默认为raw
            String audioFormat = request.getAudioFormat() != null ? request.getAudioFormat() : "raw";
            
            // 处理语音聊天
            byte[] responseAudio = voiceChatService.processVoiceChat(
                    audioData, 
                    audioFormat,
                    request.getGroupId(), 
                    loginUser.getId(),
                    request.getVoiceType(),
                    request.getSpeedRatio());
            
            // 编码响应音频
            String responseBase64 = Base64.getEncoder().encodeToString(responseAudio);
            
            return ResultUtils.success(responseBase64);
            
        } catch (Exception e) {
            log.error("语音聊天失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "语音聊天失败: " + e.getMessage());
        }
    }
    
    /**
     * 语音转文本
     */
    @PostMapping("/to-text")
    @Operation(summary = "语音转文本", description = "将语音转换为文本")
    public BaseResponse<VoiceToTextResponse> voiceToText(
            @RequestParam("audio") MultipartFile audioFile,
            HttpServletRequest request) {
        
        // 验证用户
        userService.getLoginUser(request);
        
        try {
            // 获取音频数据
            byte[] audioData = audioFile.getBytes();
            
            // 获取音频格式
            String originalFilename = audioFile.getOriginalFilename();
            String audioFormat = "raw";
            if (originalFilename != null && originalFilename.contains(".")) {
                audioFormat = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            }
            
            // 转换为文本
            String text = voiceChatService.voiceToText(audioData, audioFormat);
            
            VoiceToTextResponse response = new VoiceToTextResponse();
            response.setText(text);
            
            return ResultUtils.success(response);
            
        } catch (Exception e) {
            log.error("语音转文本失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "语音转文本失败: " + e.getMessage());
        }
    }
    
    /**
     * 文本转语音
     */
    @PostMapping("/to-voice")
    @Operation(summary = "文本转语音", description = "将文本转换为语音")
    public ResponseEntity<byte[]> textToVoice(
            @RequestBody TextToVoiceRequest request,
            HttpServletRequest httpRequest) {
        
        // 验证参数
        if (request == null || request.getText() == null || request.getText().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文本内容不能为空");
        }
        
        // 验证用户
        User loginUser = userService.getLoginUser(httpRequest);
        
        try {
            // 转换为语音
            byte[] audioData = voiceChatService.textToVoice(
                    request.getText(),
                    request.getVoiceType(),
                    request.getSpeedRatio());
            
            // 返回音频响应
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("audio", "pcm"));
            headers.setContentLength(audioData.length);
            
            return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("文本转语音失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文本转语音失败: " + e.getMessage());
        }
    }
    
    /**
     * 文本转语音（Base64）
     */
    @PostMapping("/to-voice/base64")
    @Operation(summary = "文本转语音(Base64)", description = "将文本转换为Base64编码的语音")
    public BaseResponse<String> textToVoiceBase64(
            @RequestBody TextToVoiceRequest request,
            HttpServletRequest httpRequest) {
        
        // 验证参数
        if (request == null || request.getText() == null || request.getText().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文本内容不能为空");
        }
        
        // 验证用户
        User loginUser = userService.getLoginUser(httpRequest);
        
        try {
            // 转换为语音
            byte[] audioData = voiceChatService.textToVoice(
                    request.getText(),
                    request.getVoiceType(),
                    request.getSpeedRatio());
            
            // 编码为Base64
            String audioBase64 = Base64.getEncoder().encodeToString(audioData);
            
            return ResultUtils.success(audioBase64);
            
        } catch (Exception e) {
            log.error("文本转语音失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文本转语音失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取可用音色列表
     */
    @GetMapping("/voices")
    @Operation(summary = "获取音色列表", description = "获取可用的TTS音色列表")
    public BaseResponse<List<TtsService.VoiceInfo>> getVoiceList(HttpServletRequest request) {
        userService.getLoginUser(request);
        
        List<TtsService.VoiceInfo> voices = ttsService.getVoiceList();
        return ResultUtils.success(voices);
    }
    
    /**
     * 验证分组访问权限
     */
    private void validateGroupAccess(Long groupId, Long userId) {
        ChatGroup chatGroup = chatGroupService.getById(groupId);
        if (chatGroup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分组不存在");
        }
        if (!chatGroup.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该分组");
        }
    }
}
