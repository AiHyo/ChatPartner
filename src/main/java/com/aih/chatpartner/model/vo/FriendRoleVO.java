package com.aih.chatpartner.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendRoleVO {
    private Long roleId;
    private String roleName;
    private String roleDescription;
    private String avatar;
    private String tags;
    private Integer likes;
    private Integer pinned;
    private Integer pinOrder;
    private LocalDateTime lastChatTime;
}
