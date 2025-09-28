package com.aih.chatpartner.model.dto.admin;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdminRoleUpsertRequest implements Serializable {
    private Long id; // null for create, not null for update

    private String roleName;
    private String roleDescription;
    private String greeting;
    private String systemPrompt;
    private String avatar;
    private String tags; // comma separated or json

    private Integer isSystem; // 0/1
    private Integer isActive; // 0/1
}
