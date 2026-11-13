# -*- coding: utf-8 -*-
"""重画图 5-2：左图改为「各成员代码工作量」，右图保留开发阶段提交分布。

口径与 docs/04「Git 身份对照与贡献口径」一致：
- 代码工作量 = src/ 下源文件的新增 + 删除行数，只计普通提交
- 成员身份已合并（燕光福两个邮箱、韩睿远两个作者名）
"""
import os
import subprocess
from collections import defaultdict

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib import rcParams

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
rcParams['font.sans-serif'] = ['Microsoft YaHei']
rcParams['axes.unicode_minus'] = False
rcParams['figure.dpi'] = 150

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

# 代码工作量
out = subprocess.run(['git', 'log', '--no-merges', '--numstat', '--format=__C__%ae'],
                     cwd=ROOT, capture_output=True, text=True, encoding='utf8', errors='replace').stdout
code = defaultdict(int)
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
    if os.path.splitext(path)[1].lower() not in CODE_EXT or a == '-':
        continue
    code[cur] += int(a) + (int(d) if d != '-' else 0)

people = ['燕光福', '孙嘉鹏', '张韬', '王瑞', '韩睿远']
vals = [code[p] for p in people]
total = sum(vals)

# 阶段提交分布（按日）
out2 = subprocess.run(['git', 'log', '--format=%ad', '--date=format:%m-%d'],
                      cwd=ROOT, capture_output=True, text=True, encoding='utf8', errors='replace').stdout
perday = defaultdict(int)
for line in out2.splitlines():
    if line.strip():
        perday[line.strip()] += 1
order = sorted(perday, key=lambda x: (int(x[:2]), int(x[3:])))
# 只取到 11-06 交付收尾（与正文口径一致）
order = [d for d in order if (int(d[:2]), int(d[3:])) <= (11, 6)]

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(18, 7), gridspec_kw={'width_ratios': [1, 1.15]})

colors = ['#2f6db5', '#3a9e6f', '#c9892f', '#7a63ad', '#c4623f']
ypos = range(len(people))[::-1]
bars = ax1.barh(list(ypos), vals, color=colors, height=0.62)
for y, v in zip(ypos, vals):
    ax1.text(v + total * 0.012, y, '%d 行（%.1f%%）' % (v, 100.0 * v / total),
             va='center', fontsize=11)
ax1.set_yticks(list(ypos))
ax1.set_yticklabels(people, fontsize=12)
ax1.set_xlabel('代码工作量（新增 + 删除行数）', fontsize=12)
ax1.set_title('各成员代码工作量（合计 %d 行）' % total, fontsize=14, pad=14)
ax1.set_xlim(0, max(vals) * 1.32)
ax1.grid(axis='x', ls='--', alpha=0.35)
ax1.set_axisbelow(True)
for s in ('top', 'right'):
    ax1.spines[s].set_visible(False)

xs = order
ys = [perday[d] for d in order]
ax2.plot(range(len(xs)), ys, marker='o', color='#2f6db5', lw=2,
         markerfacecolor='white', markeredgewidth=2, ms=7)
ax2.fill_between(range(len(xs)), ys, color='#2f6db5', alpha=0.12)
for i, v in enumerate(ys):
    ax2.text(i, v + max(ys) * 0.035, str(v), ha='center', fontsize=9)
ax2.set_xticks(range(len(xs)))
ax2.set_xticklabels(xs, rotation=0, fontsize=10)
ax2.set_ylabel('当日提交数', fontsize=12)
ax2.set_title('开发阶段提交分布（2026-10-20 ~ 11-06）', fontsize=14, pad=14)
ax2.grid(axis='y', ls='--', alpha=0.35)
ax2.set_axisbelow(True)
ax2.set_ylim(0, max(ys) * 1.18)
for s in ('top', 'right'):
    ax2.spines[s].set_visible(False)

fig.tight_layout()
outp = os.path.join(ROOT, 'design', 'images', 'fig5-2_git_stats.png')
fig.savefig(outp, bbox_inches='tight', facecolor='white')
print('saved', outp)
print()
for p, v in zip(people, vals):
    print('  %-6s %6d 行  %5.1f%%' % (p, v, 100.0 * v / total))
