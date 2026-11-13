# -*- coding: utf-8 -*-
"""把实验三 docx 中的 9 张界面截图替换为新版截图（保留图题与版式）。"""
import sys
import os
import docx
from docx.oxml.ns import qn
from docx.shared import Cm

DOCX = sys.argv[1]
SHOT_DIR = sys.argv[2]
DRY = '--dry' in sys.argv

# 图题 -> 新截图文件
MAPPING = {
    '图 3-1 商品列表': 'fig3-1_product_list.png',
    '图 3-2 商品详情': 'fig3-2_product_detail.png',
    '图 3-3 购物车': 'fig3-3_cart.png',
    '图 3-4 确认订单': 'fig3-4_order_confirm.png',
    '图 3-5 支付页': 'fig3-5_order_pay.png',
    '图 3-6 支付成功后的订单详情': 'fig3-6_order_detail.png',
    '图 3-7 卖家商品管理': 'fig3-7_seller_products.png',
    '图 3-8 资金对账': 'fig3-8_admin_finance.png',
    '图 3-9 基础风控': 'fig3-9_admin_risk.png',
}

d = docx.Document(DOCX)
paras = d.paragraphs

# 找出每张图所在段落（含 a:blip 的段落）及其后一段图题
img_paras = []
for i, p in enumerate(paras):
    if p._p.findall('.//' + qn('a:blip')):
        cap = paras[i + 1].text.strip() if i + 1 < len(paras) else ''
        img_paras.append((i, p, cap))

print(f'found {len(img_paras)} images')
replaced = 0
for idx, p, cap in img_paras:
    target = None
    for key, fname in MAPPING.items():
        if cap.startswith(key) or key in cap:
            target = fname
            break
    if not target:
        print(f'  [{idx}] no mapping for caption: {cap[:30]}')
        continue

    path = os.path.join(SHOT_DIR, target)
    if not os.path.exists(path):
        print(f'  [{idx}] missing file: {target}')
        continue

    print(f'  [{idx}] {cap[:28]:30s} <- {target}')
    if DRY:
        replaced += 1
        continue

    # 删除段落内所有 drawing，保留段落属性
    for run in list(p.runs):
        for drawing in run._element.findall('.//' + qn('w:drawing')):
            run._element.remove(drawing)
    # 清空残余 run
    for run in list(p.runs):
        p._p.remove(run._element)

    # 插入新图片（宽度沿用正文版心，14.5cm）
    run = p.add_run()
    run.add_picture(path, width=Cm(14.5))
    replaced += 1

print(f'replaced: {replaced}')

if DRY:
    print('DRY RUN - not saved')
else:
    d.save(DOCX)
    print('saved')
