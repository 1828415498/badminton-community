# -*- coding: utf-8 -*-
"""P0 状态机验收 · 测试数据准备 / 清理。

<h2>为什么要造数据</h2>
种子数据里今天只有 1 场「可加入」的球局，「已满员 / 已开始 / 已结束」三种状态
无法用现有数据触达，而它们是加入状态机的一半。不能用真实用户数据凑，只能临时造。

<h2>两个关键设计（都是踩过之后改的）</h2>

**1. 创建者必须是别人，不能是 admin123。**

加入状态机的优先级是「已结束 → 我已加入 → 已满员 → 已开始 → 可加入」——
本来就该如此（否则「退出」按钮会消失）。这意味着**用 admin123 造数据，
它自己就是报名者，看到的永远是「已加入」**，根本观测不到「已满员」「已开始」。
所以创建者用 player01（小林），断言用 admin123 的身份做。

**2. 只造「正常未来球局」，不碰任何时间。**

第一版为了实现「已开始/已结束」，把活动的 start_time/end_time UPDATE 到过去。
两个后果都很难看：
  - 只改 activity 没改 reservation → **占场仍压在今晚 22:00–23:00，会挡住真实预约**；
    测试数据污染生产可用性，比测试本身失败更糟。
  - 更根本的是**它测错了对象**：改数据库时间去凑场景，验证的是"数据库能不能不一致"，
    而不是"前端拿到一个已过期的球局时会不会画出假的「加入」"。

正确做法：**数据库里让三条球局老老实实排在今晚 22:00–23:00，用控制浏览器时钟来造场景。**
22:00 开打 → 把页面时钟拨到 22:15 就是「已开始」，拨到 23:30 就是「已结束」。
这样测的是真实的 `stateOf()` 逻辑，数据库自始至终是一份自洽的正常数据。

<h2>三条测试球局（全部 status=1 已发布，时间不动）</h2>
  full      court 3  max=1  → 创建者自动占满，创建即满员
  probeA    court 4  max=8  → 用来在挪时钟后观察 已开始 / 已结束
  probeB    court 5  max=8  → 同上（两条是为了确认不是单点巧合）

<h2>清理</h2>
按 id 精确删除自己造的三条活动 + 占场 + 报名，把库恢复到跑测试之前的状态。
（活动「永不物理删除」是业务规则，保护的是真实用户数据；这三条是测试夹具，
留着反而会让 cg 打开首页时看到「【验收·已满员】」。）

用法（从 `frontend/` 下执行）：
    python tools/p0-fixtures.py setup      # 造三态数据
    node   tools/p0-home.mjs <TOKEN> states  # 跑状态机验收
    python tools/p0-fixtures.py teardown   # 清理（务必跑）
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

API = "http://localhost:8080/api"
# 本机 MySQL 客户端路径（跟随本项目的开发环境约定；换机器时改这一行）
MYSQL = os.environ.get("MYSQL_BIN") or r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "badminton_community"
# 状态文件落到 target/（gitignored）——它是两个脚本之间的交接物，不是源码
STATE_FILE = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "target", "p0-states.json")

# 三块互不冲突的场 + 今晚 22:00–23:00（当天最后一小时，已确认这三个场该时段空闲）
SLOT = "22:00:00"
END = "23:00:00"
SPEC = {
    "full": {"court": 3, "max": 1, "title": "【验收·已满员】"},
    "probeA": {"court": 4, "max": 8, "title": "【验收·探针A】"},
    "probeB": {"court": 5, "max": 8, "title": "【验收·探针B】"},
}

CREATOR = ("player01", "123456")   # 创建者（非观测者）
OBSERVER = ("admin123", "123456")  # 观测者（断言用，必须不在报名名单里）


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


def sql(statement):
    out = subprocess.run(
        [MYSQL, "-uroot", "-p123456", "--default-character-set=utf8mb4", "-D", DB,
         "-N", "-B", "-e", statement],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise RuntimeError(out.stderr)
    return [ln for ln in (out.stdout or "").splitlines()
            if ln.strip() and "Warning" not in ln]


def login(cred):
    """返回 (token, user_id) —— 不额外打 /auth/me，登录响应里就有。"""
    st, r = call("POST", "/auth/login", {"username": cred[0], "password": cred[1]})
    d = r.get("data") or {}
    assert d.get("token"), f"{cred[0]} 登录失败: {r}"
    return d["token"], d["user"]["id"]


def ids_of(activity_ids):
    return "(" + ",".join(str(i) for i in activity_ids) + ")"


def setup():
    import datetime

    creator, creator_id = login(CREATOR)
    observer, me = login(OBSERVER)
    assert creator_id != me, "创建者与观测者必须是不同的人，否则观测不到「已满员/已开始」"
    print(f"  创建者={CREATOR[0]}(id={creator_id})  观测者={OBSERVER[0]}(id={me})")

    today = datetime.date.today()
    ids = {}
    for key, spec in SPEC.items():
        st, r = call("POST", "/activities", {
            "title": spec["title"],
            "description": "P0 首页状态机验收的临时数据，验收结束会自动清理。",
            "start_time": f"{today}T{SLOT}",
            "end_time": f"{today}T{END}",
            "max_members": spec["max"],
            "court_ids": [spec["court"]],
        }, token=creator)
        assert r.get("code") == "0", f"创建 {key} 失败: {r}"
        aid = r["data"]["id"]
        st, r2 = call("POST", f"/admin/activities/{aid}/approve", token=observer)
        assert r2.get("code") == "0", f"审核 {key} 失败: {r2}"
        ids[key] = aid
        print(f"  {key:7s} id={aid}  {spec['court']} 号场  {today} {SLOT}~{END}"
              f"  max={spec['max']}  状态=已发布（刻意不动时间）")

    # 自检一：三条都必须是 status=1、且时间确实是今晚 22:00–23:00（没有任何被动过）
    rows = sql("SELECT id, status, DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s'), "
               "DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') FROM activity WHERE id IN "
               + ids_of(ids.values()))
    print("  DB 现状:", rows)
    for line in rows:
        aid, status, s, e = line.split("\t")
        assert status == "1", f"活动 {aid} 状态不是已发布：{status}"
        assert s.endswith(SLOT) and e.endswith(END), f"活动 {aid} 时间被意外改过：{s}~{e}"

    # 自检二：观测者（admin123）必须不在任何一条的报名名单里，
    # 否则看到的会是「已加入」而不是「已满员」—— 这正是第一版踩的坑
    reg = sql("SELECT activity_id FROM registration WHERE user_id=" + str(me)
              + " AND activity_id IN " + ids_of(ids.values()) + " AND status=1")
    assert not reg, f"观测者竟然报名了测试活动，状态机将观测不准：{reg}"
    print("  自检通过：观测者未报名任何测试球局（否则会看到「已加入」）")

    with open(STATE_FILE, "w", encoding="utf-8") as f:
        json.dump(ids, f)
    print(f"已写入 {STATE_FILE}")


def teardown():
    with open(STATE_FILE, encoding="utf-8") as f:
        ids = json.load(f)
    lst = ids_of(ids.values())

    before = sql("SELECT (SELECT COUNT(*) FROM activity), (SELECT COUNT(*) FROM reservation),"
                 " (SELECT COUNT(*) FROM registration)")
    sql(f"DELETE FROM registration WHERE activity_id IN {lst}")
    sql(f"DELETE FROM reservation WHERE activity_id IN {lst}")
    sql(f"DELETE FROM activity WHERE id IN {lst}")
    after = sql("SELECT (SELECT COUNT(*) FROM activity), (SELECT COUNT(*) FROM reservation),"
                " (SELECT COUNT(*) FROM registration)")
    print(f"  activity/reservation/registration 行数：{before[0]} → {after[0]}")
    left = sql(f"SELECT id FROM activity WHERE id IN {lst}")
    print("  残留活动：", left or "（无）")

    # 核对：三块场今晚 22:00–23:00 又能约了（真能被别人约走，才算没留污染）
    # 🔴 日期必须现算：写死某一天的话，明天再跑这条断言就在查一个不存在的时间窗，
    #    会永远返回 0 —— 「永久假绿」，比断言失败更糟。
    day = __import__("datetime").date.today().isoformat()
    free = sql("SELECT COUNT(*) FROM reservation WHERE court_id IN (3,4,5) AND status=1 "
               f"AND start_time < '{day} {END}' AND end_time > '{day} {SLOT}'")
    print(f"  今晚 22:00–23:00 三块测试场地上的有效占场数：{free[0]}")
    assert free[0] == "0", "测试场地仍被占用，需手工排查"
    print("  测试数据已完全清除，不留任何占场。")


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "setup"
    print(f"== {mode} ==")
    (setup if mode == "setup" else teardown)()
