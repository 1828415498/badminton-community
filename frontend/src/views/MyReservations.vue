<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myReservations, cancelReservation } from '@/api/reservation'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref(null)

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (status.value !== null) params.status = status.value
    const res = await myReservations(params)
    list.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function doCancel(row) {
  await ElMessageBox.confirm(
    `确认取消 ${row.court_name} ${row.start_time} 的预约吗？`,
    '取消预约',
    { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '再想想' }
  )
  await cancelReservation(row.id)
  ElMessage.success('已取消')
  await load()
}

/** 开场前不足 4 小时则不允许自己取消（后端也有校验，前端先给出提示态） */
function canCancel(row) {
  if (row.status !== 1) return false
  const start = new Date(row.start_time.replace(/-/g, '/')).getTime()
  return start - Date.now() > 4 * 3600 * 1000
}

function statusTag(row) {
  if (row.status === 2) return { type: 'info', text: '已取消' }
  const start = new Date(row.start_time.replace(/-/g, '/')).getTime()
  if (start + 3600 * 1000 < Date.now()) return { type: '', text: '已结束' }
  return { type: 'success', text: '有效' }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">我的预约</h2>
        <p class="page-sub">个人球场预约记录（不含活动占场）</p>
      </div>
      <el-radio-group v-model="status" @change="(page = 1), load()">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="1">有效</el-radio-button>
        <el-radio-button :value="2">已取消</el-radio-button>
      </el-radio-group>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" empty-text="暂无预约记录">
        <el-table-column prop="court_name" label="球场" min-width="110" />
        <el-table-column label="时间段" min-width="220">
          <template #default="{ row }">
            {{ row.start_time }} ~ {{ row.end_time.slice(11) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row).type" size="small" effect="light">
              {{ statusTag(row).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="create_time" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canCancel(row)"
              type="danger"
              link
              size="small"
              @click="doCancel(row)"
            >
              取消
            </el-button>
            <span v-else-if="row.status === 1" class="muted" style="font-size: 12px">
              不可取消
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="(page = 1), load()"
        />
      </div>

      <p class="muted note">
        提示：活动占场（你自己发起的活动锁定的场地）不在这里显示，请到「我的活动」查看。
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.table-card {
  border-radius: 10px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.note {
  margin: 12px 0 0;
}
</style>
