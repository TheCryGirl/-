# -*- coding: utf-8 -*-
"""合并 #106 的文档冲突。

三处问题：
1. 顶部与历史表：三方并行各自编号，统一重编为 v1.13
2. 尾部：#106 追加的「十二、站内消息」与 footer 争同一位置，
   且它自己编成「十二」，与 #136/#141/#137 三处已有的「十二」撞号
3. 章节重编：#136 十二、#141 十三、#137 十四、#143 十五、#106 十六，
   同步修正历史表里「详见第十二/十三章」的交叉引用
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
p = os.path.join(ROOT, 'docs', '05-接口契约.md')
t = io.open(p, encoding='utf8').read()

CONFLICT = re.compile(r'^<<<<<<< [^\n]*\n(.*?)^=======\n(.*?)^>>>>>>> [^\n]*\n', re.S | re.M)
blocks = list(CONFLICT.finditer(t))
print('  冲突块数: %d' % len(blocks))
if len(blocks) != 3:
    sys.exit('预期 3 处冲突，实际 %d' % len(blocks))

# ---------- 块1：版本号 ----------
b1_head = blocks[0].group(1)
b1_their = blocks[0].group(2)
assert '版本：' in b1_head and '版本：' in b1_their
new_b1 = '版本：v1.13\n'

# ---------- 块2：修订历史 ----------
b2_head = blocks[1].group(1)
b2_their = blocks[1].group(2)
# theirs 是「| v1.8 | ... #106 |」的形式，重编为 v1.13
row106 = None
for line in b2_their.splitlines():
    if line.startswith('| v1.8 |') and '#106' in line:
        row106 = line.replace('| v1.8 |', '| v1.13 |', 1)
        break
if row106 is None:
    sys.exit('未找到 #106 的历史行')
# head 保留（五条 + 说明），把 #106 那行插在最后一条修订记录之后
head_lines = b2_head.splitlines()
insert_at = None
for i, ln in enumerate(head_lines):
    if ln.startswith('| v1.12 |'):
        insert_at = i + 1
if insert_at is None:
    sys.exit('未找到 v1.12 行')
head_lines.insert(insert_at, row106)
new_b2 = '\n'.join(head_lines) + '\n'

# ---------- 块3：尾章 + footer ----------
b3_head = blocks[2].group(1)      # 「**版本**：v1.11（...）」
b3_their = blocks[2].group(2)     # 版本行 + 整章
# 取出 theirs 里的章节正文（去掉它自己的版本行）
idx = b3_their.find('## ')
if idx < 0:
    sys.exit('theirs 里没有章节标题')
chapter = b3_their[idx:]
# 章节号先置十六
chapter = chapter.replace('## 十二、站内消息（#106）', '## 十六、站内消息（#106）', 1)

FOOTER = ('---\n\n'
          '**文档维护人**：哭女（组长）\n'
          '**最后更新**：2026-11-17\n')
NEW_FOOTER = ('---\n\n'
              '**文档维护人**：哭女（组长）\n'
              '**最后更新**：2026-11-17\n'
              '**版本**：v1.13（含 #135 多店铺合并结算、#136 运费与优惠计价、'
              '#137 支付渠道与对账、#143 售后举证与仲裁、#138 物流与自动收货、'
              '#106 站内消息）\n')

# 组装：把 footer 之前的 --- 也吃掉，改成 章节 + footer
new_b3 = '\n' + chapter.rstrip('\n') + '\n\n' + NEW_FOOTER

# ---------- 应用三块 ----------
out = []
pos = 0
for i, m in enumerate(blocks):
    seg = t[pos:m.start()]
    if i == 2:
        # 去掉紧跟冲突之前的旧 footer（--- / 文档维护人 / 最后更新）
        seg = re.sub(r'---\n\n\*\*文档维护人\*\*：哭女（组长）\n\*\*最后更新\*\*：2026-11-17\n$', '', seg)
    out.append(seg)
    out.append([new_b1, new_b2, new_b3][i])
    pos = m.end()
out.append(t[pos:])
t2 = ''.join(out)

# ---------- 章节重编 ----------
CHAPTER_FIX = [
    ('## 十二、#141 搜索与个性化推荐增量接口', '## 十三、#141 搜索与个性化推荐增量接口'),
    ('## 十二、#137 真实支付渠道、退款异步收口与渠道对账增量接口',
     '## 十四、#137 真实支付渠道、退款异步收口与渠道对账增量接口'),
    ('## 十三、#143 售后举证与平台仲裁增量接口', '## 十五、#143 售后举证与平台仲裁增量接口'),
]
for old, new in CHAPTER_FIX:
    n = t2.count(old)
    print('  章节 %d 处: %s' % (n, old[:24]))
    t2 = t2.replace(old, new)

# ---------- 交叉引用修正 ----------
REF_FIX = [
    ('详见第十二章 | #137 |', '详见第十四章 | #137 |'),
    ('详见第十三章 | #143 |', '详见第十五章 | #143 |'),
]
for old, new in REF_FIX:
    n = t2.count(old)
    print('  交叉引用 %d 处: %s' % (n, old[:20]))
    t2 = t2.replace(old, new)

left = [ln for ln in t2.splitlines() if ln.startswith(('<<<<<<<', '=======', '>>>>>>>'))]
print('  残留冲突标记: %d' % len(left))
if left:
    sys.exit('仍有冲突标记')

io.open(p, 'w', encoding='utf8', newline='').write(t2)
print('saved')
