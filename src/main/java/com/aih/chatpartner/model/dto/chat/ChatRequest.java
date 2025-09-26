package com.aih.chatpartner.model.dto.chat;

import lombok.Data;

import java.io.Serializable;

/**
 * 聊天请求
 *
 * @author AiHyo
 */
@Data
public class ChatRequest implements Serializable {

    /**
     * 分组id
     */
    private Long groupId;

    /**
     * 消息内容
     */
    private String message;

    private static final long serialVersionUID = 1L;
}
