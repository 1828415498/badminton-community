<script setup>
/**
 * 活动报名名单（报表打印页）。
 *
 * 路由：/print/activity/:id/roster —— 独立于 AppLayout，打印时无界面元素干扰。
 *
 * 数据来源（都是已有接口，本页没有新增任何后端接口）：
 *   - getActivity(id)        活动标题 / 时间 / 人数上限 / 有效报名数 / 占用球场
 *   - listMembers(id, {...}) 报名人列表（后端只返回 status=1 的有效报名）
 *   - listVenues + listCourts 把 courtIds 翻成球场名（V1 单场馆，取第一个场馆即可）
 *
 * 权限由后端判定：活动处于公开状态（已发布/已结束）时登录用户可看名单，
 * 其余状态仅创建者与管理员可看。前端隐藏入口不算权限。
 */
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getActivity, listMembers } from '@/api/activity'
import { listVenues, listCourts } from '@/api/venue'

const route = useRoute()
const router = useRouter()

const activity = ref(null)
const members = ref([])
const courtNames = ref({})
const memberTotal = ref(0)
const loading = ref(true)
const errorText = ref('')
const printedAt = ref('')

/** 后端对 page_size 有 50 的上限，所以名单一次最多取 50 条 */
const PAGE_SIZE = 50

const STATUS_TEXT = {
  0: '待审核',
  1: '已发布',
  2: '已驳回',
  3: '已取消',
  4: '已结束'
}

const activityStatusText = computed(() =>
  activity.value ? STATUS_TEXT[activity.value.status] || '未知' : ''
)

/** 该活动占用的球场名，如「3 号场、4 号场」；拿不到就退回显示编号 */
const courtLabel = computed(() => {
  const ids = (activity.value && activity.value.court_ids) || []
  if (!ids.length) return '—'
  return ids.map((id) => courtNames.value[id] || `#${id}`).join('、')
})

const truncated = computed(() => memberTotal.value > members.value.length)

function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
    `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  )
}

function doPrint() {
  printedAt.value = nowText()
  window.print()
}

/** 球场 id → 名称。失败不影响主流程（名单本身才是重点），因此单独 try 且不抛。 */
async function loadCourtNames() {
  try {
    const venues = await listVenues()
    const first = Array.isArray(venues) ? venues[0] : null
    if (!first) return
    // 注意：这个接口返回的是【数组】，不是分页对象（它只接受 status 过滤，没有分页参数）。
    // 早先这里误用了 courts.list，导致球场名映射为空，界面上退回显示 #1、#2。
    const courts = await listCourts(first.id)
    const rows = Array.isArray(courts) ? courts : []
    const map = {}
    rows.forEach((c) => {
      map[c.id] = c.name
    })
    courtNames.value = map
  } catch (e) {
    // 忽略：courtLabel 会退回显示 "#id"
  }
}

onMounted(async () => {
  const id = route.params.id
  try {
    activity.value = await getActivity(id)
    const page = await listMembers(id, { page: 1, page_size: PAGE_SIZE })
    members.value = (page && page.list) || []
    memberTotal.value = (page && page.total) || members.value.length
    await loadCourtNames()
  } catch (e) {
    // request.js 的响应拦截器已经弹过提示，这里只做页面内留痕
    errorText.value = e && e.message ? e.message : '名单加载失败'
  } finally {
    loading.value = false
    if (!errorText.value) {
      setTimeout(doPrint, 500)
    }
  }
})
</script>

<template>
  <div class="print-page">
    <div class="toolbar no-print">
      <button type="button" class="btn btn-primary" @click="doPrint">打印 / 另存为 PDF</button>
      <button type="button" class="btn" @click="router.back()">返回</button>
      <span class="hint">打印对话框会自动弹出；若被浏览器拦截，点左边的按钮。</span>
    </div>

    <p v-if="loading" class="state">正在生成名单…</p>
    <p v-else-if="errorText" class="state state-error">{{ errorText }}</p>

    <article v-else-if="activity" class="paper">
      <header class="paper-head">
        <div class="brand">羽毛球社区</div>
        <h1 class="doc-title">活动报名名单</h1>
        <div class="doc-meta">
          <span>活动编号 <b class="tnum">{{ activity.id }}</b></span>
          <span>打印时间 <b class="tnum">{{ printedAt }}</b></span>
        </div>
      </header>

      <table class="kv">
        <tbody>
          <tr>
            <th>活动标题</th>
            <td colspan="3">{{ activity.title }}</td>
          </tr>
          <tr>
            <th>活动时间</th>
            <td colspan="3" class="tnum">
              {{ activity.start_time }} — {{ String(activity.end_time).slice(11) }}
            </td>
          </tr>
          <tr>
            <th>使用球场</th>
            <td>{{ courtLabel }}</td>
            <th>活动状态</th>
            <td>{{ activityStatusText }}</td>
          </tr>
          <tr>
            <th>发起人</th>
            <td>{{ activity.creator_nickname || '—' }}</td>
            <th>报名人数</th>
            <td class="tnum">
              <b>{{ activity.joined_count ?? members.length }}</b> / {{ activity.max_members }}
            </td>
          </tr>
        </tbody>
      </table>

      <table class="roster">
        <thead>
          <tr>
            <th class="col-no">序号</th>
            <th>昵称</th>
            <th class="col-time">报名时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(m, i) in members" :key="m.id">
            <td class="tnum col-no">{{ i + 1 }}</td>
            <td>{{ m.nickname }}</td>
            <td class="tnum col-time">{{ m.create_time }}</td>
          </tr>
          <tr v-if="!members.length">
            <td colspan="3" class="empty-row">暂无有效报名</td>
          </tr>
        </tbody>
      </table>

      <p v-if="truncated" class="warn no-print-note">
        注意：本页只打印了前 {{ members.length }} 条，该活动共 {{ memberTotal }} 条有效报名。
      </p>

      <footer class="paper-foot">
        <p>名单仅含状态为「已报名」的成员，已取消报名的成员不计入。</p>
        <p>请按名单人数提前到场并核对场地安排。</p>
      </footer>
    </article>
  </div>
</template>

<style scoped>
.print-page {
  min-height: 100vh;
  padding: 24px 16px 48px;
  background: #f4f4f2;
  color: #2c2c2a;
  font-size: 14px;
}

.toolbar {
  max-width: 720px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.btn {
  padding: 7px 16px;
  border: 1px solid #c9c7bf;
  border-radius: 8px;
  background: #fff;
  color: #2c2c2a;
  font-size: 13px;
  cursor: pointer;
}

.btn:hover {
  border-color: #8a8880;
}

.btn-primary {
  background: #2c2c2a;
  border-color: #2c2c2a;
  color: #fff;
}

.hint {
  font-size: 12px;
  color: #767570;
}

.state {
  max-width: 720px;
  margin: 32px auto;
  text-align: center;
  color: #767570;
}

.state-error {
  color: #a32d2d;
}

.paper {
  max-width: 720px;
  margin: 0 auto;
  padding: 36px 40px 28px;
  background: #fff;
  border: 1px solid #e2e0d8;
  border-radius: 10px;
}

.paper-head {
  padding-bottom: 14px;
  border-bottom: 2px solid #2c2c2a;
}

.brand {
  font-size: 12px;
  letter-spacing: 0.22em;
  color: #767570;
}

.doc-title {
  margin: 6px 0 12px;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.doc-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #767570;
}

.tnum {
  font-family: Consolas, "Cascadia Mono", ui-monospace, monospace;
  font-variant-numeric: tabular-nums;
}

.kv {
  width: 100%;
  margin-top: 20px;
  border-collapse: collapse;
  font-size: 13.5px;
}

.kv th,
.kv td {
  padding: 10px 12px;
  border: 1px solid #e2e0d8;
  text-align: left;
}

.kv th {
  width: 14%;
  background: #f7f6f2;
  font-weight: 500;
  color: #767570;
}

.roster {
  width: 100%;
  margin-top: 24px;
  border-collapse: collapse;
  font-size: 13.5px;
}

.roster th,
.roster td {
  padding: 9px 12px;
  border-bottom: 1px solid #e2e0d8;
  text-align: left;
}

.roster thead th {
  border-bottom: 1.5px solid #2c2c2a;
  font-size: 12px;
  font-weight: 600;
  color: #767570;
}

.col-no {
  width: 12%;
}

.col-time {
  width: 34%;
}

.empty-row {
  padding: 22px 12px;
  text-align: center;
  color: #767570;
}

.warn {
  margin: 14px 0 0;
  font-size: 12px;
  color: #a32d2d;
}

.paper-foot {
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px dashed #c9c7bf;
  font-size: 12px;
  line-height: 1.9;
  color: #767570;
}

.paper-foot p {
  margin: 0;
}

@media print {
  :global(body) {
    background: #fff;
  }

  .no-print {
    display: none !important;
  }

  .print-page {
    min-height: 0;
    padding: 0;
    background: #fff;
  }

  .paper {
    max-width: none;
    margin: 0;
    padding: 0;
    border: 0;
    border-radius: 0;
  }

  @page {
    size: A4;
    margin: 18mm 16mm;
  }
}
</style>
