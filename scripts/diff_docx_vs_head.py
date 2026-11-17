# -*- coding: utf-8 -*-
"""对比工作区 docx 与 HEAD 版本的内容差异（文字、图片数、表格数）。"""
import io
import subprocess
import zipfile
import docx
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

FILES = [
    '实验一_需求规格说明书.docx',
    '实验二_设计规格说明书.docx',
    '实验三_原型构建与实现.docx',
    '实验四_测试与部署.docx',
    '第五部分_实验总结.docx',
    '在线商城系统_软件工程实验报告_完整版.docx',
]


def head_bytes(path_in_repo):
    return subprocess.run(['git', 'show', 'HEAD:' + path_in_repo],
                          cwd=ROOT, capture_output=True).stdout


def stats(stream):
    d = docx.Document(stream)
    text = '\n'.join(p.text for p in d.paragraphs)
    return len(d.paragraphs), len(d.tables), len(d.inline_shapes), len(text)


print('%-42s %-22s %-22s %s' % ('文件', 'HEAD (段/表/图/字)', '当前 (段/表/图/字)', '结论'))
for name in FILES:
    rel = 'reports/' + name
    cur_path = os.path.join(ROOT, 'reports', name)
    if not os.path.exists(cur_path):
        print('%-42s 缺失' % name)
        continue
    a = head_bytes(rel)
    b = open(cur_path, 'rb').read()
    if a == b:
        print('%-42s 完全相同' % name)
        continue
    try:
        sa = stats(io.BytesIO(a))
        sb = stats(io.BytesIO(b))
    except Exception as e:
        print('%-42s 解析失败 %s' % (name, e))
        continue
    same = sa == sb
    print('%-42s %-22s %-22s %s' % (
        name,
        '%d/%d/%d/%d' % sa,
        '%d/%d/%d/%d' % sb,
        '结构相同（仅重存）' if same else '内容不同'))
