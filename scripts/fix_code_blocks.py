# -*- coding: utf-8 -*-
"""修正实验三 docx 中代码块的段落格式。

规范（_repo/AGENTS.md 4.8）：
  西文 Consolas 10.5pt、中文宋体 10.5pt；左对齐、无首行缩进、单倍行距、
  段前后各 6pt；浅灰底 F5F5F5。
"""
import sys
import docx
from docx.shared import Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

DOCX = sys.argv[1]
DRY = '--dry' in sys.argv

CODE_MARKERS = [
    '<update id=', 'idempotencyMapper', 'signatureService', 'UPDATE stock_reservation',
    'isWhiteListed(request', 'cd src', 'pnpm install', 'pnpm dev', '-- 第一步',
]


def is_code_para(p):
    text = p.text or ''
    has_marker = any(m in text for m in CODE_MARKERS)
    # 段落内有手工换行符 = 多行代码
    br = len(p._p.findall('.//' + qn('w:br')))
    if br >= 2:
        return True
    return has_marker and br >= 1


def set_shading(p, fill='F5F5F5'):
    pPr = p._p.get_or_add_pPr()
    # 移除已有 shd
    for old in pPr.findall(qn('w:shd')):
        pPr.remove(old)
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), fill)
    pPr.append(shd)


def format_code_paragraph(p):
    pf = p.paragraph_format
    pf.alignment = WD_ALIGN_PARAGRAPH.LEFT
    pf.first_line_indent = Pt(0)
    pf.left_indent = Pt(0)
    pf.right_indent = Pt(0)
    pf.space_before = Pt(6)
    pf.space_after = Pt(6)
    pf.line_spacing_rule = WD_LINE_SPACING.SINGLE
    set_shading(p)

    for run in p.runs:
        run.font.name = 'Consolas'
        run.font.size = Pt(10.5)
        rPr = run._element.get_or_add_rPr()
        rFonts = rPr.find(qn('w:rFonts'))
        if rFonts is None:
            rFonts = OxmlElement('w:rFonts')
            rPr.insert(0, rFonts)
        rFonts.set(qn('w:ascii'), 'Consolas')
        rFonts.set(qn('w:hAnsi'), 'Consolas')
        rFonts.set(qn('w:cs'), 'Consolas')
        rFonts.set(qn('w:eastAsia'), '宋体')


d = docx.Document(DOCX)
hits = []
for p in d.paragraphs:
    # 只处理原本被当成代码（含 Consolas cs 字体）的段落
    raw = p._p.xml
    if 'Consolas' not in raw:
        continue
    if is_code_para(p):
        hits.append(p)
        if not DRY:
            format_code_paragraph(p)

print(f'code blocks: {len(hits)}')
for i, p in enumerate(hits, 1):
    print(f'  [{i}] {(p.text or "")[:60].replace(chr(10), " ")}')

if DRY:
    print('DRY RUN - not saved')
else:
    d.save(DOCX)
    print('saved')
