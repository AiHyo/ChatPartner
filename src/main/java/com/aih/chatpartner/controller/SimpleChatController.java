package com.aih.chatpartner.controller;

import com.aih.chatpartner.ai.AiService;
import com.aih.chatpartner.ai.AiServiceFactory;
import com.aih.chatpartner.common.BaseResponse;
import com.aih.chatpartner.common.ResultUtils;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.model.dto.chat.ChatRequest;
import com.aih.chatpartner.model.entity.ChatGroup;
import com.aih.chatpartner.model.entity.User;
import com.aih.chatpartner.service.AiRoleService;
import com.aih.chatpartner.service.ChatGroupService;
import com.aih.chatpartner.service.ChatHistoryService;
import com.aih.chatpartner.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 简化的聊天接口
 *
 * @author AiHyo
 */
@RestController
@RequestMapping("/chat")
@Slf4j
public class SimpleChatController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ChatGroupService chatGroupService;

    @Resource
    private UserService userService;

    @Resource
    private AiServiceFactory aiServiceFactory;

    @Resource
    private AiRoleService aiRoleService;

    /**
     * 发送消息
     *
     * @param chatRequest
     * @param request
     * @return
     */
    @PostMapping("/send")
    public BaseResponse<String> sendMessage(@RequestBody ChatRequest chatRequest, HttpServletRequest request) {
        if (chatRequest == null || chatRequest.getGroupId() == null || chatRequest.getMessage() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        Long groupId = chatRequest.getGroupId();
        String message = chatRequest.getMessage().trim();

        // 验证分组权限
        ChatGroup chatGroup = chatGroupService.getById(groupId);
        if (chatGroup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分组不存在");
        }
        if (!chatGroup.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该分组");
        }

        try {
            // 保存用户消息
            chatHistoryService.saveUserMessage(groupId, loginUser.getId(), message);

            // 获取AI服务并生成回复
            AiService aiService = aiServiceFactory.getAiService(groupId);
            String aiResponse = aiService.chatInXiYangYangRole(message);

            // 保存AI回复
            chatHistoryService.saveAiMessage(groupId, loginUser.getId(), aiResponse);

            log.info("聊天完成，groupId: {}, userId: {}", groupId, loginUser.getId());
            return ResultUtils.success(aiResponse);

        } catch (Exception e) {
            log.error("聊天失败，groupId: {}, userId: {}, error: {}", groupId, loginUser.getId(), e.getMessage(), e);
            
            // 保存错误消息
            chatHistoryService.saveAiErrorMessage(groupId, loginUser.getId(), e.getMessage());
            
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "聊天失败：" + e.getMessage());
        }
    }

    /**
     * 初始化分组对话（如果没有历史记录）
     *
     * @param groupId
     * @param request
     * @return
     */
    @PostMapping("/init")
    public BaseResponse<String> initChat(@RequestParam Long groupId, HttpServletRequest request) {
        if (groupId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        
        // 验证分组权限
        ChatGroup chatGroup = chatGroupService.getById(groupId);
        if (chatGroup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分组不存在");
        }
        if (!chatGroup.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该分组");
        }

        // 检查是否已有历史记录
        boolean hasHistory = chatHistoryService.hasHistoryMessages(groupId, loginUser.getId());
        if (hasHistory) {
            return ResultUtils.success("分组已有对话历史，无需初始化");
        }

        // 使用角色问候语进行初始化
        try {
            // 获取角色的问候语
            String greeting = aiRoleService.getGreetingByRoleId(chatGroup.getRoleId());
            
            // 保存初始化消息
            chatHistoryService.saveUserMessage(groupId, loginUser.getId(), greeting);

            // 获取AI服务并生成回复
            AiService aiService = aiServiceFactory.getAiService(groupId);
            String aiResponse = aiService.chatInXiYangYangRole(greeting);

            // 保存AI回复
            chatHistoryService.saveAiMessage(groupId, loginUser.getId(), aiResponse);

            log.info("分组初始化完成，groupId: {}, userId: {}", groupId, loginUser.getId());
            return ResultUtils.success(aiResponse);

        } catch (Exception e) {
            log.error("分组初始化失败，groupId: {}, userId: {}, error: {}", groupId, loginUser.getId(), e.getMessage(), e);
            
            // 保存错误消息
            chatHistoryService.saveAiErrorMessage(groupId, loginUser.getId(), e.getMessage());
            
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化失败：" + e.getMessage());
        }
    }
}
