# -*- coding: utf-8 -*-
"""把 #49~#52 五个页面的截图插入实验三 2.4 节，作为图 3-10 ~ 图 3-14。"""
import io
import copy
import sys
import os
import docx
from docx.oxml.ns import qn

DOCX = 'reports/实验三_原型构建与实现.docx'
SHOT = 'reports/_exp3_new_shots'
DRY = '--dry' in sys.argv

INSERTS = [
    ('2.4.2 搜索与分类联动', 'fig3-10_seller_shop.png',  '图 3-10 店铺资料（卖家维护，含头像与营业状态）'),
    ('2.4.2 搜索与分类联动', 'fig3-11_admin_shops.png',  '图 3-11 店铺管理（管理员停用与恢复）'),
    ('2.4.4 收藏与浏览足迹', 'fig3-12_profile.png',      '图 3-12 个人中心（头像上传与默认收货地址）'),
    ('3. 核心代码片段',      'fig3-13_favorites.png',    '图 3-13 我的收藏'),
    ('3. 核心代码片段',      'fig3-14_history.png',      '图 3-14 浏览足迹'),
]

d = docx.Document(DOCX)

# 模板：第一张图片段 + 其后图题段
tpl_img = tpl_cap = None
for i, p in enumerate(d.paragraphs):
    if p._p.findall('.//' + qn('a:blip')):
        tpl_img = p
        tpl_cap = d.paragraphs[i + 1] if i + 1 < len(d.paragraphs) else None
        break
print(f'模板: 图 "{tpl_img.text.strip()[:16]}" 图题 "{tpl_cap.text.strip()[:20]}"')

# 锚点
anchors = {}
for i, p in enumerate(d.paragraphs):
    t = p.text.strip()
    for key in set(k for k, _, _ in INSERTS):
        if t.startswith(key) and key not in anchors:
            anchors[key] = p
print('锚点:', {k: v.text.strip()[:18] for k, v in anchors.items()})
if len(anchors) != len(set(k for k, _, _ in INSERTS)):
    print('锚点不全，退出'); sys.exit(1)

ok = 0
for anchor_key, fname, caption in INSERTS:
    path = os.path.join(SHOT, fname)
    if not os.path.exists(path):
        print(f'缺图: {fname}'); sys.exit(1)
    if DRY:
        print(f'  [{anchor_key}] <- {fname}  "{caption}"')
        ok += 1
        continue

    anchor = anchors[anchor_key]

    # 1) 复制图片段，替换图片关系
    new_img = copy.deepcopy(tpl_img._p)
    with open(path, 'rb') as fh:
        rId, _image = d.part.get_or_add_image(io.BytesIO(fh.read()))
    blip = new_img.find('.//' + qn('a:blip'))
    blip.set(qn('r:embed'), rId)
    # docPr id 需唯一，重新编号
    docPr = new_img.find('.//' + qn('wp:docPr'))
    if docPr is not None:
        docPr.set('id', str(1000 + ok))
    anchor._p.addprevious(new_img)

    # 2) 复制图题段，改文字
    new_cap = copy.deepcopy(tpl_cap._p)
    ts = new_cap.findall('.//' + qn('w:t'))
    if ts:
        ts[0].text = caption
        if not ts[0].get(qn('xml:space')):
            ts[0].set(qn('xml:space'), 'preserve')
        for extra in ts[1:]:
            extra.text = ''
    anchor._p.addprevious(new_cap)

    print(f'  [{anchor_key}] <- {fname}  "{caption}"')
    ok += 1

if DRY:
    print(f'DRY - 共 {ok} 张，未保存')
else:
    d.save(DOCX)
    print(f'saved，插入 {ok} 张')
