<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppShell from '@/layouts/AppShell.vue'
import { changeUserStatus, listUsers, resetUserPassword } from '@/api/admin'
import { notifyApiError } from '@/api/errors'
import type { AdminPasswordResetResult, UserStatus, UserSummary } from '@/api/types'
import {
  nextStatus,
  roleLabel,
  roleTagType,
  statusActionLabel,
  statusLabel,
  statusTagType,
} from '@/labels'

const loading = ref(false)
const rows = ref<UserSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const statusFilter = ref<UserStatus | ''>('')
const keyword = ref('')

const resetResult = ref<AdminPasswordResetResult | null>(null)
// 弹窗可见性由结果本身推导，避免两个状态手工同步
const resetDialogVisible = computed({
  get: () => resetResult.value !== null,
  set: (visible: boolean) => {
    if (!visible) {
      resetResult.value = null
    }
  },
})
const resetLoadingAccount = ref<string | null>(null)

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const result = await listUsers({
      page: page.value,
      size: size.value,
      status: statusFilter.value,
      keyword: keyword.value,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    notifyApiError(error, '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

/** 筛选、搜索、改每页条数都要回到第一页；翻页保持当前页。 */
function reload(resetPage = false): void {
  if (resetPage) {
    page.value = 1
  }
  void loadUsers()
}

function handleSizeChange(nextSize: number): void {
  size.value = nextSize
  reload(true)
}

function handlePageChange(nextPage: number): void {
  page.value = nextPage
  reload()
}

async function toggleStatus(row: UserSummary): Promise<void> {
  const target = nextStatus(row.status)
  const action = statusActionLabel(row.status)
  try {
    await ElMessageBox.confirm(
      `确认${action}用户 ${row.userAccount}？${target === 'BANNED' ? '该用户当前令牌会立即失效。' : ''}`,
      `${action}用户`,
      { type: 'warning', confirmButtonText: `确认${action}`, cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await changeUserStatus(row.userAccount, target)
    ElMessage.success(`${action}成功`)
    await loadUsers()
  } catch (error) {
    notifyApiError(error, `${action}失败`)
  }
}

async function resetPassword(row: UserSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认重置用户 ${row.userAccount} 的密码？该用户所有旧令牌会立即失效。`,
      '重置密码',
      { type: 'warning', confirmButtonText: '确认重置', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  resetLoadingAccount.value = row.userAccount
  try {
    resetResult.value = await resetUserPassword(row.userAccount)
  } catch (error) {
    notifyApiError(error, '重置密码失败')
  } finally {
    resetLoadingAccount.value = null
  }
}

async function copyPassword(): Promise<void> {
  if (!resetResult.value) return
  try {
    await navigator.clipboard.writeText(resetResult.value.newPassword)
    ElMessage.success('新密码已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择密码')
  }
}

function closeResetDialog(): void {
  resetResult.value = null
}

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

function formatDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return dateFormatter.format(date)
}

onMounted(loadUsers)
</script>

<template>
  <AppShell>
    <div class="page-heading">
      <div>
        <p class="page-kicker">ADMIN</p>
        <h1>用户管理</h1>
        <p class="page-subtitle">分页查看账号，处理封禁、解封和密码重置。</p>
      </div>
      <el-button :loading="loading" @click="loadUsers">刷新</el-button>
    </div>

    <section class="surface-card">
      <div class="toolbar">
        <div class="toolbar-group">
          <el-input
            v-model="keyword"
            placeholder="邮箱或账号"
            clearable
            class="keyword-input"
            @keyup.enter="reload(true)"
            @clear="reload(true)"
          >
            <template #append>
              <el-button @click="reload(true)">搜索</el-button>
            </template>
          </el-input>
          <span class="toolbar-label">状态</span>
          <el-select
            v-model="statusFilter"
            placeholder="全部"
            clearable
            class="status-select"
            @change="reload(true)"
          >
            <el-option label="全部" value="" />
            <el-option label="正常" value="NORMAL" />
            <el-option label="封禁" value="BANNED" />
          </el-select>
        </div>
        <span class="toolbar-meta">共 {{ total }} 个账号</span>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="userAccount"
        class="user-table"
        table-layout="fixed"
      >
        <el-table-column prop="userAccount" label="账号" width="130" show-overflow-tooltip />
        <el-table-column label="昵称" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column prop="emailMasked" label="邮箱" width="170" show-overflow-tooltip />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" effect="plain">
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="140">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="最后登录" width="140">
          <template #default="{ row }">{{ formatDate(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button
              size="small"
              :type="row.status === 'BANNED' ? 'success' : 'danger'"
              plain
              @click="toggleStatus(row)"
            >
              {{ statusActionLabel(row.status) }}
            </el-button>
            <el-button
              size="small"
              :loading="resetLoadingAccount === row.userAccount"
              @click="resetPassword(row)"
            >
              重置密码
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="resetDialogVisible"
      title="密码已重置"
      width="min(92vw, 520px)"
      @closed="closeResetDialog"
    >
      <template v-if="resetResult">
        <p class="dialog-lead">请立即把下面的临时密码交给用户。关闭弹窗后将无法再次查看。</p>
        <el-form label-position="top">
          <el-form-item label="账号">
            <el-input :model-value="resetResult.userAccount" readonly />
          </el-form-item>
          <el-form-item label="临时密码">
            <div class="password-row">
              <el-input :model-value="resetResult.newPassword" readonly />
              <el-button @click="copyPassword">复制</el-button>
            </div>
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button type="primary" @click="resetDialogVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<style scoped>
.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.page-kicker {
  margin: 0 0 8px;
  color: var(--uc-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.page-heading h1 {
  margin: 0;
  font-size: 30px;
  letter-spacing: -0.02em;
}

.page-subtitle {
  margin: 8px 0 0;
  color: var(--uc-muted);
}

.surface-card {
  padding: 24px;
  border: 1px solid var(--uc-line);
  border-radius: 16px;
  background: var(--uc-surface);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-label {
  color: var(--uc-muted);
  font-size: 13px;
}

.keyword-input {
  width: 240px;
}

.status-select {
  width: 140px;
}

.toolbar-meta {
  color: var(--uc-muted);
  font-size: 13px;
}

.user-table {
  width: 100%;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.dialog-lead {
  margin: 0 0 16px;
  color: var(--uc-danger);
  font-size: 13px;
  line-height: 1.7;
}

.password-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.password-row .el-input {
  flex: 1;
}

@media (max-width: 767px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .pagination-row {
    justify-content: center;
  }
}
</style>
