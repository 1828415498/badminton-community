<script setup>
/**
 * 球员档案。
 *
 * <h2>为什么重写</h2>
 * 原来这一页是 `el-descriptions` 展示「用户名 / ID / 注册时间」—— 那是
 * **数据库行详情**，不是在讲一个人。用户在这页看不到任何关于自己打球的经历，
 * 还要填一个图片 URL 才能换头像。
 *
 * 而"我发起过几场、报名过几场、接下来要打哪场"这些数据**后端全都有接口**，
 * 之前一个都没用上。这一页就是把它们用起来。
 *
 * <h2>只显示算得出的数字</h2>
 * 「羽毛球水平」「常打区域」「球友」在 user 表里没有对应字段
 * （只有 id / username / nickname / avatarUrl / role / createTime），
 * **编一个就是骗人**，所以不做。
 *
 * <h2>一个查不到的数字</h2>
 * 社区 9 个接口里**没有「我发布的帖子」变体**（只有公开列表与详情），
 * 所以"发过多少帖"算不出来 —— 不显示，也不用别的数据凑。
 *
 * <h2>两个列表为什么按状态分，而不是按"我发起的/我参加的"分</h2>
 * 第一版按发起/参加分，结果两块内容大量重复：创建球局时后端会自动给创建者
 * 写一条「已报名」记录，所以**我发起的球局必然同时属于"我参加的"**
 * （实测 player01：上图 3 行与下图 3 行完全相同）。
 * 另外待审核(status=0)的球局被混进了"接下来"，而行组件不显示状态，
 * 用户会以为可以报名。
 *
 * 现在改成按**确定性**分：
 *   - 「接下来的球局」= 已发布(status=1) 且尚未开始 —— 确定会发生
 *   - 「需要你跟进」  = 我发起的、待审核(0) 或已驳回(2) —— 还没定下来
 * 两个集合天然不重叠。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { updateMe } from '@/api/auth'
import { uploadImage } from '@/api/file'
import { myActivities, myRegistrations } from '@/api/activity'
import { myReservations } from '@/api/reservation'
import { useUserStore } from '@/stores/user'
import {
  ActivityRow,
  AppAvatar,
  AppButton,
  AppInput,
  AppSheet,
  AppSkeleton,
  EmptyState,
  StatNumber
} from '@/components'
import { dateLong, normTime, parseTime, timeRange } from '@/utils/datetime'

const store = useUserStore()

const me = computed(() => store.user || {})
const isAdmin = computed(() => me.value.role === 1)

/** "2026 年 9 月加入" —— 原始 ISO 串不该出现在界面上 */
const joinedAt = computed(() => {
  const p = parseTime(me.value.create_time)
  if (!p) return ''
  return `${p.date.getFullYear()} 年 ${p.date.getMonth() + 1} 月加入`
})

// ---------- 数据 ----------
/**
 * 单页上限。
 *
 * <p>数量类统计尽量用后端的 `total`（那是 COUNT，精确）。但
 * "报名的球局数"必须过滤掉已取消的报名，只能从列表里数 ——
 * 一旦总条数超过一页，数出来的就是个偏小的值。</p>
 *
 * <p>那种情况下**宁可不显示也不显示错的**：界面上会退化成 "100+"，
 * 明确表示"至少这么多"。</p>
 */
const PAGE_SIZE = 100

const loading = ref(true)
const failed = ref(false)

const hosted = ref({ list: [], total: 0, complete: true })
const joined = ref({ list: [], total: 0, complete: true })
const booked = ref({ list: [], total: 0, complete: true })

async function fetchAll(fetcher) {
  const res = await fetcher({ page: 1, page_size: PAGE_SIZE })
  const total = res?.total ?? 0
  return { list: res?.list || [], total, complete: total <= PAGE_SIZE }
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    const [h, j, b] = await Promise.all([
      fetchAll(myActivities),
      fetchAll(myRegistrations),
      fetchAll(myReservations)
    ])
    hosted.value = h
    joined.value = j
    booked.value = b
  } catch (e) {
    // 页面自己的失败态。不写 catch 的话这个拒绝会升级成未处理的 Promise 拒绝，
    // 把"控制台零报错"这条验收标准废掉（用户其实已经从提示框知道了）。
    failed.value = true
  } finally {
    loading.value = false
  }
}

// ---------- 三个数字 ----------
const hostedCount = computed(() => hosted.value.total)

/** 报名球局：`total` 含已取消的报名（registration.status = 2），不能直接用 */
const joinedCount = computed(() =>
  joined.value.complete
    ? joined.value.list.filter((r) => r.registration?.status === 1).length
    : null
)

/** 订场次数：reservation.status = 2 是已取消，同样要过滤 */
const bookedCount = computed(() =>
  booked.value.complete ? booked.value.list.filter((r) => r.status === 1).length : null
)

/** 数不满一页时给 "N+"，不假装精确 */
function statValue(v, total) {
  return v === null ? `${total}+` : v
}

// ---------- 接下来的球局 ----------
/**
 * 合并「我发起的」与「我报名的」并**去重**。
 *
 * <p>⚠️ 去重是必须的：创建球局时后端会自动给创建者写一条「已报名」记录，
 * 所以同一场球会同时出现在两个列表里。直接相加或直接拼接都会算两次。</p>
 *
 * <p>只收 `status === 1`（已发布）—— **待审核的球局还没定下来**，
 * 放进"接下来"会让人以为可以报名（细节见文件头）。</p>
 */
const upcoming = computed(() => {
  const now = Date.now()
  const byId = new Map()

  const usable = (a) => {
    if (!a || a.status !== 1) return false
    const p = parseTime(a.start_time)
    return !!p && p.date.getTime() > now
  }

  for (const a of hosted.value.list) {
    if (usable(a)) byId.set(a.id, a)
  }
  for (const r of joined.value.list) {
    if (r.registration?.status !== 1) continue
    if (usable(r.activity)) byId.set(r.activity.id, r.activity)
  }

  return [...byId.values()].sort((x, y) =>
    normTime(x.start_time).localeCompare(normTime(y.start_time))
  )
})

const nextGame = computed(() => upcoming.value[0] || null)

const nextGameWhen = computed(() => {
  if (!nextGame.value) return ''
  const day = dateLong(nextGame.value.start_time)
  const range = timeRange(nextGame.value.start_time, nextGame.value.end_time)
  return `${day} · ${range}`
})

// ---------- 需要你跟进 ----------
/**
 * 我发起的、还没定下来的：待审核(0) 与已驳回(2)。
 *
 * 这两个状态都需要用户知道（等审核 / 被驳回要处理），但它们**不该出现在
 * "接下来"里** —— 那一块表达的是"确定会发生的球局"。
 */
const needsAttention = computed(() =>
  hosted.value.list.filter((a) => a.status === 0 || a.status === 2)
)

// ---------- 编辑资料 ----------
const sheetOpen = ref(false)
const saving = ref(false)
const form = ref({ nickname: '', avatarUrl: '' })
const errors = ref({ nickname: '' })

/** 打开弹层时记下原头像 —— 「撤销」要回到打开前那张，而不是回到"没有头像" */
const originalAvatar = ref('')

/** 头像上传的瞬时状态（弹层关掉就重置，不参与保存） */
const fileInput = ref(null)
const uploading = ref(false)
const dragging = ref(false)
const uploadError = ref('')

/**
 * 前端这道限制与后端保持一致
 * （app.upload.allowed-extensions / spring.servlet.multipart.max-file-size）。
 * 它只是体验优化，**不是安全边界** —— 后端必须独立再校验一遍。
 */
const MAX_AVATAR_BYTES = 5 * 1024 * 1024
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

function openEdit() {
  form.value = {
    nickname: me.value.nickname || '',
    avatarUrl: me.value.avatar_url || ''
  }
  originalAvatar.value = form.value.avatarUrl
  errors.value = { nickname: '' }
  uploadError.value = ''
  dragging.value = false
  sheetOpen.value = true
}

function validate() {
  const e = { nickname: '' }
  const n = form.value.nickname.trim()
  if (!n) e.nickname = '昵称不能为空'
  else if (n.length > 64) e.nickname = `不能超过 64 个字符（当前 ${n.length}）`
  errors.value = e
  return !e.nickname
}

// ---------- 头像上传 ----------

/**
 * 上传区主文案。
 *
 * 关键：必须区分「本来就有的头像」和「这次刚选的」。
 * 只看 form.avatarUrl 是否非空就写"已选择头像"是错的 ——
 * 用户可能一个字都没动过，却看到"已选择"，会以为系统背着他干了什么。
 */
const uploaderMain = computed(() => {
  if (uploading.value) return '正在上传…'
  if (!form.value.avatarUrl) return '点击，或把图片拖到这里'
  return form.value.avatarUrl === originalAvatar.value ? '点击更换头像' : '已选择新头像'
})

/** 点整块 = 点隐藏的 file input（可聚焦的那个是块本身，见模板注释） */
function pickAvatar() {
  if (uploading.value) return
  fileInput.value?.click()
}

function onFileChange(e) {
  const file = e.target.files && e.target.files[0]
  // 连续两次选同一个文件不会再触发 change，清空 value 才能再次选中
  e.target.value = ''
  if (file) startUpload(file)
}

function onDrop(e) {
  dragging.value = false
  if (uploading.value) return
  const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]
  if (file) startUpload(file)
}

/** 选好文件立刻上传，拿回 URL 先存进表单；点「保存」时才写进资料 */
async function startUpload(file) {
  uploadError.value = ''
  if (!ALLOWED_TYPES.includes(file.type)) {
    uploadError.value = '只支持 JPG / PNG / WebP / GIF 格式的图片'
    return
  }
  if (file.size > MAX_AVATAR_BYTES) {
    uploadError.value = `图片大小不能超过 5MB（当前 ${(file.size / 1024 / 1024).toFixed(1)}MB）`
    return
  }

  uploading.value = true
  try {
    const res = await uploadImage(file)
    form.value.avatarUrl = res.url
  } catch (err) {
    // 拦截器已经弹过 toast，这里把文案再落回弹层内，用户不用去追右上角
    uploadError.value = (err && err.message) || '上传失败，请重试'
  } finally {
    uploading.value = false
  }
}

/** 撤销本次上传，用回打开弹层时的头像 */
function clearAvatar() {
  form.value.avatarUrl = originalAvatar.value
  uploadError.value = ''
}

async function save() {
  if (!validate()) return
  saving.value = true
  try {
    const payload = { nickname: form.value.nickname.trim() }
    // 留空 = 保留当前头像（后端语义：不提供的字段不改动）
    if (form.value.avatarUrl) payload.avatar_url = form.value.avatarUrl
    await updateMe(payload)
    await store.fetchMe()
    sheetOpen.value = false
    ElMessage.success('已保存')
  } catch (e) {
    // 拦截器已弹过提示，这里只保证流程不中断
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- 身份区：这是一个人，不是一条数据库记录 -->
    <header class="me">
      <AppAvatar :size="76" :src="me.avatar_url" :name="me.nickname" ring />
      <div class="me__text">
        <h1 class="me__name">{{ me.nickname || '球友' }}</h1>
        <!-- 分隔圆点是 aria-hidden 的装饰元素（不承载信息）。
             每段之间留一个换行空格：flex 容器不渲染这个空白，
             但读屏与复制文本时能得到正确的停顿。 -->
        <p class="me__meta">
          <span class="tnum">@{{ me.username }}</span>
          <template v-if="joinedAt">
            <span class="me__sep" aria-hidden="true"></span>
            {{ joinedAt }}
          </template>
          <template v-if="isAdmin">
            <span class="me__sep" aria-hidden="true"></span>
            <span class="me__admin">管理员</span>
          </template>
        </p>
      </div>
      <AppButton variant="outline" size="sm" @click="openEdit">编辑资料</AppButton>
    </header>

    <!-- 三个数字：全部由现有接口算出（发起的用后端 COUNT，其余从列表过滤） -->
    <section class="stats" aria-label="我的数据">
      <template v-if="loading">
        <AppSkeleton v-for="i in 3" :key="i" width="78px" height="46px" />
      </template>
      <template v-else>
        <StatNumber :value="hostedCount" label="我发起的球局" size="lg" />
        <StatNumber :value="statValue(joinedCount, joined.total)" label="我报名的球局" size="lg" />
        <StatNumber :value="statValue(bookedCount, booked.total)" label="我订过的场地" size="lg" />
      </template>
    </section>

    <!-- 接下来的球局：球员最想知道的一件事，也是最好的回访钩子 -->
    <section class="block">
      <div class="block__head">
        <h2 class="block__title">接下来的球局</h2>
        <RouterLink v-if="upcoming.length" class="block__more" to="/activities/mine">
          全部 →
        </RouterLink>
      </div>

      <div v-if="loading" class="panel block__skeleton">
        <AppSkeleton v-for="i in 2" :key="i" height="62px" />
      </div>

      <div v-else-if="upcoming.length" class="panel">
        <p class="block__lead tnum">最近一场 {{ nextGameWhen }}</p>
        <!-- 行间分隔由 ActivityRow 内置（.act-row + .act-row），页面不用管 -->
        <ActivityRow v-for="a in upcoming.slice(0, 4)" :key="a.id" :activity="a" />
      </div>

      <div v-else class="panel">
        <EmptyState
          title="接下来还没有安排"
          description="看看最近有谁开局，或者自己发起一场。"
        >
          <AppButton @click="$router.push('/activities')">去找球局</AppButton>
          <AppButton variant="outline" @click="$router.push('/activities/new')">发起一场</AppButton>
        </EmptyState>
      </div>
    </section>

    <!-- 需要你跟进：只在真有内容时出现，避免占一块空白 -->
    <section v-if="loading || needsAttention.length" class="block">
      <div class="block__head">
        <h2 class="block__title">需要你跟进</h2>
        <RouterLink class="block__more" to="/activities/mine">全部 →</RouterLink>
      </div>

      <div v-if="loading" class="panel block__skeleton">
        <AppSkeleton v-for="i in 2" :key="i" height="62px" />
      </div>

      <div v-else class="panel">
        <p class="block__lead">这几场还没定下来，等审核通过后才会开放报名。</p>
        <ActivityRow v-for="a in needsAttention" :key="a.id" :activity="a" />
      </div>
    </section>

    <!-- 编辑资料收进弹层：它是次要动作，不该占半屏 -->
    <AppSheet v-model="sheetOpen" title="编辑资料" width="480px">
      <div class="form">
        <AppInput
          v-model="form.nickname"
          label="昵称"
          placeholder="其他球友看到的名字"
          :maxlength="64"
          :error="errors.nickname"
          required
        />

        <!-- 头像上传。
             整块是一个 <button> 而不是 <div>：它可点击，就必须能 Tab 到、
             能用回车触发。原生 input[type=file] 藏在外面 —— 各浏览器对它的
             渲染完全无法统一，藏起来由按钮代理点击是通行做法。 -->
        <div class="field">
          <span class="field__label">头像</span>
          <button
            type="button"
            class="uploader"
            :class="{ 'is-dragging': dragging, 'is-busy': uploading }"
            @click="pickAvatar"
            @dragover.prevent="dragging = true"
            @dragleave.prevent="dragging = false"
            @drop.prevent="onDrop"
          >
            <AppAvatar :size="52" :src="form.avatarUrl" :name="form.nickname" />
            <span class="uploader__text">
              <span class="uploader__main">{{ uploaderMain }}</span>
              <span class="uploader__sub">JPG / PNG / WebP / GIF，单张不超过 5MB</span>
            </span>
          </button>
          <input
            ref="fileInput"
            class="uploader__file"
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            @change="onFileChange"
          />

          <!-- 错误提示和「撤销」必须能同时出现：
               已经选了新头像之后又传了个非法文件，如果两者互斥，
               用户就点不到撤销了（第一版用 v-else-if，正是这个毛病）。 -->
          <p v-if="uploadError" class="uploader__error">{{ uploadError }}</p>
          <AppButton
            v-if="form.avatarUrl && form.avatarUrl !== originalAvatar"
            variant="ghost"
            size="sm"
            @click="clearAvatar"
          >
            撤销，用回原来的头像
          </AppButton>
        </div>
      </div>

      <template #footer>
        <AppButton variant="ghost" @click="sheetOpen = false">取消</AppButton>
        <AppButton :loading="saving" @click="save">保存</AppButton>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
/* ---------- 身份区 ---------- */
.me {
  display: flex;
  align-items: center;
  gap: var(--s4);
  padding-bottom: var(--s5);
  border-bottom: 1px solid var(--line);
}

.me__text {
  flex: 1;
  min-width: 0;
}

.me__name {
  font-size: var(--fs-h2);
  font-weight: 700;
  letter-spacing: -0.015em;
  color: var(--ink);
  line-height: 1.2;
}

.me__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-top: var(--s1);
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

/* 分隔用真元素而不是「·」字符：它不承载信息，是纯视觉分隔。
   作为文字时对比度只有 1.42:1，而作为装饰就不受对比度约束。 */
.me__sep {
  display: inline-block;
  width: 3px;
  height: 3px;
  margin: 0 8px;
  border-radius: 50%;
  background: var(--line-2);
}

.me__admin {
  color: var(--warn);
}

/* ---------- 数字区 ---------- */
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--s5);
  padding: var(--s5) 0;
  border-bottom: 1px solid var(--line);
}

/* ---------- 区块 ---------- */
.block {
  margin-top: var(--s6);
}

.block__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--s3);
  margin-bottom: var(--s3);
}

.block__title {
  font-size: var(--fs-h3);
  font-weight: 650;
  color: var(--ink);
}

.block__more {
  font-size: var(--fs-sm);
  color: var(--ink-3);
  text-decoration: none;
  white-space: nowrap;
}

.block__more:hover {
  color: var(--ink);
}

/* 列表上方的一行说明：把"为什么这些排在这里"讲清楚，而不是重复每一行 */
.block__lead {
  padding: var(--s3) var(--s5);
  border-bottom: 1px solid var(--line);
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.block__skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--s3);
  padding: var(--s4);
}

/* ---------- 编辑弹层 ---------- */
.form {
  display: flex;
  flex-direction: column;
  gap: var(--s4);
}

/* ---------- 头像上传 ---------- */
.field {
  display: flex;
  flex-direction: column;
  gap: var(--s2);
  align-items: flex-start;
}

.field__label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-2);
}

.uploader {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--s4);
  padding: var(--s3) var(--s4);
  border: 1px dashed var(--line-2);
  border-radius: var(--r-sm);
  background: var(--surface-2);
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s, background 0.16s;
}

/* hover / 拖拽悬停：用墨色虚线 + 提亮底色，不加阴影不换品牌色
   （荧光描边在近白底上根本看不见，这条踩过） */
.uploader:hover,
.uploader.is-dragging {
  border-color: var(--ink);
  background: var(--surface);
}

.uploader.is-busy {
  cursor: progress;
}

/* 头像在 flex 里会被压扁，固定住 */
.uploader > *:first-child {
  flex-shrink: 0;
}

.uploader__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.uploader__main {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--ink);
}

/* 次要说明用 ink-3（5.0:1）而不是把主文字调透明 ——
   opacity 会等比压低对比度，那是本项目的硬性规定 */
.uploader__sub {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

/* 原生 file input 留在 DOM 里但不占位：它只负责被 .click() 触发 */
.uploader__file {
  display: none;
}

.uploader__error {
  font-size: var(--fs-sm);
  color: var(--danger);
}

@media (max-width: 640px) {
  .me {
    flex-wrap: wrap;
  }

  .me__name {
    font-size: var(--fs-h3);
  }

  .stats {
    gap: var(--s3);
  }
}
</style>
