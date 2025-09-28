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
          <el-tooltip content="语音对话（功能待定）" placement="bottom">
            <el-button circle :disabled="!selectedRoleId" title="语音对话">
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
          <div class="bubble">{{ m.message }}</div>
          <div class="time">{{ formatTime(m.createTime) }}</div>
        </div>
        <div v-if="messages.length === 0 && currentGroupId" class="empty">暂无消息，开始对话吧</div>
        <div v-if="!currentGroupId && selectedRoleId" class="empty">该角色暂无分组，请先创建分组后再聊天</div>
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
import { onMounted, ref, computed, nextTick } from 'vue'
import { listFriends, removeFriend, updatePin, type FriendRoleVO } from '@/services/friends'
import { getLatestGroupByRole, getGroupsByRole, type ChatGroup, createGroup, updateGroup as apiUpdateGroup, deleteGroup as apiDeleteGroup } from '@/services/chatGroups'
import { getHistoryByCursor, getLatest, type ChatHistoryItem } from '@/services/chatHistory'
import { sendMessage } from '@/services/chat'
import { useRoute } from 'vue-router'
import { Microphone, Collection, CirclePlus } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'

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

function formatTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleString()
}

async function loadFriends() {
  friends.value = await listFriends()
}

async function onSelectRole(roleId: number) {
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
  sending.value = true
  try {
    await sendMessage(currentGroupId.value!, text.value.trim())
    text.value = ''
    // 重新拉最新
    const latest = await getLatest(currentGroupId.value!)
    messages.value = latest
    await nextTick(); scrollToBottom()
  } finally {
    sending.value = false
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
  currentGroupId.value = groupId
  messages.value = []
  nextCursor.value = null
  hasMore.value = false
  await loadHistory(true)
  await nextTick(); scrollToBottom()
}

async function onCreateGroup() {
  if (!selectedRoleId.value) return
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
.chat-page { display: grid; grid-template-columns: 280px 1fr; height: calc(100vh - 60px); }
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

.chat-main { display: flex; flex-direction: column; }
.chat-header { height: 56px; border-bottom: 1px solid #eee; display: flex; align-items: center; justify-content: space-between; padding: 0 12px; gap: 8px; }
.chat-header .actions { display: flex; align-items: center; gap: 8px; }
.messages { flex: 1; overflow: auto; padding: 8px 12px; display: flex; flex-direction: column; gap: 8px; }
.load-more { text-align: center; margin: 4px 0; }
.msg { display: inline-flex; max-width: 70%; }
.msg .bubble { padding: 8px 12px; border-radius: 16px; background: #f5f5f5; }
.msg .time { align-self: flex-end; margin-left: 8px; color: #aaa; font-size: 12px; }
.msg.user { align-self: flex-end; }
.msg.user .bubble { background: #409eff; color: #fff; }
.msg.ai { align-self: flex-start; }
.composer { border-top: 1px solid #eee; padding: 8px; display: grid; grid-template-columns: 1fr 100px; gap: 8px; }
.empty { text-align: center; color: #999; padding: 16px; }

.group-item { padding: 8px; border-radius: 8px; cursor: pointer; }
.group-item:hover { background: #f6f7fb; }
.group-item.active { background: #eef2ff; }
.group-item { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 8px; }
.group-item .name { font-weight: 600; }
.group-item .last { color: #999; font-size: 12px; }
.group-item .more { justify-self: end; color: #999; }
</style>
