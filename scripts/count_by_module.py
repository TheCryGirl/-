# -*- coding: utf-8 -*-
"""按模块统计各成员的代码行数，核对分工与代码归属是否一致。"""
import subprocess
import os
import re
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

IDENTITY = {
    'guangfuyan99@gmail.com': '燕光福',
    '1125961132@qq.com': '燕光福',
    'kl660616@163.com': '孙嘉鹏',
    'yynqycq@gmail.com': '王瑞',
    '340972330+Nolset886@users.noreply.github.com': '张韬',
    'fgwew053-byte@users.noreply.github.com': '韩睿远',
    'fgwew053@gmail.com': '韩睿远',
}
CODE_EXT = {'.java', '.vue', '.js', '.mjs', '.ts', '.yaml', '.yml',
            '.sql', '.xml', '.html', '.css', '.properties'}

out = subprocess.run(
    ['git', 'log', '--no-merges', '--numstat', '--format=__C__%ae'],
    cwd=ROOT, capture_output=True, text=True, encoding='utf8', errors='replace'
).stdout

mod = defaultdict(lambda: defaultdict(int))
cur = None
for line in out.splitlines():
    if line.startswith('__C__'):
        cur = IDENTITY.get(line[5:].strip())
        continue
    if not cur or not line.strip():
        continue
    parts = line.split('\t')
    if len(parts) != 3:
        continue
    a, d, path = parts
    if not path.startswith('src/'):
        continue
    ext = os.path.splitext(path)[1].lower()
    if ext not in CODE_EXT or a == '-':
        continue
    seg = path.split('/')
    if len(seg) < 2:
        continue
    m = seg[1]
    if m == 'web':
        m = 'web'
    elif m not in ('gateway', 'user-service', 'product-service', 'cart-service',
                   'order-service', 'payment-service', 'docker', 'scripts'):
        m = seg[1] if m else 'other'
    mod[m][cur] += int(a) + (int(d) if d != '-' else 0)

people = ['燕光福', '王瑞', '孙嘉鹏', '张韬', '韩睿远']
mods = ['gateway', 'user-service', 'product-service', 'cart-service',
        'order-service', 'payment-service', 'web', 'docker', 'scripts']

print('模块             ' + ''.join('%9s' % p for p in people) + '      合计')
for m in mods:
    if m not in mod:
        continue
    row = [mod[m].get(p, 0) for p in people]
    if sum(row) == 0:
        continue
    print('%-16s' % m + ''.join('%9d' % v for v in row) + '%10d' % sum(row))

print()
print('各模块的主要作者（占比 > 50%）:')
for m in mods:
    if m not in mod:
        continue
    row = {p: mod[m].get(p, 0) for p in people}
    tot = sum(row.values())
    if tot == 0:
        continue
    top = max(row, key=row.get)
    print('  %-16s %s  %5.1f%%  (%d / %d 行)' % (m, top, 100.0 * row[top] / tot, row[top], tot))
