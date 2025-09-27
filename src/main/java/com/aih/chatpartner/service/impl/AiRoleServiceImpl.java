package com.aih.chatpartner.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aih.chatpartner.mapper.AiRoleMapper;
import com.aih.chatpartner.mapper.UserAiMapper;
import com.aih.chatpartner.model.entity.AiRole;
import com.aih.chatpartner.model.entity.UserAi;
import com.aih.chatpartner.service.AiRoleService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI角色服务实现
 *
 * @author AiHyo
 */
@Service
@Slf4j
public class AiRoleServiceImpl extends ServiceImpl<AiRoleMapper, AiRole> implements AiRoleService {

    @Resource
    private UserAiMapper userAiMapper;

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

    @Override
    public List<AiRole> searchRoles(String q, String tag, String sort, Integer page, Integer size, Long userId, Boolean onlyNotFriend) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("isActive", 1)
                .eq("isDelete", 0);

        if (StrUtil.isNotBlank(q)) {
            String like = "%" + q.trim() + "%";
            qw.and("(roleName like ? or roleDescription like ?)", like, like);
        }
        if (StrUtil.isNotBlank(tag)) {
            // 简单包含匹配，生产可改 JSON_TAG 查询
            String likeTag = "%" + tag.trim() + "%";
            qw.and("(tags like ?)", likeTag);
        }

        // 排序：hot|new|name
        String s = StrUtil.blankToDefault(sort, "hot");
        switch (s) {
            case "new" -> qw.orderBy("createTime", false);
            case "name" -> qw.orderBy("roleName", true);
            default -> qw.orderBy("likes", false).orderBy("createTime", false);
        }

        int p = (page == null || page < 1) ? 1 : page;
        int sz = (size == null || size <= 0) ? 12 : size;
        qw.limit(sz).offset((p - 1) * sz);

        List<AiRole> list = this.list(qw);
        if (Boolean.TRUE.equals(onlyNotFriend) && userId != null && CollUtil.isNotEmpty(list)) {
            // 过滤掉已加好友的角色
            List<UserAi> binds = userAiMapper.selectListByQuery(QueryWrapper.create().eq("userId", userId).eq("isDelete", 0));
            Set<Long> friendRoleIds = binds.stream().map(UserAi::getRoleId).collect(Collectors.toSet());
            list = list.stream().filter(r -> !friendRoleIds.contains(r.getId())).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<String> listAllTags() {
        List<AiRole> roles = this.list(QueryWrapper.create().eq("isActive", 1).eq("isDelete", 0));
        Set<String> set = new LinkedHashSet<>();
        for (AiRole r : roles) {
            if (StrUtil.isBlank(r.getTags())) continue;
            String[] arr = r.getTags().split(",");
            for (String t : arr) {
                String s = t.trim();
                if (StrUtil.isNotBlank(s)) set.add(s);
            }
        }
        return new ArrayList<>(set);
    }

    @Override
    public boolean likeRole(Long roleId) {
        AiRole role = this.getById(roleId);
        if (role == null) return false;
        Integer likes = role.getLikes() == null ? 0 : role.getLikes();
        role.setLikes(likes + 1);
        return this.updateById(role);
    }
}
