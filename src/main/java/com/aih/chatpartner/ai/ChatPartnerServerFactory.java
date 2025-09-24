package com.aih.chatpartner.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * ChatPartner 服务创建工厂
 * </p>
 *
 * @author zeng.liqiang
 * @date 2025/9/24
 */
@Slf4j
@Configuration
public class ChatPartnerServerFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    /**
     * ChatPartner 服务创建工厂
     *
     * @return ChatPartnerServer 实例
     */
    @Bean
    public ChatPartnerServer chatPartnerServer() {
        log.info("Creating chatPartnerFactoryServer with ChatModel: {}", chatModel.getClass().getSimpleName());

        return AiServices.builder(ChatPartnerServer.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}
