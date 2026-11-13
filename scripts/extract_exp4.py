# -*- coding: utf-8 -*-
"""从合订本提取「第四部分 测试与部署」为独立分册，并把一级标题改为封面。

做法：
1. 新建空文档
2. 复制合订本的样式定义、页面设置（页边距 3.4/2.5/2.7/2.7）
3. 逐块深拷贝第四部分内容，重建图片关系
4. 在正文前插入封面（沿用分册原封面格式）
5. 把「第四部分  测试与部署」H1 降级，保证标题层级从 1. 引言 开始
"""
import io
import copy
import sys
import docx
from docx.oxml.ns import qn
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH

FULL = 'reports/在线商城系统_软件工程实验报告_完整版.docx'
OUT = 'reports/实验四_测试与部署.docx'
DRY = '--dry' in sys.argv

src = docx.Document(FULL)

# ---- 定位第四部分 ----
start = end = None
for i, p in enumerate(src.paragraphs):
    if p.style.name == 'Heading 1' and '第四部分' in p.text:
        start = i
    elif start is not None and p.style.name == 'Heading 1' and i > start:
        end = i
        break
if end is None:
    end = len(src.paragraphs)
print(f'第四部分: 段落 {start} .. {end-1}')

# ---- 新建文档并继承样式/页面 ----
out = docx.Document()

# 页面设置：照搬合订本
src_sec = src.sections[0]
out_sec = out.sections[0]
out_sec.page_width = src_sec.page_width
out_sec.page_height = src_sec.page_height
out_sec.top_margin = src_sec.top_margin
out_sec.bottom_margin = src_sec.bottom_margin
out_sec.left_margin = src_sec.left_margin
out_sec.right_margin = src_sec.right_margin
out_sec.header_distance = src_sec.header_distance
out_sec.footer_distance = src_sec.footer_distance
print(f'页面: {out_sec.page_width.cm:.2f} x {out_sec.page_height.cm:.2f} cm, '
      f'边距 {out_sec.top_margin.cm:.2f}/{out_sec.bottom_margin.cm:.2f}/'
      f'{out_sec.left_margin.cm:.2f}/{out_sec.right_margin.cm:.2f}')

# 复制样式（段落样式）
from docx.enum.style import WD_STYLE_TYPE
existing = set(s.style_id for s in out.styles)
style_map = {}
for st in src.styles:
    if st.type != WD_STYLE_TYPE.PARAGRAPH:
        continue
    if st.style_id in existing:
        continue
    try:
        new_st = copy.deepcopy(st.element)
        out.styles.element.append(new_st)
        style_map[st.style_id] = st.name
    except Exception as e:
        pass
print(f'复制样式: {len(style_map)} 个')

# ---- 逐块复制正文 ----
body_src = src.element.body
children = list(body_src)
i_start = children.index(src.paragraphs[start]._p)
i_end = children.index(src.paragraphs[end]._p) if end < len(src.paragraphs) else len(children)
blocks = children[i_start:i_end]
print(f'待复制块级元素: {len(blocks)}')

out_body = out.element.body
# 清空默认空段落
for p in list(out.paragraphs):
    p._p.getparent().remove(p._p)

copied = 0
for block in blocks:
    new = copy.deepcopy(block)
    for blip in new.findall('.//' + qn('a:blip')):
        rid = blip.get(qn('r:embed'))
        if not rid:
            continue
        blob = src.part.related_parts[rid].blob
        new_rid, _ = out.part.get_or_add_image(io.BytesIO(blob))
        blip.set(qn('r:embed'), new_rid)
    out_body.append(new)
    copied += 1
print(f'已复制 {copied} 个块')

if DRY:
    print('DRY - 不保存')
    sys.exit(0)

out.save(OUT)
print('saved', OUT)
