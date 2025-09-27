package com.aih.chatpartner.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.service.ChatGroupService;
import com.aih.chatpartner.mapper.ChatGroupMapper;
import com.aih.chatpartner.mapper.ChatHistoryMapper;
import com.aih.chatpartner.model.dto.chathistory.ChatHistoryCursorPageResponse;
import com.aih.chatpartner.model.entity.ChatGroup;
import com.aih.chatpartner.model.entity.ChatHistory;
import com.aih.chatpartner.model.enums.MessageTypeEnum;
import com.aih.chatpartner.service.ChatHistoryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 对话历史服务实现
 *
 * @author AiHyo
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    private ChatGroupService chatGroupService;
    @Resource
    private ChatGroupMapper chatGroupMapper;

    @Override
    public void validChatHistory(ChatHistory chatHistory, boolean add) {
        if (chatHistory == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String message = chatHistory.getMessage();
        String messageType = chatHistory.getMessageType();
        Long groupId = chatHistory.getGroupId();
        Long userId = chatHistory.getUserId();

        // 创建时，参数不能为空
        if (add) {
            if (StrUtil.hasBlank(message, messageType) || groupId == null || userId == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
            }
        }
        // 有参数则校验
        if (StrUtil.isNotBlank(message) && message.length() > 50000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容过长");
        }
        if (StrUtil.isNotBlank(messageType)) {
            MessageTypeEnum messageTypeEnum = MessageTypeEnum.getEnumByValue(messageType);
            if (messageTypeEnum == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息类型不正确");
            }
        }
    }

    @Override
    public ChatHistory saveUserMessage(Long groupId, Long userId, String message) {
        if (groupId == null || userId == null || StrUtil.isBlank(message)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setGroupId(groupId);
        chatHistory.setUserId(userId);
        chatHistory.setMessage(message);
        chatHistory.setMessageType(MessageTypeEnum.USER.getValue());
        chatHistory.setCreateTime(LocalDateTime.now());
        chatHistory.setUpdateTime(LocalDateTime.now());

        validChatHistory(chatHistory, true);
        boolean result = this.save(chatHistory);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存用户消息失败");
        }

        log.info("保存用户消息成功，groupId: {}, userId: {}, messageId: {}", groupId, userId, chatHistory.getId());
        // 更新分组最近聊天时间
        ChatGroup group = new ChatGroup();
        group.setId(groupId);
        group.setLastChatTime(chatHistory.getCreateTime());
        chatGroupMapper.updateByQuery(group, QueryWrapper.create().eq("id", groupId));
        return chatHistory;
    }

    @Override
    public ChatHistory saveAiMessage(Long groupId, Long userId, String message) {
        if (groupId == null || userId == null || StrUtil.isBlank(message)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setGroupId(groupId);
        chatHistory.setUserId(userId);
        chatHistory.setMessage(message);
        chatHistory.setMessageType(MessageTypeEnum.AI.getValue());
        chatHistory.setCreateTime(LocalDateTime.now());
        chatHistory.setUpdateTime(LocalDateTime.now());

        validChatHistory(chatHistory, true);
        boolean result = this.save(chatHistory);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存AI消息失败");
        }

        log.info("保存AI消息成功，groupId: {}, userId: {}, messageId: {}", groupId, userId, chatHistory.getId());
        // 更新分组最近聊天时间
        ChatGroup group = new ChatGroup();
        group.setId(groupId);
        group.setLastChatTime(chatHistory.getCreateTime());
        chatGroupMapper.updateByQuery(group, QueryWrapper.create().eq("id", groupId));
        return chatHistory;
    }

    @Override
    public ChatHistory saveAiErrorMessage(Long groupId, Long userId, String errorMessage) {
        if (groupId == null || userId == null || StrUtil.isBlank(errorMessage)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String aiErrorMessage = "抱歉，我遇到了一些问题：" + errorMessage;
        return saveAiMessage(groupId, userId, aiErrorMessage);
    }

    @Override
    public List<ChatHistory> getChatHistoryByGroupId(Long groupId, Long userId, long current, long size) {
        if (groupId == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("groupId", groupId)
                .eq("userId", userId)
                .eq("isDelete", 0)
                .orderBy("createTime", false)
                .limit(size)
                .offset((current - 1) * size);
        
        List<ChatHistory> historyList = this.list(queryWrapper);
        // 反转列表，确保按时间正序（老的在前，新的在后）
        if (CollUtil.isNotEmpty(historyList)) {
            historyList = historyList.reversed();
        }
        
        return historyList;
    }

    @Override
    public List<ChatHistory> getLatestChatHistory(Long groupId, Long userId, int limit) {
        if (groupId == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("groupId", groupId)
                .eq("userId", userId)
                .eq("isDelete", 0)
                .orderBy("createTime", false)
                .limit(limit);
        
        List<ChatHistory> historyList = this.list(queryWrapper);
        // 反转列表，确保按时间正序（老的在前，新的在后）
        if (CollUtil.isNotEmpty(historyList)) {
            historyList = historyList.reversed();
        }
        
        return historyList;
    }

    @Override
    public boolean deleteChatHistoryByGroupId(Long groupId) {
        if (groupId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper queryWrapper = QueryWrapper.create();
        queryWrapper.eq("groupId", groupId);
        
        boolean result = this.remove(queryWrapper);
        log.info("删除分组对话历史，groupId: {}, result: {}", groupId, result);
        return result;
    }

    @Override
    public int loadChatHistoryToMemory(Long groupId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 直接构造查询条件，获取最新的对话历史
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq("groupId", groupId)
                    .eq("isDelete", 0)
                    .orderBy("createTime", false)
                    .limit(maxCount);
            
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }
            
            // 反转列表，确保按时间正序（老的在前，新的在后）
            historyList = historyList.reversed();
            
            // 按时间顺序添加到记忆中
            int loadedCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            
            for (ChatHistory history : historyList) {
                if (MessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (MessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            
            log.info("成功为 groupId: {} 加载了 {} 条历史对话", groupId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，groupId: {}, error: {}", groupId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }

    @Override
    public boolean hasHistoryMessages(Long groupId, Long userId) {
        if (groupId == null || userId == null) {
            return false;
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("groupId", groupId)
                .eq("userId", userId)
                .eq("isDelete", 0)
                .limit(1);
        
        return this.count(queryWrapper) > 0;
    }

    @Override
    public ChatHistoryCursorPageResponse getChatHistoryByCursor(Long groupId, Long userId, String cursor, int limit, boolean asc) {
        if (groupId == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("groupId", groupId)
                .eq("userId", userId)
                .eq("isDelete", 0);

        LocalDateTime cursorTime = null;
        Long cursorId = null;
        if (StrUtil.isNotBlank(cursor)) {
            try {
                String[] arr = cursor.split(",");
                long epochMillis = Long.parseLong(arr[0]);
                cursorId = Long.parseLong(arr[1]);
                cursorTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
                // createTime < cursorTime OR (createTime = cursorTime AND id < cursorId)
                queryWrapper.and(" (createTime < ? or (createTime = ? and id < ?)) ", cursorTime, cursorTime, cursorId);
            } catch (Exception e) {
                log.warn("解析游标失败 cursor={}, 使用首页策略", cursor);
            }
        }

        queryWrapper.orderBy("createTime", false).orderBy("id", false).limit(limit);
        List<ChatHistory> list = this.list(queryWrapper);

        boolean hasMore = CollUtil.isNotEmpty(list) && list.size() >= limit;
        if (asc && CollUtil.isNotEmpty(list)) {
            list = list.reversed();
        }

        String nextCursor = null;
        if (CollUtil.isNotEmpty(list)) {
            // 取本批次中“最旧”的一条作为下页游标
            ChatHistory last = asc ? list.get(0) : list.get(list.size() - 1);
            // 注意：如果 asc=true，list 已升序，最旧的是第一个；若 asc=false（默认降序），最旧的是最后一个
            long epoch = last.getCreateTime() == null ? 0L : last.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            nextCursor = epoch + "," + last.getId();
        }

        ChatHistoryCursorPageResponse resp = new ChatHistoryCursorPageResponse();
        resp.setItems(list);
        resp.setHasMore(hasMore);
        resp.setNextCursor(nextCursor);
        return resp;
    }
}
