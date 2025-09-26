package com.aih.chatpartner.model.enums;

import lombok.Getter;

/**
 * 消息类型枚举
 *
 * @author AiHyo
 */
@Getter
public enum MessageTypeEnum {

    USER("user", "用户消息"),
    AI("ai", "AI回复");

    private final String value;
    private final String text;

    MessageTypeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据 value 获取枚举
     */
    public static MessageTypeEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (MessageTypeEnum anEnum : MessageTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
