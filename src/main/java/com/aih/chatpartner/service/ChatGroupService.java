package com.aih.chatpartner.service;

import com.aih.chatpartner.model.entity.ChatGroup;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 对话分组服务
 *
 * @author AiHyo
 */
public interface ChatGroupService extends IService<ChatGroup> {

    /**
     * 校验分组
     *
     * @param chatGroup
     * @param add
     */
    void validChatGroup(ChatGroup chatGroup, boolean add);

    /**
     * 获取查询包装类
     *
     * @param chatGroupQueryRequest
     * @return
     */
    // QueryWrapper<ChatGroup> getQueryWrapper(ChatGroupQueryRequest chatGroupQueryRequest);

    /**
     * 获取分组封装
     *
     * @param chatGroup
     * @param request
     * @return
     */
    // ChatGroupVO getChatGroupVO(ChatGroup chatGroup, HttpServletRequest request);

    /**
     * 分页获取分组封装
     *
     * @param chatGroupPage
     * @param request
     * @return
     */
    // Page<ChatGroupVO> getChatGroupVOPage(Page<ChatGroup> chatGroupPage, HttpServletRequest request);

    /**
     * 根据用户ID获取分组列表
     *
     * @param userId
     * @return
     */
    List<ChatGroup> getChatGroupsByUserId(Long userId);

    /**
     * 删除分组及其相关数据
     *
     * @param groupId
     * @param userId
     * @return
     */
    boolean deleteChatGroupWithHistory(Long groupId, Long userId);
}
