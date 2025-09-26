package com.aih.chatpartner.model.entity;

import com.mybatisflex.annotation.*;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI角色
 *
 * @author AiHyo
 */
@Table("ai_role")
@Data
public class AiRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 角色名称
     */
    @Column("roleName")
    private String roleName;

    /**
     * 角色描述
     */
    @Column("roleDescription")
    private String roleDescription;

    /**
     * 角色问候语
     */
    @Column("greeting")
    private String greeting;

    /**
     * 系统提示词（包含个性设定）
     */
    @Column("systemPrompt")
    private String systemPrompt;

    /**
     * 角色头像URL
     */
    @Column("avatar")
    private String avatar;

    /**
     * 创建者ID（系统角色为null）
     */
    @Column("creatorId")
    private Long creatorId;

    /**
     * 是否系统预设角色
     */
    @Column("isSystem")
    private Integer isSystem;

    /**
     * 是否启用
     */
    @Column("isActive")
    private Integer isActive;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
