#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""版本号管理：读/写 version.properties，供 build.gradle.kts 与 publish_ota.py 共用。

用法：
    python bump_build.py                 # 只 build +1（UI 微调、修 bug 都用这个）
    python bump_build.py --show          # 打印当前版本，不改
    python bump_build.py --marketing 5.4.0   # 换大版本，build 归 1

versionName 拼装规则（与 app/build.gradle.kts 里的 `fullVersionName()` 保持一致）：
    工具箱开发版<marketing> (build <build>)
"""

import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROPS = os.path.join(HERE, "version.properties")

PREFIX = "工具箱开发版"


def read_props(path=PROPS):
    data = {}
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            data[k.strip()] = v.strip()
    return data


def write_props(marketing, build, path=PROPS):
    """保留注释与原顺序，只替换两项的值。"""
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()
    out = []
    seen_m = seen_b = False
    for line in lines:
        s = line.strip()
        if s.startswith("marketing="):
            out.append("marketing=%s\n" % marketing)
            seen_m = True
        elif s.startswith("build="):
            out.append("build=%s\n" % build)
            seen_b = True
        else:
            out.append(line)
    if not seen_m:
        out.append("marketing=%s\n" % marketing)
    if not seen_b:
        out.append("build=%s\n" % build)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.writelines(out)


def full_version_name(marketing, build):
    return "%s%s (build %s)" % (PREFIX, marketing, build)


def main():
    ap = argparse.ArgumentParser(description="版本号管理（version.properties）")
    ap.add_argument("--show", action="store_true", help="只打印当前版本")
    ap.add_argument("--marketing", help="设置大版本号，例如 5.4.0（同时把 build 归 1）")
    ap.add_argument("--build", type=int, help="直接指定 build 号")
    args = ap.parse_args()

    props = read_props()
    marketing = props.get("marketing", "0.0.0")
    build = int(props.get("build", "1"))

    if args.show:
        print("marketing : %s" % marketing)
        print("build     : %d" % build)
        print("versionName: %s" % full_version_name(marketing, build))
        return 0

    if args.marketing:
        marketing = args.marketing.strip()
        build = 1
    elif args.build is not None:
        build = args.build
    else:
        build += 1

    write_props(marketing, build)
    print("marketing  : %s" % marketing)
    print("build      : %d" % build)
    print("versionName: %s" % full_version_name(marketing, build))
    print("versionCode: %d" % build)
    return 0


if __name__ == "__main__":
    sys.exit(main())
