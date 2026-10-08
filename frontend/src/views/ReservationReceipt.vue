<script setup>
/**
 * 预约凭证（报表打印页）。
 *
 * 路由：/print/reservation/:id —— 独立于 AppLayout，页面上没有任何导航与 Tab，
 * 因此打印时不需要"藏掉一堆界面元素"。
 *
 * 数据来自 GET /api/reservations/{id}/receipt，而该接口查的是数据库视图
 * v_reservation_detail（见 sql/views.sql）—— 场馆名、占用时长、来源、状态文本
 * 这四个字段在 reservation 表里都不存在，是视图实时算出来的。
 *
 * 权限由后端判定（本人或管理员），本页不做任何权限判断 —— 前端隐藏入口不算权限。
 */
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getReservationReceipt } from '@/api/reservation'

const route = useRoute()
const router = useRouter()

const receipt = ref(null)
const loading = ref(true)
const errorText = ref('')
const printedAt = ref('')

function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
    `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  )
}

function doPrint() {
  // 打印时间在弹出对话框「之前」记下，避免出现在纸上的时间比实际晚
  printedAt.value = nowText()
  window.print()
}

onMounted(async () => {
  try {
    receipt.value = await getReservationReceipt(route.params.id)
  } catch (e) {
    // request.js 的响应拦截器已经弹过 ElMessage 了，这里只负责在页面里留痕，
    // 不再重复提示（error.__toasted 标记就是为这种情况准备的）
    errorText.value = e && e.message ? e.message : '凭证加载失败'
  } finally {
    loading.value = false
    if (!errorText.value) {
      // 这是个专门的打印页：进页面就把打印对话框拉起来，少点一次
      setTimeout(doPrint, 400)
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

    <p v-if="loading" class="state">正在生成凭证…</p>
    <p v-else-if="errorText" class="state state-error">{{ errorText }}</p>

    <article v-else-if="receipt" class="paper">
      <header class="paper-head">
        <div class="brand">羽毛球社区</div>
        <h1 class="doc-title">场地预约凭证</h1>
        <div class="doc-meta">
          <span>凭证编号 <b class="tnum">{{ receipt.reservation_id }}</b></span>
          <span>打印时间 <b class="tnum">{{ printedAt }}</b></span>
        </div>
      </header>

      <table class="kv">
        <tbody>
          <tr>
            <th>场馆</th>
            <td>{{ receipt.venue_name }}</td>
            <th>球场</th>
            <td>{{ receipt.court_name }}</td>
          </tr>
          <tr>
            <th>占用方</th>
            <td>{{ receipt.owner_display }}</td>
            <th>来源</th>
            <td>{{ receipt.source_type }}</td>
          </tr>
          <tr>
            <th>开始时间</th>
            <td class="tnum">{{ receipt.start_time }}</td>
            <th>结束时间</th>
            <td class="tnum">{{ receipt.end_time }}</td>
          </tr>
          <tr>
            <th>占用时长</th>
            <td class="tnum">{{ receipt.duration_hours }} 小时</td>
            <th>状态</th>
            <td>{{ receipt.status_text }}</td>
          </tr>
          <tr>
            <th>下单时间</th>
            <td class="tnum">{{ receipt.create_time }}</td>
            <th>取消时间</th>
            <td class="tnum">{{ receipt.cancel_time || '—' }}</td>
          </tr>
        </tbody>
      </table>

      <footer class="paper-foot">
        <p>本凭证由系统实时生成，不作为入场唯一依据，请配合场馆核验。</p>
        <p>开场前不足 4 小时的预约不可自行取消；如需调整请联系场馆管理员。</p>
      </footer>
    </article>
  </div>
</template>

<style scoped>
/* ---------- 屏幕样式 ---------- */
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

/* ---------- 纸张（屏幕上是白卡片，打印时就是纸） ---------- */
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
  padding: 11px 12px;
  border: 1px solid #e2e0d8;
  text-align: left;
}

.kv th {
  width: 14%;
  background: #f7f6f2;
  font-weight: 500;
  color: #767570;
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

/* ---------- 打印样式：只留纸，不留界面 ---------- */
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
