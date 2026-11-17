#!/bin/bash
# macOS 版 avd 脚本的公共函数库；调用方须先设置 SCRIPT_TAG 再 source。
# 约束：兼容 macOS 自带 /bin/bash 3.2（不能用 mapfile、关联数组、${var^^}）。

ACB_SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd -P)
ACB_DIR=$(cd "$ACB_SCRIPT_DIR/.." && pwd -P)
REPO_ROOT=$(cd "$ACB_DIR/../.." && pwd -P)
ACB_BUILD_DIR=$ACB_DIR/build
ACB_LOG_DIR=$ACB_BUILD_DIR/avd-logs
ACB_DIST_DIR=$ACB_DIR/dist

# Apple Silicon 的 qemu 只有 darwin-aarch64 后端，必须用 arm64-v8a 镜像
ACB_SYSTEM_IMAGE_PKG="system-images;android-28;default;arm64-v8a"
ACB_SYSTEM_IMAGE_REL="system-images/android-28/default/arm64-v8a"
ACB_SYSTEM_IMAGE_ABI="arm64-v8a"
ACB_DEVICE_PROFILE="automotive_1024p_landscape"

ACB_SDKMANAGER=""
ACB_EMULATOR=""
ACB_EMULATOR_CHECK=""
ACB_ADB=""

ACCEL_CODE=0
ACCEL_TEXT=""

acb_step() {
    printf '[%s] %s\n' "$SCRIPT_TAG" "$1"
}

acb_warn() {
    printf '[%s] WARN: %s\n' "$SCRIPT_TAG" "$1"
}

acb_fail() {
    printf '[%s] ERROR: %s\n' "$SCRIPT_TAG" "$1"
    exit 1
}

acb_trim_slashes() {
    local p=$1
    while [ -n "$p" ] && [ "${p%/}" != "$p" ] && [ "$p" != "/" ]; do
        p=${p%/}
    done
    printf '%s' "$p"
}

acb_require_avd_name() {
    case "$1" in
        "")
            acb_fail "empty AVD name"
            ;;
        *[!A-Za-z0-9._-]*)
            acb_fail "invalid AVD name (allowed characters: A-Z a-z 0-9 . _ -): $1"
            ;;
    esac
}

acb_host_os() {
    uname -s
}

acb_host_arch() {
    uname -m
}

# 用逗号连接 stdin 各行（pid 列表用）。
acb_join_lines() {
    local out="" line
    while IFS= read -r line; do
        [ -n "$line" ] || continue
        if [ -z "$out" ]; then
            out=$line
        else
            out=$out,$line
        fi
    done
    printf '%s' "$out"
}

acb_quote_cmd() {
    local out="" a
    for a in "$@"; do
        out="$out $(printf '%q' "$a")"
    done
    printf '%s' "${out# }"
}

# 优先 ANDROID_* 环境变量，否则 ~/Library/Android/sdk。
acb_detect_sdk_root() {
    local root=""
    if [ -n "${1:-}" ]; then
        root=$1
    elif [ -n "${ANDROID_SDK_ROOT:-}" ]; then
        root=$ANDROID_SDK_ROOT
    elif [ -n "${ANDROID_HOME:-}" ]; then
        root=$ANDROID_HOME
    else
        root="$HOME/Library/Android/sdk"
    fi
    acb_trim_slashes "$root"
}

# 解析顺序需与 emulator 一致，否则同一个 AVD 会落到不同目录。
acb_detect_avd_root() {
    local root=""
    if [ -n "${ANDROID_AVD_HOME:-}" ]; then
        root=$ANDROID_AVD_HOME
    elif [ -n "${ANDROID_EMULATOR_HOME:-}" ]; then
        root="$ANDROID_EMULATOR_HOME/avd"
    elif [ -n "${ANDROID_USER_HOME:-}" ]; then
        root="$ANDROID_USER_HOME/avd"
    elif [ -n "${ANDROID_SDK_HOME:-}" ]; then
        root="$ANDROID_SDK_HOME/.android/avd"
    else
        root="$HOME/.android/avd"
    fi
    acb_trim_slashes "$root"
}

acb_detect_java_home() {
    local explicit=${1:-}
    if [ -n "$explicit" ]; then
        printf '%s' "$(acb_trim_slashes "$explicit")"
        return 0
    fi
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        printf '%s' "$(acb_trim_slashes "$JAVA_HOME")"
        return 0
    fi
    local cand
    for cand in \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        "$HOME/Library/Java/JavaVirtualMachines"/*/Contents/Home \
        "$HOME/.sdkman/candidates/java/current" \
        "/opt/homebrew/opt/openjdk/bin/.." \
        "/usr/local/opt/openjdk/bin/.."; do
        if [ -x "$cand/bin/java" ]; then
            printf '%s' "$(acb_trim_slashes "$cand")"
            return 0
        fi
    done
    if command -v java >/dev/null 2>&1; then
        cand=$(command -v java)
        printf '%s' "$(acb_trim_slashes "$(dirname "$(dirname "$cand")")")"
        return 0
    fi
    return 1
}

acb_find_sdkmanager() {
    local sdk=$1 cand
    ACB_SDKMANAGER=""
    for cand in \
        "$sdk/cmdline-tools/latest/bin/sdkmanager" \
        "$sdk/tools/bin/sdkmanager"; do
        if [ -x "$cand" ]; then
            ACB_SDKMANAGER=$cand
            return 0
        fi
    done
    if command -v sdkmanager >/dev/null 2>&1; then
        ACB_SDKMANAGER=$(command -v sdkmanager)
        return 0
    fi
    return 1
}

acb_find_emulator() {
    local sdk=$1
    ACB_EMULATOR=""
    if [ -x "$sdk/emulator/emulator" ]; then
        ACB_EMULATOR="$sdk/emulator/emulator"
        return 0
    fi
    if command -v emulator >/dev/null 2>&1; then
        ACB_EMULATOR=$(command -v emulator)
        return 0
    fi
    return 1
}

acb_find_emulator_check() {
    local sdk=$1
    ACB_EMULATOR_CHECK=""
    if [ -x "$sdk/emulator/emulator-check" ]; then
        ACB_EMULATOR_CHECK="$sdk/emulator/emulator-check"
        return 0
    fi
    return 1
}

acb_find_adb() {
    local sdk=$1
    ACB_ADB=""
    if [ -x "$sdk/platform-tools/adb" ]; then
        ACB_ADB="$sdk/platform-tools/adb"
        return 0
    fi
    if command -v adb >/dev/null 2>&1; then
        ACB_ADB=$(command -v adb)
        return 0
    fi
    return 1
}

# 去掉 adb 输出中的 \r；adb 不可用时返回 1。
acb_adb_text() {
    [ -n "$ACB_ADB" ] || return 1
    "$ACB_ADB" "$@" 2>/dev/null | tr -d '\r'
}

acb_adb_online() {
    local serial=$1
    [ -n "$ACB_ADB" ] || return 1
    "$ACB_ADB" devices 2>/dev/null | tr -d '\r' |
        awk -v s="$serial" '$1 == s && $2 == "device" { found = 1 } END { exit found ? 0 : 1 }'
}

# 只匹配 "-avd <name>" 作为独立参数的进程，不碰其它正在运行的 AVD。
acb_emulator_pids() {
    pgrep -f -- "-avd $1([[:space:]]|$)" 2>/dev/null
}

acb_emulator_instances() {
    pgrep -fl -- "-avd $1([[:space:]]|$)" 2>/dev/null
}

# 从命令行提取 -port；取不到时回退到第二个参数（默认 5554）。
acb_instance_port() {
    local cmd=$1 fallback=${2:-5554} port=""
    port=$(printf '%s\n' "$cmd" |
        sed -n 's/.*-port[[:space:]][[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n 1)
    if [ -n "$port" ]; then
        printf '%s' "$port"
    else
        printf '%s' "$fallback"
    fi
}

acb_emulator_version() {
    local sdk=$1 props="$1/emulator/source.properties"
    if [ ! -f "$props" ]; then
        printf '%s' "(not installed)"
        return 0
    fi
    local line
    line=$(grep -m 1 '^Pkg\.Revision[[:space:]]*=' "$props" 2>/dev/null | tr -d '\r')
    if [ -z "$line" ]; then
        printf '%s' "(unknown)"
        return 0
    fi
    printf '%s' "$line" | sed 's/^Pkg\.Revision[[:space:]]*=[[:space:]]*//'
}

# package.xml 有单行/多行两种格式，都要能读出 <revision>。
acb_image_revision() {
    local sdk=$1 image_rel=$2 pkg=$3 xml="$1/$2/package.xml"
    if [ ! -f "$xml" ]; then
        printf '%s' "(not installed)"
        return 0
    fi
    awk -v pkg="$pkg" '
        {
            if (index($0, "<localPackage ") > 0) {
                inblock = (index($0, "path=\"" pkg "\"") > 0)
                inrev = 0
            }
            if (inblock) {
                if (index($0, "<revision>") > 0) { inrev = 1 }
                if (inrev) {
                    if (match($0, /<major>[0-9]+<\/major>/)) {
                        major = substr($0, RSTART + 7, RLENGTH - 15)
                    }
                    if (match($0, /<minor>[0-9]+<\/minor>/)) {
                        minor = substr($0, RSTART + 7, RLENGTH - 15)
                    }
                    if (match($0, /<micro>[0-9]+<\/micro>/)) {
                        micro = substr($0, RSTART + 7, RLENGTH - 15)
                    }
                    if (index($0, "</revision>") > 0) { inrev = 0 }
                }
                if (index($0, "</localPackage>") > 0) { inblock = 0 }
            }
        }
        END {
            if (major == "") { print "(unknown)" }
            else if (micro != "") { print major "." minor "." micro }
            else if (minor != "") { print major "." minor }
            else { print major }
        }' "$xml"
}

acb_list_installed() {
    local sdk=$1 out rc
    [ -n "$ACB_SDKMANAGER" ] || return 1
    out=$("$ACB_SDKMANAGER" --sdk_root="$sdk" --list_installed 2>/dev/null)
    rc=$?
    printf '%s\n' "$out" | tr -d '\r'
    return $rc
}

# 只读探测加速，结果写 ACCEL_CODE/ACCEL_TEXT（macOS 为内置 HVF）。
acb_accel_probe() {
    local sdk=$1
    ACCEL_CODE=0
    ACCEL_TEXT=""
    if [ ! -x "$sdk/emulator/emulator-check" ]; then
        ACCEL_CODE=-1
        ACCEL_TEXT="emulator-check not found"
        return 0
    fi
    ACCEL_TEXT=$("$sdk/emulator/emulator-check" accel 2>&1)
    ACCEL_CODE=$?
    ACCEL_TEXT=$(printf '%s' "$ACCEL_TEXT" | tr -d '\r')
}

acb_accel_summary() {
    local s=""
    s=$(printf '%s\n' "$ACCEL_TEXT" | grep -m 1 -E 'Hypervisor|HVF|hax' 2>/dev/null)
    if [ -z "$s" ]; then
        s=$(printf '%s\n' "$ACCEL_TEXT" | sed -n '1p')
    fi
    printf '%s' "$s"
}

acb_sha256() {
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" 2>/dev/null | awk '{ print $1 }'
    elif command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" 2>/dev/null | awk '{ print $1 }'
    else
        printf '%s' ""
    fi
}

acb_file_bytes() {
    if [ -f "$1" ]; then
        wc -c < "$1" | tr -d '[:space:]'
    else
        printf '0'
    fi
}

acb_dir_kb() {
    local kb
    if [ -d "$1" ]; then
        kb=$(du -sk "$1" 2>/dev/null | awk '{ print $1 }')
        [ -n "$kb" ] || kb=0
        printf '%s' "$kb"
    else
        printf '0'
    fi
}

# 返回 $1 所在卷的可用 KB；路径不存在时向上找最近的存在父目录。
acb_free_kb() {
    local p=$1 kb
    while [ -n "$p" ] && [ ! -e "$p" ] && [ "$p" != "/" ] && [ "$p" != "." ]; do
        p=$(dirname "$p")
    done
    [ -e "$p" ] || p=.
    kb=$(df -Pk "$p" 2>/dev/null | awk 'NR == 2 { print $4 }')
    if [ -n "$kb" ]; then
        printf '%s' "$kb"
    else
        printf '%s' "-1"
    fi
}

# KB -> GB（1024*1024），保留两位小数。
acb_kb_to_gb() {
    awk -v kb="$1" 'BEGIN { printf "%.2f", kb / 1048576 }'
}

acb_bytes_to_gb() {
    awk -v b="$1" 'BEGIN { printf "%.2f", b / 1073741824 }'
}

# 只停 "-avd <AVD>" 匹配的进程；$2 是等 adb emu kill 的超时秒数。
acb_stop_avd() {
    local avd=$1 timeout=$2 not_running_msg=$3
    local instances line pid pids port serial deadline state

    instances=$(acb_emulator_instances "$avd")
    if [ -z "$instances" ]; then
        acb_step "$not_running_msg"
        return 0
    fi
    line=$(printf '%s\n' "$instances" | head -n 1)
    port=$(acb_instance_port "$line" 5554)
    serial="emulator-$port"
    pids=$(acb_emulator_pids "$avd" | acb_join_lines)
    acb_step "stopping $serial (pid $pids)"

    acb_adb_text -s "$serial" emu kill >/dev/null 2>&1 || true

    deadline=$(( $(date +%s) + timeout ))
    while [ "$(date +%s)" -lt "$deadline" ] && [ -n "$(acb_emulator_pids "$avd")" ]; do
        sleep 1
    done
    if [ -n "$(acb_emulator_pids "$avd")" ]; then
        acb_warn "adb emu kill did not stop it; terminating the matching emulator process only"
        for pid in $(acb_emulator_pids "$avd"); do
            kill -TERM "$pid" 2>/dev/null || true
        done
        sleep 2
        for pid in $(acb_emulator_pids "$avd"); do
            kill -KILL "$pid" 2>/dev/null || true
        done
        sleep 1
    fi
    if [ -n "$(acb_emulator_pids "$avd")" ]; then
        acb_fail "failed to stop the emulator"
    fi

    # 等 adb 释放端口，避免 AVD 目录仍在写入。
    deadline=$(( $(date +%s) + 30 ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        state=$(acb_adb_text -s "$serial" get-state)
        if [ "$state" != "device" ]; then
            break
        fi
        sleep 1
    done
    sleep 1
    acb_step "stopped"
    return 0
}
