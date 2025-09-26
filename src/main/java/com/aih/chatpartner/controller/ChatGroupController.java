package com.aih.chatpartner.controller;

import com.aih.chatpartner.annotation.AuthCheck;
import com.aih.chatpartner.common.BaseResponse;
import com.aih.chatpartner.common.DeleteRequest;
import com.aih.chatpartner.common.ResultUtils;
import com.aih.chatpartner.constant.UserConstant;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.exception.ThrowUtils;
import com.aih.chatpartner.model.dto.chatgroup.ChatGroupAddRequest;
import com.aih.chatpartner.model.dto.chatgroup.ChatGroupUpdateRequest;
import com.aih.chatpartner.model.entity.ChatGroup;
import com.aih.chatpartner.model.entity.User;
import com.aih.chatpartner.service.ChatGroupService;
import com.aih.chatpartner.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话分组接口
 *
 * @author AiHyo
 */
@RestController
@RequestMapping("/chatGroup")
@Slf4j
public class ChatGroupController {

    @Resource
    private ChatGroupService chatGroupService;

    @Resource
    private UserService userService;

    /**
     * 创建对话分组
     *
     * @param chatGroupAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Long> addChatGroup(@RequestBody ChatGroupAddRequest chatGroupAddRequest, HttpServletRequest request) {
        if (chatGroupAddRequest == null) {
            throw new BusinessException(com.aih.chatpartner.exception.ErrorCode.PARAMS_ERROR);
        }
        ChatGroup chatGroup = new ChatGroup();
        BeanUtils.copyProperties(chatGroupAddRequest, chatGroup);
        
        User loginUser = userService.getLoginUser(request);
        chatGroup.setUserId(loginUser.getId());
        chatGroup.setCreateTime(LocalDateTime.now());
        chatGroup.setUpdateTime(LocalDateTime.now());
        chatGroup.setEditTime(LocalDateTime.now());
        
        chatGroupService.validChatGroup(chatGroup, true);
        boolean result = chatGroupService.save(chatGroup);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        
        long newChatGroupId = chatGroup.getId();
        return ResultUtils.success(newChatGroupId);
    }

    /**
     * 更新对话分组
     *
     * @param chatGroupUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> updateChatGroup(@RequestBody ChatGroupUpdateRequest chatGroupUpdateRequest, HttpServletRequest request) {
        if (chatGroupUpdateRequest == null || chatGroupUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        User loginUser = userService.getLoginUser(request);
        Long groupId = chatGroupUpdateRequest.getId();
        
        // 验证分组是否存在且属于当前用户
        ChatGroup existingGroup = chatGroupService.getById(groupId);
        if (existingGroup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分组不存在");
        }
        if (!existingGroup.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该分组");
        }
        
        // 构建更新对象
        ChatGroup chatGroup = new ChatGroup();
        BeanUtils.copyProperties(chatGroupUpdateRequest, chatGroup);
        chatGroup.setUserId(loginUser.getId()); // 确保用户ID不被修改
        chatGroup.setUpdateTime(LocalDateTime.now());
        chatGroup.setEditTime(LocalDateTime.now());
        
        // 校验更新数据
        chatGroupService.validChatGroup(chatGroup, false);
        
        boolean result = chatGroupService.updateById(chatGroup);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        
        log.info("更新分组成功，groupId: {}, userId: {}", groupId, loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 删除对话分组
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deleteChatGroup(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        
        boolean result = chatGroupService.deleteChatGroupWithHistory(id, user.getId());
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取对话分组
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<ChatGroup> getChatGroupById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        ChatGroup chatGroup = chatGroupService.getById(id);
        if (chatGroup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        
        User loginUser = userService.getLoginUser(request);
        // 仅创建者可以查看
        if (!chatGroup.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        
        return ResultUtils.success(chatGroup);
    }

    /**
     * 获取当前用户的对话分组列表
     *
     * @param request
     * @return
     */
    @GetMapping("/my")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<ChatGroup>> getMyChatGroups(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<ChatGroup> chatGroupList = chatGroupService.getChatGroupsByUserId(loginUser.getId());
        return ResultUtils.success(chatGroupList);
    }
}
