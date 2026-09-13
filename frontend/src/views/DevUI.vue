<script setup>
/**
 * DevUI —— 组件与令牌的检阅页（仅开发环境）
 *
 * 存在的理由：设计审查必须**看着真东西**做，不能靠读代码自评。
 * 所有组件在一页里同屏，间距/字号/颜色的偏差会立刻暴露出来。
 *
 * 路由只在 import.meta.env.DEV 时注册，生产构建里不存在这一页。
 */
import { ref } from 'vue'
import {
  AppButton,
  AppInput,
  AppBadge,
  AppAvatar,
  AppAvatarStack,
  StatNumber,
  EmptyState,
  AppSkeleton,
  AppSheet,
  RallyArc,
  ActivityRow
} from '@/components'

const sheetOpen = ref(false)
const demoText = ref('周五晚双打')
const demoError = ref('')

const PLAYERS = [
  { id: 1, nickname: '小林' },
  { id: 2, nickname: '阿杰' },
  { id: 3, nickname: '老张' },
  { id: 4, nickname: 'Wing' },
  { id: 5, nickname: '陈哥' },
  { id: 6, nickname: '小美' }
]

// ActivityRow 的三种状态。字段与后端 ActivityItemVO 一致（去掉 id 相关无用项）。
const SAMPLE_ACTS = [
  {
    id: 901,
    title: '周五晚双打约战，新手友好',
    creator_nickname: '小林',
    start_time: '2026-09-18T19:00:00',
    end_time: '2026-09-18T21:00:00',
    max_members: 8,
    joined_count: 6,
    status: 1
  },
  {
    id: 902,
    title: '就缺两个人，速来',
    creator_nickname: '阿杰',
    start_time: '2026-09-19T10:00:00',
    end_time: '2026-09-19T12:00:00',
    max_members: 8,
    joined_count: 7,
    status: 1
  },
  {
    id: 903,
    title: '已经满了的这一场',
    creator_nickname: '老张',
    start_time: '2026-09-20T14:00:00',
    end_time: '2026-09-20T16:00:00',
    max_members: 4,
    joined_count: 4,
    status: 1
  }
]

const PALETTE = [
  ['--bg', '#f7f8f7', '页面底'],
  ['--surface', '#ffffff', '面'],
  ['--surface-2', '#f1f3f2', '次级面'],
  ['--ink', '#101311', '主文字 17.6:1'],
  ['--ink-2', '#4b514d', '次级 7.6:1'],
  ['--ink-3', '#666d67', '元信息 5.0:1'],
  ['--line', '#e8eae9', '发丝描边'],
  ['--line-2', '#d6d9d7', '强调描边'],
  ['--brand', '#c6f432', '品牌（仅填充）'],
  ['--brand-600', '#5f7a10', '品牌文字版 4.9:1'],
  ['--brand-200', '#e2f4a8', '品牌浅底'],
  ['--danger', '#c0392b', '危险'],
  ['--warn', '#9a6b12', '警告'],
  ['--ok', '#2f6b2a', '正常']
]
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">Design System</h1>
        <p class="page-sub">令牌与组件的检阅页 · 仅开发环境</p>
      </div>
      <AppBadge kind="brand">dev only</AppBadge>
    </div>

    <!-- ============ 排版 ============ -->
    <section class="blk">
      <h2 class="blk__head">排版层级</h2>
      <div class="spec">
        <span class="spec__tag">display · clamp(38–68) · 700 · -0.025em</span>
        <p class="display">FIND YOUR GAME.</p>
      </div>
      <div class="spec">
        <span class="spec__tag">h1 · 38px · 700</span>
        <p class="h1">今天开打的球局</p>
      </div>
      <div class="spec">
        <span class="spec__tag">h2 · 26px · 600</span>
        <p class="h2">本周热门</p>
      </div>
      <div class="spec">
        <span class="spec__tag">h3 · 19px · 600</span>
        <p class="h3">还差 2 人</p>
      </div>
      <div class="spec">
        <span class="spec__tag">lead · 17px</span>
        <p class="lead">找到下一场属于你的球局，认识常来这个球馆的人。</p>
      </div>
      <div class="spec">
        <span class="spec__tag">body · 15px</span>
        <p>周五晚双打约战，新手友好，自带球拍。打完一起复盘。</p>
      </div>
      <div class="spec">
        <span class="spec__tag">meta · 13px · ink-3</span>
        <p class="meta">周四 17:00–19:00 · 小林发起 · 进球馆需换鞋</p>
      </div>
      <div class="spec">
        <span class="spec__tag">micro · 12px · ink-3</span>
        <p class="micro">最后更新 3 分钟前</p>
      </div>
    </section>

    <!-- ============ 色板 ============ -->
    <section class="blk">
      <h2 class="blk__head">色板</h2>
      <div class="swatches">
        <div v-for="[name, hex, note] in PALETTE" :key="name" class="swatch">
          <div class="swatch__chip" :style="{ background: hex }"></div>
          <div class="swatch__name">{{ name }}</div>
          <div class="swatch__note">{{ note }}</div>
        </div>
      </div>
    </section>

    <!-- ============ 按钮 ============ -->
    <section class="blk">
      <h2 class="blk__head">按钮</h2>

      <div class="row">
        <AppButton variant="primary">加入球局</AppButton>
        <AppButton variant="outline">创建球局</AppButton>
        <AppButton variant="ghost">稍后再说</AppButton>
        <AppButton variant="danger">取消预约</AppButton>
      </div>

      <div class="row">
        <AppButton size="sm" variant="primary">小号</AppButton>
        <AppButton size="md" variant="primary">中号</AppButton>
        <AppButton size="lg" variant="primary">大号 Hero CTA</AppButton>
      </div>

      <div class="row">
        <AppButton variant="primary" loading>提交中</AppButton>
        <AppButton variant="primary" disabled>不可用</AppButton>
        <AppButton variant="outline" disabled>不可用</AppButton>
      </div>

      <p class="micro">primary 一屏内只允许出现一个；其余动作降级为 outline / ghost / danger。</p>
    </section>

    <!-- ============ 输入 ============ -->
    <section class="blk">
      <h2 class="blk__head">输入</h2>
      <div class="grid2">
        <AppInput v-model="demoText" label="球局名称" placeholder="例如：周五晚双打约战" hint="最多 100 字" />
        <AppInput v-model="demoError" label="人数上限" placeholder="例如：8" error="不能小于 1" />
        <AppInput model-value="" label="只读样式" placeholder="禁用态" disabled />
        <AppInput model-value="" label="必填" required placeholder="带必填标记" />
      </div>
    </section>

    <!-- ============ 徽标 ============ -->
    <section class="blk">
      <h2 class="blk__head">状态徽标</h2>
      <div class="row">
        <AppBadge kind="open" />
        <AppBadge kind="full" />
        <AppBadge kind="pending" />
        <AppBadge kind="done" />
        <AppBadge kind="cancelled" />
        <AppBadge kind="brand">热门</AppBadge>
        <AppBadge kind="neutral" plain>距我 2 公里</AppBadge>
      </div>
      <p class="micro">每个状态除了颜色还有一个圆点 —— 颜色不能是唯一的信息载体。</p>
    </section>

    <!-- ============ 头像 ============ -->
    <section class="blk">
      <h2 class="blk__head">头像</h2>
      <div class="row row--center">
        <AppAvatar :size="52" name="小林" />
        <AppAvatar :size="40" name="Wing" />
        <AppAvatar :size="28" name="阿杰" />
        <AppAvatar :size="28" name="" />
        <span class="divider-v"></span>
        <AppAvatarStack :users="PLAYERS" :max="4" :size="30" />
      </div>
    </section>

    <!-- ============ 大数字 ============ -->
    <section class="blk">
      <h2 class="blk__head">大数字</h2>
      <div class="row row--top">
        <StatNumber value="2" unit="人" label="还差" label-position="before" size="lg" tone="brand" />
        <StatNumber value="2" unit="人" label="还差" size="lg" tone="brand" />
        <StatNumber value="6" unit="/ 8" label="已报名" size="lg" />
        <StatNumber value="12" unit="场" label="我打过" size="md" />
        <StatNumber value="0" label="本周暂无" size="md" tone="quiet" />
      </div>
      <p class="micro">
        brand 强调用「荧光笔划线」而不是品牌色文字 —— 荧光色当文字对比度不足 3:1。
        第一、二项对比 <code>label-position</code> 的 before / below：
        中文「还差 2 人」是固定语序，标签放下一行会读成「2 人 / 还差」。
      </p>
    </section>

    <!-- ============ 球局行 ============ -->
    <section class="blk">
      <h2 class="blk__head">球局行（首页与列表共用）</h2>
      <div class="panel">
        <ActivityRow v-for="a in SAMPLE_ACTS" :key="a.id" :activity="a" />
      </div>
      <p class="micro" style="margin-top: 10px">
        信息权重：时间 &gt; 剩余名额 &gt; 标题 &gt; 发起人。
        只剩 1–2 个位置时名额用品牌色划线（"手慢无"的唯一提示）。
        行内不放「加入」按钮 —— 加入动作归详情页所有。
      </p>
    </section>

    <!-- ============ 轨迹弧 ============ -->
    <section class="blk">
      <h2 class="blk__head">轨迹弧（品牌母题）</h2>
      <div class="arcbox">
        <RallyArc tone="brand" />
        <RallyArc tone="line" />
        <RallyArc tone="ink" dashed />
      </div>
    </section>

    <!-- ============ 骨架 ============ -->
    <section class="blk">
      <h2 class="blk__head">骨架屏</h2>
      <div class="row row--center">
        <AppSkeleton height="44px" width="44px" circle />
        <div style="flex: 1; max-width: 320px">
          <AppSkeleton height="15px" width="60%" />
          <div style="height: 8px"></div>
          <AppSkeleton height="13px" width="85%" />
        </div>
      </div>
    </section>

    <!-- ============ 弹层 ============ -->
    <section class="blk">
      <h2 class="blk__head">弹层</h2>
      <AppButton variant="outline" @click="sheetOpen = true">打开弹层</AppButton>
      <p class="micro" style="margin-top: 8px">
        桌面居中、移动端从底部升起。Esc 可关、焦点锁在面板内、关闭后焦点回到触发按钮。
      </p>
      <AppSheet v-model="sheetOpen" title="发起球局">
        <p class="lead">场地会在提交后立即锁定，审核通过后开放报名。</p>
        <div style="height: 16px"></div>
        <AppInput model-value="" label="球局名称" placeholder="例如：周五晚双打约战" />
        <template #footer>
          <AppButton variant="ghost" @click="sheetOpen = false">取消</AppButton>
          <AppButton variant="primary" @click="sheetOpen = false">提交审核</AppButton>
        </template>
      </AppSheet>
    </section>

    <!-- ============ 空状态 ============ -->
    <section class="blk">
      <h2 class="blk__head">空状态</h2>
      <div class="panel">
        <EmptyState
          title="这几天还没有人开球局"
          description="要不你来发起第一场？场地锁定后就能开放报名了。"
        >
          <AppButton variant="primary">发起球局</AppButton>
          <AppButton variant="ghost">看看场地</AppButton>
        </EmptyState>
      </div>
    </section>
  </div>
</template>

<style scoped>
.blk {
  padding: var(--s6) 0;
  border-top: 1px solid var(--line);
}

.blk:first-of-type {
  border-top: 0;
  padding-top: var(--s4);
}

.blk__head {
  margin-bottom: var(--s5);
  font-size: var(--fs-sm);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--ink-3);
}

.spec {
  margin-bottom: var(--s5);
}

.spec__tag {
  display: block;
  margin-bottom: var(--s2);
  font-family: var(--font-mono, monospace);
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.row {
  display: flex;
  align-items: flex-end;
  gap: var(--s3);
  flex-wrap: wrap;
  margin-bottom: var(--s4);
}

.row--center {
  align-items: center;
}

.row--top {
  align-items: flex-start;
}

.divider-v {
  width: 1px;
  height: 28px;
  background: var(--line);
}

.grid2 {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--s4);
}

.swatches {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--s3);
}

.swatch__chip {
  height: 48px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
}

.swatch__name {
  margin-top: 6px;
  font-size: var(--fs-xs);
  font-weight: 600;
  color: var(--ink);
}

.swatch__note {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.arcbox {
  display: grid;
  gap: var(--s4);
  padding: var(--s4);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
}

/* 球局行之间的发丝线（组件自带 scoped 样式，这里用 :deep 穿透） */
.blk :deep(.act-row + .act-row) {
  border-top: 1px solid var(--line);
}
</style>
