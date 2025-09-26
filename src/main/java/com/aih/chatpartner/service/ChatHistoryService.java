package com.aih.chatpartner.service;

import com.aih.chatpartner.model.entity.ChatHistory;
import com.mybatisflex.core.service.IService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.List;

/**
 * 对话历史服务
 *
 * @author AiHyo
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 校验对话历史
     *
     * @param chatHistory
     * @param add
     */
    void validChatHistory(ChatHistory chatHistory, boolean add);

    /**
     * 保存用户消息
     *
     * @param groupId
     * @param userId
     * @param message
     * @return
     */
    ChatHistory saveUserMessage(Long groupId, Long userId, String message);

    /**
     * 保存AI回复消息
     *
     * @param groupId
     * @param userId
     * @param message
     * @return
     */
    ChatHistory saveAiMessage(Long groupId, Long userId, String message);

    /**
     * 保存AI错误消息
     *
     * @param groupId
     * @param userId
     * @param errorMessage
     * @return
     */
    ChatHistory saveAiErrorMessage(Long groupId, Long userId, String errorMessage);

    /**
     * 分页获取分组的对话历史
     *
     * @param groupId
     * @param userId
     * @param current
     * @param size
     * @return
     */
    List<ChatHistory> getChatHistoryByGroupId(Long groupId, Long userId, long current, long size);

    /**
     * 获取分组最新的对话历史
     *
     * @param groupId
     * @param userId
     * @param limit
     * @return
     */
    List<ChatHistory> getLatestChatHistory(Long groupId, Long userId, int limit);

    /**
     * 删除分组的所有对话历史
     *
     * @param groupId
     * @return
     */
    boolean deleteChatHistoryByGroupId(Long groupId);

    /**
     * 加载历史对话到内存中
     *
     * @param groupId
     * @param chatMemory
     * @param maxCount
     * @return
     */
    int loadChatHistoryToMemory(Long groupId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 检查分组是否有对话历史
     *
     * @param groupId
     * @param userId
     * @return
     */
    boolean hasHistoryMessages(Long groupId, Long userId);
}
