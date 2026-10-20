# -*- coding: utf-8 -*-
"""对 office_write_docx 生成的报告做版式后处理：
1) 生成封面并删除原始开头
2) 图片居中 + 限制最大宽高（防止超页）
3) 图题 / 表题居中、取消首行缩进
4) 表格：三线表边框、按内容分配列宽、表内字号五号、表头跨页重复
5) 页脚页码（— N —）
"""
import sys
from docx import Document
from docx.shared import Pt, Mm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

PATH = sys.argv[1]
doc = Document(PATH)
body = doc.element.body

# ---------- 基础工具 ----------
def set_run_font(run, cn="宋体", ascii_="Times New Roman", size=None,
                 bold=None, underline=None, color=None):
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    if underline is not None:
        run.font.underline = underline
    if color is not None:
        run.font.color.rgb = RGBColor.from_string(color)
    run.font.name = ascii_
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn('w:rFonts'))
    if rFonts is None:
        rFonts = OxmlElement('w:rFonts')
        rPr.append(rFonts)
    rFonts.set(qn('w:ascii'), ascii_)
    rFonts.set(qn('w:hAnsi'), ascii_)
    rFonts.set(qn('w:eastAsia'), cn)


def para_format(p, align=None, indent_chars=None, space_before=0,
                space_after=0, line_spacing=None):
    pf = p.paragraph_format
    if align is not None:
        p.alignment = align
    if indent_chars is not None:
        pPr = p._p.get_or_add_pPr()
        ind = pPr.find(qn('w:ind'))
        if ind is None:
            ind = OxmlElement('w:ind')
            pPr.append(ind)
        ind.set(qn('w:firstLineChars'), str(int(indent_chars * 100)))
        ind.set(qn('w:firstLine'), str(int(indent_chars * 240)))
    pf.space_before = Pt(space_before)
    pf.space_after = Pt(space_after)
    if line_spacing is not None:
        pf.line_spacing = line_spacing


# ---------- 1. 找到正文起点，删除其前的所有元素 ----------
anchor = None
for p in doc.paragraphs:
    if p.text.strip().startswith("1 项目概述与迭代规划"):
        anchor = p._p
        break
if anchor is None:
    raise SystemExit("未找到正文起点")

removed = 0
for child in list(body):
    if child is anchor:
        break
    if child.tag == qn('w:p') or child.tag == qn('w:tbl'):
        body.remove(child)
        removed += 1
print("removed leading blocks:", removed)

# ---------- 2. 构造封面 ----------
cover = []

def cov_para(text_runs, align=WD_ALIGN_PARAGRAPH.CENTER, size=12,
             before=0, after=0, cn="宋体", ascii_="Times New Roman",
             bold=False, line=1.5):
    p = doc.add_paragraph()
    para_format(p, align=align, indent_chars=0, space_before=before,
                space_after=after, line_spacing=line)
    for t, kw in text_runs:
        r = p.add_run(t)
        set_run_font(r, cn=kw.get('cn', cn), ascii_=kw.get('ascii', ascii_),
                     size=kw.get('size', size), bold=kw.get('bold', bold),
                     underline=kw.get('underline'))
    cover.append(p._p)
    return p

# 顶部留白
cov_para([("", {})], after=0)

cov_para([("河北工业大学", {"size": 26, "cn": "黑体", "ascii": "SimHei", "bold": True})], after=8)
cov_para([("软件工程课程实验", {"size": 26, "cn": "黑体", "ascii": "SimHei", "bold": True})], after=22)
cov_para([("实  验  报  告", {"size": 34, "cn": "黑体", "ascii": "SimHei", "bold": True})], after=12)
cov_para([("（第一部分  需求规格说明书）", {"size": 13, "cn": "楷体", "ascii": "Times New Roman"})], after=38)

cov_para([("题目：", {"size": 16, "cn": "黑体", "ascii": "SimHei"}),
          ("云原生微服务架构实践——在线商城系统",
           {"size": 16, "cn": "黑体", "ascii": "SimHei", "bold": True})], after=44)

for label in ("专　　业：", "班　　级：", "组　　　：", "组　　长：",
              "成　　员：", "指导教师：", "完成日期："):
    p = cov_para([(label, {"size": 14}),
                  (" " * 16, {"size": 14, "underline": True})],
                 align=WD_ALIGN_PARAGRAPH.CENTER, after=9, line=1.4)

# 分页符
p = doc.add_paragraph()
para_format(p, indent_chars=0)
run = p.add_run()
br = OxmlElement('w:br')
br.set(qn('w:type'), 'page')
run._element.append(br)
cover.append(p._p)

# 把封面元素整体移动到 body 开头
for el in cover:
    body.remove(el)
for i, el in enumerate(cover):
    body.insert(i, el)
print("cover blocks:", len(cover))

# ---------- 3. 计算可用正文宽 ----------
sec = doc.sections[0]
pg_w = sec.page_width
left = sec.left_margin
right = sec.right_margin
usable_w = pg_w - left - right
# 页脚空间：可用高度
HEAD_FOOT_MARGIN = Mm(17)
usable_h = sec.page_height - sec.top_margin - sec.bottom_margin - Mm(4)
# 表格左右单元格内边距（默认 108 twips ≈ 1.9mm 各一侧）需从表宽中扣除
TBL_W = int(usable_w) - 2 * 108 * 635
print("usable w=%.1fmm h=%.1fmm" % (usable_w / 36000, usable_h / 36000))

# ---------- 4. 图片：居中 + 限制最大宽高 ----------
max_w = int(usable_w)
max_h = int(usable_h * 0.86)   # 为图题留出同页空间，同时尽量放大高瘦图


def para_of(el):
    while el is not None and el.tag != qn('w:p'):
        el = el.getparent()
    return el


def set_pf(p_el, *, keep_next=None, page_break_before=None, center=None):
    pPr = p_el.find(qn('w:pPr'))
    if pPr is None:
        pPr = OxmlElement('w:pPr')
        p_el.insert(0, pPr)
    if keep_next is not None:
        for tag in ('w:keepNext',):
            e = pPr.find(qn(tag))
            if keep_next and e is None:
                pPr.append(OxmlElement(tag))
            elif not keep_next and e is not None:
                pPr.remove(e)
    if page_break_before is not None:
        e = pPr.find(qn('w:pageBreakBefore'))
        if page_break_before and e is None:
            pPr.append(OxmlElement('w:pageBreakBefore'))
        elif not page_break_before and e is not None:
            pPr.remove(e)
    if center:
        jc = pPr.find(qn('w:jc'))
        if jc is None:
            jc = OxmlElement('w:jc')
            pPr.append(jc)
        jc.set(qn('w:val'), 'center')
        ind = pPr.find(qn('w:ind'))
        if ind is not None:
            pPr.remove(ind)
    return pPr


from copy import deepcopy

# w:pgMar 的单位是 twip（1/1440 英寸），不是 EMU。原始文档把上下边距
# 写成了 540000（=375 英寸）这类 EMU 数值，LibreOffice 一直靠钳制才能排版，
# Word 里会直接报"页边距超出纸张"。这里统一按 twip 重写一遍。
TW = 1440.0 / 25.4          # 1mm 对应的 twip 数
NORMAL_MB = int(round(25 * TW))   # 常规上下边距 25mm，与左右 1417 一致
TIGHT_MB = int(round(15 * TW))    # 图独占页时收到 15mm

BASE_SECTPR = deepcopy(doc.sections[0]._sectPr)


def make_sectpr(top_tw, bottom_tw):
    sp = deepcopy(BASE_SECTPR)
    pgMar = sp.find(qn('w:pgMar'))
    if pgMar is not None:
        pgMar.set(qn('w:top'), str(int(top_tw)))
        pgMar.set(qn('w:bottom'), str(int(bottom_tw)))
    return sp


def new_empty_para():
    """分节符必须挂在段落上；这个空段落要压到零高，避免多出一行空白。"""
    p = OxmlElement('w:p')
    pPr = OxmlElement('w:pPr')
    sp = OxmlElement('w:spacing')
    sp.set(qn('w:before'), '0')
    sp.set(qn('w:after'), '0')
    sp.set(qn('w:line'), '20')       # 最小行高
    sp.set(qn('w:lineRule'), 'exact')
    pPr.append(sp)
    p.append(pPr)
    r = OxmlElement('w:r')
    rPr = OxmlElement('w:rPr')
    sz = OxmlElement('w:sz')
    sz.set(qn('w:val'), '2')
    rPr.append(sz)
    r.append(rPr)
    p.append(r)
    return p


def end_section_at(p_el, sectPr):
    """把 sectPr 放进该段落的 pPr：该段落是这一节的最后一段，
    sectPr 描述的是“到此处为止”这一节。"""
    pPr = p_el.find(qn('w:pPr'))
    if pPr is None:
        pPr = OxmlElement('w:pPr')
        p_el.insert(0, pPr)
    for e in pPr.findall(qn('w:sectPr')):
        pPr.remove(e)
    pPr.append(sectPr)


# 正文各节统一 25mm 上下边距（原始文档把这里写成了 EMU 数值，须重写）
for sp in doc.element.body.findall('.//' + qn('w:sectPr')):
    pgMar = sp.find(qn('w:pgMar'))
    if pgMar is not None:
        pgMar.set(qn('w:top'), str(NORMAL_MB))
        pgMar.set(qn('w:bottom'), str(NORMAL_MB))

# 高瘦图（用例图 / 活动图 / 域模型）独占一页：从新页开始、按整页高度铺满。
# 高度留出余量，让"图 + 图题 + 紧随其后的解说段"落在同一页——
# 否则图占满整页后解说被挤到下一页，会留下只有几行字的准空白页。
FIG_MAX_H = int(usable_h * 0.78)

n_img = 0
fig_pages = 0


def heading_above(el):
    """图片正上方若只有带 keepNext 的标题（可能不止一级），返回最上面那个。
    把一个整页大图的 pageBreakBefore 写在图上时，LibreOffice 不认标题的
    keepNext，标题会单独留在上一页页尾，下一页开头就是图，中间留一大片白。
    所以分页点要挪到标题上。"""
    anchor = el
    prev = el.getprevious()
    while prev is not None and prev.tag == qn('w:p'):
        pPr = prev.find(qn('w:pPr'))
        if pPr is None or pPr.find(qn('w:keepNext')) is None:
            break
        if not ''.join(prev.itertext()).strip():
            break
        anchor = prev
        prev = prev.getprevious()
    return anchor


for shape in doc.inline_shapes:
    w, h = shape.width, shape.height
    el = para_of(shape._inline.getparent())
    is_tall = float(h) / float(w) > 1.15
    if is_tall and el is not None:
        ratio = min(max_w / float(w), FIG_MAX_H / float(h))
        shape.width, shape.height = int(w * ratio), int(h * ratio)
        anchor = heading_above(el)
        set_pf(el, center=True, page_break_before=False)
        set_pf(anchor, page_break_before=True)
        fig_pages += 1
        n_img += 1
        continue
    ratio = min(max_w / float(w), max_h / float(h))
    shape.width, shape.height = int(w * ratio), int(h * ratio)
    n_img += 1
    if el is not None:
        set_pf(el, center=True)
print("images resized:", n_img, " figure-only pages:", fig_pages)

# ---------- 5. 图题 / 表题居中 ----------
import re
CAP = re.compile(r'^\s*[图表]\s*\d+\s*[-–—]\s*\d+')
n_cap = 0
for p in doc.paragraphs:
    if CAP.match(p.text):
        para_format(p, align=WD_ALIGN_PARAGRAPH.CENTER, indent_chars=0,
                    space_before=3, space_after=6, line_spacing=1.15)
        # 表题在表格上方，须与表格同页；图题在图片下方、后接正文，
        # 若也 keep_with_next 会把正文一起拖到下一页，造成大面积空白。
        p.paragraph_format.keep_with_next = p.text.strip().startswith('表')
        n_cap += 1
print("captions centered:", n_cap)

# ---------- 5b. 标题与后续内容不分页 ----------
HEAD = re.compile(r'^\s*(\d+(\.\d+)*)\s+\S')
n_head = 0
paras = doc.paragraphs
for i, p in enumerate(paras):
    txt = p.text.strip()
    if not txt:
        continue
    st = (p.style.name or '')
    is_head = st.startswith('Heading') or bool(HEAD.match(txt))
    if not is_head:
        continue
    # 短标题（<=40 字）才可能是标题，避免误伤正文中以数字开头的句子
    if len(txt) > 40:
        continue
    p.paragraph_format.keep_with_next = True
    n_head += 1
    # 图题前若紧跟图片段落，跳过
print("headings keep_with_next:", n_head)

# ---------- 5c. 图片段落：与图题保持同页 ----------
for i, p in enumerate(paras):
    if not p._p.findall('.//' + qn('w:drawing')):
        continue
    p.paragraph_format.keep_with_next = True

# ---------- 6. 表格处理 ----------
def disp_len(s):
    return sum(2 if ord(c) > 127 else 1 for c in s)


def set_cell_borders(cell, top=None, bottom=None, left=None, right=None):
    tcPr = cell._tc.get_or_add_tcPr()
    borders = tcPr.find(qn('w:tcBorders'))
    if borders is None:
        borders = OxmlElement('w:tcBorders')
        tcPr.append(borders)
    for tag, spec in (('w:top', top), ('w:bottom', bottom),
                      ('w:left', left), ('w:right', right)):
        if spec is None:
            continue
        el = borders.find(qn(tag))
        if el is None:
            el = OxmlElement(tag)
            borders.append(el)
        for k, v in spec.items():
            el.set(qn('w:' + k), str(v))


NONE = {"val": "none", "sz": 0, "space": 0, "color": "auto"}

for t_idx, table in enumerate(doc.tables, start=1):
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False

    # 6.1 列宽：先算不可断词的最小宽度，再按内容长度分配余量
    rows = table.rows
    ncol = len(table.columns)
    TOKEN = re.compile(r'[A-Za-z0-9_\-\+\./:]+')

    def min_token_len(txt):
        """最长不可断词的显示宽度（汉字可任意断行，按 1 计）"""
        best = 1
        for tok in TOKEN.findall(txt):
            best = max(best, len(tok))
        return best

    floors, desired = [], []
    for ci in range(ncol):
        mx_tok = 2
        mx_all = 0
        total = 0
        cnt = 0
        for row in rows:
            try:
                txt = row.cells[ci].text.strip()
            except IndexError:
                continue
            mx_tok = max(mx_tok, min_token_len(txt))
            L = disp_len(txt)
            mx_all = max(mx_all, L)
            total += L
            cnt += 1
        avg = total / max(cnt, 1)
        desired.append(max(min(mx_all, 40) * 0.6 + min(avg, 24) * 0.4, 3.0))
        # 一列至少容纳最长词 + 少量余量（一个汉字宽）
        floors.append(mx_tok + 2.2)

    # 1 个汉字（10.5pt 字号）≈ 133350 EMU；1 个西文字符 ≈ 0.5 汉字宽
    EM = 133350.0
    if sum(floors) > 0.80 * (TBL_W / EM):
        k = 0.80 * (TBL_W / EM) / sum(floors)
        floors = [f * k for f in floors]
    floor_px = [f * EM for f in floors]
    rest = max(TBL_W - sum(floor_px), 0)

    dsum = sum(desired)
    extra = [rest * d / dsum for d in desired]
    widths = [int(f + e) for f, e in zip(floor_px, extra)]
    diff = TBL_W - sum(widths)
    widths[-1] += diff
    # 最后一列若被压成负数，从最大列挪
    if widths[-1] < int(floor_px[-1]):
        need = int(floor_px[-1]) - widths[-1]
        bi = max(range(ncol), key=lambda i: widths[i])
        widths[bi] -= need
        widths[-1] += need

    # 6.2 tblLayout = fixed，tblW = 可用宽
    tblPr = table._tbl.tblPr
    layout = tblPr.find(qn('w:tblLayout'))
    if layout is None:
        layout = OxmlElement('w:tblLayout')
        tblPr.append(layout)
    layout.set(qn('w:type'), 'fixed')
    tblW = tblPr.find(qn('w:tblW'))
    if tblW is None:
        tblW = OxmlElement('w:tblW')
        tblPr.append(tblW)
    tblW.set(qn('w:type'), 'dxa')
    tblW.set(qn('w:w'), str(int(TBL_W / 635)))

    # 6.3 三线表：整体上下粗线，无竖线、无内部横线
    old = tblPr.find(qn('w:tblBorders'))
    if old is not None:
        tblPr.remove(old)
    tb = OxmlElement('w:tblBorders')
    for tag, sz in (('w:top', 12), ('w:bottom', 12),
                    ('w:left', 0), ('w:right', 0),
                    ('w:insideH', 0), ('w:insideV', 0)):
        el = OxmlElement(tag)
        if sz == 0:
            el.set(qn('w:val'), 'none')
            el.set(qn('w:sz'), '0')
        else:
            el.set(qn('w:val'), 'single')
            el.set(qn('w:sz'), str(sz))
            el.set(qn('w:color'), '000000')
        el.set(qn('w:space'), '0')
        tb.append(el)
    tblPr.append(tb)
    # tcBorders 优先级高于 tblBorders，先清掉
    for row in rows:
        for cell in row.cells:
            tcPr = cell._tc.get_or_add_tcPr()
            ob = tcPr.find(qn('w:tcBorders'))
            if ob is not None:
                tcPr.remove(ob)

    # 6.4 grid 列宽 + 单元格宽
    grid = table._tbl.find(qn('w:tblGrid'))
    if grid is not None:
        for gc, w in zip(grid.findall(qn('w:gridCol')), widths):
            gc.set(qn('w:w'), str(int(w / 635)))
    for row in rows:
        for ci, cell in enumerate(row.cells):
            if ci < len(widths):
                cell.width = widths[ci]

    # 6.5 表头行：跨页重复 + 下细线；所有行都禁止在行内断开
    for ri, row in enumerate(rows):
        trPr = row._tr.get_or_add_trPr()
        if trPr.find(qn('w:cantSplit')) is None:
            trPr.append(OxmlElement('w:cantSplit'))
        if ri == 0:
            if trPr.find(qn('w:tblHeader')) is None:
                trPr.append(OxmlElement('w:tblHeader'))
            for cell in row.cells:
                set_cell_borders(cell, bottom={"val": "single", "sz": 6,
                                               "space": 0, "color": "000000"})

    # 页尾只剩「表头 + 一行」很难看：让表头和首行粘在一起。
    # 列少的表（如用例表只有「项目/内容」两列）单行往往很高，把两行一起
    # 绑定会在页尾顶出大片空白，所以只对列多的表绑定首行。
    keep_rows = (0, 1) if ncol >= 4 else (0,)
    for ri in keep_rows:
        if ri >= len(rows):
            continue
        for cell in rows[ri].cells:
            for p in cell.paragraphs:
                p.paragraph_format.keep_with_next = True

    # 6.6 表内文字：五号、单倍行距、段前后 0
    for ri, row in enumerate(rows):
        for ci, cell in enumerate(row.cells):
            centered = ci == 0 and disp_len(table.rows[0].cells[ci].text) <= 8
            for p in cell.paragraphs:
                para_format(p, align=WD_ALIGN_PARAGRAPH.CENTER if centered
                            else WD_ALIGN_PARAGRAPH.LEFT,
                            indent_chars=0, space_before=1.5, space_after=1.5,
                            line_spacing=1.0)
                for run in p.runs:
                    run.font.size = Pt(10.5)
                    if ri == 0:
                        run.bold = True

print("tables processed:", len(doc.tables))

# ---------- 7. 页脚页码 ----------
footer = sec.footer
footer.is_linked_to_previous = False
fp = footer.paragraphs[0] if footer.paragraphs else footer.add_paragraph()
for r in list(fp.runs):
    r._element.getparent().remove(r._element)
fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
para_format(fp, align=WD_ALIGN_PARAGRAPH.CENTER, indent_chars=0)

r1 = fp.add_run("— ")
set_run_font(r1, size=10.5)
fld_begin = OxmlElement('w:fldChar')
fld_begin.set(qn('w:fldCharType'), 'begin')
r2 = fp.add_run()
set_run_font(r2, size=10.5)
r2._element.append(fld_begin)

instr = OxmlElement('w:instrText')
instr.set(qn('xml:space'), 'preserve')
instr.text = ' PAGE '
r3 = fp.add_run()
set_run_font(r3, size=10.5)
r3._element.append(instr)

fld_sep = OxmlElement('w:fldChar')
fld_sep.set(qn('w:fldCharType'), 'separate')
r4 = fp.add_run()
set_run_font(r4, size=10.5)
r4._element.append(fld_sep)

r5 = fp.add_run("1")
set_run_font(r5, size=10.5)

fld_end = OxmlElement('w:fldChar')
fld_end.set(qn('w:fldCharType'), 'end')
r6 = fp.add_run()
set_run_font(r6, size=10.5)
r6._element.append(fld_end)

r7 = fp.add_run(" —")
set_run_font(r7, size=10.5)

doc.save(PATH)
print("saved:", PATH)
