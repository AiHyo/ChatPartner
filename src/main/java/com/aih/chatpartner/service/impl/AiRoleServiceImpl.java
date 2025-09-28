package com.aih.chatpartner.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aih.chatpartner.model.dto.admin.AdminRoleUpsertRequest;
import com.aih.chatpartner.mapper.AiRoleMapper;
import com.aih.chatpartner.mapper.UserAiMapper;
import com.aih.chatpartner.mapper.RoleLikeMapper;
import com.aih.chatpartner.model.entity.AiRole;
import com.aih.chatpartner.model.entity.UserAi;
import com.aih.chatpartner.model.entity.RoleLike;
import com.aih.chatpartner.service.AiRoleService;
import com.aih.chatpartner.common.PageResponse;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    @Resource
    private RoleLikeMapper roleLikeMapper;

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
        QueryWrapper qw = buildQuery(q, tag, sort);

        int p = (page == null || page < 1) ? 1 : page;
        int sz = (size == null || size <= 0) ? 12 : size;
        qw.limit(sz).offset((p - 1) * sz);

        List<AiRole> list = this.list(qw);
        if (Boolean.TRUE.equals(onlyNotFriend) && userId != null && CollUtil.isNotEmpty(list)) {
            // 过滤掉已加好友的角色
            List<UserAi> binds = userAiMapper.selectListByQuery(QueryWrapper.create().eq("userId", userId));
            Set<Long> friendRoleIds = binds.stream().map(UserAi::getRoleId).collect(Collectors.toSet());
            list = list.stream().filter(r -> !friendRoleIds.contains(r.getId())).collect(Collectors.toList());
        }
        return list;
    }

    private QueryWrapper buildQuery(String q, String tag, String sort) {
        QueryWrapper qw = QueryWrapper.create()
            .eq("isActive", 1)
            .eq("isDelete", 0);

        if (StrUtil.isNotBlank(q)) {
            String like = "%" + q.trim() + "%";
            qw.and("(roleName like ? or roleDescription like ?)", like, like);
        }
        if (StrUtil.isNotBlank(tag)) {
            String likeTag = "%" + tag.trim() + "%";
            qw.and("(tags like ?)", likeTag);
        }

        String s = StrUtil.blankToDefault(sort, "hot");
        switch (s) {
            case "new" -> qw.orderBy("createTime", false);
            case "name" -> qw.orderBy("roleName", true);
            default -> qw.orderBy("likes", false).orderBy("createTime", false);
        }
        return qw;
    }

    private QueryWrapper buildBaseQuery(String q, String tag) {
        QueryWrapper qw = QueryWrapper.create()
            .eq("isActive", 1)
            .eq("isDelete", 0);
        if (StrUtil.isNotBlank(q)) {
            String like = "%" + q.trim() + "%";
            qw.and("(roleName like ? or roleDescription like ?)", like, like);
        }
        if (StrUtil.isNotBlank(tag)) {
            String likeTag = "%" + tag.trim() + "%";
            qw.and("(tags like ?)", likeTag);
        }
        return qw;
    }

    @Override
    public PageResponse<AiRole> searchRolesPage(String q, String tag, String sort, Integer page, Integer size, Long userId, Boolean onlyNotFriend) {
        int p = (page == null || page < 1) ? 1 : page;
        int sz = (size == null || size <= 0) ? 12 : size;

        // 基础查询（不带分页、无排序）统计总数
        QueryWrapper baseQw = buildBaseQuery(q, tag);
        long total;
        List<AiRole> all;
        if (Boolean.TRUE.equals(onlyNotFriend) && userId != null) {
            // 简化实现：取全量后做过滤再计数（数据量小时可接受）
            all = this.list(baseQw);
            List<UserAi> binds = userAiMapper.selectListByQuery(QueryWrapper.create().eq("userId", userId));
            Set<Long> friendRoleIds = binds.stream().map(UserAi::getRoleId).collect(Collectors.toSet());
            List<AiRole> filtered = all.stream().filter(r -> !friendRoleIds.contains(r.getId())).collect(Collectors.toList());
            total = filtered.size();
        } else {
            total = this.count(baseQw);
        }

        // 分页查询
        QueryWrapper pageQw = buildQuery(q, tag, sort).limit(sz).offset((p - 1) * sz);
        List<AiRole> items = this.list(pageQw);
        if (Boolean.TRUE.equals(onlyNotFriend) && userId != null && CollUtil.isNotEmpty(items)) {
            List<UserAi> binds = userAiMapper.selectListByQuery(QueryWrapper.create().eq("userId", userId));
            Set<Long> friendRoleIds = binds.stream().map(UserAi::getRoleId).collect(Collectors.toSet());
            items = items.stream().filter(r -> !friendRoleIds.contains(r.getId())).collect(Collectors.toList());
        }
        return PageResponse.of(items, total, p, sz);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long roleId, Long userId) {
        if (roleId == null || userId == null) return false;
        
        AiRole role = this.getById(roleId);
        if (role == null) return false;
        int current = role.getLikes() == null ? 0 : role.getLikes();
        
        // 查询是否已点赞
        RoleLike existingLike = roleLikeMapper.selectOneByQuery(QueryWrapper.create()
            .eq("userId", userId).eq("roleId", roleId));
        
        if (existingLike != null) {
            // 已点赞，取消点赞（物理删除）
            roleLikeMapper.deleteById(existingLike.getId());
            role.setLikes(Math.max(0, current - 1));
            this.updateById(role);
            return false;
        } else {
            // 未点赞，添加点赞
            RoleLike rl = new RoleLike();
            rl.setUserId(userId);
            rl.setRoleId(roleId);
            rl.setCreateTime(LocalDateTime.now());
            rl.setUpdateTime(LocalDateTime.now());
            roleLikeMapper.insert(rl);
            role.setLikes(current + 1);
            this.updateById(role);
            return true;
        }
    }

    @Override
    public List<Long> listLikedRoleIds(Long userId) {
        if (userId == null) return Collections.emptyList();
        List<RoleLike> likes = roleLikeMapper.selectListByQuery(
            QueryWrapper.create().eq("userId", userId)
        );
        return likes.stream().map(RoleLike::getRoleId).toList();
    }

    /**
     * 管理端：分页
     */
    @Override
    public PageResponse<AiRole> adminPage(String q, String tag, String sort, Integer page, Integer size,
                                          Integer isActive, Integer isSystem) {
        int p = (page == null || page < 1) ? 1 : page;
        int sz = (size == null || size <= 0) ? 12 : size;

        QueryWrapper base = QueryWrapper.create()
            .eq("isDelete", 0);
        if (StrUtil.isNotBlank(q)) {
            String like = "%" + q.trim() + "%";
            base.and("(roleName like ? or roleDescription like ?)", like, like);
        }
        if (StrUtil.isNotBlank(tag)) {
            String likeTag = "%" + tag.trim() + "%";
            base.and("(tags like ?)", likeTag);
        }
        if (isActive != null) {
            base.eq("isActive", isActive);
        }
        if (isSystem != null) {
            base.eq("isSystem", isSystem);
        }

        long total = this.count(base);

        QueryWrapper pageQw = base.clone();
        String s = StrUtil.blankToDefault(sort, "new");
        switch (s) {
            case "name" -> pageQw.orderBy("roleName", true);
            case "hot" -> pageQw.orderBy("likes", false).orderBy("createTime", false);
            default -> pageQw.orderBy("createTime", false);
        }
        pageQw.limit(sz).offset((p - 1) * sz);
        List<AiRole> items = this.list(pageQw);
        return PageResponse.of(items, total, p, sz);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long adminCreate(AdminRoleUpsertRequest req, Long adminUserId) {
        if (req == null || StrUtil.isBlank(req.getRoleName())) {
            return null;
        }
        AiRole r = new AiRole();
        r.setRoleName(req.getRoleName().trim());
        r.setRoleDescription(StrUtil.blankToDefault(req.getRoleDescription(), null));
        r.setGreeting(StrUtil.blankToDefault(req.getGreeting(), "你好！我是新的AI角色。"));
        r.setSystemPrompt(StrUtil.blankToDefault(req.getSystemPrompt(), ""));
        r.setAvatar(StrUtil.blankToDefault(req.getAvatar(), null));
        r.setTags(StrUtil.blankToDefault(req.getTags(), null));
        r.setLikes(0);
        r.setIsSystem(req.getIsSystem() == null ? 0 : req.getIsSystem());
        r.setIsActive(req.getIsActive() == null ? 1 : req.getIsActive());
        // 系统角色可不设置 creatorId
        if (r.getIsSystem() != null && r.getIsSystem() == 0) {
            r.setCreatorId(adminUserId);
        }
        boolean ok = this.save(r);
        return ok ? r.getId() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adminUpdate(AdminRoleUpsertRequest req) {
        if (req == null || req.getId() == null) return false;
        AiRole exist = this.getById(req.getId());
        if (exist == null || (exist.getIsDelete() != null && exist.getIsDelete() == 1)) return false;
        if (StrUtil.isNotBlank(req.getRoleName())) exist.setRoleName(req.getRoleName().trim());
        exist.setRoleDescription(StrUtil.blankToDefault(req.getRoleDescription(), exist.getRoleDescription()));
        if (req.getGreeting() != null) exist.setGreeting(req.getGreeting());
        if (req.getSystemPrompt() != null) exist.setSystemPrompt(req.getSystemPrompt());
        if (req.getAvatar() != null) exist.setAvatar(req.getAvatar());
        if (req.getTags() != null) exist.setTags(req.getTags());
        if (req.getIsSystem() != null) exist.setIsSystem(req.getIsSystem());
        if (req.getIsActive() != null) exist.setIsActive(req.getIsActive());
        return this.updateById(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adminChangeStatus(Long id, Integer isActive) {
        if (id == null || isActive == null) return false;
        AiRole r = new AiRole();
        r.setId(id);
        r.setIsActive(isActive);
        return this.updateById(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adminDelete(Long id) {
        if (id == null) return false;
        AiRole r = new AiRole();
        r.setId(id);
        r.setIsDelete(1);
        return this.updateById(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adminDeleteBatch(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) return false;
        boolean ok = true;
        for (Long id : ids) {
            AiRole r = new AiRole();
            r.setId(id);
            r.setIsDelete(1);
            ok = ok && this.updateById(r);
        }
        return ok;
    }
}
