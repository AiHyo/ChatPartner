# 语音聊天API测试文档

## 功能说明

本系统实现了基于七牛云语音识别(ASR)和语音合成(TTS)的AI语音聊天功能。用户可以通过语音与AI进行对话，系统将：
1. 将用户的语音转换为文本（ASR）
2. 使用LLM模型生成回复
3. 将回复文本转换为语音（TTS）

## 配置要求

### 1. 七牛云配置

在 `application-local.yml` 中添加七牛云API密钥：

```yaml
# 七牛云语音服务配置
qiniu:
  api-key: your-qiniu-api-key-here
  base-url: https://openai.qiniu.com/v1
  asr-ws-url: wss://openai.qiniu.com/v1/voice/asr
  tts-ws-url: wss://openai.qiniu.com/v1/voice/tts
```

### 2. 注意事项

- **ASR功能限制**：当前ASR功能需要音频文件的公网URL，在生产环境中需要配置七牛云对象存储服务
- **演示模式**：在未配置对象存储的情况下，ASR将返回模拟结果
- **TTS功能**：可正常使用，支持多种音色和语速调节

## API接口说明

### 1. 语音聊天（文件上传）

**接口地址**：`POST /api/voice/chat`

**请求参数**：
- `audio`: 音频文件（MultipartFile）
- `groupId`: 聊天分组ID（必填）
- `voiceType`: TTS音色类型（可选，如：qiniu_zh_female_tmjxxy）
- `speedRatio`: TTS语速比例（可选，范围：0.5-2.0）

**响应**：返回MP3格式的语音数据

**cURL示例**：
```bash
curl -X POST "http://localhost:8123/api/voice/chat" \
  -H "Cookie: SESSION=your-session-id" \
  -F "audio=@/path/to/audio.mp3" \
  -F "groupId=1" \
  -F "voiceType=qiniu_zh_female_tmjxxy" \
  -F "speedRatio=1.0" \
  --output response.mp3
```

### 2. 语音聊天（Base64格式）

**接口地址**：`POST /api/voice/chat/base64`

**请求体**：
```json
{
  "audioBase64": "音频的Base64编码",
  "audioFormat": "mp3",
  "groupId": 1,
  "voiceType": "qiniu_zh_female_tmjxxy",
  "speedRatio": 1.0
}
```

**响应**：
```json
{
  "code": 0,
  "data": "响应音频的Base64编码",
  "message": "ok"
}
```

### 3. 语音转文本

**接口地址**：`POST /api/voice/to-text`

**请求参数**：
- `audio`: 音频文件（MultipartFile）

**响应**：
```json
{
  "code": 0,
  "data": {
    "text": "识别出的文本内容"
  },
  "message": "ok"
}
```

### 4. 文本转语音

**接口地址**：`POST /api/voice/to-voice`

**请求体**：
```json
{
  "text": "要转换的文本",
  "voiceType": "qiniu_zh_female_tmjxxy",
  "speedRatio": 1.0
}
```

**响应**：返回MP3格式的语音数据

### 5. 文本转语音（Base64）

**接口地址**：`POST /api/voice/to-voice/base64`

**请求体**：
```json
{
  "text": "要转换的文本",
  "voiceType": "qiniu_zh_female_tmjxxy",
  "speedRatio": 1.0
}
```

**响应**：
```json
{
  "code": 0,
  "data": "音频的Base64编码",
  "message": "ok"
}
```

### 6. 获取音色列表

**接口地址**：`GET /api/voice/voices`

**响应**：
```json
{
  "code": 0,
  "data": [
    {
      "voiceType": "qiniu_zh_female_tmjxxy",
      "voiceName": "甜美教学小源",
      "category": "传统音色",
      "url": "https://aitoken-public.qnaigc.com/ai-voice/qiniu_zh_female_tmjxxy.mp3"
    }
    // ... 更多音色
  ],
  "message": "ok"
}
```

## 支持的音色

### 中文音色
- `qiniu_zh_female_tmjxxy` - 甜美教学小源
- `qiniu_zh_female_wwxkjx` - 温柔小客教学
- `qiniu_zh_male_ywxdkf` - 阳光小达开放
- `qiniu_zh_male_cgxksc` - 沉稳小康市场

## 测试步骤

### 1. 测试TTS功能
```bash
# 测试文本转语音
curl -X POST "http://localhost:8123/api/voice/to-voice/base64" \
  -H "Content-Type: application/json" \
  -H "Cookie: SESSION=your-session-id" \
  -d '{
    "text": "你好，我是AI语音助手，很高兴为您服务！",
    "voiceType": "qiniu_zh_female_tmjxxy",
    "speedRatio": 1.0
  }'
```

### 2. 测试完整语音聊天流程

1. 准备一个音频文件（MP3或WAV格式）
2. 使用上述语音聊天接口发送请求
3. 接收并播放返回的音频响应

### 3. 使用JavaScript测试

```javascript
// 文本转语音示例
async function textToSpeech(text) {
    const response = await fetch('http://localhost:8123/api/voice/to-voice/base64', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify({
            text: text,
            voiceType: 'qiniu_zh_female_tmjxxy',
            speedRatio: 1.0
        })
    });
    
    const result = await response.json();
    if (result.code === 0) {
        // 将Base64音频转换为Blob并播放
        const audioBase64 = result.data;
        const audioBlob = base64ToBlob(audioBase64, 'audio/mp3');
        const audioUrl = URL.createObjectURL(audioBlob);
        const audio = new Audio(audioUrl);
        audio.play();
    }
}

function base64ToBlob(base64, mimeType) {
    const byteCharacters = atob(base64);
    const byteNumbers = new Array(byteCharacters.length);
    for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
    }
    const byteArray = new Uint8Array(byteNumbers);
    return new Blob([byteArray], { type: mimeType });
}
```

## 故障排除

### 1. ASR返回演示模式消息
- **原因**：ASR需要音频文件的公网URL，当前未配置七牛云对象存储
- **解决方案**：
  - 在生产环境中配置七牛云对象存储
  - 实现音频文件上传功能
  - 或使用支持流式处理的WebSocket接口

### 2. TTS请求失败
- **可能原因**：
  - API密钥无效或过期
  - 网络连接问题
  - 文本过长（建议控制在500字以内）
  - 音色类型不正确

### 3. 权限错误
- **原因**：用户未登录或无权访问指定分组
- **解决方案**：确保用户已登录并使用正确的groupId

## 性能建议

1. **音频格式**：推荐使用MP3格式，文件较小，传输快速
2. **采样率**：16000Hz足够语音识别使用
3. **文本长度**：TTS单次请求文本建议不超过500字
4. **缓存**：可以缓存常用的TTS结果，避免重复生成

## 后续优化建议

1. **实现音频文件上传**：配置七牛云对象存储，实现完整的ASR功能
2. **WebSocket支持**：实现实时语音流处理
3. **音频格式转换**：支持更多音频格式的自动转换
4. **语音活动检测（VAD）**：自动检测语音开始和结束
5. **多语言支持**：添加英文等其他语言的语音识别和合成
