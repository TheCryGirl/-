# -*- coding: utf-8 -*-
"""扫全仓 Markdown，检查表格各行列数是否一致（跳过代码块）。"""
import glob
import re

def cells(line):
    """数单元格数：先去掉转义的 \\| 和反引号内被转义的管道，再数未转义的 |。"""
    s = line.replace('\\|', '')
    return s.count('|') - 1


bad = 0
for f in glob.glob('**/*.md', recursive=True):
    if any(x in f for x in ('node_modules', 'target', '.git')):
        continue
    lines = open(f, encoding='utf8').read().splitlines()
    i = 0
    in_code = False
    while i < len(lines):
        ln = lines[i]
        if ln.strip().startswith('```'):
            in_code = not in_code
            i += 1
            continue
        if not in_code and ln.strip().startswith('|') and i + 1 < len(lines) \
                and re.match(r'^\s*\|[\s\-:|]+\|\s*$', lines[i + 1]):
            ncol = cells(ln)
            j = i + 2
            while j < len(lines) and lines[j].strip().startswith('|'):
                c = cells(lines[j])
                if c != ncol:
                    print('  列数不一致 %s:%d  表头 %d 列，本行 %d 列' % (f, j + 1, ncol, c))
                    bad += 1
                j += 1
            i = j
            continue
        i += 1

print('表格问题: %d 处' % bad)
