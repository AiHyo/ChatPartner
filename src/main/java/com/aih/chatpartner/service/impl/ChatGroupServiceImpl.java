package com.aih.chatpartner.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.mapper.ChatGroupMapper;
import com.aih.chatpartner.model.entity.ChatGroup;
import com.aih.chatpartner.service.ChatGroupService;
import com.aih.chatpartner.service.ChatHistoryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * 对话分组服务实现
 *
 * @author AiHyo
 */
@Service
@Slf4j
public class ChatGroupServiceImpl extends ServiceImpl<ChatGroupMapper, ChatGroup> implements ChatGroupService {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Override
    public void validChatGroup(ChatGroup chatGroup, boolean add) {
        if (chatGroup == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String groupName = chatGroup.getGroupName();
        Long roleId = chatGroup.getRoleId();
        Long userId = chatGroup.getUserId();

        // 创建时，参数不能为空
        if (add) {
            if (StrUtil.isBlank(groupName) || userId == null || roleId == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
        }
        // 有参数则校验
        if (StrUtil.isNotBlank(groupName) && groupName.length() > 128) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分组名称过长");
        }
    }

    @Override
    public List<ChatGroup> getChatGroupsByUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("userId", userId)
                .orderBy("updateTime", false);
        return this.list(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteChatGroupWithHistory(Long groupId, Long userId) {
        if (groupId == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 验证分组是否存在且属于当前用户
        ChatGroup chatGroup = this.getById(groupId);
        if (chatGroup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分组不存在");
        }
        if (!chatGroup.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该分组");
        }
        try {
            // 删除分组相关的对话历史
            chatHistoryService.deleteChatHistoryByGroupId(groupId);
            // 删除分组
            boolean result = this.removeById(groupId);
            log.info("删除分组成功，groupId: {}, userId: {}", groupId, userId);
            return result;
        } catch (Exception e) {
            log.error("删除分组失败，groupId: {}, userId: {}, error: {}", groupId, userId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除分组失败");
        }
    }
}
