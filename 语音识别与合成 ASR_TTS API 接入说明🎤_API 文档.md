本文档介绍如何使用七牛云的语音识别（ASR）和语音合成（TTS）相关 API，包括接口说明、参数、响应结构、示例等。

------

## 公共信息

### **API 接入点**

API 提供两个接入域名：

- **主要接入点**：`https://openai.qiniu.com/v1` （推荐使用）
- **备用接入点**：`https://api.qnaigc.com/v1` （备用域名）

建议优先使用主要接入点 `openai.qiniu.com`，如遇网络问题可切换至备用接入点。

### 鉴权方式

所有接口均需在请求头中携带鉴权信息：

- Header 名称：`Authorization`
- Header 格式：`Bearer <你的七牛云 AI API KEY>`

示例：

```
Authorization: Bearer <你的七牛云 AI API KEY>
```

------

## 1. 获取接口密钥

在调用 API 前，请先获取七牛云的 AI Token API 密钥。

------

## 2. 支持接口列表

🎤

| 接口名      | 说明         |
| :---------- | :----------- |
| /voice/asr  | 语音文字识别 |
| /voice/tts  | 文字生成语音 |
| /voice/list | 获取音色列表 |

------

## 3. 语音文字识别（ASR）

- 支持中英等多语种语音到文字的识别，嘈杂环境下识别准确率超95%。
- 支持音频格式：raw / wav / mp3 / ogg。
- ASR 接口的输出可作为 AI 推理的输入文本。

### 3.1 请求方式

- URL: `POST /voice/asr`
- Content-Type: `application/json`

#### 请求参数

| 字段      | 类型   | 必填 | 说明                 | 示例值    |
| :-------- | :----- | :--- | :------------------- | :-------- |
| model     | string | 是   | 模型名称，固定为 asr | asr       |
| audio     | object | 是   | 音频参数             | 见下      |
| └─ format | string | 是   | 音频格式（如 mp3）   | mp3       |
| └─ url    | string | 是   | 音频文件的公网 URL   | https://… |

#### 请求示例

```
{
  "model": "asr",
  "audio": {
    "format": "mp3",
    "url": "https://static.qiniu.com/ai-inference/example-resources/example.mp3"
  }
}
```

#### 响应结构

| 字段          | 类型   | 说明                 |
| :------------ | :----- | :------------------- |
| reqid         | string | 请求唯一标识         |
| operation     | string | 操作类型，固定为 asr |
| data          | object | 识别结果             |
| └─ audio_info | object | 音频信息             |
| └─ duration   | int    | 音频时长（毫秒）     |
| └─ result     | object | 识别文本及附加信息   |
| └─ additions  | object | 附加信息             |
| └─ duration   | string | 音频时长（字符串）   |
| └─ text       | string | 识别出的文本         |

#### 响应示例

```
{
  "reqid": "bdf5e1b1bcaca22c7a9248aba2804912",
  "operation": "asr",
  "data": {
    "audio_info": { "duration": 9336 },
    "result": {
      "additions": { "duration": "9336" },
      "text": "七牛的文化是做一个简单的人，做一款简单的产品，做一家简单的公司。"
    }
  }
}
```

#### curl 请求示例

```
export OPENAI_BASE_URL="https://openai.qiniu.com/v1"
export OPENAI_API_KEY="<你的七牛云 AI API KEY>"

curl --location "$OPENAI_BASE_URL/voice/asr" \
--header "Content-Type: application/json" \
--header "Authorization: Bearer $OPENAI_API_KEY" \
--data '{
    "model":"asr",
    "audio": {
        "format": "mp3",
        "url": "https://static.qiniu.com/ai-inference/example-resources/example.mp3"
    }
}'
```

### 基于 NodeJS 的实时语音识别推理代码示例

该示例包含实时的语音识别并调用七牛云的 LLM 进行推理的代码，启动之后，对着麦克风说话即可开始测试。

```
// 录音转文本并调用 OpenAI LLM
// 依赖：node-record-lpcm16、node-fetch、openai、pako、ws、uuid
// 其中 node-record-lpcm16 依赖 sox，需要提前安装，可以参考该文档：https://github.com/gillesdemey/node-record-lpcm16

const pako = require('pako')
const WebSocket = require('ws')
const { OpenAI } = require('openai')
const { v4: uuidv4 } = require('uuid')
const record = require('node-record-lpcm16')

// 配置
const OPENAI_MODEL = 'deepseek-v3' // 可根据需要更换
const OPENAI_KEY = '你的 OPENAI_TOKEN'
const OPENAI_BASE_URL = 'https://openai.qiniu.com/v1'
const openai = new OpenAI({ baseURL: OPENAI_BASE_URL, apiKey: OPENAI_KEY })

class LiveASR2LLM {
  constructor({
    wsUrl = 'wss://openai.qiniu.com/v1/voice/asr',
    token = OPENAI_KEY,
    sampleRate = 16000,
    channels = 1,
    bits = 16,
    segDuration = 300,
    silenceTimeout = 1500,
    openaiModel = OPENAI_MODEL
  } = {}) {
    this.wsUrl = wsUrl
    this.token = token
    this.sampleRate = sampleRate
    this.channels = channels
    this.bits = bits
    this.segDuration = segDuration
    this.silenceTimeout = silenceTimeout
    this.openaiModel = openaiModel
    this.seq = 1
    this.ws = null
    this.llmOffset = 0
    this.llmTimer = null
    this.lastAsrText = ''
  }

  /**
   * 生成协议头
   */
  generateHeader(messageType = 1, flags = 1, serial = 1, compress = 1) {
    const header = Buffer.alloc(4)
    header[0] = (1 << 4) | 1
    header[1] = (messageType << 4) | flags
    header[2] = (serial << 4) | compress
    header[3] = 0
    return header
  }
  /**
   * 生成协议头后的序列号部分
   */
  generateBeforePayload(sequence) {
    const buf = Buffer.alloc(4)
    buf.writeInt32BE(sequence)
    return buf
  }
  /**
   * 发送 ASR 配置包
   */
  sendConfig() {
    const req = {
      user: { uid: uuidv4() },
      audio: {
        format: 'pcm', sample_rate: this.sampleRate, bits: this.bits, channel: this.channels, codec: 'raw'
      },
      request: { model_name: 'asr', enable_punc: true }
    }
    let payload = Buffer.from(JSON.stringify(req), 'utf8')
    payload = pako.gzip(payload)
    const msg = Buffer.concat([
      this.generateHeader(1, 1, 1, 1),
      this.generateBeforePayload(this.seq),
      Buffer.alloc(4, 0),
      payload
    ])
    msg.writeInt32BE(payload.length, 8)
    this.ws.send(msg)
  }
  /**
   * 发送音频分片数据
   */
  sendAudioChunk(chunk) {
    this.seq++
    const compressed = pako.gzip(chunk)
    const msg = Buffer.concat([
      this.generateHeader(2, 1, 1, 1),
      this.generateBeforePayload(this.seq),
      Buffer.alloc(4, 0),
      compressed
    ])
    msg.writeInt32BE(compressed.length, 8)
    this.ws.send(msg)
  }
  /**
   * 解析服务端返回的文本内容，兼容多种协议格式
   * @param {Buffer} data - WebSocket 接收到的原始数据
   * @returns {string} 识别出的文本
   */
  parseTextFromResponse(data) {
    try {
      if (!Buffer.isBuffer(data)) return ''
      const headerSize = data[0] & 0x0f
      const messageType = data[1] >> 4
      const messageTypeSpecificFlags = data[1] & 0x0f
      const serializationMethod = data[2] >> 4
      const messageCompression = data[2] & 0x0f
      let payload = data.slice(headerSize * 4)
      if (messageTypeSpecificFlags & 0x01) {
        payload = payload.slice(4)
      }
      if (messageType === 0b1001 && payload.length >= 4) {
        const payloadSize = payload.readInt32BE(0)
        payload = payload.slice(4, 4 + payloadSize)
      }
      if (messageCompression === 0b0001) {
        payload = pako.ungzip(payload)
      }
      let obj
      if (serializationMethod === 0b0001) {
        obj = JSON.parse(payload.toString('utf8'))
      } else {
        obj = payload.toString('utf8')
      }
      if (obj && obj.result && obj.result.text) return obj.result.text
      if (obj && obj.payload_msg && obj.payload_msg.result && obj.payload_msg.result.text) return obj.payload_msg.result.text
      if (typeof obj === 'string') return obj
      return ''
    } catch (e) {
      console.error('[ASR] parseTextFromResponse 解析失败:', e)
      return ''
    }
  }
  /**
   * 调用 LLM 进行推理，仅发送本次新增文本
   * @param {string} text - 新识别出的文本
   */
  tryCallLLM(text) {
    if (!text) return
    console.log('[LLM] 发送:', text)
    openai.chat.completions.create({
      model: this.openaiModel,
      messages: [
        { role: 'system', content: '你是一个语音助手。' },
        { role: 'user', content: text }
      ]
    })
      .then(chatCompletion => {
        const reply = chatCompletion.choices[0].message.content
        console.log('[LLM] 回复:', reply)
      })
      .catch(err => console.error('[LLM] 推理失败:', err))
  }
  /**
   * 启动 WebSocket 连接并监听麦克风，自动识别与推理
   */
  start() {
    this.ws = new WebSocket(this.wsUrl, {
      headers: { Authorization: `Bearer ${this.token}` }
    })
    let rec = null
    let stream = null
    let isLLMReplying = false
    this.ws.on('open', () => {
      try {
        this.sendConfig()
        console.log('[ASR] WebSocket 连接已建立，开始录音...')
        rec = record.record({ sampleRate: this.sampleRate, channels: this.channels, threshold: 0, verbose: false, recordProgram: 'sox', silence: '1.0' })
        stream = rec.stream()
        stream.on('data', chunk => {
          if (!isLLMReplying) {
            try {
              this.sendAudioChunk(chunk)
            } catch (err) {
              console.error('[ASR] 音频分片发送失败:', err)
            }
          }
        })
        stream.on('error', err => {
          console.error('[ASR] 录音流错误:', err)
        })
      } catch (err) {
        console.error('[ASR] WebSocket open 阶段异常:', err)
      }
    })
    this.ws.on('message', async (data) => {
      try {
        const text = this.parseTextFromResponse(data)
        if (text === this.lastAsrText) return // 内容没变化
        this.lastAsrText = text
        // 检测 asr 内容与上次处理的位置是否更长
        // 更长说明是有新的识别结果
        if (text.length > this.llmOffset) {
          const newText = text.slice(this.llmOffset).trim()
          if (newText) {
            console.log('[ASR] 识别文本:', newText)
            if (this.llmTimer) clearTimeout(this.llmTimer)
            this.llmTimer = setTimeout(async () => {
              if (rec) {
                rec.stop()
                rec = null
                stream = null
              }
              isLLMReplying = true
              await this.tryCallLLM(newText)
              isLLMReplying = false
              this.llmOffset += newText.length
              rec = record.record({ sampleRate: this.sampleRate, channels: this.channels, threshold: 0, verbose: false, recordProgram: 'sox', silence: '1.0' })
              stream = rec.stream()
              stream.on('data', chunk => {
                if (!isLLMReplying) {
                  try {
                    this.sendAudioChunk(chunk)
                  } catch (err) {
                    console.error('[ASR] 音频分片发送失败:', err)
                  }
                }
              })
              stream.on('error', err => {
                console.error('[ASR] 录音流错误:', err)
              })
            }, this.silenceTimeout)
          }
        }
      } catch (err) {
        console.error('[ASR] 消息处理异常:', err)
      }
    })
    this.ws.on('close', () => {
      console.log('[ASR] WebSocket 已关闭')
    })
    this.ws.on('error', (err) => {
      console.error('[ASR] WebSocket 错误:', err)
    })
  }
}

new LiveASR2LLM().start()
```

#### 基于 Python 的代码示例

```
import asyncio
import gzip
import json
import time
import uuid
import websockets
import pyaudio

# -------------------- 协议相关常量和函数 --------------------

PROTOCOL_VERSION = 0b0001

# Message Types
FULL_CLIENT_REQUEST = 0b0001
AUDIO_ONLY_REQUEST = 0b0010
FULL_SERVER_RESPONSE = 0b1001
SERVER_ACK = 0b1011
SERVER_ERROR_RESPONSE = 0b1111

# Message Type Specific Flags
NO_SEQUENCE = 0b0000
POS_SEQUENCE = 0b0001
NEG_SEQUENCE = 0b0010
NEG_WITH_SEQUENCE = 0b0011

# 序列化和压缩方式
NO_SERIALIZATION = 0b0000
JSON_SERIALIZATION = 0b0001
NO_COMPRESSION = 0b0000
GZIP_COMPRESSION = 0b0001

def generate_header(message_type=FULL_CLIENT_REQUEST,
                    message_type_specific_flags=NO_SEQUENCE,
                    serial_method=JSON_SERIALIZATION,
                    compression_type=GZIP_COMPRESSION,
                    reserved_data=0x00):
    header = bytearray()
    header_size = 1
    header.append((PROTOCOL_VERSION << 4) | header_size)
    header.append((message_type << 4) | message_type_specific_flags)
    header.append((serial_method << 4) | compression_type)
    header.append(reserved_data)
    return header

def generate_before_payload(sequence: int):
    before_payload = bytearray()
    before_payload.extend(sequence.to_bytes(4, 'big', signed=True))
    return before_payload

def parse_response(res):
    """
    如果 res 是 bytes，则按协议解析；
    如果 res 是 str，则直接返回文本内容，避免出现位移操作错误。
    """
    if not isinstance(res, bytes):
        return {'payload_msg': res}
    header_size = res[0] & 0x0f
    message_type = res[1] >> 4
    message_type_specific_flags = res[1] & 0x0f
    serialization_method = res[2] >> 4
    message_compression = res[2] & 0x0f
    payload = res[header_size * 4:]
    result = {}
    if message_type_specific_flags & 0x01:
        seq = int.from_bytes(payload[:4], "big", signed=True)
        result['payload_sequence'] = seq
        payload = payload[4:]
    result['is_last_package'] = bool(message_type_specific_flags & 0x02)
    if message_type == FULL_SERVER_RESPONSE:
        payload_size = int.from_bytes(payload[:4], "big", signed=True)
        payload_msg = payload[4:]
    elif message_type == SERVER_ACK:
        seq = int.from_bytes(payload[:4], "big", signed=True)
        result['seq'] = seq
        if len(payload) >= 8:
            payload_size = int.from_bytes(payload[4:8], "big", signed=False)
            payload_msg = payload[8:]
        else:
            payload_msg = b""
    elif message_type == SERVER_ERROR_RESPONSE:
        code = int.from_bytes(payload[:4], "big", signed=False)
        result['code'] = code
        payload_size = int.from_bytes(payload[4:8], "big", signed=False)
        payload_msg = payload[8:]
    else:
        payload_msg = payload

    if message_compression == GZIP_COMPRESSION:
        try:
            payload_msg = gzip.decompress(payload_msg)
        except Exception as e:
            pass
    if serialization_method == JSON_SERIALIZATION:
        try:
            payload_text = payload_msg.decode("utf-8")
            payload_msg = json.loads(payload_text)
        except Exception as e:
            pass
    else:
        payload_msg = payload_msg.decode("utf-8", errors="ignore")
    result['payload_msg'] = payload_msg
    return result

# -------------------- 基于麦克风采集 PCM 数据的 ASR 测试客户端 --------------------

class AsrMicClient:
    def __init__(self, token, ws_url, seg_duration=100, sample_rate=16000, channels=1, bits=16, format="pcm", **kwargs):
        """
        :param token: 鉴权 token
        :param ws_url: ASR websocket 服务地址
        :param seg_duration: 分段时长，单位毫秒
        :param sample_rate: 采样率（Hz）
        :param channels: 通道数（一般单声道为 1）
        :param bits: 采样位数（16 表示 16 位）
        :param format: 音频格式，这里设为 "pcm"
        """
        self.token = token
        self.ws_url = ws_url
        self.seg_duration = seg_duration  # 毫秒
        self.sample_rate = sample_rate
        self.channels = channels
        self.bits = bits
        self.format = format
        self.uid = kwargs.get("uid", "test")
        self.codec = kwargs.get("codec", "raw")
        self.streaming = kwargs.get("streaming", True)

    def construct_request(self, reqid):
        req = {
            "user": {"uid": self.uid},
            "audio": {
                "format": self.format,
                "sample_rate": self.sample_rate,
                "bits": self.bits,
                "channel": self.channels,
                "codec": self.codec,
            },
            "request": {"model_name": "asr", "enable_punc": True}
        }
        return req

    async def stream_mic(self):
        """
        异步生成麦克风采集的 PCM 数据段，
        使用 pyaudio 读取数据时设置 exception_on_overflow=False 避免输入溢出异常。
        """
        p = pyaudio.PyAudio()
        stream = p.open(
            format=pyaudio.paInt16,
            channels=self.channels,
            rate=self.sample_rate,
            input=True,
            frames_per_buffer=1024)
        bytes_per_frame = self.channels * (self.bits // 8)
        frames_needed = int(self.sample_rate * self.seg_duration / 1000)
        bytes_needed = frames_needed * bytes_per_frame
        frames = []
        while True:
            try:
                data = await asyncio.to_thread(stream.read, 1024, False)
            except Exception as e:
                print("麦克风读取错误:", e)
                continue
            frames.append(data)
            if sum(len(f) for f in frames) >= bytes_needed:
                segment = b"".join(frames)[:bytes_needed]
                yield segment
                frames = []

    async def execute(self):
        reqid = str(uuid.uuid4())
        seq = 1
        request_params = self.construct_request(reqid)
        payload_bytes = json.dumps(request_params).encode("utf-8")
        payload_bytes = gzip.compress(payload_bytes)
        # 构造初始配置信息请求
        full_client_request = bytearray(generate_header(message_type_specific_flags=POS_SEQUENCE))
        full_client_request.extend(generate_before_payload(sequence=seq))
        full_client_request.extend((len(payload_bytes)).to_bytes(4, "big"))
        full_client_request.extend(payload_bytes)
        headers = {"Authorization": "Bearer " + self.token}
        # 用于记录上一次满足条件的响应文本与时间
        begin_time = time.time()
        print(f"开始时间：{begin_time}")

        try:
            async with websockets.connect(self.ws_url, extra_headers=headers, max_size=1000000000) as ws:
                await ws.send(full_client_request)
                try:
                    res = await asyncio.wait_for(ws.recv(), timeout=10.0)
                except asyncio.TimeoutError:
                    print(f"{time.time() - begin_time}毫秒等待配置信息响应超时")
                    return
                result = parse_response(res)
                print(f"{time.time() - begin_time}毫秒配置响应：", result)

                # 开始采集麦克风音频并分段发送
                async for chunk in self.stream_mic():
                    seq += 1
                    audio_only_request = bytearray(
                        generate_header(message_type=AUDIO_ONLY_REQUEST,
                                        message_type_specific_flags=POS_SEQUENCE))
                    audio_only_request.extend(generate_before_payload(sequence=seq))
                    compressed_chunk = gzip.compress(chunk)
                    audio_only_request.extend((len(compressed_chunk)).to_bytes(4, "big"))
                    audio_only_request.extend(compressed_chunk)
                    await ws.send(audio_only_request)
                    try:
                        res = await asyncio.wait_for(ws.recv(), timeout=5.0)
                        result = parse_response(res)
                        print(f"{time.time() - begin_time}毫秒接收响应：", result)
                        
                    except asyncio.TimeoutError:
                        pass
                    await asyncio.sleep(self.seg_duration / 1000.0)
        except Exception as e:
            print("异常：", e)

    def run(self):
        asyncio.run(self.execute())

# -------------------- 入口 --------------------

if __name__ == '__main__':
    # 替换下面的 token 与 ws_url 为你的实际参数 停止直接ctrl+c即可
    token = "sk-xxx"
    ws_url = "wss://openai.qiniu.com/v1/voice/asr"
    seg_duration = 300 # 分段时长，单位毫秒,网络环境不好建议调大，否则会丢包
    client = AsrMicClient(token=token, ws_url=ws_url, seg_duration=seg_duration, format="pcm")
    client.run()

"""
在 macOS 上，你可以通过 Homebrew 安装它：
brew install portaudio
安装完成后，再尝试安装 PyAudio：
pip install pyaudio
"""
```

------

## 4. 文字合成语音（TTS）

- 丰富的多语言、音色库选择，支持情感与语速调节。
- AI 推理出的文本可作为 TTS 接口的输入。

### 4.1 获取音色列表

- URL: `GET /voice/list`

#### 响应结构

| 字段       | 类型   | 说明             |
| :--------- | :----- | :--------------- |
| voice_name | string | 音色名称         |
| voice_type | string | 音色             |
| url        | string | 试听音频链接     |
| category   | string | 音色分类         |
| updatetime | int    | 更新时间（毫秒） |

#### 响应示例

```
[
  {
    "voice_name": "甜美教学小源",
    "voice_type": "qiniu_zh_female_tmjxxy",
    "url": "https://aitoken-public.qnaigc.com/ai-voice/qiniu_zh_female_tmjxxy.mp3",
    "category": "传统音色",
    "updatetime": 1747812605559
  }
  // ...更多音色
]
```

### curl 请求示例

```
# 获取支持的音色列表
export OPENAI_BASE_URL="https://openai.qiniu.com/v1"
export OPENAI_API_KEY="<你的七牛云 AI API KEY>"

curl --location "$OPENAI_BASE_URL/voice/list" \
--header "Authorization: Bearer $OPENAI_API_KEY"
```

### 4.2 文字转语音

- URL: `POST /voice/tts`
- Content-Type: `application/json`

#### 请求参数

| 字段           | 类型   | 必填 | 说明               | 示例值                 |
| :------------- | :----- | :--- | :----------------- | :--------------------- |
| audio          | object | 是   | 音频参数           | 见下                   |
| └─ voice_type  | string | 是   | 音色类型           | qiniu_zh_female_wwxkjx |
| └─ encoding    | string | 是   | 音频编码（如 mp3） | mp3                    |
| └─ speed_ratio | float  | 否   | 语速，默认 1.0     | 1.0                    |
| request        | object | 是   | 请求参数           | 见下                   |
| └─ text        | string | 是   | 需要合成的文本     | 你好，世界！           |

#### 请求示例

```
{
  "audio": {
    "voice_type": "qiniu_zh_female_wwxkjx",
    "encoding": "mp3",
    "speed_ratio": 1.0
  },
  "request": {
    "text": "你好，世界！"
  }
}
```

#### 响应结构

| 字段        | 类型   | 说明                       |
| :---------- | :----- | :------------------------- |
| reqid       | string | 请求唯一标识               |
| operation   | string | 操作类型                   |
| sequence    | int    | 序列号，通常为 -1          |
| data        | string | 合成的 base64 编码音频数据 |
| addition    | object | 附加信息                   |
| └─ duration | string | 音频时长（毫秒）           |

#### 响应示例

```
{
  "reqid": "f3dff20d7d670df7adcb2ff0ab5ac7ea",
  "operation": "query",
  "sequence": -1,
  "data": "data",
  "addition": { "duration": "1673" }
}
```

#### curl 请求示例

```
# 生成例子
export OPENAI_BASE_URL="https://openai.qiniu.com/v1"
export OPENAI_API_KEY="<你的七牛云 AI API KEY>"

curl --location "$OPENAI_BASE_URL/voice/tts" \
--header "Content-Type: application/json" \
--header "Authorization: Bearer $OPENAI_API_KEY" \
--data '{
  "audio": {
    "voice_type": "qiniu_zh_female_wwxkjx",
    "encoding": "mp3",
    "speed_ratio": 1.0
  },
  "request": {
    "text": "你好，世界！"
  }
}'
```

#### 基于 Golang 的示例

```
// 基于 websocket 的实时转换示例
package main

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"

	"github.com/gorilla/websocket"
)

var addr = "openai.qiniu.com"
var token = "sk-xx"
var voiceType = "qiniu_zh_female_tmjxxy" //此处替换成需要调用的音色，
var u = url.URL{Scheme: "wss", Host: addr, Path: "/v1/voice/tts"}
var header = http.Header{
	"Authorization": []string{fmt.Sprintf("Bearer %s", token)},
	"VoiceType":     []string{voiceType},
}

type TTSRequest struct {
	Audio   `json:"audio"`
	Request `json:"request"`
}
type Audio struct {
	VoiceType  string  `json:"voice_type"`
	Encoding   string  `json:"encoding"`
	SpeedRatio float64 `json:"speed_ratio"`
}
type Request struct {
	Text string `json:"text"`
}

type RelayTTSResponse struct {
	Reqid     string    `json:"reqid"`
	Operation string    `json:"operation"`
	Sequence  int       `json:"sequence"`
	Data      string    `json:"data"`
	Addition  *Addition `json:"addition,omitempty"`
}
type Addition struct {
	Duration string `json:"duration"`
}

func main() {
	wssStream("我想测试下语音合成的效果", voiceType, "test.mp3")
}

// 流式合成
func wssStream(text, voiceType, outFile string) {
	input := setupInput(voiceType, "mp3", 1.0, text)

	c, _, err := websocket.DefaultDialer.Dial(u.String(), header)
	if err != nil {
		fmt.Println("dial err:", err)
		return
	}
	defer c.Close()
	err = c.WriteMessage(websocket.BinaryMessage, input)
	if err != nil {
		fmt.Println("write message fail, err:", err.Error())
		return
	}
	count := 0
	var audio []byte
	for {
		count++
		var message []byte
		_, message, err := c.ReadMessage()
		if err != nil {
			fmt.Println("read message fail, err:", err.Error())
			break
		}

		var resp RelayTTSResponse
		err = json.Unmarshal(message, &resp)

		if err != nil {
			fmt.Println("unmarshal fail, err:", err.Error())
			continue
		}
		d, err := base64.StdEncoding.DecodeString(resp.Data)
		if err != nil {
			fmt.Println("decode fail, err:", err.Error())
		}
		audio = append(audio, d...)

		if resp.Sequence < 0 {
			err = ioutil.WriteFile(outFile, audio, 0644)
			if err != nil {
				fmt.Println("write audio to file fail, err:", err.Error())
			}
			break
		}
	}
	if err != nil {
		fmt.Println("stream synthesis fail, err:", err.Error())
		return
	}
}

func setupInput(voiceType string, encoding string, speedRatio float64, text string) []byte {
	params := &TTSRequest{
		Audio: Audio{
			VoiceType:  voiceType,
			Encoding:   encoding,
			SpeedRatio: speedRatio,
		},
		Request: Request{
			Text: text,
		},
	}
	resStr, _ := json.Marshal(params)
	return resStr
}
```