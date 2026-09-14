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

let totalBad = 0
let totalOpacity = 0

for (const hash of hashes) {
  const page = await browser.newPage()
  await page.evaluateOnNewDocument((t) => localStorage.setItem('token', t), TOKEN)
  await page.setViewport({ width: 1280, height: 940, deviceScaleFactor: 1 })
  await page.goto('http://127.0.0.1:5173/#/' + hash, { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1800))

  const { violations, opacityText } = await page.evaluate(audit)
  const ok = violations.length === 0 && opacityText.length === 0
  console.log(`\n${ok ? '✅' : '⚠️'} /#/${hash}  —— 对比度违规 ${violations.length} 项，opacity 压暗文字 ${opacityText.length} 项`)
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
  `\n──────────────────────────────\n合计：对比度违规 ${totalBad} 项，opacity 压暗 ${totalOpacity} 项`
)
await browser.close()
process.exitCode = totalBad + totalOpacity > 0 ? 2 : 0
