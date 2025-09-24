//package com.aih.chatpartner.config;
//
//import dev.langchain4j.model.chat.ChatModel;
//import dev.langchain4j.model.chat.StreamingChatModel;
//import dev.langchain4j.model.openai.OpenAiChatModel;
//import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.time.Duration;
//
///**
// * <p>
// * AI模型配置类
// * </p>
// *
// * @author zeng.liqiang
// */
//@Slf4j
//@Configuration
//public class AiModelConfig {
//
//    @Value("${langchain4j.open-ai.chat-model.base-url:https://api.openai.com/v1}")
//    private String baseUrl;
//
//    @Value("${langchain4j.open-ai.chat-model.api-key}")
//    private String apiKey;
//
//    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-3.5-turbo}")
//    private String modelName;
//
//    @Value("${langchain4j.open-ai.chat-model.log-requests:false}")
//    private boolean logRequests;
//
//    @Value("${langchain4j.open-ai.chat-model.log-responses:false}")
//    private boolean logResponses;
//
//    @Bean
//    public ChatModel chatModel() {
//        log.info("Initializing ChatModel with baseUrl: {}, modelName: {}", baseUrl, modelName);
//
//        return OpenAiChatModel.builder()
//                .baseUrl(baseUrl)
//                .apiKey(apiKey)
//                .modelName(modelName)
//                .timeout(Duration.ofMinutes(2))
//                .maxRetries(3)
//                .logRequests(logRequests)
//                .logResponses(logResponses)
//                .build();
//    }
//
//    @Bean
//    public StreamingChatModel streamingChatModel() {
//        log.info("Initializing StreamingChatModel with baseUrl: {}, modelName: {}", baseUrl, modelName);
//
//        return OpenAiStreamingChatModel.builder()
//                .baseUrl(baseUrl)
//                .apiKey(apiKey)
//                .modelName(modelName)
//                .timeout(Duration.ofMinutes(2))
//                .logRequests(logRequests)
//                .logResponses(logResponses)
//                .build();
//    }
//}
