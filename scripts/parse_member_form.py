# -*- coding: utf-8 -*-
"""
成员材料填写表解析器

读取 _成员材料填写表.md，解析出：
1. 成员身份信息
2. 个人总结
3. 20 份互评打分 + 每人平均分
4. 贡献系数与校验

用法: python parse_member_form.py [表单路径]
"""
import io
import os
import re
import sys

FORM = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "_成员材料填写表.md"
)

PLACEHOLDER = re.compile(r"^\[\[(.*)\]\]$", re.S)
PLACEHOLDER_HINT = re.compile(r"请填|0-25|^\.\.\.$|^可选$|已填/未填|填小数")
DIMS = ["工作态度", "系统设计", "系统实现", "系统测试"]


def read(path):
    for enc in ("utf-8", "utf-8-sig", "gbk"):
        try:
            with io.open(path, encoding=enc) as f:
                return f.read()
        except UnicodeDecodeError:
            continue
    raise RuntimeError("cannot decode file")


def clean(v):
    """去掉 [[ ]] 包裹；若仍是占位说明，返回 None 表示未填"""
    if v is None:
        return None
    v = v.strip()
    m = PLACEHOLDER.match(v)
    if m:
        inner = m.group(1).strip()
        if PLACEHOLDER_HINT.search(inner) or not inner:
            return None
        return inner
    if PLACEHOLDER_HINT.search(v):
        return None
    return v


def parse_blocks(text):
    """按顶层键和二级键切块。返回 {top: {sub: {k: v}}} 或 {top: {k: v}}"""
    blocks = {}
    cur_top = None
    cur_sub = None
    for raw in text.splitlines():
        if not raw.strip() or raw.strip().startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip())
        s = raw.strip()

        m = re.match(r"^([A-Za-z_0-9\u4e00-\u9fa5]+):\s*(.*)$", s)
        if indent == 0 and m:
            cur_top = m.group(1)
            blocks.setdefault(cur_top, {})
            cur_sub = None
            if m.group(2).strip():
                blocks[cur_top]["__value__"] = m.group(2).strip()
            continue

        if indent in (2, 4) and s.endswith(":") and cur_top is not None:
            cur_sub = s[:-1].strip()
            blocks[cur_top].setdefault(cur_sub, {})
            continue

        m = re.match(r"^([^:：]+)[:：]\s*(.*)$", s)
        if m and cur_top is not None:
            k, v = m.group(1).strip(), m.group(2).strip()
            if cur_sub is not None:
                blocks[cur_top].setdefault(cur_sub, {})[k] = v
            else:
                blocks[cur_top][k] = v
    return blocks


def main():
    text = read(FORM)
    blocks = parse_blocks(text)

    out = []
    out.append("=" * 68)
    out.append("成员材料填写表 解析结果")
    out.append("=" * 68)

    # 一、身份
    out.append("")
    out.append("[一、成员身份]")
    members = {}
    for i in range(1, 6):
        b = blocks.get(f"member_{i}", {})
        code = clean(b.get("code"))
        real = clean(b.get("real_name"))
        conf = clean(b.get("confirmed"))
        members[i] = dict(code=code, real=real, confirmed=conf)
        flag = "OK" if conf in ("是", "已确认") else "未确认"
        out.append("  member_%d: %-12s 姓名=%-10s %s" % (i, code or "-", real or "[未填]", flag))

    # 二、总结
    out.append("")
    out.append("[二、个人总结]")
    summaries = {}
    for i in range(1, 6):
        got = None
        m = re.search(r"summary_%d:\s*\[\[(.*?)\]\]" % i, text, re.S)
        if m:
            body = m.group(1).strip()
            if "请填" not in body and len(body) >= 50:
                got = body
        summaries[i] = got
        out.append("  summary_%d: %s  %d 字" % (i, "已填" if got else "[未填]", len(got) if got else 0))

    # 三、互评
    out.append("")
    out.append("[三、互评打分]")
    received = {i: [] for i in range(1, 6)}
    for by in range(1, 6):
        b = blocks.get("review_by_%d" % by, {})
        out.append("")
        out.append("  review_by_%d (%s 评):" % (by, members[by]["code"]))
        if not b:
            out.append("    [整段未填]")
            continue
        for tkey, detail in b.items():
            if tkey == "__value__" or not isinstance(detail, dict):
                continue
            m = re.match(r"target_(\d)", tkey)
            if not m:
                continue
            t = int(m.group(1))
            vals = []
            for d in DIMS:
                v = clean(detail.get(d))
                vals.append(int(v) if v and v.isdigit() else None)
            reason = clean(detail.get("一句话理由"))
            if all(v is not None for v in vals):
                total = sum(vals)
                received[t].append(total)
                out.append("    -> %-12s %s 合计=%d  [%s]" % (
                    members[t]["code"], vals, total, reason or "无理由"))
            else:
                out.append("    -> %-12s [有未填项] %s" % (members[t]["code"], vals))

    # 四、平均分
    out.append("")
    out.append("[四、每人收到的平均分]")
    for t in range(1, 6):
        lst = received[t]
        if lst:
            out.append("  %-12s 收到 %d 份  平均 %.1f  明细 %s"
                       % (members[t]["code"], len(lst), sum(lst) / len(lst), lst))
        else:
            out.append("  %-12s 收到 0 份  [无数据]" % members[t]["code"])

    # 五、贡献系数
    out.append("")
    out.append("[五、贡献系数]")
    cb = blocks.get("contribution", {})
    coefs = []
    for i in range(1, 6):
        found = None
        for kk, vv in cb.items():
            if kk.startswith("member_%d_" % i):
                found = clean(vv)
                break
        try:
            fv = float(found) if found else None
        except ValueError:
            fv = None
        coefs.append(fv)
        out.append("  member_%d: %s" % (i, fv if fv is not None else "[未填]"))
    valid = [c for c in coefs if c is not None]
    if len(valid) == 5:
        avg = sum(valid) / 5
        if abs(avg - 1.0) < 1e-6:
            out.append("  校验: OK 平均为 1.0")
        else:
            out.append("  校验: NG 平均为 %.3f，需调整" % avg)
    else:
        out.append("  校验: 仅 %d/5 项已填，无法校验" % len(valid))

    # 六、完成度
    out.append("")
    out.append("[六、完成度]")
    f_sum = sum(1 for v in summaries.values() if v)
    f_rev = sum(len(v) for v in received.values())
    out.append("  个人总结: %d/5" % f_sum)
    out.append("  互评打分: %d/20" % f_rev)
    out.append("  贡献系数: %d/5" % len(valid))
    ready = (f_sum == 5 and f_rev == 20 and len(valid) == 5)
    out.append("  可生成报告: %s" % ("是" if ready else "否，仍有缺项"))

    # 七、汇总 md
    if f_rev > 0:
        path = os.path.join(os.path.dirname(FORM), "_互评汇总-自动生成.md")
        md = ["# 互评汇总（由填写表自动生成）", "",
              "| 成员 | 收到份数 | 平均分 | 明细 |", "| --- | --- | --- | --- |"]
        for t in range(1, 6):
            lst = received[t]
            avg = "%.1f" % (sum(lst) / len(lst)) if lst else "无数据"
            md.append("| %s | %d | %s | %s |" % (members[t]["code"], len(lst), avg, lst))
        io.open(path, "w", encoding="utf-8").write("\n".join(md))
        out.append("")
        out.append("已输出: %s" % path)

    out.append("=" * 68)
    result = "\n".join(out)
    io.open(os.path.join(os.path.dirname(FORM), "_解析结果.txt"), "w",
            encoding="utf-8").write(result)
    return result


if __name__ == "__main__":
    print(main())
