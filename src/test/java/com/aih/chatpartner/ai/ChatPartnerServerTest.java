package com.aih.chatpartner.ai;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ChatPartnerServerTest {

    @Resource
    private ChatPartnerServer chatPartnerServer;

    @Test
    void chatInXiYangYangRole() {
        String res = chatPartnerServer.chatInXiYangYangRole("你好呀，今天深圳台风，还得居家办公~唉~好想有更多时间自学AI~");
        System.out.println(res);
    }
}
