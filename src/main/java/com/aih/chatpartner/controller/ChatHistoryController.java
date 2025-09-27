package com.aih.chatpartner.controller;

import com.aih.chatpartner.annotation.AuthCheck;
import com.aih.chatpartner.common.BaseResponse;
import com.aih.chatpartner.common.ResultUtils;
import com.aih.chatpartner.constant.UserConstant;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.model.dto.chathistory.ChatHistoryQueryRequest;
import com.aih.chatpartner.model.entity.ChatHistory;
import com.aih.chatpartner.model.entity.User;
import com.aih.chatpartner.model.dto.chathistory.ChatHistoryCursorPageResponse;
import com.aih.chatpartner.service.ChatHistoryService;
import com.aih.chatpartner.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话历史接口
 *
 * @author AiHyo
 */
@RestController
@RequestMapping("/chatHistory")
@Slf4j
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    /**
     * 获取对话历史（分页）
     *
     * @param chatHistoryQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list")
    public BaseResponse<List<ChatHistory>> getChatHistory(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest, HttpServletRequest request) {
        if (chatHistoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long groupId = chatHistoryQueryRequest.getGroupId();
        if (groupId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);

        long current = chatHistoryQueryRequest.getCurrent();
        long pageSize = chatHistoryQueryRequest.getPageSize();

        List<ChatHistory> historyList = chatHistoryService.getChatHistoryByGroupId(
                groupId, loginUser.getId(), current, pageSize);

        return ResultUtils.success(historyList);
    }

    /**
     * 基于游标的历史分页（加载更多）
     * GET /chatHistory/cursor?groupId=...&cursor=epochMillis,id&limit=10&asc=true
     */
    @GetMapping("/cursor")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<ChatHistoryCursorPageResponse> getByCursor(
            @RequestParam Long groupId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false, defaultValue = "true") Boolean asc,
            HttpServletRequest request) {
        if (groupId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        ChatHistoryCursorPageResponse resp = chatHistoryService.getChatHistoryByCursor(groupId, loginUser.getId(), cursor, limit == null ? 10 : limit, asc != null && asc);
        return ResultUtils.success(resp);
    }

    /**
     * 获取最新对话历史
     *
     * @param groupId
     * @param request
     * @return
     */
    @GetMapping("/latest")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<ChatHistory>> getLatestChatHistory(@RequestParam Long groupId, HttpServletRequest request) {
        if (groupId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        List<ChatHistory> historyList = chatHistoryService.getLatestChatHistory(groupId, loginUser.getId(), 10);


        return ResultUtils.success(historyList);
    }

    /**
     * 检查分组是否有历史消息
     *
     * @param groupId
     * @param request
     * @return
     */
    @GetMapping("/hasHistory")
    public BaseResponse<Boolean> hasHistoryMessages(@RequestParam Long groupId, HttpServletRequest request) {
        if (groupId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        boolean hasHistory = chatHistoryService.hasHistoryMessages(groupId, loginUser.getId());

        return ResultUtils.success(hasHistory);
    }
}
