package com.aih.chatpartner.service;

import com.aih.chatpartner.model.entity.AiRole;
import com.mybatisflex.core.service.IService;

import java.util.List;

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
}
