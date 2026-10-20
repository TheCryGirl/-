# -*- coding: utf-8 -*-
"""把 UML 源渲染为 PNG。
PlantUML 在含中文的路径下会静默不输出，故先复制到纯 ASCII 临时目录渲染，
再拷回 images/。
"""
import os
import shutil
import subprocess
import tempfile

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UML = os.path.join(BASE, "uml")
IMG = os.path.join(BASE, "images")
JAR = os.path.abspath(os.path.join(BASE, "..", "_tools", "plantuml.jar"))

os.makedirs(IMG, exist_ok=True)

tmp = tempfile.mkdtemp(prefix="puml_")
try:
    srcs = [f for f in os.listdir(UML) if f.endswith(".puml")]

    # @startuml <name> 决定输出文件名，两个源共用同一 name 会互相覆盖，
    # 且不会报错——这里提前拦下。
    names = {}
    for f in srcs:
        with open(os.path.join(UML, f), encoding="utf-8") as fh:
            head = fh.readline().strip()
        name = head[len("@startuml"):].strip() if head.startswith("@startuml") else ""
        name = name or os.path.splitext(f)[0]
        names.setdefault(name, []).append(f)
    dup = {k: v for k, v in names.items() if len(v) > 1}
    if dup:
        raise SystemExit("重复的 @startuml 名称（会互相覆盖）: %s" % dup)

    for f in srcs:
        shutil.copy2(os.path.join(UML, f), os.path.join(tmp, f))

    cmd = [
        "java", "-Dfile.encoding=UTF-8", "-jar", JAR,
        "-tpng", "-charset", "UTF-8",
    ] + srcs
    r = subprocess.run(cmd, cwd=tmp, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print("STDERR:", r.stderr[:3000])

    produced = {f for f in os.listdir(tmp) if f.lower().endswith(".png")}
    ok, missing = 0, []
    for f in srcs:
        stem = os.path.splitext(f)[0]
        png = stem + ".png"
        if png in produced:
            shutil.copy2(os.path.join(tmp, png), os.path.join(IMG, png))
            size = os.path.getsize(os.path.join(IMG, png))
            print("ok   %-40s %8d B" % (png, size))
            ok += 1
        else:
            missing.append(f)
    if missing:
        print("MISSING:", missing)
    print("rendered %d / %d" % (ok, len(srcs)))
finally:
    shutil.rmtree(tmp, ignore_errors=True)
