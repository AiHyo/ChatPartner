package com.aih.chatpartner.voice.stream;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 LLM token 流按标点/长度阈值切分为句子
 */
public class TextChunker {

    private final StringBuilder buffer = new StringBuilder();
    private final int maxLen;

    public TextChunker() {
        this(60); // 默认每 ~60 字/标点切一次
    }

    public TextChunker(int maxLen) {
        this.maxLen = Math.max(20, maxLen);
    }

    /**
     * 追加 token，并返回可输出的完整句子列表
     */
    public synchronized List<String> appendAndExtract(String token) {
        List<String> out = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            return out;
        }
        buffer.append(token);

        // 标点优先作为切分点
        int idx = findSentenceBreak(buffer);
        if (idx >= 0) {
            String sentence = buffer.substring(0, idx + 1).trim();
            if (!sentence.isEmpty()) {
                out.add(sentence);
            }
            buffer.delete(0, idx + 1);
            return out;
        }

        // 长度阈值兜底
        if (buffer.length() >= maxLen) {
            String sentence = buffer.toString().trim();
            if (!sentence.isEmpty()) {
                out.add(sentence);
                buffer.setLength(0);
            }
        }
        return out;
    }

    /**
     * 冲刷剩余文本为一句
     */
    public synchronized String flushRemainder() {
        String rest = buffer.toString().trim();
        buffer.setLength(0);
        return rest;
    }

    private int findSentenceBreak(CharSequence sb) {
        String s = sb.toString();
        int idx = -1;
        char[] punct = {'。', '！', '？', '.', '!', '?', '\n'};
        for (char p : punct) {
            int i = s.indexOf(p);
            if (i >= 0) {
                if (idx == -1 || i < idx) {
                    idx = i;
                }
            }
        }
        return idx;
    }
}
