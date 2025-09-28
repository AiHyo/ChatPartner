package com.aih.chatpartner.service;

import com.aih.chatpartner.model.entity.AiRole;
import com.aih.chatpartner.common.PageResponse;
import com.mybatisflex.core.service.IService;

import java.util.List;
import com.aih.chatpartner.model.dto.admin.AdminRoleUpsertRequest;

/**
 * AI角色服务
 *
 * @author AiHyo
 */
public interface AiRoleService extends IService<AiRole> {

    /**
     * 获取所有可用的系统角色
     *
     * @return
     */
    List<AiRole> getAvailableSystemRoles();

    /**
     * 根据角色ID获取角色信息
     *
     * @param roleId
     * @return
     */
    AiRole getRoleById(Long roleId);

    /**
     * 根据角色获取问候语
     *
     * @param roleId
     * @return
     */
    String getGreetingByRoleId(Long roleId);

    /**
     * 根据角色获取系统提示词
     *
     * @param roleId
     * @return
     */
    String getSystemPromptByRoleId(Long roleId);

    /**
     * 获取用户可用的角色列表（系统角色 + 用户自定义角色）
     *
     * @param userId
     * @return
     */
    List<AiRole> getUserAvailableRoles(Long userId);

    /**
     * 创建用户自定义角色
     *
     * @param aiRole
     * @param userId
     * @return
     */
    boolean createUserRole(AiRole aiRole, Long userId);

    /**
     * 角色搜索与筛选
     * @param q 关键词
     * @param tag 标签
     * @param sort hot|new|name
     * @param page 页号(从1开始)
     * @param size 每页条数
     * @param userId 当前用户ID（用于 onlyNotFriend）
     * @param onlyNotFriend 仅显示未加好友
     */
    java.util.List<AiRole> searchRoles(String q, String tag, String sort, Integer page, Integer size, Long userId, Boolean onlyNotFriend);

    /**
     * 角色分页查询（含总数）
     */
    PageResponse<AiRole> searchRolesPage(String q, String tag, String sort, Integer page, Integer size, Long userId, Boolean onlyNotFriend);

    /**
     * 标签列表（去重）
     */
    java.util.List<String> listAllTags();

    /**
     * 点赞角色（likes+1）
     */
    boolean likeRole(Long roleId);

    /**
     * 切换点赞（已点赞则取消，未点赞则点赞），返回是否点赞后的状态
     */
    boolean toggleLike(Long roleId, Long userId);

    /**
     * 获取用户已点赞的角色ID列表
     */
    java.util.List<Long> listLikedRoleIds(Long userId);

    /**
     * 管理端：分页查询（可过滤 isActive / isSystem）
     */
    PageResponse<AiRole> adminPage(String q, String tag, String sort, Integer page, Integer size,
                                   Integer isActive, Integer isSystem);

    /**
     * 管理端：创建角色
     */
    Long adminCreate(AdminRoleUpsertRequest req, Long adminUserId);

    /**
     * 管理端：更新角色
     */
    boolean adminUpdate(AdminRoleUpsertRequest req);

    /**
     * 管理端：更新启用状态
     */
    boolean adminChangeStatus(Long id, Integer isActive);

    /**
     * 管理端：删除（逻辑删除）
     */
    boolean adminDelete(Long id);

    /**
     * 管理端：批量删除（逻辑删除）
     */
    boolean adminDeleteBatch(java.util.List<Long> ids);
}
