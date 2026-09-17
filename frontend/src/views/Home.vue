<script setup>
/**
 * Home —— 首页 · 球局广场。
 *
 * <h2>这一版在解决什么</h2>
 * 上一版首页把"这个社区长什么样"讲得很好（Hero + 7 天弧线 + 双栏），
 * 但它回答不了用户打开网站时真正在问的那句：**我今晚能不能打上球。**
 * 三个可验证的毛病：数据是截断的（今天只取 4 条却当全量用）、
 * 球局行缺场地、**整页没有一个「加入」按钮**。
 * 所以这一版把信息架构按"决策链"重排，并把「加入」放进列表行。
 *
 * <h2>结构（P0 只有两个区域）</h2>
 * ```
 * 区域 1 · 顶部状态行   今天还有 N 场球 · 还缺 M 人    9/15 周二 · 16:09
 *                      [发起球局] [全部球局 →]
 * 区域 2 · 球局广场     日期条（7 天，每格带当天场次数，默认今天）
 *                      单日球局列表（时间 · 标题 · 场地 · 人数 · 加入）
 *                      另有 N 场已满员（折叠）
 * ```
 * 「顶部状态行是上下文，列表才是主角」——所以顶部刻意不做成大数字统计区，
 * 也没有 Hero。整页唯一的品牌色实心元素是每行的「加入」按钮。
 *
 * <h2>🔴 数据层的坑（必须按天分查）</h2>
 * 后端 `clampSize()` 把 `page_size` 封顶 50，`listByStatuses` 是
 * `ORDER BY start_time DESC LIMIT`。所以「一次请求取 7 天窗口」拿到的是窗口内
 * **最晚的 50 条** —— 也就是第 7 天那一端，**今天的球局极可能一条都没有**，
 * 而且不报错（表面上"有数据"）。
 * 因此：日期条各天场次数**按天分查 ×7**（`page_size=1`，只取 `total`），
 * 选中日的列表单独查一次（`page_size=50`）。今天这一次请求同时充当日期条第 0 格。
 *
 * <h2>后端约束（不可改）</h2>
 * 公开列表 `ORDER BY start_time DESC` 对"接下来有什么球局"是反的，
 * 所以展示层用 `sortByStartAsc` 重排；后端接口与排序一律不动。
 */
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listActivities,
  registerActivity,
  cancelRegistration,
  myRegistrations
} from '@/api/activity'
import { listVenues, listCourts } from '@/api/venue'
import { useUserStore } from '@/stores/user'
import { AppButton, AppSkeleton, EmptyState, GameRow } from '@/components'
import {
  iso,
  startOf,
  endOf,
  pad,
  parseTime,
  sortByStartAsc,
  dayName,
  WEEK
} from '@/utils/datetime'

/** 一天的球局数量兜底上限（后端 clampSize 封顶 50，写 50 就是取满一页） */
const LIST_SIZE = 50
/** 日期条覆盖 7 天（与后端"可预约 7 天内"的窗口一致） */
const DAYS = 7

const store = useUserStore()
const router = useRouter()

const loading = ref(true)
const dayLoading = ref(false)
const listError = ref(false)

/** 今天的球局（首屏主体；不随日期条切换而改变） */
const todayActs = ref([])
const todayTotal = ref(0)
/** 日期条选中的"非今天"那一天的球局 */
const dayActs = ref([])
const dayTotal = ref(0)

const selected = ref(0)
const showFull = ref(false)
/** 提交中的球局 id：按行显示 loading，而不是整页转圈 */
const actingId = ref(null)
/** 我报名过的活动 id 集合（未登录时为空集） */
const joinedIds = ref(new Set())

const venueName = ref('')
const courtNames = ref({})
const courtCount = ref(0)

/** 每 60 秒走一次：让"已开始/已结束"和顶部时钟自己跟上时间，而不只是加载时算一次 */
const now = ref(new Date())

/** 日期条：[{offset, from, to, label, dateLabel, count}]。count 为 null = 没查到（显示「—」，不显示 0） */
function buildDays() {
  return Array.from({ length: DAYS }, (_, offset) => {
    const from = startOf(offset)
    const to = endOf(offset)
    return {
      offset,
      from: iso(from),
      to: iso(to),
      label: dayName(from),
      dateLabel: `${from.getMonth() + 1}/${from.getDate()} ${WEEK[from.getDay()]}`,
      count: null
    }
  })
}
const days = ref(buildDays())

const stripRef = ref(null)

// ---------- 派生：名单与状态 ----------

const currentList = computed(() => (selected.value === 0 ? todayActs.value : dayActs.value))
const currentTotal = computed(() => (selected.value === 0 ? todayTotal.value : dayTotal.value))

/** 打完了没有：比结束时间，不是开始时间（进行中的球局不算结束） */
function isOver(a) {
  const end = parseTime(a.end_time) || parseTime(a.start_time)
  return !!end && end.date.getTime() < now.value.getTime()
}

function isFull(a) {
  return (a.joined_count ?? 0) >= (a.max_members ?? 0)
}

function remainingOf(a) {
  return Math.max(0, (a.max_members ?? 0) - (a.joined_count ?? 0))
}

function startsInFuture(a) {
  const s = parseTime(a.start_time)
  return !!s && s.date.getTime() > now.value.getTime()
}

/**
 * 加入状态机（本页唯一的规则来源）。
 *
 * <p>顺序是有意的：先判"打完了"，再判"我在不在里面"，最后才判满员。
 * 理由是每种状态对应的**事实**不同 —— 已结束的球局即使显示"我已加入"也没用
 * （后端已禁止取消报名），而满员只是"别人占了位"，我仍可能已经在里面。</p>
 *
 * <p>比方案多了一格 `started`（已开始）。方案给的 5 态里，"已开始但还没结束、
 * 还有名额"的球局会落进"可加入"，而后端对已开始的球局必然拒绝报名
 * （`REGISTRATION_WINDOW_CLOSED`）—— 那等于渲染一个点了必定报错的按钮。
 * 宁可多一个状态串，也不给用户一个假的行动入口。</p>
 */
function stateOf(a) {
  if (isOver(a)) return 'over'
  if (joinedIds.value.has(a.id)) return 'joined'
  if (isFull(a)) return 'full'
  if (!startsInFuture(a)) return 'started'
  return 'join'
}

/**
 * 分组：能行动的在前，满员的折叠，不可加入的（已开始/已结束）垫底。
 *
 * <p><b>组内还要再分一层</b>：`open` 里同时有「可加入」和「我已加入」两种状态，
 * 而这一页的第一任务永远是 <b>T2 从能加入的里挑一场</b>（§J.2「可加入的在前」）。
 * 如果只按时间排，一个已经报了 3 场球的人，打开首页看到的前 3 行全是
 * 「已加入」——这一页最重要的那一行被挤到第 4 行去了。
 * 所以 `join` 排在 `joined` 前面，同状态内仍按时间升序（§J.2 的可加入优先）。</p>
 */
const groups = computed(() => {
  const open = []
  const full = []
  const closed = []
  for (const a of currentList.value) {
    const s = stateOf(a)
    if (s === 'full') full.push(a)
    else if (s === 'over' || s === 'started') closed.push(a)
    else open.push(a)
  }
  // sort 在现代引擎里是稳定的：同权重项保持 currentList 的时间升序
  open.sort((a, b) => (stateOf(a) === 'join' ? 0 : 1) - (stateOf(b) === 'join' ? 0 : 1))
  return { open, full, closed }
})

/** 顶部状态行：只统计"今天还能加入"的部分，所以计数永远是真能行动的数字 */
const todayStats = computed(() => {
  let notEnded = 0
  let joinable = 0
  let seats = 0
  for (const a of todayActs.value) {
    if (isOver(a)) continue
    notEnded += 1
    if (!isFull(a) && startsInFuture(a)) {
      joinable += 1
      seats += remainingOf(a)
    }
  }
  return { notEnded, joinable, seats }
})

const head = computed(() => {
  const s = todayStats.value
  if (s.joinable > 0) {
    return { countText: `今天还有 ${s.joinable} 场球`, needText: `还缺 ${s.seats} 人`, subText: '' }
  }
  if (s.notEnded > 0) {
    return {
      countText: '今天没有还能加入的球局',
      needText: '',
      subText: '换个日期看看，或者自己开一局把场占下来。'
    }
  }
  if (todayTotal.value > 0) {
    return { countText: '今天的球都打完了', needText: '', subText: '看看明天有没有，或者现在就开一局。' }
  }
  return {
    countText: '今天还没有人开球',
    needText: '',
    // 这句话成本为零，堵的是一个真实缺陷：发起后活动是「待审核」状态，
    // 用户回首页看不到自己的球局，会以为提交失败
    subText: '发起后需管理员审核，通过后才会出现在这里。场地提交即锁定。'
  }
})

const empty = computed(() => {
  if (selected.value !== 0) {
    return {
      title: `${days.value[selected.value].label}还没有球局`,
      desc: '换个日期看看，或者自己开一局 —— 场地会被立即锁定。'
    }
  }
  if (todayTotal.value > 0) {
    if (todayStats.value.notEnded > 0) {
      return { title: '今天没有还能加入的球局', desc: '换个日期看看，或者自己开一局把场占下来。' }
    }
    return { title: '今天的球都打完了', desc: '看看明天有没有，或者现在就开一局。' }
  }
  return {
    title: '今天还没有人开球',
    desc: '发起后需管理员审核，通过后才会出现在这里。场地提交即锁定。'
  }
})

const dateLabel = computed(() => {
  const d = now.value
  return `${d.getMonth() + 1}/${d.getDate()} ${WEEK[d.getDay()]}`
})
const clock = computed(() => `${pad(now.value.getHours())}:${pad(now.value.getMinutes())}`)

/** 已满员那一行的摘要：「19:00 高手局、18:00 双打」（超过 2 场加"等"） */
const fullSummary = computed(() => {
  const parts = groups.value.full.map((a) => {
    const p = parseTime(a.start_time)
    return p ? `${pad(p.h)}:${pad(p.m)} ${a.title}` : a.title
  })
  if (parts.length <= 2) return parts.join('、')
  return `${parts.slice(0, 2).join('、')} 等`
})

/**
 * court_ids → 场地名。
 *
 * <p>三段守卫，缺一不可：`court_ids` 存的是 **court 表的 id 而不是场号**，
 * 而当前种子数据恰好 id == 场号，所以直接把 id 渲染成"N 号场"会在这个数据集里
 * 侥幸对、换个数据集就错。因此：①没有 id 就不显示；②场地表还没加载出来就先不显示
 * （宁可不显示，也不显示一个可能是错的场号）；③真的对不上才说"场地待定"。</p>
 */
function placeOf(a) {
  const ids = a.court_ids || []
  if (!ids.length) return ''
  if (!courtCount.value) return ''
  return ids.map((id) => courtNames.value[id] || '场地待定').join('、')
}

// ---------- 数据 ----------

/** 把某一天的查询结果写回对应的状态（total 传 null 表示"没查到"，日期条会显示「—」） */
function applyDay(offset, rows, total) {
  days.value[offset].count = total
  if (offset === 0) {
    todayActs.value = rows
    todayTotal.value = total ?? 0
  } else {
    dayActs.value = rows
    dayTotal.value = total ?? 0
  }
}

async function load() {
  loading.value = true
  listError.value = false
  days.value = buildDays()

  // 一天一个请求：第 0 天要列表（page_size=50），其余 6 天只要总条数（page_size=1）。
  // 这样做而不是"一次查 7 天"，理由见文件头的坑。
  const dayTasks = days.value.map((d, i) =>
    listActivities({
      status: 1,
      start_from: d.from,
      start_to: d.to,
      page: 1,
      page_size: i === 0 ? LIST_SIZE : 1
    })
  )
  const tasks = [
    ...dayTasks,
    listVenues(),
    store.token ? myRegistrations({ status: 1, page: 1, page_size: 50 }) : Promise.resolve(null)
  ]
  const VENUE_INDEX = DAYS
  const MINE_INDEX = DAYS + 1

  try {
    // allSettled 而不是 all：某一格失败不该让整页空白
    const res = await Promise.allSettled(tasks)

    // ① 今天的球局（首屏主体）
    const todayRes = res[0]
    if (todayRes.status === 'fulfilled') {
      const rows = sortByStartAsc(todayRes.value.list || [])
      applyDay(0, rows, todayRes.value.total ?? rows.length)
    } else {
      applyDay(0, [], null)
      listError.value = true
    }

    // ② 日期条其余 6 天：只取 total。失败写 null（显示「—」）而不是 0 ——
    //    "这天 0 场"和"没查到"是两件事，把失败说成 0 就是编数据
    for (let i = 1; i < DAYS; i++) {
      const r = res[i]
      days.value[i].count = r.status === 'fulfilled' ? (r.value.total ?? 0) : null
    }

    // ③ 场地名（把 court_ids 显示成"2 号场"）
    const venuesRes = res[VENUE_INDEX]
    if (venuesRes.status === 'fulfilled' && venuesRes.value?.length) {
      venueName.value = venuesRes.value[0].name || ''
      try {
        const courts = await listCourts(venuesRes.value[0].id, { status: 1 })
        const map = {}
        for (const c of courts || []) map[c.id] = c.name
        courtNames.value = map
        courtCount.value = (courts || []).length
      } catch (e) {
        /* 拿不到场地名：placeOf 返回空串，行内不显示场地，而不是把 id 当场号 */
      }
    }

    // ④ 我报名过的活动 → 决定行内是「加入」还是「已加入 退出」
    const mineRes = res[MINE_INDEX]
    if (mineRes.status === 'fulfilled' && mineRes.value) {
      const ids = new Set()
      for (const row of mineRes.value.list || []) {
        if (row.activity?.id != null) ids.add(row.activity.id)
      }
      joinedIds.value = ids
    }
  } finally {
    loading.value = false
  }
}

/** 单独查一天（切换日期条时用） */
async function fetchDay(offset) {
  dayLoading.value = true
  const d = days.value[offset]
  try {
    const r = await listActivities({
      status: 1,
      start_from: d.from,
      start_to: d.to,
      page: 1,
      page_size: LIST_SIZE
    })
    const rows = sortByStartAsc(r.list || [])
    applyDay(offset, rows, r.total ?? rows.length)
    listError.value = false
  } catch (e) {
    applyDay(offset, [], null)
    listError.value = true
  } finally {
    dayLoading.value = false
  }
}

async function selectDay(offset) {
  if (offset === selected.value) return
  selected.value = offset
  showFull.value = false

  if (offset === 0 && !listError.value) {
    // 今天的列表首屏已经拉过且是完整的（page_size=50），不必再查一次
  } else {
    await fetchDay(offset)
  }
  scrollSelectedIntoView(offset)
}

/** 横向滚动时把选中格带回视野（只滚一次，且用 nearest 避免整页跳动） */
function scrollSelectedIntoView(offset) {
  nextTick(() => {
    const el = stripRef.value?.children?.[offset]
    if (el) el.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  })
}

/** 加入失败后用它把本地计数纠正回服务端的真相（比如名额刚好被别人抢走） */
async function refreshCurrentDay() {
  try {
    if (selected.value === 0) {
      const d = days.value[0]
      const r = await listActivities({
        status: 1,
        start_from: d.from,
        start_to: d.to,
        page: 1,
        page_size: LIST_SIZE
      })
      const rows = sortByStartAsc(r.list || [])
      applyDay(0, rows, r.total ?? rows.length)
    } else {
      await fetchDay(selected.value)
    }
  } catch (e) {
    /* 纠偏也失败就维持现状：拦截器已经提示过用户了 */
  }
}

// ---------- 动作 ----------

async function onJoin(a) {
  // 未登录：先引导登录，登录后回首页（Login.vue 支持 redirect 参数）
  if (!store.token) {
    ElMessage.info('登录后就能加入这场球局')
    router.push({ path: '/login', query: { redirect: '/' } })
    return
  }
  if (actingId.value) return

  actingId.value = a.id
  try {
    await registerActivity(a.id)
    // 本地 +1，不重拉整个列表：一次报名不该让整页闪一下
    a.joined_count = (a.joined_count ?? 0) + 1
    joinedIds.value = new Set([...joinedIds.value, a.id])
    ElMessage.success('已加入，记得准时到场')
  } catch (e) {
    // 失败原因已由拦截器提示（满员/活动已开始…）。
    // 这里补一次刷新，把本地显示纠正回服务端真相 —— 宁可闪一下，不要显示假名额。
    await refreshCurrentDay()
  } finally {
    actingId.value = null
  }
}

async function onQuit(a) {
  try {
    await ElMessageBox.confirm('退出后名额会释放给其他球友，可以随时再加入。', '退出这场球局', {
      type: 'warning',
      confirmButtonText: '退出'
    })
  } catch (e) {
    // 用户点了取消：ElMessageBox 用 reject 表达取消，不是异常，直接返回。
    // （不接住它就会变成一条未处理的 Promise 拒绝）
    return
  }

  if (actingId.value) return
  actingId.value = a.id
  try {
    await cancelRegistration(a.id)
    a.joined_count = Math.max(0, (a.joined_count ?? 0) - 1)
    const next = new Set(joinedIds.value)
    next.delete(a.id)
    joinedIds.value = next
    ElMessage.success('已退出')
  } catch (e) {
    await refreshCurrentDay()
  } finally {
    actingId.value = null
  }
}

let timer = null

onMounted(() => {
  load()
  timer = setInterval(() => {
    now.value = new Date()
  }, 60000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="home">
    <div class="home__inner">
      <!-- ================= 区域 1 · 顶部状态行 =================
           它是上下文，不是主角：一行状态 + 一行日期时间 + 两个次要动作。
           刻意不做大数字统计区，也刻意不做 Hero —— 页面唯一的品牌色实心元素
           应该是列表行里的「加入」。 ================= -->
      <header class="hd">
        <div class="hd__row">
          <p v-if="loading" class="hd__status">
            <AppSkeleton height="20px" width="188px" />
          </p>
          <p v-else class="hd__status">
            <span class="tnum">{{ head.countText }}</span>
            <template v-if="head.needText">
              <i class="hd__dot" aria-hidden="true"></i>
              <span class="tnum">{{ head.needText }}</span>
            </template>
          </p>

          <p class="hd__when tnum">
            <span>{{ dateLabel }}</span>
            <i class="hd__dot" aria-hidden="true"></i>
            <span>{{ clock }}</span>
          </p>
        </div>

        <p v-if="head.subText" class="hd__sub">{{ head.subText }}</p>

        <div class="hd__acts">
          <AppButton variant="outline" size="sm" to="/activities/new">发起球局</AppButton>
          <RouterLink class="more" to="/activities">
            全部球局
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M9 6l6 6-6 6" />
            </svg>
          </RouterLink>
        </div>
      </header>

      <!-- ================= 区域 2 · 球局广场（页面主体） ================= -->
      <section class="games" aria-label="球局">
        <!-- 日期条：每格带当天场次数。这一格同时承担了上一版"7 天弧线"想表达的
             "哪天有球、哪天多"，但只占一行高度，而且可以直接点。 -->
        <div ref="stripRef" class="strip" role="group" aria-label="选择日期">
          <button
            v-for="d in days"
            :key="d.offset"
            type="button"
            class="strip__cell"
            :class="{ 'is-on': selected === d.offset }"
            :aria-pressed="selected === d.offset"
            :title="d.dateLabel"
            @click="selectDay(d.offset)"
          >
            <span class="strip__label">{{ d.label }}</span>
            <span class="strip__count tnum">
              {{ d.count === null ? '—' : `${d.count} 场` }}
            </span>
          </button>
        </div>

        <div class="games__body" :aria-busy="loading || dayLoading">
          <!-- 骨架：形状与真实行一致，避免"先空后跳" -->
          <template v-if="loading || dayLoading">
            <div v-for="i in 3" :key="i" class="game-skel">
              <AppSkeleton height="22px" width="52px" />
              <div class="game-skel__mid">
                <AppSkeleton height="17px" width="46%" />
                <AppSkeleton height="13px" width="34%" style="margin-top: 6px" />
              </div>
              <AppSkeleton height="32px" width="60px" radius="var(--r-sm)" />
            </div>
          </template>

          <!-- 加载失败必须和"这天没有球局"分开说：把故障说成事实是假信息 -->
          <EmptyState
            v-else-if="listError"
            title="没能加载球局"
            description="网络或服务可能有问题，稍后再试一次。"
          >
            <AppButton variant="outline" @click="load">重新加载</AppButton>
          </EmptyState>

          <EmptyState v-else-if="!currentList.length" :title="empty.title" :description="empty.desc">
            <AppButton to="/activities/new">发起球局</AppButton>
            <AppButton
              v-if="selected < days.length - 1"
              variant="outline"
              @click="selectDay(selected + 1)"
            >
              看看{{ days[selected + 1].label }}
            </AppButton>
            <AppButton v-else variant="outline" @click="selectDay(0)">回到今天</AppButton>
          </EmptyState>

          <div v-else class="games__list">
            <!-- 能行动的：加入 / 已加入可退出 -->
            <GameRow
              v-for="a in groups.open"
              :key="a.id"
              :activity="a"
              :place="placeOf(a)"
              :state="stateOf(a)"
              :busy="actingId === a.id"
              @join="onJoin"
              @quit="onQuit"
            />

            <!-- 已满员：保留（它说明社区活跃）但不和可加入的抢注意力，收成一行 -->
            <template v-if="groups.full.length">
              <button
                type="button"
                class="fold"
                :aria-expanded="showFull"
                @click="showFull = !showFull"
              >
                <span class="fold__text">另有 {{ groups.full.length }} 场已满员（{{ fullSummary }}）</span>
                <svg class="fold__chev" :class="{ 'is-open': showFull }" width="13" height="13"
                     viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
                     stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M6 9l6 6 6-6" />
                </svg>
              </button>

              <template v-if="showFull">
                <GameRow
                  v-for="a in groups.full"
                  :key="a.id"
                  :activity="a"
                  :place="placeOf(a)"
                  :state="stateOf(a)"
                  :busy="false"
                  @join="onJoin"
                  @quit="onQuit"
                />
              </template>
            </template>

            <!-- 已开始 / 已结束：垫底并整行降级。它们的数量只说明"今天热闹过" -->
            <GameRow
              v-for="a in groups.closed"
              :key="a.id"
              :activity="a"
              :place="placeOf(a)"
              :state="stateOf(a)"
              :busy="false"
              @join="onJoin"
              @quit="onQuit"
            />
          </div>

          <!-- 一页取满 50 条时如实说明，而不是假装这就是全部 -->
          <p v-if="!loading && !dayLoading && !listError && currentList.length < currentTotal" class="games__more">
            还有 {{ currentTotal - currentList.length }} 场没显示 ——
            <RouterLink to="/activities">去全部球局</RouterLink>
          </p>
        </div>
      </section>

      <!-- 页脚：规则是产品的一部分，比一个装饰性页脚有用 -->
      <footer class="foot">
        <p class="foot__line">
          <span v-if="venueName">{{ venueName }}</span>
          <i v-if="venueName" class="hd__dot" aria-hidden="true"></i>
          <span>可预约 7 天内的整点时段</span>
        </p>
        <p class="foot__line">个人预约提前 4 小时可免费取消 · 同时最多 2 个重叠预约</p>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.home__inner {
  max-width: 1120px;
  margin: 0 auto;
  /* 首屏是最贵的资产：顶部留白收窄，留白用在区块之间 */
  padding: var(--s6) var(--s5) var(--s8);
}

/* ---- 分隔圆点：不用「·」字符（小字号下几乎看不见，且会被读屏念出来） ---- */
.hd__dot {
  flex-shrink: 0;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--line-2);
}

/* ================= 区域 1 · 顶部状态行 ================= */
.hd {
  margin-bottom: var(--s6);
}

.hd__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--s3) var(--s5);
  flex-wrap: wrap;
}

.hd__status {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--s2);
  margin: 0;
  font-size: var(--fs-h3);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ink);
}

.hd__when {
  display: inline-flex;
  align-items: center;
  gap: var(--s2);
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.hd__sub {
  margin: var(--s2) 0 0;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.hd__acts {
  display: flex;
  align-items: center;
  gap: var(--s4);
  margin-top: var(--s4);
}

.more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: var(--fs-sm);
  color: var(--ink-3);
  transition: color 0.14s;
}

.more:hover {
  color: var(--ink);
}

/* ================= 区域 2 · 日期条 ================= */
.strip {
  display: flex;
  gap: var(--s2);
  margin-bottom: var(--s5);
  /* 手机上 7 格放不下 → 横向滚动。列表页那些"自动滚到首个可用项"的经验
     在这里同样适用（见 selectDay 里的 scrollSelectedIntoView）。 */
  overflow-x: auto;
  scroll-snap-type: x proximity;
  padding-bottom: 2px;
}

.strip__cell {
  flex: 1 1 0;
  min-width: 78px;
  scroll-snap-align: start;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  padding: var(--s2) var(--s1) 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.14s, background 0.14s, color 0.14s;
}

.strip__cell:hover {
  border-color: var(--line-2);
}

/* 选中态：品牌色填充 + 墨色文字（对比度 14.6:1）。
   荧光色只做填充，绝不当文字色 —— 这是全站唯一的荧光用法。 */
.strip__cell.is-on {
  background: var(--brand);
  border-color: var(--brand);
  color: var(--brand-ink);
}

.strip__label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink);
}

.strip__count {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.strip__cell.is-on .strip__label,
.strip__cell.is-on .strip__count {
  color: var(--brand-ink);
}

/* ================= 区域 2 · 球局列表 ================= */
.game-skel {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  gap: var(--s4);
  align-items: center;
  padding: var(--s3);
}

.game-skel__mid {
  min-width: 0;
}

/* 行间分隔统一由容器给（行、折叠行、垫底行一视同仁），
   GameRow 自己不画线 —— 它有可能被夹在别的元素中间 */
.games__list > * + * {
  border-top: 1px solid var(--line);
}

/* 已满员折叠行：一行灰字，可展开。满员信息要保留（说明社区活跃），
   但不能和可加入的球局抢注意力。 */
.fold {
  display: flex;
  align-items: center;
  gap: var(--s2);
  width: 100%;
  padding: var(--s3);
  border: 0;
  border-radius: var(--r-sm);
  background: none;
  font-family: inherit;
  font-size: var(--fs-sm);
  color: var(--ink-3);
  text-align: left;
  cursor: pointer;
  transition: background 0.14s, color 0.14s;
}

.fold:hover {
  background: var(--surface);
  color: var(--ink);
}

.fold__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fold__chev {
  flex-shrink: 0;
  transition: transform 0.14s;
}

.fold__chev.is-open {
  transform: rotate(180deg);
}

.games__more {
  margin: var(--s5) 0 0;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.games__more a {
  color: var(--ink);
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* ================= 页脚 ================= */
.foot {
  margin-top: var(--s8);
  padding-top: var(--s5);
  border-top: 1px solid var(--line);
}

.foot__line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--s2);
  margin: 0;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.foot__line + .foot__line {
  margin-top: var(--s1);
}

/* ================= 移动端 ================= */
@media (max-width: 640px) {
  .home__inner {
    padding: var(--s5) var(--s4) var(--s7);
  }

  .hd__row {
    flex-direction: column;
    gap: var(--s1);
  }

  .hd__status {
    font-size: var(--fs-lead);
  }

  .hd__acts :deep(.app-btn) {
    flex: 1;
  }

  .hd__acts {
    gap: var(--s3);
  }

  /* 手机上让"全部球局"占满剩余宽度，两个动作高度一致 */
  .more {
    padding: var(--s2) var(--s3);
    border: 1px solid var(--line-2);
    border-radius: var(--r-sm);
  }

  .game-skel {
    grid-template-columns: 52px minmax(0, 1fr) auto;
    gap: var(--s3);
    padding: var(--s3) var(--s2);
  }
}
</style>
