package com.aih.chatpartner.model.dto.chathistory;

import com.aih.chatpartner.model.entity.ChatHistory;
import lombok.Data;

import java.util.List;

@Data
public class ChatHistoryCursorPageResponse {
    private List<ChatHistory> items;
    private String nextCursor; // 格式：epochMillis,id
    private boolean hasMore;
}
