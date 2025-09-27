package com.aih.chatpartner.service;

import com.aih.chatpartner.model.vo.FriendRoleVO;

import java.util.List;

public interface FriendService {
    List<FriendRoleVO> listFriends(Long userId);
    boolean addFriend(Long userId, Long roleId);
    boolean removeFriend(Long userId, Long roleId);
    boolean updatePin(Long userId, Long roleId, boolean pinned, Integer pinOrder);
}
