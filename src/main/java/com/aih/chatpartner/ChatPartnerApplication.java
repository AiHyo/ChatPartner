package com.aih.chatpartner;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aih.chatpartner.mapper")
public class ChatPartnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatPartnerApplication.class, args);
    }

}
