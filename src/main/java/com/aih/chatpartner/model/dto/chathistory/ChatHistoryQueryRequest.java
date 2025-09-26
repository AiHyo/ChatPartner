package com.aih.chatpartner.model.dto.chathistory;

import lombok.Data;

import java.io.Serializable;

/**
 * 查询对话历史请求
 *
 * @author AiHyo
 */
@Data
public class ChatHistoryQueryRequest implements Serializable {

    /**
     * 分组id
     */
    private Long groupId;

    /**
     * 当前页号
     */
    private long current = 1;

    /**
     * 页面大小
     */
    private long pageSize = 10;

    private static final long serialVersionUID = 1L;
}
