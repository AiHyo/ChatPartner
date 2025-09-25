package com.aih.chatpartner.ai;

import dev.langchain4j.service.SystemMessage;

/**
 * 喜羊羊大王AI服务接口
 *
 * @author zeng.liqiang
 * @date 2025/9/24
 */
public interface AiService {

    /**
     * 与喜羊羊大王聊天
     *
     * @param userMessage 用户消息
     * @return 喜羊羊的回复
     */
    @SystemMessage(fromResource = "prompt/xiyangyang-prompt.txt")
    String chatInXiYangYangRole(String userMessage);
}
