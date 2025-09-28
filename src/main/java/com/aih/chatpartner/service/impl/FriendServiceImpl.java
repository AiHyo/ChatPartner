package com.aih.chatpartner.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.aih.chatpartner.exception.BusinessException;
import com.aih.chatpartner.exception.ErrorCode;
import com.aih.chatpartner.mapper.UserAiMapper;
import com.aih.chatpartner.model.entity.AiRole;
import com.aih.chatpartner.model.entity.ChatGroup;
import com.aih.chatpartner.model.entity.UserAi;
import com.aih.chatpartner.model.vo.FriendRoleVO;
import com.aih.chatpartner.service.AiRoleService;
import com.aih.chatpartner.service.ChatGroupService;
import com.aih.chatpartner.service.FriendService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;

@Service
public class FriendServiceImpl implements FriendService {

    @Resource
    private UserAiMapper userAiMapper;
    @Resource
    private ChatGroupService chatGroupService;
    @Resource
    private AiRoleService aiRoleService;

    @Override
    public List<FriendRoleVO> listFriends(Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        List<UserAi> binds = userAiMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("userId", userId)
                        .orderBy("pinned", false)
                        .orderBy("pinOrder", true)
        );
        List<FriendRoleVO> out = new ArrayList<>();
        if (CollUtil.isEmpty(binds)) return out;
        for (UserAi b : binds) {
            AiRole role = aiRoleService.getById(b.getRoleId());
            if (role == null) continue;
            ChatGroup latest = chatGroupService.getLatestGroupByUserAndRole(userId, b.getRoleId());
            FriendRoleVO vo = new FriendRoleVO();
            vo.setRoleId(role.getId());
            vo.setRoleName(role.getRoleName());
            vo.setRoleDescription(role.getRoleDescription());
            vo.setAvatar(role.getAvatar());
            vo.setTags(role.getTags());
            vo.setLikes(role.getLikes());
            vo.setPinned(b.getPinned());
            vo.setPinOrder(b.getPinOrder());
            vo.setLastChatTime(latest == null ? null : latest.getLastChatTime());
            out.add(vo);
        }
        // 再次按 pinned desc, pinOrder asc, lastChatTime desc 排序（双重保险）
        out.sort((a, b) -> {
            int c1 = Integer.compare(b.getPinned() == null ? 0 : b.getPinned(), a.getPinned() == null ? 0 : a.getPinned());
            if (c1 != 0) return c1;
            int c2 = Integer.compare(a.getPinOrder() == null ? 0 : a.getPinOrder(), b.getPinOrder() == null ? 0 : b.getPinOrder());
            if (c2 != 0) return c2;
            long t1 = a.getLastChatTime() == null ? 0L : a.getLastChatTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long t2 = b.getLastChatTime() == null ? 0L : b.getLastChatTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            return Long.compare(t2, t1);
        });
        return out;
    }

    @Override
    public boolean addFriend(Long userId, Long roleId) {
        if (userId == null || roleId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        
        // 查询是否已经是好友
        UserAi exist = userAiMapper.selectOneByQuery(QueryWrapper.create()
            .eq("userId", userId).eq("roleId", roleId));
            
        if (exist != null) {
            return true; // 已经是好友
        }
        
        // 添加新的好友关系
        UserAi rec = new UserAi();
        rec.setUserId(userId);
        rec.setRoleId(roleId);
        rec.setPinned(0);
        rec.setPinOrder(0);
        rec.setCreateTime(LocalDateTime.now());
        rec.setUpdateTime(LocalDateTime.now());
        return userAiMapper.insert(rec) > 0;
    }

    @Override
    public boolean removeFriend(Long userId, Long roleId) {
        if (userId == null || roleId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        UserAi exist = userAiMapper.selectOneByQuery(QueryWrapper.create().eq("userId", userId).eq("roleId", roleId));
        if (exist == null) return true;
        // 物理删除好友关系
        return userAiMapper.deleteById(exist.getId()) > 0;
    }

    @Override
    public boolean updatePin(Long userId, Long roleId, boolean pinned, Integer pinOrder) {
        if (userId == null || roleId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);
        UserAi exist = userAiMapper.selectOneByQuery(QueryWrapper.create().eq("userId", userId).eq("roleId", roleId));
        if (exist == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "好友不存在");
        exist.setPinned(pinned ? 1 : 0);
        exist.setPinOrder(pinned ? (pinOrder == null ? 0 : pinOrder) : 0);
        exist.setUpdateTime(LocalDateTime.now());
        return userAiMapper.update(exist) > 0;
    }
}
