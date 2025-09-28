<template>
  <div class="admin-page">
    <div class="toolbar">
      <el-input v-model="q" placeholder="搜索角色名称/描述" clearable @keyup.enter="fetch" style="max-width: 320px" />
      <el-input v-model="tag" placeholder="标签(包含匹配)" clearable style="max-width: 200px" />
      <el-select v-model="sort" style="width: 140px">
        <el-option label="热门" value="hot" />
        <el-option label="最新" value="new" />
        <el-option label="名称" value="name" />
      </el-select>
      <el-select v-model="isActive" placeholder="启用状态" clearable style="width: 140px">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-select v-model="isSystem" placeholder="是否系统" clearable style="width: 140px">
        <el-option label="系统角色" :value="1" />
        <el-option label="用户角色" :value="0" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button type="success" @click="onCreate">新增角色</el-button>
      <el-popconfirm title="确认批量删除选中项？" @confirm="onBatchDelete">
        <template #reference>
          <el-button type="danger" :disabled="!selection.length">批量删除</el-button>
        </template>
      </el-popconfirm>
    </div>

    <el-table :data="list" border style="width:100%" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="id" label="ID" width="110" />
      <el-table-column prop="roleName" label="名称" min-width="160" />
      <el-table-column prop="roleDescription" label="简介" min-width="220" show-overflow-tooltip />
      <el-table-column prop="tags" label="标签" width="200" show-overflow-tooltip />
      <el-table-column prop="likes" label="点赞" width="90" />
      <el-table-column prop="isSystem" label="系统" width="80">
        <template #default="{ row }"><el-tag :type="row.isSystem ? 'warning' : 'info'">{{ row.isSystem ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="isActive" label="启用" width="120">
        <template #default="{ row }">
          <el-switch :model-value="row.isActive === 1" @change="(val:boolean)=>onToggleActive(row, val)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="onView(row)">查看</el-button>
          <el-button size="small" type="primary" plain @click="onEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该角色？" @confirm="() => onDelete(row)">
            <template #reference>
              <el-button size="small" type="danger" plain>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增角色' : '编辑角色'" width="680px">
      <el-form :model="form" label-width="96px" :rules="rules" ref="formRef">
        <el-form-item label="名称" prop="roleName">
          <el-input v-model="form.roleName" maxlength="64" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.roleDescription" type="textarea" maxlength="256" />
        </el-form-item>
        <el-form-item label="问候语">
          <el-input v-model="form.greeting" type="textarea" maxlength="512" />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="头像">
          <el-input v-model="form.avatar" placeholder="头像URL" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔或JSON" />
        </el-form-item>
        <el-form-item label="系统角色">
          <el-switch v-model="form.isSystemBool" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isActiveBool" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import type { AiRole } from '@/services/roles'
import { adminPage, adminCreate, adminUpdate, adminChangeStatus, adminDelete, adminDeleteBatch, type AdminRoleUpsertRequest } from '@/services/adminRoles'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const q = ref('')
const tag = ref('')
const sort = ref<'hot' | 'new' | 'name'>('new')
const isActive = ref<number | undefined>()
const isSystem = ref<number | undefined>()

const page = ref(1)
const size = ref(12)
const total = ref(0)

const list = ref<AiRole[]>([])
const selection = ref<AiRole[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const form = reactive<any>({
  id: undefined,
  roleName: '',
  roleDescription: '',
  greeting: '',
  systemPrompt: '',
  avatar: '',
  tags: '',
  isSystemBool: false,
  isActiveBool: true,
})
const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

async function fetch() {
  const data = await adminPage({ q: q.value, tag: tag.value, sort: sort.value, page: page.value, size: size.value, isActive: isActive.value, isSystem: isSystem.value })
  list.value = data.items
  total.value = data.total
}

function onSearch() {
  page.value = 1
  fetch()
}

function onSelectionChange(rows: AiRole[]) {
  selection.value = rows
}

function onCreate() {
  dialogMode.value = 'create'
  Object.assign(form, { id: undefined, roleName: '', roleDescription: '', greeting: '', systemPrompt: '', avatar: '', tags: '', isSystemBool: false, isActiveBool: true })
  dialogVisible.value = true
}
function onEdit(row: AiRole) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    roleName: row.roleName,
    roleDescription: row.roleDescription,
    greeting: row.greeting,
    systemPrompt: row.systemPrompt,
    avatar: row.avatar,
    tags: row.tags,
    isSystemBool: row.isSystem === 1,
    isActiveBool: row.isActive === 1,
  })
  dialogVisible.value = true
}

async function onSubmit() {
  // @ts-ignore
  await formRef.value?.validate()
  const body: AdminRoleUpsertRequest = {
    id: form.id,
    roleName: form.roleName,
    roleDescription: form.roleDescription,
    greeting: form.greeting,
    systemPrompt: form.systemPrompt,
    avatar: form.avatar,
    tags: form.tags,
    isSystem: form.isSystemBool ? 1 : 0,
    isActive: form.isActiveBool ? 1 : 0,
  }
  if (dialogMode.value === 'create') {
    const id = await adminCreate(body)
    if (id) ElMessage.success('创建成功')
  } else {
    await adminUpdate(body)
    ElMessage.success('保存成功')
  }
  dialogVisible.value = false
  fetch()
}

async function onToggleActive(row: AiRole, val: boolean) {
  await adminChangeStatus(row.id as number, val ? 1 : 0)
  row.isActive = val ? 1 : 0
}

async function onDelete(row: AiRole) {
  await adminDelete(row.id as number)
  ElMessage.success('已删除')
  fetch()
}

async function onBatchDelete() {
  if (!selection.value.length) return
  const ids = selection.value.map(x => x.id as number)
  await adminDeleteBatch(ids)
  ElMessage.success('批量删除成功')
  fetch()
}

function onView(row: AiRole) {
  ElMessageBox.alert(
    `<div style='text-align:left'>`+
    `<div><b>ID：</b>${row.id}</div>`+
    `<div><b>名称：</b>${row.roleName}</div>`+
    `<div><b>简介：</b>${row.roleDescription || ''}</div>`+
    `<div><b>标签：</b>${row.tags || ''}</div>`+
    `<div><b>系统：</b>${row.isSystem ? '是' : '否'}</div>`+
    `<div><b>启用：</b>${row.isActive ? '是' : '否'}</div>`+
    `</div>`,
    '角色详情', { dangerouslyUseHTMLString: true }
  )
}

onMounted(fetch)
</script>

<style scoped>
.admin-page { padding: 16px; }
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.pager { display: flex; justify-content: center; padding: 12px 0; }
</style>
