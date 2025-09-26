package com.aih.chatpartner.service.impl;

import com.aih.chatpartner.mapper.AiRoleMapper;
import com.aih.chatpartner.model.entity.AiRole;
import com.aih.chatpartner.service.AiRoleService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI角色服务实现
 *
 * @author AiHyo
 */
@Service
@Slf4j
public class AiRoleServiceImpl extends ServiceImpl<AiRoleMapper, AiRole> implements AiRoleService {

    @Override
    public List<AiRole> getAvailableSystemRoles() {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("isSystem", 1)
                .eq("isActive", 1)
                .orderBy("id", true);
        return this.list(queryWrapper);
    }

    @Override
    public AiRole getRoleById(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return this.getById(roleId);
    }

    @Override
    public String getGreetingByRoleId(Long roleId) {
        AiRole aiRole = getRoleById(roleId);
        return aiRole != null ? aiRole.getGreeting() : "你好！欢迎使用聊天功能！";
    }

    @Override
    public String getSystemPromptByRoleId(Long roleId) {
        AiRole aiRole = getRoleById(roleId);
        return aiRole != null ? aiRole.getSystemPrompt() : null;
    }

    @Override
    public List<AiRole> getUserAvailableRoles(Long userId) {
        // 获取系统角色
        List<AiRole> systemRoles = getAvailableSystemRoles();
        
        // 获取用户自定义角色
        QueryWrapper userRoleQuery = QueryWrapper.create()
                .eq("creatorId", userId)
                .eq("isActive", 1)
                .orderBy("createTime", false);
        List<AiRole> userRoles = this.list(userRoleQuery);
        
        // 合并结果
        systemRoles.addAll(userRoles);
        return systemRoles;
    }

    @Override
    public boolean createUserRole(AiRole aiRole, Long userId) {
        if (aiRole == null || userId == null) {
            return false;
        }
        
        // 设置为用户自定义角色
        aiRole.setCreatorId(userId);
        aiRole.setIsSystem(0);
        aiRole.setIsActive(1);
        
        return this.save(aiRole);
    }
}
