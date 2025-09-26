package com.aih.chatpartner.model.dto.chatgroup;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新对话分组请求
 *
 * @author AiHyo
 */
@Data
public class ChatGroupUpdateRequest implements Serializable {

    /**
     * 分组id
     */
    private Long id;

    /**
     * 分组名称
     */
    private String groupName;

    /**
     * AI角色ID
     */
    private Long roleId;

    private static final long serialVersionUID = 1L;
}
