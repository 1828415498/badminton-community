/**
 * P0 首页验收（前端）。
 *
 * <h2>用法</h2>
 * ```
 * # 前置：后端 8080、前端 5173 都在跑
 * TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
 *   -H 'Content-Type: application/json' \
 *   -d '{"username":"admin123","password":"123456"}' | python -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
 *
 * node tools/p0-home.mjs "$TOKEN" clean    # 真实数据下验结构 + 出图
 * python tools/p0-fixtures.py setup        # 造三态测试数据
 * node tools/p0-home.mjs "$TOKEN" states   # 状态机 + 加入/退出
 * python tools/p0-fixtures.py teardown     # 清理（必须跑，否则库里留着「【验收·xxx】」）
 * ```
 *
 * <h2>为什么令牌要走命令行</h2>
 * 原来从临时文件读（`%TEMP%/tk.txt`），那是上一轮脚本互相传参的土办法 ——
 * 文件丢了/过期了脚本只会报一堆莫名的失败。走 argv 和 `a11y-contrast.mjs` 一致，
 * 令牌从哪来由调用方决定。
 *
 * <h2>设计原则（都是踩过的坑）</h2>
 *  1. **期望值从接口现算，不写死。** 写死条数/凭据的断言第二次跑必然失败
 *     （本项目已有两个回归脚本因此废掉）。
 *  2. **断言"真正要保证的事"。** 不验"有没有 .strip__count 这个元素"，
 *     而验"日期条上的数字 == 接口当天返回的 total" —— 后者才是"数据没被截断"的证据。
 *  3. **失败要说明"期望什么"。** 只 console.log 出 null 会被当成"本来就没有"。
 *  4. **不假设"当前没人报名/没有头像"这类一次性前提**，用相对断言。
 *  5. 🔴 **不靠改数据库时间来造场景**：那样测的是"数据库能不能被弄不一致"，
 *     而不是"前端拿到过期球局会不会画出假的「加入」"。改用拨浏览器时钟（见 openAt）。
 */
// fetch 用 Node 内置的（Node 18+ 全局可用），不引 node-fetch —— 少一个依赖
import puppeteer from 'puppeteer-core/lib/puppeteer/puppeteer-core.js'
import fs from 'node:fs'

const ROOT = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const args = process.argv.slice(2)
const TOKEN = args.find((a) => a.includes('.'))   // JWT 一定带两个点
const MODE = args.includes('states') ? 'states' : 'clean'
const SHOTS = `${ROOT}target/shots`
const FE = 'http://127.0.0.1:5173'
const API = 'http://localhost:8080/api'
const STATE_FILE = `${ROOT}target/p0-states.json`

if (!TOKEN) {
  console.error(
    '\n❌ 用法：node tools/p0-home.mjs <TOKEN> [clean|states]\n' +
      '   TOKEN 是 /api/auth/login 返回的 data.token（形如 xxx.yyy.zzz）。\n' +
      '   首页 /home 在登录守卫后面，没有合法令牌整页都进不去。\n'
  )
  process.exit(3)
}

let pass = 0
let fail = 0
function expect(got, want, note) {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  ok ? pass++ : fail++
  console.log(`  ${ok ? 'OK  ' : 'FAIL'} ${note}`)
  if (!ok) console.log(`       got =${JSON.stringify(got)}\n       want=${JSON.stringify(want)}`)
}
function show(v, note) {
  console.log(`       ${note}: ${JSON.stringify(v)}`)
}

// ---------------- 期望值：从接口现算 ----------------
const pad = (n) => String(n).padStart(2, '0')
const dstr = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

async function apiDay(offset) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  const q = `status=1&start_from=${dstr(d)}T00:00:00&start_to=${dstr(d)}T23:59:59&page=1&page_size=50`
  const r = await (await fetch(`${API}/activities?${q}`)).json()
  return r.data
}

const days = []
for (let i = 0; i < 7; i++) days.push(await apiDay(i))
const today = days[0]
const courtMap = {}
const venues = (await (await fetch(`${API}/venues`)).json()).data
for (const c of (await (await fetch(`${API}/venues/${venues[0].id}/courts?status=1`)).json()).data) {
  courtMap[c.id] = c.name
}
console.log(`== 接口现算的期望值 ==`)
console.log(`   今天 ${today.total} 场，7 天分布 [${days.map((d) => d.total).join(', ')}]`)
console.log(`   场地映射 ${JSON.stringify(courtMap)}`)
if (MODE === 'states') console.log(`   测试数据 ${fs.readFileSync(STATE_FILE, 'utf-8')}`)

// ---------------- 浏览器 ----------------
const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: 'new',
  args: ['--no-sandbox']
})
const page = await browser.newPage()
await page.setViewport({ width: 1440, height: 900 })

const jsErrors = []
page.on('pageerror', (e) => jsErrors.push(String(e.message)))
const httpErrors = []
page.on('response', (res) => {
  if (res.status() >= 400 && !res.url().includes('favicon')) {
    httpErrors.push(`${res.status()} ${res.request().method()} ${res.url()}`)
  }
})

const isAnon = false // ⚠️ /home 在登录守卫后面（只有 /login、/register 是 meta.public），
                     // 所以验收必须带登录态；「未登录」那一态单独在 J 段用新会话验证。
const tk = TOKEN
await page.evaluateOnNewDocument((t) => localStorage.setItem('token', t), tk)

await page.goto(`${FE}/#/`, { waitUntil: 'networkidle2' })
// 等首屏数据落定（截断修复后是 8 个并行请求，慢网下要给足时间）
await page.waitForFunction(() => !document.querySelector('.games__body[aria-busy="true"]'), { timeout: 25000 })
await new Promise((r) => setTimeout(r, 400))

const txt = (sel) => page.$eval(sel, (el) => el.textContent.trim()).catch(() => null)
const count = (sel) => page.$$eval(sel, (els) => els.length).catch(() => 0)

// ============================================================
console.log('\n== A. 旧结构确实被删掉了 ==')
expect(await count('.hero'), 0, '没有 Hero 区（旧版主视觉已删除）')
expect(await count('.traj'), 0, '没有 7 天弧线 GameTrajectory（已删除组件）')
expect(await count('.next-game'), 0, '没有 NextGame「下一场球」卡（会推荐加不进去的局）')
await page.evaluate(() => {
  window.__t = document.body.textContent
})
const bodyHas = (s) => page.evaluate((x) => window.__t.includes(x), s)
expect(await bodyHas('今晚有球打吗'), false, '旧 Hero 标题「今晚有球打吗？」已不在页面')
show(await count('.panel'), '页面上 .panel 数量（旧版是 4 个装饰面板）')

console.log('\n== B. 区域 1 · 顶部状态行（是上下文，不是主角） ==')
expect(await count('.hd'), 1, '顶部状态行存在且只有一个')
const hdStatus = await txt('.hd__status')
expect(hdStatus !== null, true, '状态行有文字')
show(hdStatus, '状态行文案')
// 状态行必须是"有内容的真实播报"，不能是占位符。
// 不写死具体数字 —— 这一页的内容随时钟变（早上/晚上/打完之后文案不同），
// 写死数字只会让脚本在某个时段必然失败，那是脚本的错。
const HD_SHAPES = ['今天还有', '今天没有还能加入', '今天的球都打完了', '今天还没有人开球']
expect(HD_SHAPES.some((s) => (hdStatus || '').includes(s)), true,
  '状态行是四种真实播报之一（不是空占位）')
const hdH = await page.$eval('.hd', (el) => el.getBoundingClientRect().height)
expect(hdH < 140, true, `顶部区高度 ${Math.round(hdH)}px < 140px（没有重新变成大 Hero）`)

console.log('\n== C. 区域 2 · 日期条（修截断的核心证据） ==')
const cells = await page.$$eval('.strip__cell', (els) =>
  els.map((e) => ({
    label: e.querySelector('.strip__label')?.textContent.trim(),
    countText: e.querySelector('.strip__count')?.textContent.trim()
  }))
)
expect(cells.length, 7, '日期条有 7 格')
expect(cells[0]?.label, '今天', '第 1 格是「今天」（不是从明天开始）')
show(cells.map((c) => `${c.label}:${c.countText}`), '日期条各格')

// 每条断言都写成"日期条显示的数字 == 接口当天 total" ——
// 这才是"数据没被截断"的证据；断言"有 7 个格子"什么都证明不了
const wantCounts = days.map((d) => `${d.total} 场`)
const gotCounts = cells.map((c) => c.countText)
expect(gotCounts, wantCounts, '7 天的场次数与接口逐日 total 完全一致（不是被 LIMIT 截剩的那一段）')

console.log('\n== D. 单日列表：信息是否够做决策 ==')
const rows = await page.$$eval('.game-row', (els) =>
  els.map((e) => ({
    state: [...e.classList].find((c) => c.startsWith('is-')),
    time: e.querySelector('.game-row__time')?.textContent.trim(),
    title: e.querySelector('.game-row__title')?.textContent.trim(),
    meta: e.querySelector('.game-row__meta')?.textContent.replace(/\s+/g, ' ').trim()
  }))
)
// 满员的行被折叠成一行灰字（§J.2），所以"接口当天有几场" = 可见行数 + 折叠行里藏着的场数。
// 不写成 `rows.length == total` —— 那等于要求"满员的行不许折叠"，正好把设计反过来了。
const foldCount = await page.$$eval('.fold', (els) =>
  els.reduce((n, e) => n + (Number((e.textContent.match(/另有 (\d+) 场/) || [])[1]) || 0), 0))
expect(rows.length + foldCount, today.total,
  `可见行数(${rows.length}) + 折叠的满员场数(${foldCount}) == 接口当天 total(${today.total})`)
for (const r of rows) {
  console.log(`       [${(r.state || '').replace('is-', '')}] ${r.time} ${r.title} · ${r.meta}`)
}
const firstReal = today.list.find((a) => (a.court_ids || []).length && a.title === rows[0]?.title)
if (firstReal) {
  const wantPlace = firstReal.court_ids.map((id) => courtMap[id]).join('、')
  const row = rows.find((r) => r.title === firstReal.title)
  expect(!!row && row.meta.includes(wantPlace), true,
    `球局行显示了真实场地名「${wantPlace}」（court_ids 真的做了 id→场号映射）`)
  const wantNum = `已报 ${firstReal.joined_count}/${firstReal.max_members} 人`
  expect(!!row && row.meta.includes(wantNum), true, `球局行显示了人数「${wantNum}」`)
  expect(!!row && row.time === firstReal.start_time.slice(11, 16), true,
    `球局行显示的时间 == 接口 start_time（${firstReal.start_time.slice(11, 16)}）`)
}

console.log('\n== E. 「加入」——首页最明确的行动 ==')
const joinBtns = await page.$$eval('.game-row .app-btn', (els) =>
  els.map((e) => e.textContent.trim())
)
show(joinBtns, '可加入按钮')
// 「加入」按钮的数量必须 == 可加入的行数。
// 不写成"整页只能有 1 个按钮"——列表型操作页本来就该每行一个动作（§J.2），
// 真正要保证的是**只有能行动的行才有按钮**，下面连同不可行动的行一起断言。
const joinRows = await page.$$eval('.game-row', (els) =>
  els.filter((e) => [...e.classList].includes('is-join')).length)
expect(joinBtns.filter((t) => t === '加入').length, joinRows,
  `「加入」按钮数 == 可加入的行数（${joinRows}）—— 不多不少`)
expect(joinBtns.filter((t) => t === '加入').length > 0, true,
  '首页有「加入」（旧版一个都没有）')
// 反向断言：不可行动的行里不能出现按钮。这才是"避免页面变成一堆按钮"的量化形式
const deadBtns = await page.$$eval('.game-row.is-full, .game-row.is-over, .game-row.is-started, .game-row.is-joined',
  (els) => els.filter((e) => e.querySelector('.app-btn')).length)
expect(deadBtns, 0, '已满员/已结束/已开始/已加入的行里没有任何实心按钮（状态被弱化为徽标或文字）')

console.log('\n== F. 移动端 ==')
await page.setViewport({ width: 390, height: 844, isMobile: true })
await new Promise((r) => setTimeout(r, 400))
const gridAreas = await page.$eval('.game-row', (el) =>
  getComputedStyle(el).gridTemplateAreas.replace(/"/g, '').trim()
).catch(() => null)
expect(gridAreas, 'time act main main', '手机上时间与行动提到第一行，标题下移')
const stripOverflow = await page.$eval('.strip', (el) => {
  const s = getComputedStyle(el)
  return { ox: s.overflowX, scrollable: el.scrollWidth > el.clientWidth }
})
expect(stripOverflow.ox, 'auto', '日期条在窄屏可横向滚动（7 格放不下）')
show(stripOverflow, '日期条滚动状态')
const bodyOverflow = await page.evaluate(() =>
  document.documentElement.scrollWidth - document.documentElement.clientWidth)
expect(bodyOverflow <= 1, true, `手机上没有横向溢出（多出 ${bodyOverflow}px）`)
await page.screenshot({ path: `${SHOTS}/80-首页-移动端.png`, fullPage: true })
console.log('       已存 80-首页-移动端.png')

await page.setViewport({ width: 1440, height: 900 })
await new Promise((r) => setTimeout(r, 400))

// ============================================================
/**
 * 开一个"时钟被拨到指定时刻"的页面。
 *
 * 为什么不改数据库时间：那样测的是"数据库能不能被弄得不一致"，
 * 而不是"前端拿到一个已过期的球局时会不会画出假的「加入」"。
 * 把时钟拨到 22:15 / 23:30，库里那三场 22:00–23:00 的球局本身一动不动，
 * 被测的正是真实的 `stateOf()` 逻辑。
 *
 * 只在 `new Date()` 与 `Date.now()` 上加偏移；带参数的 `new Date(y,m,d,…)`
 * 保持原样 —— `parseTime()` 正是用后者把接口返回的时间串变成 Date 的。
 */
async function openAt(h, m) {
  const ctx = await browser.createBrowserContext()
  const p = await ctx.newPage()
  const shift = new Date().setHours(h, m, 0, 0) - Date.now()
  await p.evaluateOnNewDocument(({ offset, token }) => {
    localStorage.setItem('token', token)
    const Orig = Date
    class Shifted extends Orig {
      constructor(...a) {
        if (a.length === 0) super(Orig.now() + offset)
        else super(...a)
      }
      static now() {
        return Orig.now() + offset
      }
    }
    window.Date = Shifted
  }, { offset: shift, token: TOKEN })
  await p.setViewport({ width: 1440, height: 900, deviceScaleFactor: 2 })
  await p.goto(`${FE}/#/`, { waitUntil: 'networkidle2' })
  await p.waitForFunction(() => !document.querySelector('.games__body[aria-busy="true"]'), { timeout: 25000 })
  await new Promise((r) => setTimeout(r, 400))
  return { ctx, p }
}

async function rowsOf(p) {
  const list = await p.$$eval('.game-row', (els) =>
    els.map((e) => ({
      title: e.querySelector('.game-row__title')?.textContent.trim(),
      state: [...e.classList].find((c) => c.startsWith('is-'))?.replace('is-', ''),
      meta: e.querySelector('.game-row__meta')?.textContent.replace(/\s+/g, ' ').trim(),
      act: e.querySelector('.game-row__act')?.textContent.replace(/\s+/g, ' ').trim(),
      actTag: e.querySelector('.game-row__act')?.firstElementChild?.tagName
    })))
  const by = {}
  for (const r of list) by[r.title] = r
  return { list, by }
}

/** 展开"另有 N 场已满员"那一行（满员默认折叠 —— §J.2 的要求） */
async function expandFold(p) {
  const has = await p.$('.fold')
  if (!has) return null
  const text = await p.$eval('.fold', (e) => e.textContent.replace(/\s+/g, ' ').trim())
  await p.evaluate(() => document.querySelector('.fold').click())
  await new Promise((r) => setTimeout(r, 350))
  return text
}

if (MODE === 'states') {
console.log('\n== G. 加入状态机 · 时钟 21:00（三场 22:00 的球都还没开始） ==')
{
  const { ctx, p } = await openAt(21, 0)
  const before = await rowsOf(p)
  for (const r of before.list) console.log(`       [${r.state}] ${r.title} · ${r.meta} · ${r.act}`)

  // 满员的行默认不在列表里 —— 它被折叠了。这是 §J.2「已满员的折叠成一行灰字」的要求。
  expect(before.by['【验收·已满员】'], undefined,
    '已满员的球局默认不占列表（折叠成一行灰字）')
  const foldText = await p.$eval('.fold', (e) => e.textContent.replace(/\s+/g, ' ').trim())
  expect(foldText.includes('已满员'), true, `折叠行如实说明：${foldText}`)
  expect(foldText.includes('1'), true, '折叠行带满员场次数')

  const after = await (async () => {
    await expandFold(p)
    return rowsOf(p)
  })()
  const fullRow = after.by['【验收·已满员】']
  expect(fullRow?.state, 'full', 'max=1 且创建者占满 → 状态 full')
  expect(fullRow?.act, '已满员', '满员行显示「已满员」')
  expect(fullRow?.actTag, 'SPAN', '满员用徽标而不是 <button>（不占 Tab 顺序）')
  expect((fullRow?.meta || '').includes('还差'), false,
    '满员行不显示「还差 N 人」（那是假信息）')

  expect(before.by['【验收·探针A】']?.state, 'join', '未开始的空位球局 → 状态 join')
  expect(before.by['【验收·探针A】']?.act, '加入', '可加入的行有「加入」')

  // 顶部计数与列表必须自洽：说有几场能加入，就得有几行带「加入」
  const hdText = await p.$eval('.hd__status', (e) => e.textContent.replace(/\s+/g, ' ').trim())
  const hdNum = Number((hdText.match(/(\d+)\s*场球/) || [])[1])
  const joinRows = before.list.filter((r) => r.state === 'join').length
  expect(hdNum, joinRows, `顶部「${hdText}」里的场次数 == 列表里可加入的行数（${joinRows}）`)
  await p.screenshot({ path: `${SHOTS}/81-状态机-2100-可加入.png`, fullPage: true })
  console.log('       已存 81-状态机-2100-可加入.png')
  await ctx.close()
}

  console.log('\n== H. 加入状态机 · 时钟 22:15（球局进行中，报名通道已关） ==')
  {
    const { ctx, p } = await openAt(22, 15)
    const { list, by } = await rowsOf(p)
    for (const r of list) console.log(`       [${r.state}] ${r.title} · ${r.meta} · ${r.act}`)

    expect(by['【验收·探针A】']?.state, 'started', '已开打但没结束 → 状态 started')
    expect(by['【验收·探针A】']?.act, '已开始', '显示「已开始」')
    expect(by['【验收·探针A】']?.actTag, 'SPAN', '已开始用徽标（后端必然拒绝报名，不能给假入口）')

    // 这是本轮最关键的负向断言：一个"点了必定报错"的按钮都不能存在
    const btns = await p.$$eval('.game-row .app-btn', (els) => els.length)
    expect(btns, 0, '整页 0 个「加入」按钮 —— 没有任何球局可以报名')
    // 折叠里的满员行同样不能有按钮（展开后再确认一次，避免"藏起来就查不到"）
    await expandFold(p)
    const btnsAfter = await p.$$eval('.game-row .app-btn', (els) => els.length)
    expect(btnsAfter, 0, '展开满员折叠行后，整页仍是 0 个「加入」按钮')
    const hdText = await p.$eval('.hd__status', (e) => e.textContent.replace(/\s+/g, ' ').trim())
    expect(hdText.includes('没有还能加入'), true, `顶部如实说：${hdText}`)
    await p.screenshot({ path: `${SHOTS}/82-状态机-2215-已开始.png`, fullPage: true })
    console.log('       已存 82-状态机-2215-已开始.png')
    await ctx.close()
  }

  console.log('\n== I. 加入状态机 · 时钟 23:30（都打完了） ==')
  {
    const { ctx, p } = await openAt(23, 30)
    const { list, by } = await rowsOf(p)
    for (const r of list) console.log(`       [${r.state}] ${r.title} · ${r.meta} · ${r.act}`)

    expect(by['【验收·探针A】']?.state, 'over', '打完了 → 状态 over')
    expect(by['【验收·探针A】']?.act, '已结束', '显示「已结束」')
    expect(by['【验收·已满员】']?.state, 'over', 'over 优先级最高（已结束的不再谈满员）')
    const btns = await p.$$eval('.game-row .app-btn', (els) => els.length)
    expect(btns, 0, '整页 0 个「加入」按钮')
    const hdText = await p.$eval('.hd__status', (e) => e.textContent.replace(/\s+/g, ' ').trim())
    expect(hdText.includes('打完了'), true, `顶部如实说：${hdText}`)
    await p.screenshot({ path: `${SHOTS}/83-状态机-2330-已结束.png`, fullPage: true })
    console.log('       已存 83-状态机-2330-已结束.png')
    await ctx.close()
  }

  console.log('\n== I2. 加入 → 已加入 → 退出（真实改数据，跑完自己复原） ==')
  {
    const { ctx, p } = await openAt(21, 0)
    const realTitle = '【验收·探针B】'
    const before = await p.evaluate((t) => {
      const r = [...document.querySelectorAll('.game-row')].find((e) =>
        e.querySelector('.game-row__title')?.textContent.trim() === t)
      return Number((r.querySelector('.game-row__meta').textContent.match(/已报 (\d+)\//) || [])[1])
    }, realTitle)
    console.log(`       ${realTitle} 加入前 joined=${before}`)

    await p.evaluate((t) => {
      const r = [...document.querySelectorAll('.game-row')].find((e) =>
        e.querySelector('.game-row__title')?.textContent.trim() === t)
      r.querySelector('.game-row__act .app-btn').click()
    }, realTitle)
    await p.waitForFunction((t) => {
      const r = [...document.querySelectorAll('.game-row')].find((e) =>
        e.querySelector('.game-row__title')?.textContent.trim() === t)
      return r && [...r.classList].includes('is-joined')
    }, { timeout: 15000 }, realTitle)
    expect(true, true, '点「加入」后该行变成 is-joined')

    const after = await p.evaluate((t) => {
      const r = [...document.querySelectorAll('.game-row')].find((e) =>
        e.querySelector('.game-row__title')?.textContent.trim() === t)
      return { meta: r.querySelector('.game-row__meta').textContent.replace(/\s+/g, ' ').trim(),
               act: r.querySelector('.game-row__act').textContent.replace(/\s+/g, ' ').trim() }
    }, realTitle)
    expect(after.meta.includes(`已报 ${before + 1}/`), true,
      `本地计数 +1（已报 ${before + 1}/…，不重拉整页）`)
    expect(after.act.includes('已加入') && after.act.includes('退出'), true,
      '变「已加入 退出」（两个独立条件，退出没被挤掉）')
    show(after, '已加入后的行')
    await p.screenshot({ path: `${SHOTS}/84-状态机-已加入.png`, fullPage: true })
    console.log('       已存 84-状态机-已加入.png')

    // 退出：走确认框
    await p.evaluate((t) => {
      const r = [...document.querySelectorAll('.game-row')].find((e) =>
        e.querySelector('.game-row__title')?.textContent.trim() === t)
      r.querySelector('.game-row__quit').click()
    }, realTitle)
    await new Promise((r) => setTimeout(r, 600))
    await p.evaluate(() => {
      const b = [...document.querySelectorAll('.el-message-box__btns button, .el-message-box button')]
        .find((x) => x.textContent.trim() === '退出')
      if (b) b.click()
    })
    await p.waitForFunction((t) => {
      const r = [...document.querySelectorAll('.game-row')].find((e) =>
        e.querySelector('.game-row__title')?.textContent.trim() === t)
      return r && [...r.classList].includes('is-join')
    }, { timeout: 15000 }, realTitle)
    expect(true, true, '确认退出后回到可加入状态（数据已复原）')
    const back = await p.evaluate((t) => {
      const r = [...document.querySelectorAll('.game-row')].find((e) =>
        e.querySelector('.game-row__title')?.textContent.trim() === t)
      return Number((r.querySelector('.game-row__meta').textContent.match(/已报 (\d+)\//) || [])[1])
    }, realTitle)
    expect(back, before, `退出后 joined 回到原值 ${before}（服务端与本地一致）`)
    await ctx.close()
  }
} else {
  console.log('\n== G/H/I. 三态数据未就绪，跳过状态机（先跑 p0_states.py setup） ==')
}

// ============================================================
// 独立章节：未登录访客到底能不能看到这个首页。
// 这一条不是"顺手加的"，它决定 §J 里「未登录」那一态是否真的存在。
console.log('\n== J. 未登录访客能否到达球局广场（影响状态机第 1 态） ==')
{
  const anon = await browser.createBrowserContext()
  const p2 = await anon.newPage()
  await p2.goto(`${FE}/#/`, { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1200))
  const url = p2.url()
  const sawList = (await p2.$$('.game-row')).length
  const onLogin = url.includes('/login')
  console.log(`       未登录访问 #/ → 最终地址 ${url}`)
  console.log(`       未登录时页面上的球局行数：${sawList}`)
  if (onLogin) {
    console.log('       ⚠️ 结论：首页当前在登录守卫后面 —— 访客看不到球局广场，')
    console.log('          §J 的「未登录」态在本版实现里不可达（已如实记录，未擅自改路由）。')
  } else {
    console.log('       结论：访客可以直接看到球局广场。')
  }
  expect(onLogin || sawList > 0, true, '记录未登录访问的真实行为（不预设结论）')
  await anon.close()
}

// ============================================================
console.log('\n== 收尾 ==')
expect(jsErrors.length, 0, '无未捕获的 JS 异常')
if (jsErrors.length) console.log('       ' + jsErrors.join('\n       '))
expect(httpErrors.length, 0, '无失败的接口/资源请求（favicon 除外）')
if (httpErrors.length) console.log('       ' + httpErrors.join('\n       '))

if (MODE === 'clean') {
  // 桌面出图：回到首屏（带登录态，因为 /home 本来就在守卫后面）
  await page.goto(`${FE}/#/`, { waitUntil: 'networkidle2' })
  await page.waitForFunction(() => !document.querySelector('.games__body[aria-busy="true"]'), { timeout: 20000 })
  await new Promise((r) => setTimeout(r, 600))
  await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 2 })
  await page.screenshot({ path: `${SHOTS}/82-首页-桌面.png`, fullPage: true })
  console.log('       已存 82-首页-桌面.png')
}

await browser.close()
console.log(`\n===== ${pass}/${pass + fail} 项断言通过 =====`)
if (fail > 0) process.exitCode = 1
