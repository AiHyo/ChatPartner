package com.aih.chatpartner.model.entity;

import com.mybatisflex.annotation.*;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-AI 角色映射（好友）
 */
@Table("user_ai")
@Data
public class UserAi implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("userId")
    private Long userId;

    @Column("roleId")
    private Long roleId;

    @Column("pinned")
    private Integer pinned;

    @Column("pinOrder")
    private Integer pinOrder;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
