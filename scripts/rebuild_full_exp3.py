# -*- coding: utf-8 -*-
"""用最新的实验三分册内容替换合订本的第三部分。

分册标题层级比合订本低一级（分册 H2/H3/H4 → 合订本 H3/H4/H5），
因此复制时整体降一级，H1「第三部分」保持不动。
"""
import io
import copy
import sys
import os
import docx
from docx.oxml.ns import qn

FULL = 'reports/在线商城系统_软件工程实验报告_完整版.docx'
SEP = 'reports/实验三_原型构建与实现.docx'
DRY = '--dry' in sys.argv

full = docx.Document(FULL)
sep = docx.Document(SEP)

# 分册正文起点（跳过 14 段封面）
body_start = None
for i, p in enumerate(sep.paragraphs):
    if p.style.name.startswith('Heading'):
        body_start = i
        break
print(f'分册正文起点: 段 {body_start}  "{sep.paragraphs[body_start].text.strip()[:30]}"')

# 合订本第三部分范围
h1_idx = h1_end = None
for i, p in enumerate(full.paragraphs):
    if p.style.name == 'Heading 1' and '第三部分' in p.text:
        h1_idx = i
    elif h1_idx is not None and p.style.name == 'Heading 1' and i > h1_idx:
        h1_end = i
        break
if h1_end is None:
    h1_end = len(full.paragraphs)
print(f'合订本第三部分: H1 在 {h1_idx}，下一 H1 在 {h1_end}')

# 分册样式 → 合订本样式（降一级）
def find_style(doc, style_id):
    for st in doc.styles:
        if st.style_id == style_id:
            return st
    return None

LEVEL_MAP = {
    'Heading 2': 'Heading3',
    'Heading 3': 'Heading4',
    'Heading 4': 'Heading5',
}

# 删除合订本第三部分正文（保留 H1 段）
body = full.element.body
children = list(body)
h1_el = full.paragraphs[h1_idx]._p
end_el = full.paragraphs[h1_end]._p if h1_end < len(full.paragraphs) else None
i1 = children.index(h1_el)
i2 = children.index(end_el) if end_el is not None else len(children)
to_delete = children[i1 + 1:i2]
print(f'待删除旧块: {len(to_delete)} 个')

if DRY:
    print('DRY - 不执行')
    sys.exit(0)

for el in to_delete:
    body.remove(el)

# 插入分册正文
sep_children = list(sep.element.body)
sep_start_el = sep.paragraphs[body_start]._p
j = sep_children.index(sep_start_el)
blocks = sep_children[j:]

inserted = 0
img_count = 0
for block in blocks:
    new = copy.deepcopy(block)
    # 降标题级别
    for pPr in new.findall('.//' + qn('w:pStyle')):
        sid = pPr.get(qn('w:val'))
        # 分册 styleId 形如 Heading2；映射到合订本 Heading3
        if sid in ('Heading2', 'Heading3', 'Heading4'):
            pPr.set(qn('w:val'), 'Heading' + str(int(sid[-1]) + 1))
    # 重建图片关系
    for blip in new.findall('.//' + qn('a:blip')):
        rid = blip.get(qn('r:embed'))
        if not rid:
            continue
        blob = sep.part.related_parts[rid].blob
        new_rid, _ = full.part.get_or_add_image(io.BytesIO(blob))
        blip.set(qn('r:embed'), new_rid)
        img_count += 1
    end_el.addprevious(new) if end_el is not None else body.append(new)
    inserted += 1

print(f'已插入 {inserted} 块，图片关系 {img_count} 个')
full.save(FULL)
print('saved')
