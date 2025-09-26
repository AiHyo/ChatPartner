# ChatPartner API 测试命令集

## 🚀 完整测试流程 (curl命令)

### 📝 环境变量设置

**Windows PowerShell**:
```powershell
# 设置基础URL
$BASE_URL = "http://localhost:8080"
$CONTENT_TYPE = "Content-Type: application/json"
```

**Windows CMD**:
```cmd
set BASE_URL=http://localhost:8080
set CONTENT_TYPE=Content-Type: application/json
```

**Linux/Mac**:
```bash
export BASE_URL="http://localhost:8080"
export CONTENT_TYPE="Content-Type: application/json"
```

---

## 1️⃣ 用户注册

**Windows PowerShell/CMD**:
```cmd
curl -X POST "http://localhost:8080/user/register" ^
  -H "Content-Type: application/json" ^
  -d "{\"userAccount\":\"testuser001\",\"userPassword\":\"123456789\",\"checkPassword\":\"123456789\",\"userName\":\"测试用户\"}" ^
  -c cookies.txt
```

**Linux/Mac/Git Bash**:
```bash
curl -X POST "${BASE_URL}/user/register" \
  -H "${CONTENT_TYPE}" \
  -d '{
    "userAccount": "testuser001",
    "userPassword": "123456789",
    "checkPassword": "123456789",
    "userName": "测试用户"
  }' \
  -c cookies.txt
```

**预期响应**：
```json
{
  "code": 0,
  "data": 1234567890123456789,
  "message": "ok"
}
```

---

## 2️⃣ 用户登录

```bash
curl -X POST "${BASE_URL}/user/login" \
  -H "${CONTENT_TYPE}" \
  -d '{
    "userAccount": "testuser001",
    "userPassword": "123456789"
  }' \
  -c cookies.txt \
  -b cookies.txt
```

**预期响应**：
```json
{
  "code": 0,
  "data": {
    "id": 1234567890123456789,
    "userName": "测试用户",
    "userAccount": "testuser001",
    "userAvatar": null,
    "userRole": "user",
    "createTime": "2025-09-26T12:16:25"
  },
  "message": "ok"
}
```

---

## 3️⃣ 获取可用角色列表

```bash
curl -X GET "${BASE_URL}/aiRole/available" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt
```

**预期响应**：
```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "roleName": "喜羊羊",
      "roleDescription": "聪明可爱的小羊",
      "greeting": "咩咩！你好呀！我是喜羊羊，很高兴见到你！有什么问题尽管问我吧！",
      "systemPrompt": "你是喜羊羊，一只聪明、勇敢、善良的小羊...",
      "isSystem": 1,
      "isActive": 1
    }
  ],
  "message": "ok"
}
```

---

## 4️⃣ 创建对话分组

```bash
curl -X POST "${BASE_URL}/chatGroup/add" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt \
  -d '{
    "groupName": "我的AI助手",
    "roleId": 1
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "data": 1234567890123456790,
  "message": "ok"
}
```

---

## 4️⃣ 获取用户分组列表

```bash
curl -X GET "${BASE_URL}/chatGroup/my" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt
```

**预期响应**：
```json
{
  "code": 0,
  "data": [
    {
      "id": 1234567890123456790,
      "groupName": "我的AI助手",
      "initPrompt": "你好，我是你的专属AI助手，有什么可以帮助你的吗？",
      "aiRole": "友善助手",
      "userId": 1234567890123456789,
      "createTime": "2025-09-26T12:16:25",
      "updateTime": "2025-09-26T12:16:25"
    }
  ],
  "message": "ok"
}
```

---

## 5️⃣ 更新对话分组

```bash
curl -X POST "${BASE_URL}/chatGroup/update" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt \
  -d '{
    "id": 1234567890123456790,
    "groupName": "升级版AI助手",
    "initPrompt": "你好！我是你的升级版AI助手，现在更加智能了！有什么问题尽管问我吧！",
    "aiRole": "智能专家"
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "data": true,
  "message": "ok"
}
```

---

## 6️⃣ 获取分组详情

```bash
curl -X GET "${BASE_URL}/chatGroup/get?id=1234567890123456790" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt
```

**预期响应**：
```json
{
  "code": 0,
  "data": {
    "id": 1234567890123456790,
    "groupName": "升级版AI助手",
    "initPrompt": "你好！我是你的升级版AI助手，现在更加智能了！有什么问题尽管问我吧！",
    "aiRole": "智能专家",
    "userId": 1234567890123456789,
    "createTime": "2025-09-26T12:16:25",
    "updateTime": "2025-09-26T12:17:30"
  },
  "message": "ok"
}
```

---

## 7️⃣ 检查分组历史消息

```bash
curl -X GET "${BASE_URL}/chatHistory/hasHistory?groupId=1234567890123456790" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt
```

**预期响应**：
```json
{
  "code": 0,
  "data": false,
  "message": "ok"
}
```

---

## 8️⃣ 初始化分组对话

```bash
curl -X POST "${BASE_URL}/chat/init?groupId=1234567890123456790" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt
```

**预期响应**：
```json
{
  "code": 0,
  "data": "你好！欢迎使用我的服务！我是喜羊羊，很高兴为你提供帮助！有什么问题都可以问我哦！",
  "message": "ok"
}
```

---

## 9️⃣ 发送消息

```bash
curl -X POST "${BASE_URL}/chat/send" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt \
  -d '{
    "groupId": 1234567890123456790,
    "message": "你能为我介绍一下你的功能吗？"
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "data": "当然可以！我是喜羊羊，我有很多功能呢！我可以陪你聊天，回答各种问题，还可以帮你解决生活中的小困难。比如我可以...",
  "message": "ok"
}
```

---

## 🔟 获取最新对话历史

```bash
curl -X GET "${BASE_URL}/chatHistory/latest?groupId=1234567890123456790" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt
```

**预期响应**：
```json
{
  "code": 0,
  "data": [
    {
      "id": 1234567890123456791,
      "message": "你好！我是你的升级版AI助手，现在更加智能了！有什么问题尽管问我吧！",
      "messageType": "user",
      "groupId": 1234567890123456790,
      "userId": 1234567890123456789,
      "createTime": "2025-09-26T12:18:00"
    },
    {
      "id": 1234567890123456792,
      "message": "你好！欢迎使用我的服务！我是喜羊羊，很高兴为你提供帮助！有什么问题都可以问我哦！",
      "messageType": "ai",
      "groupId": 1234567890123456790,
      "userId": 1234567890123456789,
      "createTime": "2025-09-26T12:18:01"
    }
  ],
  "message": "ok"
}
```

---

## 1️⃣1️⃣ 分页获取对话历史

```bash
curl -X POST "${BASE_URL}/chatHistory/list" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt \
  -d '{
    "groupId": 1234567890123456790,
    "current": 1,
    "pageSize": 10
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "data": [
    {
      "id": 1234567890123456791,
      "message": "你好！我是你的升级版AI助手，现在更加智能了！有什么问题尽管问我吧！",
      "messageType": "user",
      "groupId": 1234567890123456790,
      "userId": 1234567890123456789,
      "createTime": "2025-09-26T12:18:00"
    }
  ],
  "message": "ok"
}
```

---

## 1️⃣2️⃣ 继续对话测试

```bash
# 发送第二条消息
curl -X POST "${BASE_URL}/chat/send" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt \
  -d '{
    "groupId": 1234567890123456790,
    "message": "请帮我写一首关于春天的小诗"
  }'

# 发送第三条消息
curl -X POST "${BASE_URL}/chat/send" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt \
  -d '{
    "groupId": 1234567890123456790,
    "message": "谢谢你，写得很好！"
  }'
```

---

## 1️⃣3️⃣ 删除对话分组

```bash
curl -X POST "${BASE_URL}/chatGroup/delete" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt \
  -d '{
    "id": 1234567890123456790
  }'
```

**预期响应**：
```json
{
  "code": 0,
  "data": true,
  "message": "ok"
}
```

---

## 🔧 批量测试脚本

将以下内容保存为 `test-all.sh`：

```bash
#!/bin/bash

# 设置变量
BASE_URL="http://localhost:8080"
CONTENT_TYPE="Content-Type: application/json"

echo "🚀 开始完整功能测试..."

# 1. 用户注册
echo "1️⃣ 测试用户注册..."
curl -X POST "${BASE_URL}/user/register" \
  -H "${CONTENT_TYPE}" \
  -d '{
    "userAccount": "testuser001",
    "userPassword": "123456789",
    "checkPassword": "123456789",
    "userName": "测试用户"
  }' \
  -c cookies.txt -s | jq .

# 2. 用户登录
echo "2️⃣ 测试用户登录..."
curl -X POST "${BASE_URL}/user/login" \
  -H "${CONTENT_TYPE}" \
  -d '{
    "userAccount": "testuser001",
    "userPassword": "123456789"
  }' \
  -c cookies.txt -b cookies.txt -s | jq .

# 3. 创建分组
echo "3️⃣ 测试创建分组..."
GROUP_ID=$(curl -X POST "${BASE_URL}/chatGroup/add" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt \
  -d '{
    "groupName": "测试AI助手",
    "initPrompt": "你好，我是你的测试AI助手！",
    "aiRole": "测试助手"
  }' -s | jq -r .data)

echo "创建的分组ID: ${GROUP_ID}"

# 4. 初始化对话
echo "4️⃣ 测试初始化对话..."
curl -X POST "${BASE_URL}/chat/init?groupId=${GROUP_ID}" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt -s | jq .

# 5. 发送消息
echo "5️⃣ 测试发送消息..."
curl -X POST "${BASE_URL}/chat/send" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt \
  -d "{
    \"groupId\": ${GROUP_ID},
    \"message\": \"你好，请介绍一下你自己\"
  }" -s | jq .

# 6. 获取历史
echo "6️⃣ 测试获取历史..."
curl -X GET "${BASE_URL}/chatHistory/latest?groupId=${GROUP_ID}" \
  -H "${CONTENT_TYPE}" \
  -b cookies.txt -s | jq .

echo "✅ 测试完成！"
```

---

## 💡 Windows系统使用指南

### 🎯 推荐方式（PowerShell）：
```powershell
# 1. 以管理员身份运行PowerShell
# 2. 执行测试脚本
.\windows-test.ps1
```

### 🔧 备选方式1（CMD批处理）：
```cmd
# 双击运行或在CMD中执行
windows-test.bat
```

### 🔧 备选方式2（Git Bash）：
```bash
# 如果安装了Git Bash，可以使用Linux风格命令
chmod +x test-all.sh
./test-all.sh
```

### 🔧 备选方式3（手动执行）：
```cmd
# 在CMD中逐个执行curl命令
curl -X POST "http://localhost:8080/user/register" ^
  -H "Content-Type: application/json" ^
  -d "{\"userAccount\":\"testuser001\",\"userPassword\":\"123456789\",\"checkPassword\":\"123456789\",\"userName\":\"测试用户\"}" ^
  -c cookies.txt
```

## 💡 通用使用说明

### 前置条件：
1. 确保ChatPartner应用已启动在 `localhost:8080`
2. Windows系统需要安装curl（Windows 10 1803+自带）
3. 可选：安装 `jq`（用于JSON格式化）

### Windows curl安装检查：
```cmd
curl --version
```
如果提示命令不存在，请：
- 升级到Windows 10 1803或更高版本
- 或下载安装curl：https://curl.se/windows/

### 重要说明：
- 🔑 所有请求都使用cookie进行会话管理
- 📝 请根据实际返回的ID值替换示例中的ID
- 🛠️ 如果端口不是8080，请修改BASE_URL
- 🔍 建议使用jq格式化JSON响应以便阅读

### 错误处理：
如果遇到错误，检查：
1. 应用是否正常启动
2. 端口是否正确
3. 数据库是否已创建相关表
4. 用户是否已成功注册和登录
