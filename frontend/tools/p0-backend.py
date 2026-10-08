# -*- coding: utf-8 -*-
"""P0 后端契约验证：court_ids 补字段 + 按天分查（修截断）+ 报名接口。"""
import json
import sys
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta

sys.stdout.reconfigure(encoding="utf-8")

API = "http://localhost:8080/api"
ok = 0
fail = 0


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}


def check(name, cond, detail=""):
    global ok, fail
    if cond:
        ok += 1
        print("  PASS  " + name)
    else:
        fail += 1
        print("  FAIL  " + name + "   -> " + json.dumps(detail, ensure_ascii=False)[:400])


def day_bounds(offset):
    d = date.today() + timedelta(days=offset)
    return (d.strftime("%Y-%m-%dT00:00:00"), d.strftime("%Y-%m-%dT23:59:59"))


# ---------- 0. 登录 ----------
st, r = call("POST", "/auth/login", {"username": "admin123", "password": "123456"})
token = (r.get("data") or {}).get("token")
print("== 0. 登录 ==")
check("admin123 登录拿到 token", st == 200 and bool(token), r)
if not token:
    sys.exit(1)

# ---------- 1. court_ids 字段存在且是真场地 ----------
print("\n== 1. 列表接口补 court_ids（P0 硬依赖 1） ==")
frm, to = day_bounds(0)
st, r = call("GET", f"/activities?status=1&start_from={frm}&start_to={to}&page=1&page_size=50")
rows = (r.get("data") or {}).get("list") or []
check("今天的列表可查", st == 200 and r.get("code") == "0", r)
check("今天有球局可测（种子数据）", len(rows) > 0, {"count": len(rows)})

if rows:
    first = rows[0]
    check("列表项含 court_ids 字段", "court_ids" in first, list(first.keys()))
    with_courts = [x for x in rows if x.get("court_ids")]
    check("至少一场球局带非空 court_ids", len(with_courts) > 0,
          {x["id"]: x.get("court_ids") for x in rows})
    check("court_ids 是数组（不是 null）", all(isinstance(x.get("court_ids"), list) for x in rows),
          {x["id"]: x.get("court_ids") for x in rows if not isinstance(x.get("court_ids"), list)})

    # court_ids 必须是真 court 表 id：拿场地接口反查
    st, rv = call("GET", "/venues")
    venues = (rv.get("data") or [])
    check("能查到场馆", len(venues) > 0, rv)
    vid = venues[0]["id"]
    st, rc = call("GET", f"/venues/{vid}/courts?status=1")
    courts = (rc.get("data") or [])
    real_ids = {c["id"] for c in courts}
    all_ids = {i for x in rows for i in (x.get("court_ids") or [])}
    check("court_ids 全部能在场地表里查到（没编造）", all_ids.issubset(real_ids),
          {"用到的": sorted(all_ids), "真实的": sorted(real_ids)})
    print("       今天球局 →", json.dumps(
        [{"id": x["id"], "title": x["title"], "court_ids": x.get("court_ids"),
          "joined": x["joined_count"], "max": x["max_members"]} for x in rows],
        ensure_ascii=False))

# ---------- 2. 复现"一次查 7 天"的截断（说明为何按天分查） ----------
print("\n== 2. 截断陷阱复现（为什么必须按天分查） ==")
f7, t7 = day_bounds(0)[0], day_bounds(6)[1]
st, r = call("GET", f"/activities?status=1&start_from={f7}&start_to={t7}&page=1&page_size=50")
wide = (r.get("data") or {}).get("list") or []
wide_total = (r.get("data") or {}).get("total")
wide_today = [x for x in wide if x["start_time"][:10] == date.today().isoformat()]
print(f"       7 天窗口一次查：total={wide_total} 返回={len(wide)} 条，其中今天={len(wide_today)} 条")
print(f"       ⚠️ 当前种子数据仅 {wide_total} 条（远不到 LIMIT 50），触发不了截断 ——"
      f"截断是结构性风险，不是当前数据能观测到的现象")

total_all = 0
per_day = []
for i in range(7):
    f, t = day_bounds(i)
    st, rd = call("GET", f"/activities?status=1&start_from={f}&start_to={t}&page=1&page_size=1")
    cnt = (rd.get("data") or {}).get("total") or 0
    per_day.append(cnt)
    total_all += cnt
print(f"       按天分查求和：{per_day} → 共 {total_all} 条")

check("按天分查的总数 == 7 天窗口的 total（分天没有漏/重）",
      total_all == wide_total, {"按天": total_all, "窗口": wide_total})

# 陷阱的成因是"排序 DESC + LIMIT"这个结构，而不是某次查询的返回条数。
# 所以这里断言结构本身：排序必须是 DESC —— 正因为是 DESC，一旦窗口内超过 50 条，
# 被 LIMIT 砍掉的恰好是**最早（也就是今天）**的那一端。这条断言在未来数据变多时依然成立。
wide_times = [x["start_time"] for x in wide]
check("后端公开列表确实是 start_time 倒序（截断陷阱的成因，也是必须分天的理由）",
      wide_times == sorted(wide_times, reverse=True), wide_times)

# 真正要保证的事：按天分查能拿全"当天"的球局，不被任何 LIMIT 截掉
st, rd = call("GET", f"/activities?status=1&start_from={day_bounds(0)[0]}"
              f"&start_to={day_bounds(0)[1]}&page=1&page_size=50")
d0 = (rd.get("data") or {})
check("今天的查询把当天全部返回（list 条数 == total，页面用的就是这个 page_size=50）",
      len(d0.get("list") or []) == d0.get("total"), {"list": len(d0.get("list") or []),
                                                     "total": d0.get("total")})

# ---------- 3. 按天过滤是"区间"而不是"整天" ----------
print("\n== 3. 时间区间过滤准确性 ==")
f, t = day_bounds(1)
st, r = call("GET", f"/activities?status=1&start_from={f}&start_to={t}&page=1&page_size=50")
tom = (r.get("data") or {}).get("list") or []
check("明天的结果全部落在明天",
      all(x["start_time"][:10] == (date.today() + timedelta(days=1)).isoformat() for x in tom),
      [x["start_time"] for x in tom])

# ---------- 4. 报名接口（加入状态机依赖 myRegistrations） ----------
print("\n== 4. 报名接口契约 ==")
st, r = call("GET", "/registrations/mine?status=1&page=1&page_size=50", token=token)
mine = (r.get("data") or {}).get("list") or []
check("我的报名可查且是嵌套结构 registration/activity",
      st == 200 and (not mine or ("registration" in mine[0] and "activity" in mine[0])),
      mine[:1])
check("嵌套的 activity 带 id（前端按它判定已加入）",
      all(m.get("activity", {}).get("id") is not None for m in mine),
      [m.get("activity") for m in mine])
print(f"       admin123 已报名 {len(mine)} 场")

st, r = call("GET", "/activities?status=1&start_from=" + day_bounds(0)[0]
             + "&start_to=" + day_bounds(0)[1] + "&page=1&page_size=50")
anon_rows = (r.get("data") or {}).get("list") or []
check("未登录也能看到列表（访客可用）", st == 200 and len(anon_rows) == len(rows), len(anon_rows))

st, r = call("GET", "/activities?status=0")
check("非公开状态 status=0 匿名被拒（权限未被破坏）", r.get("code") != "0", r)

# ---------- 5. 列表 page_size 封顶仍为 50 ----------
print("\n== 5. 既有约束未被破坏 ==")
st, r = call("GET", "/activities?status=1&page=1&page_size=999")
check("page_size 仍被夹到 50（clampSize 未被绕过）",
      (r.get("data") or {}).get("page_size") == 50, (r.get("data") or {}).get("page_size"))

print(f"\n===== 结果：{ok} passed, {fail} failed =====")
sys.exit(1 if fail else 0)
