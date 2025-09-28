<template>
  <div class="chat-page">
    <aside class="sidebar">
      <div class="side-top">
        <el-input v-model="friendFilter" placeholder="搜索好友角色" clearable />
      </div>
      <div class="friend-list">
        <div v-for="f in filteredFriends" :key="f.roleId"
             :class="['friend-item', { active: f.roleId === selectedRoleId }]"
             @click="onSelectRole(f.roleId)">
          <div class="avatar" :style="{ backgroundImage: f.avatar ? `url(${f.avatar})` : 'none' }">
            <span v-if="!f.avatar">👤</span>
          </div>
          <div class="info">
            <div class="name">
              <span>{{ f.roleName }}</span>
              <el-tag v-if="f.pinned" size="small" type="warning" effect="plain">置顶</el-tag>
            </div>
            <div class="last">{{ formatTime(f.lastChatTime) }}</div>
          </div>
          <el-dropdown class="more" trigger="contextmenu">
            <span class="el-dropdown-link">⋮</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="togglePin(f)">{{ f.pinned ? '取消置顶' : '置顶' }}</el-dropdown-item>
                <el-dropdown-item divided type="danger" @click="removeFriendClick(f)">删除好友</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </aside>

    <section class="chat-main">
      <div class="chat-header">
        <div class="title">{{ currentRoleTitle }}</div>
        <div class="actions">
          <el-tooltip content="语音对话" placement="bottom">
            <el-button circle :disabled="!selectedRoleId" @click="openVoiceChat" title="语音对话">
              <el-icon><Microphone /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="话题记录" placement="bottom">
            <el-button circle :disabled="!selectedRoleId" @click="toggleGroupDrawer" title="话题记录">
              <el-icon><Collection /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="新话题" placement="bottom">
            <el-button circle type="primary" :disabled="!selectedRoleId || creatingGroup" @click="onCreateGroup" :loading="creatingGroup" title="新话题">
              <el-icon><CirclePlus /></el-icon>
            </el-button>
          </el-tooltip>
          <el-divider direction="vertical" />
          <el-button size="small" @click="reload">刷新</el-button>
        </div>
      </div>
      <div class="messages" ref="msgBox">
        <div class="load-more" v-if="hasMore">
          <el-button link @click="loadMore" :loading="loadingMore">加载更多</el-button>
        </div>
        <div v-for="m in messages" :key="m.id" :class="['msg', m.messageType]">
          <template v-if="m.messageType === 'ai'">
            <div class="msg-avatar left"><div class="avatar"><el-image v-if="currentRoleAvatar" :src="currentRoleAvatar" fit="cover" /></div></div>
            <div class="msg-content"><div class="bubble">{{ m.message }}</div><div class="time">{{ formatTime(m.createTime) }}</div></div>
          </template>
          <template v-else>
            <div class="msg-content right"><div class="bubble">{{ m.message }}</div><div class="time">{{ formatTime(m.createTime) }}</div></div>
            <div class="msg-avatar right"><div class="avatar"><el-image v-if="userAvatar" :src="userAvatar" fit="cover" /></div></div>
          </template>
        </div>
        <div v-if="messages.length === 0 && currentGroupId" class="empty">暂无消息，开始对话吧</div>
        <div v-if="!currentGroupId && selectedRoleId" class="empty">该角色暂无分组，请先创建分组后再聊天</div>
        <!-- Voice chat overlay covering only the messages area -->
        <div v-if="voice.show" class="voice-overlay">
          <div class="overlay-bg"></div>
          <div class="overlay-content">
            <div class="center">
              <div class="pulse-circle" :class="{ muted: voice.muted }" :style="{ boxShadow: `0 0 0 ${8 + 8*voice.level}px rgba(64,158,255,0.15)` }">
                <el-icon class="center-icon"><Microphone /></el-icon>
              </div>
              <div class="hint">正在语音对话中…</div>
              <div class="realtime">
                <div class="line" v-if="voice.asrPartial">你：{{ voice.asrPartial }}</div>
                <div class="line ai" v-if="voice.llmPartial">AI：{{ voice.llmPartial }}</div>
              </div>
            </div>
            <div class="controls">
              <el-tooltip :content="voice.muted ? '取消静音' : '静音'" placement="top">
                <el-button circle @click="toggleMute" :type="voice.muted ? 'warning' : 'default'">
                  <el-icon><component :is="voice.muted ? Mute : Microphone" /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="挂断" placement="top">
                <el-button circle type="danger" class="hangup" @click="hangup">
                  <el-icon><Phone /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
      </div>
      <div class="composer">
        <el-input v-model="text" type="textarea" :rows="2" placeholder="输入消息，回车发送"
                  @keyup.enter.prevent="send"/>
        <el-button type="primary" :disabled="!canSend" @click="send" :loading="sending">发送</el-button>
      </div>
      <el-drawer v-model="groupDrawerVisible" title="话题记录" direction="rtl" size="360px">
        <div class="group-drawer">
          <el-input v-model="groupFilter" placeholder="搜索话题" clearable style="margin-bottom: 8px" />
          <el-empty v-if="selectedRoleId && groups.length === 0" description="暂无话题，点击新话题开始对话" />
          <el-scrollbar height="calc(100vh - 200px)">
            <div class="group-item" v-for="g in filteredGroups" :key="g.id" :class="{ active: g.id === currentGroupId }" @click="switchGroup(g.id)">
              <div class="name">{{ g.groupName || '未命名话题' }}</div>
              <div class="last">{{ formatTime(g.lastChatTime) }}</div>
              <el-dropdown class="more" trigger="click" @click.stop>
                <span class="el-dropdown-link" @click.stop>⋮</span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click.stop="renameGroup(g)">重命名</el-dropdown-item>
                    <el-dropdown-item divided type="danger" @click.stop="deleteGroupClick(g)">删除会话</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </el-scrollbar>
        </div>
      </el-drawer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, reactive, computed, nextTick } from 'vue'
import { listFriends, removeFriend, updatePin, type FriendRoleVO } from '@/services/friends'
import { getLatestGroupByRole, getGroupsByRole, type ChatGroup, createGroup, updateGroup as apiUpdateGroup, deleteGroup as apiDeleteGroup } from '@/services/chatGroups'
import { getHistoryByCursor, getLatest, type ChatHistoryItem } from '@/services/chatHistory'
import { sendMessageStreamSse } from '@/services/chat'
import { useRoute } from 'vue-router'
import { Microphone, Collection, CirclePlus, Mute, Phone } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()

const friendFilter = ref('')
const friends = ref<FriendRoleVO[]>([])
const selectedRoleId = ref<number | undefined>(undefined)
const currentGroupId = ref<number | undefined>(undefined)

const messages = ref<ChatHistoryItem[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const loadingMore = ref(false)
const sending = ref(false)
const text = ref('')
const msgBox = ref<HTMLDivElement>()
const creatingGroup = ref(false)
const groupDrawerVisible = ref(false)
const groups = ref<ChatGroup[]>([])
const groupFilter = ref('')

// avatars
const userStore = useUserStore()
const userAvatar = computed(() => userStore.user?.userAvatar || '')
const currentRoleAvatar = computed(() => friends.value.find(f => f.roleId === selectedRoleId.value)?.avatar || '')

// streaming cancel handle
const cancelStream = ref<null | (() => void)>(null)

function formatTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleString()
}

// --- Voice chat overlay state & logic ---
type VoiceState = {
  show: boolean
  ws: WebSocket | null
  connected: boolean
  status: string
  asrPartial: string
  llmPartial: string
  muted: boolean
  level: number
  // mic
  audioCtx: AudioContext | null
  mediaStream: MediaStream | null
  sourceNode: MediaStreamAudioSourceNode | null
  processorNode: ScriptProcessorNode | null
  TARGET_SR: number
  // tts playback
  currentChunks: Uint8Array[]
  audioQueue: Uint8Array[]
  playing: boolean
}

const voice = reactive<VoiceState>({
  show: false,
  ws: null,
  connected: false,
  status: 'idle',
  asrPartial: '',
  llmPartial: '',
  muted: false,
  level: 0,
  audioCtx: null,
  mediaStream: null,
  sourceNode: null,
  processorNode: null,
  TARGET_SR: 16000,
  currentChunks: [],
  audioQueue: [],
  playing: false,
})

function computeWsUrl() {
  // 临时直接连接后端，绕过Vite代理进行测试
  if (import.meta.env.DEV) {
    return 'ws://localhost:8123/api/ws/voice-chat'
  }
  // Production: use current origin
  const origin = location.origin.replace(/^http/, 'ws')
  return origin + '/api/ws/voice-chat'
}

function b64ToBytes(b64: string) {
  try {
    const cleaned = (b64 || '').replace(/[^A-Za-z0-9+/=]/g, '')
    const byteChars = atob(cleaned)
    const bytes = new Uint8Array(byteChars.length)
    for (let i = 0; i < byteChars.length; i++) bytes[i] = byteChars.charCodeAt(i)
    return bytes
  } catch {
    return new Uint8Array()
  }
}

function playNext() {
  if (voice.playing) return
  const item = voice.audioQueue.shift()
  if (!item) return
  voice.playing = true
  const blob = new Blob([item], { type: 'audio/mpeg' })
  const url = URL.createObjectURL(blob)
  const audio = new Audio(url)
  let watchdog: any = setTimeout(() => {
    try { audio.pause() } catch {}
    try { URL.revokeObjectURL(url) } catch {}
    voice.playing = false
    playNext()
  }, 30000)
  audio.onended = () => {
    if (watchdog) { clearTimeout(watchdog); watchdog = null }
    try { URL.revokeObjectURL(url) } catch {}
    voice.playing = false
    playNext()
  }
  audio.onerror = () => {
    if (watchdog) { clearTimeout(watchdog); watchdog = null }
    try { URL.revokeObjectURL(url) } catch {}
    voice.playing = false
    playNext()
  }
  audio.play().catch(() => {
    if (watchdog) { clearTimeout(watchdog); watchdog = null }
    voice.playing = false
    playNext()
  })
}

function downsampleBuffer(buffer: Float32Array, sampleRate: number, outSampleRate: number) {
  if (outSampleRate === sampleRate) return buffer
  const ratio = sampleRate / outSampleRate
  const newLen = Math.round(buffer.length / ratio)
  const result = new Float32Array(newLen)
  let offsetResult = 0
  let offsetBuffer = 0
  while (offsetResult < result.length) {
    const nextOffsetBuffer = Math.round((offsetResult + 1) * ratio)
    let accum = 0, count = 0
    for (let i = offsetBuffer; i < nextOffsetBuffer && i < buffer.length; i++) { 
      const val = buffer[i]
      if (val !== undefined) {
        accum += val
        count++
      }
    }
    result[offsetResult] = count > 0 ? (accum / count) : 0
    offsetResult++
    offsetBuffer = nextOffsetBuffer
  }
  return result
}

function floatTo16BitPCM(float32Array: Float32Array) {
  const buffer = new ArrayBuffer(float32Array.length * 2)
  const view = new DataView(buffer)
  let offset = 0
  for (let i = 0; i < float32Array.length; i++, offset += 2) {
    let s = Math.max(-1, Math.min(1, float32Array[i] || 0))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true)
  }
  return new Uint8Array(buffer)
}

async function startMic() {
  try {
    voice.mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, noiseSuppression: true, echoCancellation: true, sampleRate: 16000 },
      video: false,
    })
    voice.audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)()
    voice.sourceNode = voice.audioCtx.createMediaStreamSource(voice.mediaStream)
    const inputSR = voice.audioCtx.sampleRate
    const bufferSize = 4096
    voice.processorNode = voice.audioCtx.createScriptProcessor(bufferSize, 1, 1)
    let frameCount = 0
    voice.processorNode.onaudioprocess = (e: AudioProcessingEvent) => {
      const input = e.inputBuffer.getChannelData(0)
      const down = downsampleBuffer(input, inputSR || voice.audioCtx!.sampleRate, voice.TARGET_SR)
      const pcm16 = floatTo16BitPCM(down)
      // 更新可视化音量（简单平均绝对值标准化）
      let sum = 0
      for (let i = 0; i + 1 < pcm16.length; i += 2) {
        const lo = (pcm16[i] || 0) & 0xff
        const hi = (pcm16[i+1] || 0) & 0xff
        const v = (hi << 8) | lo
        sum += Math.abs(v)
      }
      const avg = sum / Math.max(1, pcm16.length / 2)
      voice.level = Math.min(1, avg / 3000)
      frameCount++
      if (!voice.ws || voice.ws.readyState !== WebSocket.OPEN) return
      if (voice.muted) return
      if (pcm16 && pcm16.byteLength > 0) {
        try { 
          voice.ws.send(pcm16.buffer)
        } catch (e) {
          console.error('[WS] send audio error:', e)
        }
      }
    }
    voice.sourceNode.connect(voice.processorNode)
    voice.processorNode.connect(voice.audioCtx.destination)
  } catch (err: any) {
    ElMessage.error('麦克风启动失败：' + (err?.message || err))
  }
}

function stopMic() {
  try {
    if (voice.processorNode) { voice.processorNode.disconnect(); (voice.processorNode as any).onaudioprocess = null }
    if (voice.sourceNode) voice.sourceNode.disconnect()
    if (voice.audioCtx) voice.audioCtx.close()
    if (voice.mediaStream) voice.mediaStream.getTracks().forEach(t => t.stop())
  } catch {}
  voice.processorNode = null
  voice.sourceNode = null
  voice.audioCtx = null
  voice.mediaStream = null
}

function openVoiceChat() {
  if (!selectedRoleId.value) return
  const ensureGroup = async () => {
    if (!currentGroupId.value) {
      try {
        creatingGroup.value = true
        const newId = await createGroup(selectedRoleId.value!, '语音会话')
        currentGroupId.value = newId
        await loadGroups()
      } finally {
        creatingGroup.value = false
      }
    }
  }
  ensureGroup().then(() => startVoiceSession())
}

function startVoiceSession() {
  if (!currentGroupId.value) { ElMessage.warning('请先创建或选择一个话题'); return }
  const url = computeWsUrl()
  console.log('[WS] connecting to:', url)
  const ws = new WebSocket(url)
  voice.ws = ws
  voice.show = true
  voice.connected = false
  voice.status = 'connecting'
  voice.asrPartial = ''
  voice.llmPartial = ''
  voice.muted = false
  voice.level = 0
  voice.currentChunks = []
  voice.audioQueue = []
  voice.playing = false

  ws.onopen = () => {
    voice.connected = true
    voice.status = 'connected'
    console.log('[WS] opened, sending start message')
    const payload = {
      type: 'start',
      groupId: Number(currentGroupId.value),
      voiceType: 'qiniu_zh_female_tmjxxy',
      speedRatio: 1.0,
      audioFormat: 'raw'
    }
    ws.send(JSON.stringify(payload))
    startMic()
  }
  ws.onclose = () => {
    voice.connected = false
    voice.status = 'closed'
    stopMic()
    // 关闭后刷新一次消息，以便看到已持久化的对话
    if (currentGroupId.value) {
      getLatest(currentGroupId.value).then(list => { messages.value = list; nextTick().then(scrollToBottom) })
    }
    // 自动收起覆盖层
    setTimeout(() => { voice.show = false }, 200)
  }
  ws.onerror = (evt) => {
    voice.status = 'error'
    console.error('[WS] error:', evt)
    ElMessage.error('WebSocket连接错误')
  }
  ws.onmessage = (evt: MessageEvent) => {
    try {
      const data = JSON.parse(evt.data)
      switch (data.type) {
        case 'started':
          break
        case 'asr_partial':
          voice.asrPartial = data.text || ''
          break
        case 'asr_final':
          voice.asrPartial = data.text || ''
          break
        case 'asr_closed':
          // ASR 已关闭（来自 stop 或服务端结束），可以收起覆盖层
          // 问题2：立即刷新消息历史，显示语音期间的对话
          if (currentGroupId.value) {
            getLatest(currentGroupId.value).then(list => { 
              messages.value = list
              nextTick().then(scrollToBottom)
            })
          }
          setTimeout(() => { voice.show = false }, 200)
          break
        case 'llm_partial':
          voice.llmPartial = (voice.llmPartial || '') + String(data.text || '')
          if (voice.llmPartial.length > 200) voice.llmPartial = voice.llmPartial.slice(-200)
          break
        case 'tts_start':
          voice.currentChunks = []
          break
        case 'tts_chunk':
          if (data.data) {
            const bytes = b64ToBytes(data.data)
            if (bytes && bytes.length > 0) voice.currentChunks.push(bytes)
          }
          break
        case 'tts_done': {
          const total = voice.currentChunks.reduce((s, c) => s + c.length, 0)
          if (total > 0) {
            const combined = new Uint8Array(total)
            let offset = 0
            for (const chunk of voice.currentChunks) { combined.set(chunk, offset); offset += chunk.length }
            voice.audioQueue.push(combined)
            playNext()
          }
          voice.currentChunks = []
          break
        }
        case 'tts_interrupted':
          // 问题5：TTS被用户打断，清空音频队列
          voice.audioQueue = []
          voice.playing = false
          voice.currentChunks = []
          console.log('TTS interrupted:', data.reason)
          break
        case 'error':
          // 可在覆盖层上显示错误提示
          console.error('Voice chat error:', data.message)
          break
      }
    } catch {
      // ignore non-JSON
    }
  }
}

function toggleMute() {
  voice.muted = !voice.muted
}

function hangup() {
  try {
    if (voice.ws && voice.ws.readyState === WebSocket.OPEN) {
      // 请求服务端进行最终一次 ASR（REST 兜底）并触发 LLM/TTS，客户端不立即关闭 WS，等待服务端处理
      try { voice.ws.send(JSON.stringify({ type: 'stop' })) } catch {}
    }
  } catch {}
  stopMic()
  // 不立即关闭/隐藏，等待 asr_closed 或 ws.onclose 回调
}

onBeforeUnmount(() => {
  try { if (voice.ws && voice.ws.readyState === WebSocket.OPEN) voice.ws.close() } catch {}
  stopMic()
  if (cancelStream.value) { try { cancelStream.value() } catch {} cancelStream.value = null }
})

const filteredFriends = computed(() => {
  const q = friendFilter.value.trim().toLowerCase()
  if (!q) return friends.value
  return friends.value.filter(f => ((f.roleName || '').toLowerCase().indexOf(q) !== -1))
})

const filteredGroups = computed(() => {
  const q = groupFilter.value.trim().toLowerCase()
  if (!q) return groups.value
  return groups.value.filter((g: ChatGroup) => ((g.groupName || '').toLowerCase().indexOf(q) !== -1))
})

const currentRoleTitle = computed(() => {
  const role = friends.value.find(f => f.roleId === selectedRoleId.value)
  return role ? role.roleName : '请选择角色'
})

async function loadFriends() {
  friends.value = await listFriends()
}

async function onSelectRole(roleId: number) {
  // cancel ongoing stream when switching role
  if (cancelStream.value) { try { cancelStream.value() } catch {} cancelStream.value = null }
  selectedRoleId.value = roleId
  // 查找最近分组
  const latest = await getLatestGroupByRole(roleId)
  currentGroupId.value = latest?.id
  await loadGroups()
  messages.value = []
  nextCursor.value = null
  hasMore.value = false
  if (currentGroupId.value) {
    await loadHistory(true)
    await nextTick(); scrollToBottom()
  }
}

async function loadHistory(initial = false) {
  if (!currentGroupId.value) return
  const resp = await getHistoryByCursor(currentGroupId.value, initial ? undefined : nextCursor.value || undefined, 10, true)
  if (initial) {
    messages.value = resp.items
  } else {
    messages.value = [...resp.items, ...messages.value]
  }
  hasMore.value = resp.hasMore
  nextCursor.value = resp.nextCursor || null
}

async function loadMore() {
  loadingMore.value = true
  try {
    await loadHistory(false)
  } finally {
    loadingMore.value = false
  }
}

const canSend = computed(() => !!text.value.trim() && !!currentGroupId.value)

async function send() {
  if (!canSend.value) return
  const content = text.value.trim()
  text.value = ''
  // 先乐观添加用户消息
  const userMsg: ChatHistoryItem = { id: Math.random(), groupId: currentGroupId.value!, userId: 0, messageType: 'user', message: content, createTime: new Date().toISOString() }
  messages.value = [...messages.value, userMsg]
  // 占位的 AI 流式消息
  const aiMsg: ChatHistoryItem = { id: -1, groupId: currentGroupId.value!, userId: 0, messageType: 'ai', message: '', createTime: new Date().toISOString() }
  messages.value = [...messages.value, aiMsg]
  await nextTick(); scrollToBottom()

  sending.value = true
  try {
    const idx = messages.value.length - 1 // ai placeholder index
    cancelStream.value = sendMessageStreamSse(
      currentGroupId.value!,
      content,
      (token: string) => {
        // append token
        if (messages.value[idx]) {
          messages.value[idx] = { ...messages.value[idx], message: (messages.value[idx].message || '') + token }
          // 滚动到底部
          if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
        }
      },
      async () => {
        // 拉取最新，获得持久化后的完整 AI 消息
        const latest = await getLatest(currentGroupId.value!)
        messages.value = latest
        await nextTick(); scrollToBottom()
        sending.value = false
        cancelStream.value = null
      },
      () => {
        sending.value = false
        cancelStream.value = null
      }
    )
  } catch {
    sending.value = false
    cancelStream.value = null
  }
}

function scrollToBottom() {
  if (!msgBox.value) return
  msgBox.value.scrollTop = msgBox.value.scrollHeight
}

async function reload() {
  await loadFriends()
  if (selectedRoleId.value) {
    await onSelectRole(selectedRoleId.value)
  }
}

async function togglePin(f: FriendRoleVO) {
  await updatePin(f.roleId, !(f.pinned === 1), f.pinOrder ?? 0)
  await loadFriends()
}

async function removeFriendClick(f: FriendRoleVO) {
  try {
    await ElMessageBox.confirm(`确认删除好友「${f.roleName}」？`, '删除好友', { type: 'warning' })
  } catch { return }
  await removeFriend(f.roleId)
  if (selectedRoleId.value === f.roleId) {
    selectedRoleId.value = undefined
    currentGroupId.value = undefined
    messages.value = []
  }
  await loadFriends()
}

async function loadGroups() {
  if (!selectedRoleId.value) { groups.value = []; return }
  groups.value = await getGroupsByRole(selectedRoleId.value)
}

function toggleGroupDrawer() {
  groupDrawerVisible.value = !groupDrawerVisible.value
}

async function switchGroup(groupId: number) {
  if (!groupId || currentGroupId.value === groupId) return
  // cancel ongoing stream when switching group
  if (cancelStream.value) { try { cancelStream.value() } catch {} cancelStream.value = null }
  currentGroupId.value = groupId
  messages.value = []
  nextCursor.value = null
  hasMore.value = false
  await loadHistory(true)
  await nextTick(); scrollToBottom()
}

async function onCreateGroup() {
  if (!selectedRoleId.value) return
  if (cancelStream.value) { try { cancelStream.value() } catch {} cancelStream.value = null }
  creatingGroup.value = true
  try {
    const newId = await createGroup(selectedRoleId.value, '新话题')
    currentGroupId.value = newId
    await loadGroups()
    await loadHistory(true)
    await nextTick(); scrollToBottom()
  } finally {
    creatingGroup.value = false
  }
}

async function renameGroup(g: ChatGroup) {
  try {
    const { value } = await ElMessageBox.prompt('输入新的会话名称', '重命名会话', {
      inputValue: g.groupName || '新话题',
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空'
    })
    const name = String(value).trim()
    if (!name) return
    await apiUpdateGroup({ id: g.id, groupName: name })
    ElMessage.success('重命名成功')
    await loadGroups()
  } catch { /* cancelled */ }
}

async function deleteGroupClick(g: ChatGroup) {
  try {
    await ElMessageBox.confirm('确定删除该会话及其消息记录吗？此操作不可恢复', '删除会话', { type: 'warning' })
  } catch { return }
  await apiDeleteGroup(g.id)
  if (currentGroupId.value === g.id) {
    currentGroupId.value = undefined
    messages.value = []
    nextCursor.value = null
    hasMore.value = false
  }
  await loadGroups()
  ElMessage.success('已删除会话')
}

onMounted(async () => {
  await loadFriends()
  // 从路由入参预选
  const roleIdParam = route.params.roleId ? Number(route.params.roleId) : undefined
  if (roleIdParam) {
    await onSelectRole(roleIdParam)
  }
})

</script>

<style scoped>
.chat-page { display: grid; grid-template-columns: 320px minmax(0, 1fr); height: 100%; min-height: 0; }
.sidebar { border-right: 1px solid #eee; display: flex; flex-direction: column; }
.side-top { padding: 8px; }
.friend-list { overflow: auto; padding: 8px; }
.friend-item { display: grid; grid-template-columns: 44px 1fr 24px; align-items: center; padding: 8px; border-radius: 8px; cursor: pointer; }
.friend-item.active { background: #f5f7fa; }
.friend-item:hover { background: #f9fafb; }
.friend-item .avatar { width: 40px; height: 40px; border-radius: 8px; background: #f2f2f2; background-size: cover; background-position: center; display: flex; align-items: center; justify-content: center; font-size: 18px; }
.friend-item .info { margin-left: 8px; }
.friend-item .name { font-weight: 600; display: flex; align-items: center; gap: 6px; }
.friend-item .last { color: #999; font-size: 12px; }
.friend-item .more { justify-self: end; color: #999; }

.chat-main { display: flex; flex-direction: column; min-width: 0; width: 100%; align-items: stretch; }
.chat-header { height: 56px; border-bottom: 1px solid #eee; display: flex; align-items: center; justify-content: space-between; padding: 0 12px; gap: 8px; width: 100%; }
.messages { position: relative; flex: 1 1 auto; overflow: auto; padding: 8px 12px; display: flex; flex-direction: column; gap: 8px; min-height: 0; width: 100%; }
.composer { border-top: 1px solid #eee; padding: 8px; display: grid; grid-template-columns: 1fr 100px; gap: 8px; width: 100%; box-sizing: border-box; }
.msg { display: grid; grid-template-columns: 40px 1fr; gap: 8px; align-items: flex-end; max-width: 80%; }
.msg .bubble { padding: 8px 12px; border-radius: 16px; background: #f5f5f5; }
.msg .time { margin-top: 2px; color: #aaa; font-size: 12px; }
.msg .avatar { width: 40px; height: 40px; border-radius: 8px; overflow: hidden; background: #f2f2f2; }
.msg .avatar :deep(img), .msg .avatar :deep(.el-image__inner) { width: 100%; height: 100%; object-fit: cover; }
.msg.user { justify-self: end; align-self: flex-end; margin-left: auto; grid-template-columns: 1fr 40px; }
.msg.user .msg-content { text-align: right; }
.msg.user .msg-avatar.right { justify-self: end; }
.msg.ai { align-self: flex-start; }
.empty { text-align: center; color: #999; padding: 16px; }

.group-item { padding: 8px; border-radius: 8px; cursor: pointer; }
.group-item:hover { background: #f6f7fb; }
.group-item.active { background: #eef2ff; }
.group-item { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 8px; }
.group-item .name { font-weight: 600; }
.group-item .last { color: #999; font-size: 12px; }
.group-item .more { justify-self: end; color: #999; }

/* Voice overlay */
.voice-overlay { position: absolute; inset: 0; z-index: 20; display: flex; align-items: center; justify-content: center; }
.voice-overlay .overlay-bg { position: absolute; inset: 0; backdrop-filter: blur(3px); background: rgba(0,0,0,0.35); }
.voice-overlay .overlay-content { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; gap: 16px; color: #fff; }
.voice-overlay .center { display: flex; flex-direction: column; align-items: center; gap: 10px; }
.voice-overlay .pulse-circle { width: 140px; height: 140px; border-radius: 50%; background: rgba(64,158,255,0.85); display: flex; align-items: center; justify-content: center; animation: pulse 1.6s infinite ease-in-out; }
.voice-overlay .pulse-circle.muted { background: rgba(148,163,184,0.85); }
.voice-overlay .center-icon { font-size: 54px; color: #fff; }
.voice-overlay .hint { font-size: 14px; color: #e5e7eb; }
.voice-overlay .realtime { max-width: 70vw; text-align: center; }
.voice-overlay .realtime .line { color: #e5e7eb; font-size: 14px; white-space: nowrap; text-overflow: ellipsis; overflow: hidden; }
.voice-overlay .realtime .line.ai { color: #c7d2fe; }
.voice-overlay .controls { display: flex; gap: 20px; margin-top: 8px; }
.voice-overlay .hangup { background: #ef4444; border-color: #ef4444; }

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(64,158,255, 0.4); }
  70% { transform: scale(1); box-shadow: 0 0 0 20px rgba(64,158,255, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(64,158,255, 0); }
}
</style>
