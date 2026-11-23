#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把一个安装包发布进 ota/ 目录，供 App 的「检查更新」下载。

写出的文件（与 App 里 RemoteEndpoints.ota 的路径一一对应）：

    ota/releases/psaboxlite/lasted.apk
    ota/releases/psaboxlite/lasted_md5
    ota/releases/psaboxlite/lasted_version
    ota/releases/psaboxlite/lasted_info

用法：

    python publish_ota.py --apk build/app-release.apk --version 5.3.4
    python publish_ota.py --apk x.apk --version 5.3.4 --info 新版特性.txt
    python publish_ota.py --apk x.apk --version 5.3.4 --info-text "只发一次"

注意：
  * --version 必须与被打的那个包的 versionName **完全一致**，否则设备上永远
    「有新版本」并反复下载（VersionUtils 是字符串相等比较）。
  * --info 是「新版特性」弹窗正文；不给则沿用已有的 lasted_info，再没有就写空。
  * --dry-run 只打印将要写什么，不落盘。
"""

import argparse
import datetime
import hashlib
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# 版本号唯一真源：仓库根目录 version.properties（与 app/build.gradle.kts 共用）
VERSION_PROPS = os.path.join(os.path.dirname(HERE), "version.properties")


def read_version_props(path=VERSION_PROPS):
    """读 marketing / build，返回拼接后的 versionName（与 build.gradle.kts 一致）。"""
    data = {}
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            data[k.strip()] = v.strip()
    if "marketing" not in data or "build" not in data:
        return None
    return "工具箱开发版%s (build %s)" % (data["marketing"], data["build"])


def md5_of(path):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    default_out = os.path.join(here, "releases", "psaboxlite")

    ap = argparse.ArgumentParser(description="发布 OTA 到 ota/releases/psaboxlite/")
    ap.add_argument("--apk", required=True, help="要发布的 APK 路径")
    ap.add_argument(
        "--version",
        help="该 APK 的 versionName；不给则自动从 version.properties 读（推荐）",
    )
    ap.add_argument("--out", default=default_out, help="输出目录（默认 ota/releases/psaboxlite）")
    ap.add_argument("--name", default="lasted.apk", help="输出 APK 文件名（默认 lasted.apk）")
    ap.add_argument("--info", help="新版特性正文文件（UTF-8）")
    ap.add_argument("--info-text", help="新版特性正文，直接给字符串（优先于 --info）")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    apk = os.path.abspath(args.apk)
    if not os.path.isfile(apk):
        print("找不到 APK: %s" % apk)
        return 2

    out = os.path.abspath(args.out)
    digest = md5_of(apk)
    size = os.path.getsize(apk)
    if args.version:
        version = args.version.strip()
    else:
        auto = read_version_props()
        if not auto:
            print("没给 --version，也读不到 version.properties：请显式传 --version")
            return 2
        version = auto
        print("版本号自动取自 version.properties")

    info = None
    if args.info_text is not None:
        info = args.info_text
    elif args.info:
        with open(os.path.abspath(args.info), "r", encoding="utf-8") as f:
            info = f.read()

    # 更新日志自动带发布日期时间（用户 2026-11-23 要求）。
    # 正文里已经有「发布」字样就不重复加，避免手工写的版本叠两层。
    if info is not None:
        info = info.strip("\n")
        if "发布时间" not in info and "发布：" not in info:
            now = datetime.datetime.now()
            stamp = "%d-%02d-%02d %02d:%02d" % (
                now.year, now.month, now.day, now.hour, now.minute,
            )
            info = "发布时间：%s\n\n%s" % (stamp, info)

    print("APK     : %s" % apk)
    print("大小    : %d B (%.2f MB)" % (size, size / 1048576.0))
    print("md5     : %s" % digest)
    print("version : %s" % version)
    print("输出目录: %s" % out)
    if args.dry_run:
        print("--dry-run：不落盘。")
        return 0

    os.makedirs(out, exist_ok=True)
    targets = {
        args.name: None,
        "lasted_md5": digest + "\n",
        "lasted_version": version + "\n",
    }
    if info is not None:
        targets["lasted_info"] = info if info.endswith("\n") else info + "\n"

    # APK 走二进制拷贝，其它按 UTF-8 + LF 写
    dst_apk = os.path.join(out, args.name)
    shutil.copyfile(apk, dst_apk)
    print("写入    : %s (%d B)" % (dst_apk, os.path.getsize(dst_apk)))

    for name, content in targets.items():
        if content is None:
            continue
        p = os.path.join(out, name)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(content)
        print("写入    : %s (%d B)" % (p, os.path.getsize(p)))

    # 校验一遍，防止手误
    back = md5_of(dst_apk)
    if back != digest:
        print("!! 拷贝后 md5 不一致：%s != %s" % (back, digest))
        return 3

    print("完成。把 ota/ 下的变动提交并推送到 GitHub 即可，raw 直链约 5 分钟后生效。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
