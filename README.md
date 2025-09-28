# ChatPartner - 语音聊天助手

一个可以语音对话的AI助手，支持实时语音交流，就像和真人聊天一样。

## 快速开始

### 第一步：配置AI模型

在 `src/main/resources` 文件夹里创建 `application-local.yml` 文件，填入你的AI配置：

```yaml
# AI模型配置（必填）
ai:
  base-url: "你的AI服务地址"    # 比如：https://api.deepseek.com/v1
  api-key: "你的API密钥"       # 从AI服务商那里获取
  model: "模型名称"            # 比如：deepseek-v3
```

> 💡 我们默认用的是DeepSeek，你也可以换成其他支持OpenAI格式的AI服务

---

### 第二步：配置语音服务（可选）

如果你想用语音对话功能，需要申请七牛云的语音服务：

```yaml
# 七牛云语音服务（语音对话需要）
qiniu:
  api-key: "你的七牛云密钥"
```

## 运行项目

### 准备工作

你需要安装：
- Java 21
- Node.js 22
- MySQL、Redis

**1. 启动后端**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**2. 启动前端**

```bash
cd chat-partner-frontend
npm install
npm run dev
```

**3. 打开浏览器**

访问 `http://localhost:5173` 就可以开始聊天了！

## 常见问题

**Q: 连不上怎么办？**
- 检查后端是否启动成功（看到Spring Boot启动日志）
- 确认端口没被占用

**Q: 语音功能用不了？**
- 浏览器需要允许麦克风权限
- 检查七牛云配置是否正确

**Q: AI不回复？**
- 检查AI配置的地址和密钥是否正确
- 看看后端日志有没有报错

---

## 项目特色

### 🎯 主要功能
- **语音对话**：像打电话一样和AI聊天
- **文字聊天**：传统的打字聊天方式  
- **多角色**：可以和不同性格的AI角色对话
- **实时响应**：AI边想边说，不用等很久

### 🛠️ 技术特点
- **纯净架构**：不依赖第三方AI平台，完全自主可控
- **实时流式**：从说话到听到回复，全程流畅无卡顿
- **开发友好**：代码结构清晰，容易修改和扩展

---

## 适合什么人用？

### 👥 目标用户
- **内容创作者**：想快速把想法变成文字，但打字太慢
- **学生/上班族**：需要整理笔记、翻译文档、提取重点
- **开发者**：想测试AI功能，但不想被第三方平台限制
- **普通用户**：就是想有个AI朋友聊聊天

### 💡 使用场景
- 开车时口述想法，AI帮你整理成文字
- 把中文文档快速翻译成英文邮件
- 会议结束后，让AI提取出待办事项
- 测试不同AI角色的对话效果

## 技术架构

### 🏗️ 语音聊天实现原理
```
用户说话 → 浏览器录音 → WebSocket传输 → 七牛ASR识别 → 
AI流式生成 → 文本分句 → 七牛TTS合成 → 音频流返回 → 浏览器播放
```

### 🎙️ 核心实现

#### 1. 前端音频处理
```javascript
// 浏览器录音：16kHz/16bit/mono PCM
navigator.mediaDevices.getUserMedia({ audio: true })
// 实时传输音频数据到后端
websocket.send(pcmAudioData)
```

#### 2. 后端流式处理
```java
@Component
public class VoiceStreamHandler {
    // WebSocket接收音频 → 七牛ASR → LLM流式生成 → TTS合成
    // 全程流式处理，最小化延迟
}
```

#### 3. 关键技术点
- **实时音频流**：浏览器 ↔ 后端 WebSocket 双向通信
- **流式AI生成**：LLM token 逐个生成，边生成边合成语音
- **智能分句**：按标点符号切分，每句话独立TTS合成
- **背压控制**：防止音频积压，保证实时性

### 🔧 技术栈
- **后端**：Spring Boot + WebSocket + LangChain4j
- **前端**：Vue 3 + Web Audio API
- **AI模型**：支持OpenAI格式的任意LLM
- **语音服务**：七牛云 ASR + TTS
- **数据库**：MySQL + Redis
