# -*- coding: utf-8 -*-
"""按代码工作量统计各成员贡献（合并同一人的多个 Git 身份）。

口径：
- 只算普通提交（--no-merges），合并提交不带内容差异，计入会重复。
- 代码文件：src/ 下 .java/.vue/.js/.mjs/.ts/.yaml/.yml/.sql/.xml/.html/.css
- 非代码：docs/ reports/ design/ 及根目录 md 等
- 二进制（docx/png/jpg）git 只报 "-"，不计行数，单独统计文件数。
"""
import subprocess
import os
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 合并后的身份：邮箱/作者名 -> 成员
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
CODE_PREFIX = 'src/'
BIN_EXT = {'.docx', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.xlsx', '.jar', '.zip'}


def run(args):
    return subprocess.run(args, cwd=ROOT, capture_output=True, text=True, encoding='utf8', errors='replace').stdout


# 逐文件统计：git log --numstat --no-merges --format=...
out = run(['git', 'log', '--no-merges', '--numstat', '--format=__C__%ae'])

code = defaultdict(lambda: {'add': 0, 'del': 0, 'files': 0})
other = defaultdict(lambda: {'add': 0, 'del': 0, 'files': 0})
binary = defaultdict(lambda: 0)
unknown = defaultdict(int)
commits = defaultdict(int)

cur = None
for line in out.splitlines():
    if line.startswith('__C__'):
        email = line[5:].strip()
        cur = IDENTITY.get(email)
        if cur is None:
            unknown[email] += 1
            cur = None
        else:
            commits[cur] += 1
        continue
    if not cur or not line.strip():
        continue
    parts = line.split('\t')
    if len(parts) != 3:
        continue
    a, d, path = parts
    ext = os.path.splitext(path)[1].lower()
    if ext in BIN_EXT:
        binary[cur] += 1
        continue
    is_code = path.startswith(CODE_PREFIX) and ext in CODE_EXT
    bucket = code if is_code else other
    bucket[cur]['files'] += 1
    if a != '-':
        bucket[cur]['add'] += int(a)
    if d != '-':
        bucket[cur]['del'] += int(d)

people = ['燕光福', '王瑞', '孙嘉鹏', '张韬', '韩睿远']
print('成员        提交数   代码 新增   删除   净增   代码文件   文档行数   二进制文件')
for p in people:
    c = code[p]
    o = other[p]
    print('  %-8s %5d  %8d %6d %6d %8d %10d %10d' % (
        p, commits[p], c['add'], c['del'], c['add'] - c['del'], c['files'],
        o['add'] + o['del'], binary[p]))

tc = sum(code[p]['add'] + code[p]['del'] for p in people)
print()
print('代码总行数（新增+删除）:', tc)
print()
print('占比（按代码行数）:')
for p in people:
    v = code[p]['add'] + code[p]['del']
    print('  %-8s %6.1f%%' % (p, 100.0 * v / tc if tc else 0))

if unknown:
    print()
    print('未识别的邮箱:', dict(unknown))
