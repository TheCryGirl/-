# -*- coding: utf-8 -*-
"""从 surefire 报告统计各模块测试用例数。"""
import glob
import os
import xml.etree.ElementTree as ET

per = {}
total = [0, 0, 0, 0]

for f in glob.glob('src/*/target/surefire-reports/TEST-*.xml'):
    parts = f.replace('\\', '/').split('/')
    mod = parts[1]
    r = ET.parse(f).getroot()
    t = int(r.get('tests', 0))
    fa = int(r.get('failures', 0))
    er = int(r.get('errors', 0))
    sk = int(r.get('skipped', 0))
    per.setdefault(mod, [0, 0, 0, 0])
    per[mod][0] += t
    per[mod][1] += fa
    per[mod][2] += er
    per[mod][3] += sk
    total[0] += t
    total[1] += fa
    total[2] += er
    total[3] += sk

print('模块              用例  失败  错误  跳过')
for m in sorted(per):
    v = per[m]
    print('  %-16s %4d  %4d  %4d  %4d' % (m, v[0], v[1], v[2], v[3]))
print('  %-16s %4d  %4d  %4d  %4d' % ('合计', total[0], total[1], total[2], total[3]))
