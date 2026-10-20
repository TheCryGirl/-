# -*- coding: utf-8 -*-
"""把已排版好的报告改造成指导书附件「实验报告样本」第一部的结构。

依据：2026 版《软件工程实验》实验指导书 印刷页 20–21（PDF 第 2–3 页）
「一、需求规格说明书」模板。与自拟结构的差异：

1) 缺「一、需求规格说明书」这个「部」标题；
2) 各级标题缺英文名（模板写作 1. 项目概述与迭代规划（Project overview ...））；
3) 图表编号在模板里按「部-序号」连续编排（第一部内从表1-1 起编，
   第二部再从表2-1 起编），而本文档按「章-序号」编，导致
   图2-5 之后跳到图3-1、再跳到图4-1，表也分了 1/2/3 三套；
4) 4 张表原先是无题表，模板要求有「表 x-y 标题」。

必须先跑 postprocess_docx.py，再跑本脚本（本脚本不再改动版式，
只增删段落与改文字）。

用法：python restructure_template.py <docx>
"""
import copy
import re
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt
from docx.text.paragraph import Paragraph

PATH = sys.argv[1]
doc = Document(PATH)
body = doc.element.body


# ---------- 工具 ----------
def set_para_text(p, new):
    """保留首个 run 的字符格式，其余 run 清空。"""
    runs = p.runs
    if not runs:
        p.add_run(new)
        return
    runs[0].text = new
    for r in runs[1:]:
        r.text = ""


def make_para(text, size, bold=False, cn="宋体", ascii_="Times New Roman",
              align=WD_ALIGN_PARAGRAPH.CENTER, before=0, after=0, line=1.5):
    p = doc.add_paragraph()
    p.alignment = align
    pf = p.paragraph_format
    pf.space_before = Pt(before)
    pf.space_after = Pt(after)
    pf.line_spacing = line
    pf.first_line_indent = 0
    r = p.add_run(text)
    r.font.size = Pt(size)
    r.bold = bold
    r.font.name = ascii_
    rPr = r._element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:ascii"), ascii_)
    rFonts.set(qn("w:hAnsi"), ascii_)
    rFonts.set(qn("w:eastAsia"), cn)
    return p


def move_before(el, anchor):
    body.remove(el)
    anchor.addprevious(el)


# ---------- 0. 幂等守卫 ----------
for p in doc.paragraphs:
    if p.text.strip() == "一、需求规格说明书":
        raise SystemExit("本文档已经是模板结构，无需重复改造")

# ---------- 1. 标题补齐英文名（照模板措辞） ----------
HEAD_MAP = {
    "1 项目概述与迭代规划":
        "1. 项目概述与迭代规划（Project overview and iteration planning）",
    "1.1 项目背景与目标":
        "1.1 项目背景与目标（project background and objectives）",
    "1.2 迭代规划": "1.2 迭代规划（iteration planning）",
    "2 系统用例分析": "2. 系统用例分析（System Use Case Analysis）",
    "2.1 角色分析": "2.1 角色分析（Actor Analysis）",
    "2.2 用例分析": "2.2 用例分析（Use Case Analysis）",
    "2.3 用例建模": "2.3 用例建模（Use Case Modeling）",
    "2.4 数据建模": "2.4 数据建模（Data Modeling）",
    "3 非功能需求": "3. 非功能需求（No-Functional Requirement）",
    "4 参考资料": "4. 参考资料（References）",
}

n_head = 0
for p in doc.paragraphs:
    t = p.text.strip()
    if t in HEAD_MAP:
        set_para_text(p, HEAD_MAP[t])
        # 加英文名后标题超过 postprocess 的 40 字阈值，keep_with_next 会失效，
        # 这里补回来：标题不落单在页尾
        p.paragraph_format.keep_with_next = True
        n_head += 1
print("headings renamed:", n_head)

# 一级标题变长后会在页面上折行，若沿用两端对齐，中文字符间会被拉开字距。
# 模板里标题是左对齐的，这里统一改过来。
n_left = 0
for p in doc.paragraphs:
    if p.style.name in ("Heading 1", "Heading 2", "Title"):
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        n_left += 1
print("headings left-aligned:", n_left)

# 标题样式把「黑体」同时写给了 ascii/hAnsi，英文名会被当汉字排版，
# 字距明显变宽。模板标题的中文是黑体、西文是 Times New Roman，这里对齐。
from docx.oxml.ns import nsdecls
n_font = 0
for p in doc.paragraphs:
    if not p.style.name.startswith("Heading"):
        continue
    for r in p.runs:
        rPr = r._element.get_or_add_rPr()
        rFonts = rPr.find(qn("w:rFonts"))
        if rFonts is None:
            rFonts = OxmlElement("w:rFonts")
            rPr.insert(0, rFonts)
        rFonts.set(qn("w:ascii"), "Times New Roman")
        rFonts.set(qn("w:hAnsi"), "Times New Roman")
        rFonts.set(qn("w:cs"), "Times New Roman")
    n_font += 1
print("heading runs westernized:", n_font)

# ---------- 2. 图表按「部-序号」重编 ----------
FIG_MAP = {
    "1-1": "1-1", "1-2": "1-2",                      # 两张用户故事地图
    "2-1": "1-3", "2-2": "1-4", "2-3": "1-5",
    "2-4": "1-6", "2-5": "1-7",                      # 五张用例图
    "3-1": "1-8", "3-2": "1-9",
    "3-3": "1-10", "3-4": "1-11",                    # 四张活动图
    "4-1": "1-12",                                    # 域模型
}
TBL_MAP = {
    "1-1": "1-4", "1-2": "1-5",                      # 角色列表、用例列表
    "2-1": "1-7", "2-2": "1-8", "2-3": "1-9",
    "2-4": "1-10", "2-5": "1-11", "2-6": "1-12",
    "2-7": "1-13", "2-8": "1-14", "2-9": "1-15",
    "2-10": "1-16",                                   # 十张用例表
    "2-11": "1-17", "2-12": "1-18",                   # 名词短语、CRC
    "3-1": "1-19",                                    # 非功能需求
}

PAT = re.compile(r"(图|表)\s*(\d+)\s*[-–—]\s*(\d+)")


def renum(m):
    kind, key = m.group(1), "%s-%s" % (m.group(2), m.group(3))
    new = (FIG_MAP if kind == "图" else TBL_MAP).get(key)
    return "%s %s" % (kind, new) if new else m.group(0)


n_renum = 0
for p in doc.paragraphs:
    old = p.text
    if not PAT.search(old):
        continue
    new = PAT.sub(renum, old)
    if new != old:
        set_para_text(p, new)
        n_renum += 1
print("paragraphs renumbered:", n_renum)

# ---------- 3. 给 4 张无题表补题注 ----------
# 克隆已有题注段，保证字号、间距、居中与其它题注完全一致。
model_cap = None
for p in doc.paragraphs:
    if re.match(r"^\s*表\s*1-\d+\s", p.text):
        model_cap = p._p
        break
if model_cap is None:
    raise SystemExit("未找到可克隆的题注段落")

# 编号与上面 TBL_MAP 的映射保持一致：1-1..1-3 在前，1-6 在角色/用例表之后
NEW_CAPS = [
    ("目标用户", "表 1-1 目标用户与核心价值"),
    ("迭代", "表 1-2 迭代规划与主要交付物"),
    ("用户故事", "表 1-3 产品 Backlog 与优先级"),
    ("用例范围", "表 1-6 功能模块划分与微服务映射"),
]

n_cap = 0
for col_key, cap_text in NEW_CAPS:
    target = None
    for tbl in doc.tables:
        if col_key in [c.text.strip() for c in tbl.rows[0].cells]:
            target = tbl._tbl
            break
    if target is None:
        raise SystemExit("未找到表头含「%s」的表格" % col_key)
    new_el = copy.deepcopy(model_cap)
    target.addprevious(new_el)
    set_para_text(Paragraph(new_el, doc), cap_text)
    n_cap += 1
print("captions inserted:", n_cap)

# ---------- 4. 参考资料：编号从 1 重新开始 ----------
def restart_numbering(p_list):
    """这些段落沿用了前面某串列表的 numId，编号会接着往下排（4、5、6…）。
    新建一个引用同一 abstractNum 的 num，并给它 startOverride=1。"""
    from docx.oxml.ns import nsdecls
    numbering = doc.part.numbering_part.element
    first = p_list[0]._p.find(qn("w:pPr")).find(qn("w:numPr"))
    old_id = int(first.find(qn("w:numId")).get(qn("w:val")))
    abs_id = None
    for num in numbering.findall(qn("w:num")):
        if int(num.get(qn("w:numId"))) == old_id:
            abs_id = num.find(qn("w:abstractNumId")).get(qn("w:val"))
            break
    if abs_id is None:
        return 0

    used = [int(n.get(qn("w:numId"))) for n in numbering.findall(qn("w:num"))]
    new_id = max(used) + 1
    num = OxmlElement("w:num")
    num.set(qn("w:numId"), str(new_id))
    a = OxmlElement("w:abstractNumId")
    a.set(qn("w:val"), abs_id)
    num.append(a)
    ov = OxmlElement("w:lvlOverride")
    ov.set(qn("w:ilvl"), "0")
    st = OxmlElement("w:startOverride")
    st.set(qn("w:val"), "1")
    ov.append(st)
    num.append(ov)
    numbering.append(num)

    for p in p_list:
        np = p._p.find(qn("w:pPr")).find(qn("w:numPr"))
        np.find(qn("w:numId")).set(qn("w:val"), str(new_id))
    return len(p_list)


refs = []
in_refs = False
for p in doc.paragraphs:
    if p.text.strip().startswith("4. 参考资料"):
        in_refs = True
        continue
    if in_refs:
        if not p.text.strip():
            continue
        if p.style.name.startswith("Heading"):
            break
        refs.append(p)
if refs:
    n_ref = restart_numbering(refs)
    print("references renumbered from 1:", n_ref)
    # 长条目被两端对齐拉出大空隙，改左对齐
    for p in refs:
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT

# ---------- 5. 目录页 + 部标题 ----------
anchor = None
for p in doc.paragraphs:
    if p.text.strip().startswith("1. 项目概述与迭代规划"):
        anchor = p._p
        break
if anchor is None:
    raise SystemExit("未找到正文起点")

# 封面末段已带一个分页符，目录标题不再加 page_break_before，否则多出空白页
toc_head = make_para("目　　录", 16, bold=True, cn="黑体", ascii_="SimHei",
                     after=24)
toc_blocks = [toc_head._p]

# 目录条目照模板列 5 个部分；本册只交第一部，其余标注后续分册。
# 页码与页脚一致：封面 1、目录 2，正文从 3 开始。
TOC_ITEMS = [
    ("1  软件需求规格说明书", "3"),
    ("2  设计规格说明书", "—"),
    ("3  系统原型构建与实现", "—"),
    ("4  测试报告", "—"),
    ("5  实验总结", "—"),
]
for title, page in TOC_ITEMS:
    p = make_para("", 12, align=WD_ALIGN_PARAGRAPH.LEFT, before=8, after=8)
    p.paragraph_format.tab_stops.add_tab_stop(
        Cm(15.0), WD_TAB_ALIGNMENT.RIGHT, WD_TAB_LEADER.DOTS)
    p.runs[0].text = "%s\t%s" % (title, page)
    toc_blocks.append(p._p)

p = make_para("（本册为实验报告第一部分，其余部分为后续分册；"
              "页码为第一部内页码。）", 10.5, before=24, after=0)

part_title = make_para("一、需求规格说明书", 18, bold=True, cn="黑体",
                       ascii_="SimHei", after=18)
part_title.paragraph_format.page_break_before = True

for el in toc_blocks:
    move_before(el, anchor)
move_before(p._p, anchor)
move_before(part_title._p, anchor)
print("toc blocks:", len(toc_blocks), "+ note + part title")

doc.save(PATH)
print("saved:", PATH)
