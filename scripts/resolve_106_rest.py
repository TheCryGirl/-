# -*- coding: utf-8 -*-
"""解决 #106 合并剩余冲突：IdGenerator / schema.sql / schema-h2.sql / api/index.js / README。

全部是「两边各加新的方法、表、接口」的并集。
顺带修正 #136 的 DDL 笔误：`COMMENT '分地区运费规则'` 少了等号，MySQL 会报语法错误。
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFLICT = re.compile(r'^<<<<<<< [^\n]*\n(.*?)^=======\n(.*?)^>>>>>>> [^\n]*\n', re.S | re.M)


def resolve(path, fn):
    full = os.path.join(ROOT, path)
    t = io.open(full, encoding='utf8').read()
    blocks = list(CONFLICT.finditer(t))
    if not blocks:
        print('  - %-42s 无冲突' % os.path.basename(path))
        return
    out, pos = [], 0
    for m in blocks:
        out.append(t[pos:m.start()])
        out.append(fn(m.group(1), m.group(2), path))
        pos = m.end()
    out.append(t[pos:])
    t2 = ''.join(out)
    left = sum(1 for ln in t2.splitlines() if ln.startswith(('<<<<<<<', '=======', '>>>>>>>')))
    print('  ✓ %-42s %d 处，残留 %d' % (os.path.basename(path), len(blocks), left))
    if left:
        sys.exit('仍有标记: ' + path)
    io.open(full, 'w', encoding='utf8', newline='').write(t2)


def fn_union(head, theirs, path):
    name = os.path.basename(path)
    if name == 'IdGenerator.java':
        # head 末方法缺闭合括号（下一行的 } 提供），补上后接 theirs
        return head.rstrip('\n') + '\n    }\n\n' + theirs
    if name == 'schema.sql':
        fixed = head.replace("COMMENT '分地区运费规则';", "COMMENT='分地区运费规则';")
        return fixed.rstrip('\n') + '\n\n' + theirs
    if name == 'schema-h2.sql':
        return head.rstrip('\n') + '\n\n' + theirs
    if name == 'index.js':
        return head + theirs
    if name == 'README.md':
        # 买家行取 theirs（含消息功能），卖家行取 head（含 #138 的物流轨迹补录）
        h = head.splitlines()
        t_ = theirs.splitlines()
        buyer = next((x for x in t_ if x.startswith('| 买家')), None)
        seller = next((x for x in h if x.startswith('| 卖家')), None)
        if buyer is None or seller is None:
            sys.exit('README 行匹配失败')
        return buyer + '\n' + seller + '\n'
    sys.exit('未定义: ' + name)


for f in ['src/user-service/src/main/java/com/mall/user/common/IdGenerator.java',
          'src/user-service/src/main/resources/schema.sql',
          'src/user-service/src/test/resources/schema-h2.sql',
          'src/web/src/api/index.js',
          'README.md']:
    resolve(f, fn_union)

print('\n完成')
