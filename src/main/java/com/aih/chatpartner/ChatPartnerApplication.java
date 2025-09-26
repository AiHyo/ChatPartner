package com.aih.chatpartner;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.aih.chatpartner.mapper")
public class ChatPartnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatPartnerApplication.class, args);
    }

}
