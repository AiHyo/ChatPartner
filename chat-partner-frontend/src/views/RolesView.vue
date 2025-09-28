<template>
  <div class="roles-page">
    <div class="toolbar">
      <el-input v-model="q" placeholder="搜索角色名称/描述" clearable @keyup.enter="fetch" style="max-width: 320px" />
      <el-select v-model="tag" placeholder="选择标签" clearable filterable style="width: 220px">
        <el-option v-for="t in tags" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="sort" style="width: 160px">
        <el-option label="热门" value="hot" />
        <el-option label="最新" value="new" />
        <el-option label="名称" value="name" />
      </el-select>
      <el-checkbox v-model="onlyNotFriend">仅显示未加好友</el-checkbox>
      <el-button type="primary" @click="fetch">查询</el-button>
    </div>

    <el-row :gutter="16">
      <el-col v-for="r in list" :key="r.id" :span="6">
        <el-card class="role-card" shadow="hover">
          <div class="card-header">
            <div class="avatar">
              <el-image v-if="r.avatar" :src="r.avatar" fit="cover" loading="lazy">
                <template #error>
                  <div class="avatar-fallback">🧩</div>
                </template>
              </el-image>
              <div v-else class="avatar-fallback">🧩</div>
            </div>
            <div class="meta">
              <div class="name">{{ r.roleName }}</div>
              <div class="desc">{{ r.roleDescription }}</div>
            </div>
          </div>
          <div class="tags">
            <el-tag v-for="t in (r.tags || '').split(',').filter(Boolean)" :key="t" size="small" type="info" effect="light">{{ t.trim() }}</el-tag>
          </div>
          <div class="actions">
            <div class="left">
              <el-button size="small" :type="likedMap[r.id] ? 'danger' : 'default'" plain @click="onToggleLike(r)">
                <span v-if="likedMap[r.id]">❤️</span>
                <span v-else>🤍</span>
                <span style="margin-left:4px">{{ r.likes ?? 0 }}</span>
              </el-button>
            </div>
            <div class="right">
              <template v-if="friendRoleMap[r.id]">
                <el-button size="small" type="danger" plain @click="onRemoveFriend(r)">删除好友</el-button>
              </template>
              <template v-else>
                <el-button size="small" type="primary" @click="onAddFriend(r)">添加好友</el-button>
              </template>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="pager">
      <el-pagination
        background
        layout="prev, pager, next"
        :page-size="size"
        :current-page="page"
        :total="total"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listTags, searchRolesPage, toggleLike, getLikedRoleIds, type AiRole } from '@/services/roles'
import { listFriends, addFriend, removeFriend } from '@/services/friends'
import { useUserStore } from '@/stores/user'

const q = ref('')
const tag = ref<string | undefined>()
const sort = ref<'hot' | 'new' | 'name'>('hot')
const onlyNotFriend = ref(false)
const page = ref(1)
const size = ref(12)
const total = ref(0)

const tags = ref<string[]>([])
const list = ref<AiRole[]>([])
const friendRoleMap = ref<Record<number, boolean>>({})
const likedMap = ref<Record<number, boolean>>({})

async function fetchTags() {
  tags.value = await listTags()
}

async function fetchFriends() {
  const store = useUserStore()
  await store.ensureInit()
  if (!store.isLoggedIn) { friendRoleMap.value = {}; return }
  const friends = await listFriends()
  const map: Record<number, boolean> = {}
  for (const f of friends) map[f.roleId] = true
  friendRoleMap.value = map
}

async function fetchLiked() {
  const store = useUserStore()
  await store.ensureInit()
  if (!store.isLoggedIn) { likedMap.value = {}; return }
  const ids = await getLikedRoleIds()
  const map: Record<number, boolean> = {}
  for (const id of ids) map[id as number] = true
  likedMap.value = map
}

async function fetch() {
  const data = await searchRolesPage({ q: q.value, tag: tag.value, sort: sort.value, page: page.value, size: size.value, onlyNotFriend: onlyNotFriend.value })
  list.value = data.items
  total.value = data.total
}

function onPageChange(p: number) {
  page.value = p
  fetch()
}

async function onAddFriend(r: AiRole) {
  await addFriend(r.id)
  await fetchFriends()
  await fetch()
}

async function onRemoveFriend(r: AiRole) {
  await removeFriend(r.id)
  await fetchFriends()
  await fetch()
}

async function onToggleLike(r: AiRole) {
  const liked = await toggleLike(r.id)
  likedMap.value[r.id] = liked
  const cur = r.likes ?? 0
  r.likes = Math.max(0, cur + (liked ? 1 : -1))
}

onMounted(async () => {
  await fetchTags()
  await fetchFriends()
  await fetchLiked()
  await fetch()
})
</script>

<style scoped>
.roles-page { padding: 16px; }
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.role-card { margin-bottom: 16px; cursor: default; }
/* 让卡片内容固定高度并使用列布局，保证底部按钮对齐 */
.role-card :deep(.el-card__body) { display: flex; flex-direction: column; height: 220px; }
.card-header { display: flex; gap: 12px; }
.avatar { width: 56px; height: 56px; border-radius: 8px; overflow: hidden; background: #f5f5f5; display: flex; align-items: center; justify-content: center; }
.avatar :deep(img), .avatar :deep(.el-image__inner) { width: 100%; height: 100%; object-fit: cover; }
.avatar-fallback { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; font-size: 24px; color: #999; }
.meta { display: flex; flex-direction: column; }
.name { font-weight: 600; font-size: 16px; }
/* 多行省略，避免卡片拉高 */
.desc { color: #666; font-size: 12px; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
/* 固定标签区域高度，避免有无标签时卡片高度不一致 */
.tags { margin: 8px 0; display: flex; gap: 8px; flex-wrap: wrap; min-height: 24px; max-height: 48px; overflow: hidden; }
.actions { display: flex; align-items: center; justify-content: space-between; margin-top: auto; }
.pager { display: flex; justify-content: center; margin: 16px 0; }
</style>
