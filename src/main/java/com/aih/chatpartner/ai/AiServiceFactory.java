package com.aih.chatpartner.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * <p>
 * AI 服务创建工厂
 * </p>
 *
 * @author zeng.liqiang
 * @date 2025/9/24
 */
@Slf4j
@Configuration
public class AiServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;


    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<Long, AiService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
            })
            .build();


    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiService aiCodeGeneratorService() {
        return getAiService(0L);
    }

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiService getAiService(Long appId) {
        return serviceCache.get(appId, this::createAiService);
    }

    /**
     * 创建 AI 代码生成服务
     *
     * @return
     */
    public AiService createAiService(Long appId) {
        log.info("Creating AiService with ChatModel: {}", chatModel.getClass().getSimpleName());

        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();

        // 从数据库加载历史对话到记忆中 - 懒加载
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);

        return AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemory(chatMemory)
                .build();
    }

    /**
     * ChatPartner 服务创建工厂
     *
     * @return ChatPartnerServer 实例
     */
    @Bean
    public AiService chatPartnerServer() {
        log.info("Creating chatPartnerFactoryServer with ChatModel: {}", chatModel.getClass().getSimpleName());

        return AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}
