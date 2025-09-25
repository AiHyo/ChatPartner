package com.aih.chatpartner.controller;

import com.aih.chatpartner.ai.AiService;
import com.aih.chatpartner.common.BaseResponse;
import com.aih.chatpartner.common.ResultUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 喜羊羊聊天控制器
 *
 * @author zeng.liqiang
 * @date 2025/9/24
 */
@RestController
@RequestMapping("/chat")
@Slf4j
public class ChatController {

    @Resource
    private AiService aiService;

    /**
     * 与喜羊羊聊天
     */
    @PostMapping("/xiyanyang")
    public BaseResponse<?> chatWithXiYangYang(@RequestBody ChatRequest request) {
        try {
            String userMessage = request.getMessage();
            if (userMessage == null || userMessage.trim().isEmpty()) {
                return ResultUtils.error(400, "消息不能为空咩~");
            }

            log.info("用户消息: {}", userMessage);
            String response = aiService.chatInXiYangYangRole(userMessage);
            log.info("喜羊羊回复: {}", response);

            return ResultUtils.success(response);
        } catch (Exception e) {
            log.error("聊天出错了咩: ", e);
            return ResultUtils.error(500, "哎呀，出错了咩~ " + e.getMessage());
        }
    }

    /**
     * 聊天请求体
     */
    public static class ChatRequest {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
