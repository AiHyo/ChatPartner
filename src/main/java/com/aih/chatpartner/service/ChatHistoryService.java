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
     * 获取分组最早的一条消息
     */
    ChatHistory getEarliestMessage(Long groupId, Long userId);

    /**
     * 若最早一条等于角色问候语且被标记为用户消息，则修正为 AI 消息。
     * @return 是否执行了修正
     */
    boolean repairGreetingIfNeeded(Long groupId, Long userId, String greeting);

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

    /**
     * 游标分页获取历史消息（用于“加载更多”）
     *
     * 规则：
     * - 首次（cursor 为空）：按 createTime desc, id desc 取 limit 条，再反转为升序返回
     * - 下页：取比 cursor(createTime,id) 更“旧”的记录（createTime < cursorTime 或 (createTime = cursorTime 且 id < cursorId)）
     *
     * @param groupId 分组ID
     * @param userId  用户ID
     * @param cursor  形如 "epochMillis,id"，为空表示首页
     * @param limit   返回条数（建议 10）
     * @param asc     返回数据是否升序（建议为 true）
     * @return 带 nextCursor 与 hasMore 的结果
     */
    com.aih.chatpartner.model.dto.chathistory.ChatHistoryCursorPageResponse getChatHistoryByCursor(
            Long groupId, Long userId, String cursor, int limit, boolean asc);
}
