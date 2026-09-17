/**
 * a11y-contrast.mjs —— 按 WCAG AA 实算页面上每处文字的对比度。
 *
 * <h2>为什么需要这个脚本</h2>
 * "对比度够不够"靠眼睛判断是不可靠的，而且会被反复踩：
 *   1. 曾把品牌色文字版当成 4.6:1 报出去，实算只有 3.25:1；
 *   2. 曾用 `opacity: 0.7` 表达"计数为 0 要弱化" —— opacity 会**等比压低**
 *      文字对比度，5.0:1 的灰乘 0.7 就掉到 3.5:1。
 * 这两次都是"看起来没问题"，所以必须让机器算。
 *
 * <h2>用法</h2>
 *   node a11y-contrast.mjs <TOKEN> [hash1 hash2 ...]
 * 不给 hash 时用下面的默认清单。
 *
 * <h2>它检查什么</h2>
 * - 每个**含直接文字**的元素：文字色 vs 有效背景色（沿祖先链合成 rgba）
 * - 阈值：正文 4.5:1；大字号（≥24px，或 ≥18.66px 且加粗）3:1
 * - 另外单独报告：含文字的元素上挂了 `opacity < 1`（会破坏对比度，且不算禁用态豁免）
 * - 跳过：`aria-hidden`、视觉隐藏（clip/1px）、以及**真正的禁用态**
 *   （`:disabled` / `.is-inert` / `aria-disabled`），WCAG 对禁用控件豁免
 */
import puppeteer from 'puppeteer-core'

const TOKEN = process.argv[2]
const hashes = process.argv.slice(3).length
  ? process.argv.slice(3)
  : ['home', 'activities', 'venues', 'community', 'profile', 'activities/1']

/**
 * 拦一道"令牌传错"。
 *
 * 踩过：把 hash 当成 token 传进来（`npm run a11y /`），等于用 `token="/"` 跑。
 * 后果不是报错，而是**一堆假通过** —— 未登录时公开页照样渲染、照样"0 违规"，
 * 只有需要登录态才能在 DOM 里出现的弹层会静默漏掉，最后报一个语焉不详的"弹层未展开"。
 * 所以宁可在这里直接停下，也不给一份看起来绿色的报告。
 */
if (!TOKEN || !TOKEN.includes('.')) {
  console.error(
    '\n❌ 用法：npm run a11y <TOKEN> [hash1 hash2 ...]\n' +
      '   第一个参数必须是登录接口返回的 JWT（形如 xxx.yyy.zzz），不是页面 hash。\n' +
      '   拿到它：POST /api/auth/login {username,password} → data.token\n'
  )
  process.exit(3)
}

const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
  args: ['--no-sandbox', '--hide-scrollbars', '--lang=zh-CN']
})

/** 在页面里执行：返回所有违规项 */
function audit() {
  const parse = (c) => {
    const m = String(c).match(/rgba?\(([^)]+)\)/)
    if (!m) return null
    const p = m[1].split(',').map((x) => parseFloat(x.trim()))
    return { r: p[0], g: p[1], b: p[2], a: p[3] === undefined ? 1 : p[3] }
  }
  const blend = (top, bottom) => ({
    r: top.r * top.a + bottom.r * (1 - top.a),
    g: top.g * top.a + bottom.g * (1 - top.a),
    b: top.b * top.a + bottom.b * (1 - top.a),
    a: 1
  })
  const lum = ({ r, g, b }) => {
    const f = (v) => {
      const s = v / 255
      return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
  }
  const ratio = (a, b) => {
    const l1 = lum(a)
    const l2 = lum(b)
    return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
  }

  /** 该元素的有效背景：沿祖先链（根 → 元素）逐层合成，底色按白色画布算 */
  function effectiveBg(el) {
    const chain = []
    let n = el
    while (n && n.nodeType === 1) {
      const bg = parse(getComputedStyle(n).backgroundColor)
      if (bg && bg.a > 0) chain.push(bg)
      n = n.parentElement
    }
    let base = { r: 255, g: 255, b: 255, a: 1 }
    for (let i = chain.length - 1; i >= 0; i--) base = blend(chain[i], base)
    return base
  }

  const isExempt = (el) => {
    if (el.closest('[aria-hidden="true"]')) return true
    if (el.matches(':disabled') || el.closest('[aria-disabled="true"]')) return true
    if (el.closest('.is-inert, .is-busy')) return true
    const cs = getComputedStyle(el)
    // 视觉隐藏（sr-only）
    if (cs.clip !== 'auto' || cs.width === '1px') return true
    if (cs.visibility === 'hidden' || cs.display === 'none') return true
    return false
  }

  const violations = []
  const opacityText = []

  for (const el of document.querySelectorAll('body *')) {
    // 只看"有直接文字"的元素
    const own = [...el.childNodes]
      .filter((n) => n.nodeType === 3)
      .map((n) => n.textContent.trim())
      .join('')
    if (!own) continue
    if (isExempt(el)) continue

    const cs = getComputedStyle(el)
    const fg = parse(cs.color)
    if (!fg) continue
    const bg = effectiveBg(el)
    const composed = fg.a < 1 ? blend(fg, bg) : fg
    const r = ratio(composed, bg)

    const size = parseFloat(cs.fontSize)
    const weight = parseInt(cs.fontWeight, 10) || 400
    const large = size >= 24 || (size >= 18.66 && weight >= 700)
    const need = large ? 3 : 4.5

    if (r < need) {
      violations.push({
        text: own.slice(0, 24),
        sel: el.tagName.toLowerCase() + (el.className ? '.' + String(el.className).split(' ')[0] : ''),
        ratio: Math.round(r * 100) / 100,
        need,
        color: cs.color,
        bg: `rgb(${Math.round(bg.r)}, ${Math.round(bg.g)}, ${Math.round(bg.b)})`,
        size: Math.round(size)
      })
    }

    // 含文字的元素被 opacity 压暗（祖先也算）
    let n = el
    let minOpacity = 1
    while (n && n.nodeType === 1) {
      const o = parseFloat(getComputedStyle(n).opacity)
      if (!Number.isNaN(o)) minOpacity = Math.min(minOpacity, o)
      n = n.parentElement
    }
    if (minOpacity < 1) {
      opacityText.push({
        text: own.slice(0, 24),
        sel: el.tagName.toLowerCase() + (el.className ? '.' + String(el.className).split(' ')[0] : ''),
        opacity: minOpacity,
        effRatio: Math.round(r * minOpacity * 100) / 100
      })
    }
  }
  return { violations, opacityText }
}

/**
 * 有些文字藏在弹层里，靠 URL 到不了。给这些路径配一个"先做点什么"的钩子，
 * 否则弹层里的控件永远扫不到 —— 那是**假通过**，比不检查更危险。
 */
const OPENERS = {
  profile: {
    open: async (page) => {
      // 先等触发按钮真的出现再点。原来固定 sleep 600ms —— 页面/接口稍慢就是"点了没反应"，
      // 于是弹层扫不到、报成"检查无效"，让人以为是弹层坏了。等元素比等时间可靠。
      const TRIGGER = () => [...document.querySelectorAll('button')].find(
        (x) => x.textContent.trim() === '编辑资料'
      )
      try {
        await page.waitForFunction(
          (src) => new Function('return (' + src + ')()')(),
          { timeout: 8000 },
          TRIGGER.toString()
        )
      } catch (e) {
        return false // 按钮都没出现，交给调用方判"无效"
      }
      await page.evaluate((src) => {
        const btn = new Function('return (' + src + ')()')()
        if (btn) btn.click()
      }, TRIGGER.toString())
      // 再等弹层里那个必须存在的元素，而不是固定 600ms
      try {
        await page.waitForSelector('.uploader', { timeout: 8000 })
      } catch (e) {
        return false
      }
      /**
       * 🔴 元素"存在"不等于"已经看得见"。
       * 弹层是带入场动画的（`opacity 0 → 1` 的 CSS transition），元素一 attach 就被扫的话，
       * 沿祖先链合成出来的 opacity 还是 0 → 整片文字被报成"等效对比度 0:1"的假违规。
       * （第一版固定 sleep 600ms 恰好躲过了动画，改成"等元素"之后反而全军覆没。）
       *
       * 这里等的正是 audit 自己要算的那个量：沿祖先链合成的有效 opacity。
       * 比 `getAnimations()` 稳 —— 无限动画的 finished 永远不 resolve，且它不覆盖"还没开始"的过渡。
       */
      try {
        await page.waitForFunction(
          () => {
            const el = document.querySelector('.sheet-panel') || document.querySelector('.uploader')
            if (!el) return false
            let o = 1
            for (let x = el; x && x.nodeType === 1; x = x.parentElement) {
              o *= parseFloat(getComputedStyle(x).opacity || '1')
            }
            return o > 0.99
          },
          { timeout: 8000 }
        )
      } catch (e) {
        return false // 一直没变不透明，说明弹层没真正出现，判无效而不是硬扫
      }
      await new Promise((r) => setTimeout(r, 150))
      return true
    },
    /** 打开后必须出现的东西：没出现就说明这一步没生效，本次结论作废 */
    expect: '.uploader'
  }
}

/** 每个页面查两遍：正常态 +（如果有钩子）弹层展开态 */
const targets = []
for (const h of hashes) {
  targets.push({ hash: h, label: `/#/${h}` })
  if (OPENERS[h]) {
    targets.push({ hash: h, label: `/#/${h}（弹层展开）`, opener: OPENERS[h] })
  }
}

let totalBad = 0
let totalOpacity = 0
/**
 * "检查没跑成"必须和"查出违规"分开计数。
 * 踩过：把"弹层未展开"折算成 1 项违规，于是①报告口径失真（看起来像对比度不合格）
 * ②真正的失效原因（令牌不对/页面慢）被一个数字盖掉了。
 * 两者都会让脚本非零退出，所以分开计数不会放过任何一次真失效。
 */
let totalInvalid = 0

for (const t of targets) {
  const page = await browser.newPage()
  await page.evaluateOnNewDocument((tk) => localStorage.setItem('token', tk), TOKEN)
  await page.setViewport({ width: 1280, height: 940, deviceScaleFactor: 1 })
  await page.goto('http://127.0.0.1:5173/#/' + t.hash, { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1800))

  if (t.opener) {
    // 失败重试一次：这一步依赖"点开弹层"，偶发的慢加载不该直接判无效
    let ok = await t.opener.open(page)
    if (!ok) ok = await t.opener.open(page)
    if (!ok || (t.opener.expect && !(await page.$(t.opener.expect)))) {
      console.log(
        `\n⚪ ${t.label} —— 弹层未展开（找不到 ${t.opener.expect}），本次检查无效（不是对比度违规）`
      )
      totalInvalid += 1
      await page.close()
      continue
    }
  }

  const { violations, opacityText } = await page.evaluate(audit)
  const ok = violations.length === 0 && opacityText.length === 0
  console.log(`\n${ok ? '✅' : '⚠️'} ${t.label}  —— 对比度违规 ${violations.length} 项，opacity 压暗文字 ${opacityText.length} 项`)
  for (const v of violations) {
    console.log(`   ❌ ${v.sel} "${v.text}"  ${v.ratio}:1 < ${v.need}:1  (${v.color} on ${v.bg}, ${v.size}px)`)
  }
  for (const o of opacityText) {
    console.log(`   ⚠️  ${o.sel} "${o.text}"  opacity=${o.opacity} → 等效对比度约 ${o.effRatio}:1`)
  }
  totalBad += violations.length
  totalOpacity += opacityText.length
  await page.close()
}

console.log(
  `\n──────────────────────────────\n合计：对比度违规 ${totalBad} 项，opacity 压暗 ${totalOpacity} 项` +
    (totalInvalid ? `，未能检查 ${totalInvalid} 项（报告不完整）` : '')
)
await browser.close()
// 「未能检查」也让脚本失败 —— 报告不完整不能被读成"全绿"
process.exitCode = totalBad + totalOpacity + totalInvalid > 0 ? 2 : 0
