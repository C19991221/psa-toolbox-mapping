#!/usr/bin/env bash

set -e

# Windows(Git Bash) 下 javac/jar 需要 C:/... 形式的路径，用 pwd -W；macOS/Linux 的 pwd 无 -W。
HOST_OS="$(uname -s 2>/dev/null || echo Unknown)"
case "$HOST_OS" in
    MINGW*|MSYS*|CYGWIN*)
        HERE="$(cd "$(dirname "$0")" && pwd -W)"
        REPO_ROOT="$(cd "$HERE/../.." && pwd -W)"
        ;;
    *)
        HERE="$(cd "$(dirname "$0")" && pwd)"
        REPO_ROOT="$(cd "$HERE/../.." && pwd)"
        ;;
esac
SRC="$HERE/src"
OUT="$HERE/out/classes"
JAR_OUT="$REPO_ROOT/third_party/android-car-stub.jar"

if [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
else
    JAVAC="$(command -v javac || true)"
    JAR="$(command -v jar || true)"
fi
if [ ! -x "$JAVAC" ] || [ ! -x "$JAR" ]; then
    echo "需要 JDK 17：请设置 JAVA_HOME，或把 javac/jar 放进 PATH" >&2
    exit 1
fi

if [ -z "${ANDROID_JAR:-}" ]; then
    SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [ -z "$SDK" ] && [ -f "$REPO_ROOT/local.properties" ]; then
        SDK="$(sed -n 's/^sdk\.dir[[:space:]]*=[[:space:]]*//p' "$REPO_ROOT/local.properties" | tr -d '\r' | head -1)"
    fi
    ANDROID_JAR="$SDK/platforms/android-34/android.jar"
fi
if [ ! -f "$ANDROID_JAR" ]; then
    echo "需要 SDK platform android-34 的 android.jar：设置 ANDROID_JAR，或设置 ANDROID_HOME/ANDROID_SDK_ROOT，或在 local.properties 写 sdk.dir" >&2
    exit 1
fi

rm -rf "$HERE/out"
mkdir -p "$OUT"

"$JAVAC" -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$OUT" \
    $(find "$SRC" -name '*.java')

( cd "$OUT" && "$JAR" --create --file "$JAR_OUT" --no-manifest --date=1980-01-01T00:00:02Z \
    $(find android -name '*.class' | sort -f) )
echo "完成：${JAR_OUT}（$("$JAR" tf "$JAR_OUT" | grep -c '\.class$') 个 class）"
