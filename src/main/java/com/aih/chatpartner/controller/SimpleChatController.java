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
            // 如果这是该分组的第一条消息，且内容等于角色问候语，则将其视为 AI 的自我介绍，
            // 仅保存为 AI 消息，不触发 LLM 回复，避免“AI 回复自己的自我介绍”。
            boolean hasHistory = chatHistoryService.hasHistoryMessages(groupId, loginUser.getId());
            if (!hasHistory) {
                String greeting = aiRoleService.getGreetingByRoleId(chatGroup.getRoleId());
                if (greeting != null && greeting.equals(message)) {
                    chatHistoryService.saveAiMessage(groupId, loginUser.getId(), greeting);
                    log.info("首次消息等于问候语，按AI自我介绍处理，groupId: {}, userId: {}", groupId, loginUser.getId());
                    return ResultUtils.success(greeting);
                }
            }

            // 正常流程：保存用户消息 → 生成并保存AI回复
            chatHistoryService.saveUserMessage(groupId, loginUser.getId(), message);

            AiService aiService = aiServiceFactory.getAiService(groupId);
            String aiResponse = aiService.chatInXiYangYangRole(message);

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

        // 使用角色问候语进行初始化或修复（仅 AI 自我介绍，不触发 LLM 回复）
        try {
            String greeting = aiRoleService.getGreetingByRoleId(chatGroup.getRoleId());

            // 若已存在历史，尝试修复“把问候语当作用户消息保存”的旧数据
            boolean hasHistory = chatHistoryService.hasHistoryMessages(groupId, loginUser.getId());
            if (hasHistory) {
                // 拉取最早一条进行修复（旧版本可能把问候语当作用户消息保存为第一条）
                com.aih.chatpartner.model.entity.ChatHistory first = chatHistoryService.getEarliestMessage(groupId, loginUser.getId());
                if (first != null && greeting != null
                        && greeting.equals(first.getMessage())
                        && "user".equalsIgnoreCase(first.getMessageType())) {
                    com.aih.chatpartner.model.entity.ChatHistory patch = new com.aih.chatpartner.model.entity.ChatHistory();
                    patch.setId(first.getId());
                    patch.setMessageType("ai");
                    chatHistoryService.updateById(patch);
                    log.info("修正历史问候语为AI消息，groupId: {}, msgId: {}", groupId, first.getId());
                    return ResultUtils.success(greeting);
                }
                // 已有其他历史，直接返回
                return ResultUtils.success("分组已有对话历史，无需初始化");
            }

            // 无历史：仅保存 AI 消息
            chatHistoryService.saveAiMessage(groupId, loginUser.getId(), greeting);
            log.info("分组初始化完成（仅AI自我介绍），groupId: {}, userId: {}", groupId, loginUser.getId());
            return ResultUtils.success(greeting);
        } catch (Exception e) {
            log.error("分组初始化失败，groupId: {}, userId: {}, error: {}", groupId, loginUser.getId(), e.getMessage(), e);
            
            // 保存错误消息
            chatHistoryService.saveAiErrorMessage(groupId, loginUser.getId(), e.getMessage());
            
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化失败：" + e.getMessage());
        }
    }
}
