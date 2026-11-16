#!/bin/bash
# 停止 API 28 arm64 AVD 并导出可分发的 snapshot zip（macOS Apple Silicon）。
# python3 zipfile（ZIP64）打包，默认先停模拟器；解压即为 ~/.android/avd 布局。

set -u
SCRIPT_TAG=snapshot
. "$(dirname "$0")/macos-common.sh"

AvdName=psa_api28
ZipPath=""
SdkRoot=""
NoStop=0
KeepStage=0
Force=0
DryRun=0
TimeoutSec=120
ChecksumMaxMB=64

usage() {
    cat <<'EOF'
Usage: bash tools/android-car-boot/avd/snapshot.sh [options]

Options (PowerShell spellings such as -AvdName / -ZipPath / -Force are accepted):
  --avd-name NAME        AVD name (default: psa_api28)
  --zip-path PATH        output zip (default: tools/android-car-boot/dist/<name>-<stamp>.zip)
  --sdk-root PATH        Android SDK root (default: $ANDROID_SDK_ROOT,
                         $ANDROID_HOME or ~/Library/Android/sdk)
  --no-stop              do not stop a running emulator (inconsistent snapshot)
  --keep-stage           keep the staging directory
  --force                overwrite an existing zip / replace a stale staging dir
  --dry-run              print the plan without stopping, copying or zipping
  --timeout-sec N        stop timeout in seconds (default: 120)
  --checksum-max-mb N    max file size for per-file sha256 (default: 64)
  -h, --help             show this help

Only an emulator process whose command line contains "-avd <name>" is stopped.
Exit code 0 on success, 1 on failure.
EOF
}

is_int() {
    case "$1" in
        ''|*[!0-9]*) return 1 ;;
    esac
    return 0
}

args=("$@")
argi=0
argc=${#args[@]}
while [ "$argi" -lt "$argc" ]; do
    raw=${args[$argi]}
    argi=$((argi + 1))
    hasval=0
    val=""
    case "$raw" in
        *=*)
            key=${raw%%=*}
            val=${raw#*=}
            hasval=1
            ;;
        *)
            key=$raw
            ;;
    esac
    norm=$(printf '%s' "$key" | sed 's/^-*//' | tr 'A-Z' 'a-z' | tr -d '_-')
    case "$norm" in
        avdname|name)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            AvdName=$val
            ;;
        zippath)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            ZipPath=$val
            ;;
        sdkroot)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            SdkRoot=$val
            ;;
        timeoutsec)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            TimeoutSec=$val
            ;;
        checksummaxmb)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            ChecksumMaxMB=$val
            ;;
        nostop)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            NoStop=1
            ;;
        keepstage)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            KeepStage=1
            ;;
        force)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            Force=1
            ;;
        dryrun)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            DryRun=1
            ;;
        h|help)
            usage
            exit 0
            ;;
        "")
            acb_fail "empty option"
            ;;
        *)
            acb_fail "unknown option: $raw (see --help)"
            ;;
    esac
done
acb_require_avd_name "$AvdName"
is_int "$TimeoutSec" || acb_fail "invalid timeout: $TimeoutSec"
is_int "$ChecksumMaxMB" || acb_fail "invalid checksum max: $ChecksumMaxMB"

if ! command -v python3 >/dev/null 2>&1; then
    acb_fail "python3 not found; it is required to write manifest.json and the zip (macOS: xcode-select --install)"
fi
# adb 只用于优雅停止模拟器，缺失时只告警。
if ! acb_find_adb "$SdkRoot"; then
    acb_warn "adb not found: $SdkRoot/platform-tools/adb"
fi

SdkRoot=$(acb_detect_sdk_root "$SdkRoot")
AvdRoot=$(acb_detect_avd_root)
AvdDir="$AvdRoot/$AvdName.avd"
IniPath="$AvdRoot/$AvdName.ini"

Stamp=$(date +%Y%m%d-%H%M%S)
if [ -z "$ZipPath" ]; then
    ZipPath="$ACB_DIST_DIR/$AvdName-$Stamp.zip"
fi
case "$ZipPath" in
    /*) ;;
    *) ZipPath="$PWD/$ZipPath" ;;
esac
ZipDir=$(dirname "$ZipPath")
StageDir="$ACB_DIST_DIR/$AvdName-$Stamp.stage"

ExcludeNames="*.lock hardware-qemu.ini *.log"
ExcludeDirs="snapshots"

# 逻辑大小：qemu 镜像是稀疏文件，du 报已分配大小，
# 而契约要的是各文件大小之和。
logical_size_bytes() {
    find "$1" -type f -print0 2>/dev/null | xargs -0 stat -f '%z' 2>/dev/null |
        awk '{ s += $1 } END { printf "%d", s + 0 }'
}

is_running() {
    [ -n "$(acb_emulator_pids "$AvdName")" ]
}

get_free_bytes() {
    local kb
    kb=$(acb_free_kb "$1")
    if [ "$kb" -lt 0 ]; then
        printf '%s' "-1"
    else
        printf '%s' $((kb * 1024))
    fi
}

created_stamp() {
    date +%Y-%m-%dT%H:%M:%S%z | sed 's/\([0-9][0-9]\)$/:\1/'
}

write_manifest() {
    # 元数据走环境变量，文件/配置/产物清单走 TSV，避免值被 shell 改写。
    ACB_STAGE="$StageDir" \
    ACB_MANIFEST="$StageDir/manifest.json" \
    ACB_TSV_FILES="$tsv_files" \
    ACB_TSV_ARTIFACTS="$tsv_artifacts" \
    ACB_TSV_CONFIG="$tsv_config" \
    ACB_AVD_NAME="$AvdName" \
    ACB_CREATED="$created" \
    ACB_EMU_VER="$emu_ver" \
    ACB_IMAGE_PKG="$ACB_SYSTEM_IMAGE_PKG" \
    ACB_IMAGE_REV="$image_rev" \
    ACB_SDK_ROOT="$SdkRoot" \
    ACB_AVD_ROOT="$AvdRoot" \
    ACB_ZIP_PATH="$ZipPath" \
    ACB_EXCL_FILES="$ExcludeNames" \
    ACB_EXCL_DIRS="$ExcludeDirs" \
    python3 - <<'PY'
import json
import os

stage = os.environ["ACB_STAGE"]


def read_tsv(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, "r", encoding="ascii") as handle:
        for line in handle:
            line = line.rstrip("\n")
            if line:
                rows.append(line.split("\t"))
    return rows


files = []
for row in read_tsv(os.environ["ACB_TSV_FILES"]):
    rec = {"path": row[0], "size": int(row[1])}
    if len(row) > 2 and row[2]:
        rec["sha256"] = row[2]
    files.append(rec)

avd_config = {}
for row in read_tsv(os.environ["ACB_TSV_CONFIG"]):
    avd_config[row[0]] = row[1]

artifacts = {}
for row in read_tsv(os.environ["ACB_TSV_ARTIFACTS"]):
    name, path, exists, size = row[0], row[1], row[2], row[3]
    sha = row[4] if len(row) > 4 else ""
    artifacts[name] = {
        "path": path,
        "exists": exists == "1",
        "size": int(size),
        "sha256": sha,
    }

manifest = {
    "schema": 1,
    "kind": "psa-avd-snapshot",
    "created": os.environ["ACB_CREATED"],
    "avdName": os.environ["ACB_AVD_NAME"],
    "emulatorVersion": os.environ["ACB_EMU_VER"],
    "systemImage": {
        "package": os.environ["ACB_IMAGE_PKG"],
        "revision": os.environ["ACB_IMAGE_REV"],
        "sdk": 28,
    },
    "host": {
        "sdkRoot": os.environ["ACB_SDK_ROOT"],
        "avdRoot": os.environ["ACB_AVD_ROOT"],
        "psVersion": "n/a (macOS bash port)",
    },
    "zipPath": os.environ["ACB_ZIP_PATH"],
    "contents": {
        "avdIni": os.environ["ACB_AVD_NAME"] + ".ini",
        "avdDir": os.environ["ACB_AVD_NAME"] + ".avd",
        "manifest": "manifest.json",
        "restore": "RESTORE.md",
    },
    "exclusions": {
        "files": os.environ["ACB_EXCL_FILES"].split(),
        "dirs": os.environ["ACB_EXCL_DIRS"].split(),
    },
    "avdConfig": avd_config,
    "files": files,
    "artifacts": artifacts,
    "knownChanges": [
        "/system/framework/android.test.base.jar replaced by android.car.jar (minimal bootclasspath mechanism)",
        "/system/framework/arm64/boot-android.test.base.{art,art.rel,oat,vdex} removed",
        "com.android.car service installed at /system/app/CarService/CarService.apk",
        "APP APK (com.bandwa.openadb) may be installed under /data/app",
        "zygote rc / BOOTCLASSPATH / ramdisk are untouched (stock)",
    ],
    "restore": "extract into ~/.android/avd (<name>.ini + <name>.avd) and start with run.sh",
}

with open(os.environ["ACB_MANIFEST"], "w", encoding="ascii") as handle:
    json.dump(manifest, handle, indent=2, ensure_ascii=True)
    handle.write("\n")
PY
}

write_restore() {
    cat <<'EOF' | sed "s/__AVD__/$AvdName/g" > "$StageDir/RESTORE.md"
# AVD snapshot restore notes

This zip is a snapshot of the official Google Android 9 (API 28) userdebug AVD
for macOS Apple Silicon (arm64-v8a system image), provisioned with the
project's minimal bootclasspath mechanism:

- `/system/framework/android.test.base.jar` was replaced by the project `android.car.jar`;
- `/system/framework/arm64/boot-android.test.base.{art,art.rel,oat,vdex}` were removed;
- the `com.android.car` service lives at `/system/app/CarService/CarService.apk`;
- `zygote rc`, `BOOTCLASSPATH` and the ramdisk are untouched (stock).

## Restore steps

1. Install the same SDK components (see `manifest.json`: systemImage.revision and
   emulatorVersion).  `setup.sh` checks/installs emulator, platform-tools and
   `system-images;android-28;default;arm64-v8a` for you.
2. Stop any emulator that uses this AVD name.
3. Extract this zip into `~/.android/avd`, keeping the layout:

   ```
   ~/.android/avd/__AVD__.ini
   ~/.android/avd/__AVD__.avd/...
   ```

4. Start it:

   ```bash
   bash tools/android-car-boot/avd/run.sh --avd-name __AVD__
   ```

5. Verify the injection (read-only):

   ```bash
   python3 tools/android-car-boot/avd/provision.py --verify --serial emulator-5554
   ```

6. Roll back to a stock system (restore stock jar/slots, remove the system service):
   `provision.py --rollback`.

## Notes

- The snapshot contains a preallocated `userdata-qemu.img` (~8 GB) and the system overlay.
- Before the first boot check `image.sysdir.1` in `config.ini`; on macOS it must be
  `system-images/android-28/default/arm64-v8a/`.
- The `__AVD__.ini` in this zip keeps the absolute `path=` of the machine that
  created the snapshot.  When restoring on a different machine edit those lines so
  `path=` points at the extracted `__AVD__.avd` directory (and keep
  `path.rel=avd/__AVD__.avd`).
- Never use the same snapshot from two emulator instances at once (`*.lock` is excluded
  and will be recreated on start).
- Hypervisor.framework (HVF) is used on Apple Silicon; the old Windows AEHD/WHPX
  driver is not needed.
EOF
}

write_zip() {
    ACB_STAGE="$StageDir" ACB_ZIP="$ZipPath" python3 - <<'PY'
import os
import sys
import zipfile

stage = os.environ["ACB_STAGE"]
zpath = os.environ["ACB_ZIP"]

with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED, allowZip64=True) as archive:
    for root, dirs, filenames in os.walk(stage):
        dirs.sort()
        filenames.sort()
        rel_root = os.path.relpath(root, stage).replace(os.sep, "/")
        if rel_root != ".":
            archive.write(root, rel_root + "/")
        for name in filenames:
            full = os.path.join(root, name)
            rel = os.path.relpath(full, stage).replace(os.sep, "/")
            archive.write(full, rel)
PY
}

acb_step "AVD name : $AvdName"
acb_step "AVD dir  : $AvdDir"
acb_step "zip path : $ZipPath"

if [ ! -d "$AvdDir" ]; then acb_fail "AVD directory not found: $AvdDir (run setup.sh first)"; fi
if [ ! -f "$IniPath" ]; then acb_fail "AVD ini not found: $IniPath"; fi
if [ ! -d "$ACB_DIST_DIR" ]; then mkdir -p "$ACB_DIST_DIR" || acb_fail "cannot create dist directory: $ACB_DIST_DIR"; fi
if [ -e "$ZipPath" ] && [ "$Force" -eq 0 ]; then
    acb_fail "zip already exists (use --force): $ZipPath"
fi

ArtifactJar="$ACB_DIR/api/build/android.car.jar"
ArtifactService="$ACB_DIR/service/build/outputs/apk/debug/service-debug.apk"
ArtifactApp="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
ArtifactProbe="$REPO_ROOT/verify/bootinject/out/probe-app.apk"

avd_size=$(logical_size_bytes "$AvdDir")
free_bytes=$(get_free_bytes "$ZipDir")
avd_size_gb=$(awk -v b="$avd_size" 'BEGIN { printf "%.2f", b / 1073741824 }')
free_gb=$(awk -v b="$free_bytes" 'BEGIN { if (b < 0) print -1; else printf "%.2f", b / 1073741824 }')

acb_step "AVD size : $avd_size_gb GB (plus ini)"
if [ "$free_bytes" -lt 0 ]; then
    acb_step "free space on target volume: unknown"
else
    acb_step "free space on target volume: $free_gb GB"
fi

if is_running; then
    acb_step "AVD is running (pid $(acb_emulator_pids "$AvdName" | acb_join_lines)); it will be stopped"
else
    acb_step "AVD is not running"
fi

artifact_record() {
    # artifact_record <manifest 键> <路径>：输出一行 TSV。
    local name=$1 path=$2 exists=0 size=0 sha=""
    if [ -f "$path" ]; then
        exists=1
        size=$(acb_file_bytes "$path")
        sha=$(acb_sha256 "$path")
    fi
    printf '%s\t%s\t%s\t%s\t%s\n' "$name" "$path" "$exists" "$size" "$sha"
}

if [ "$DryRun" -eq 1 ]; then
    acb_step "[dry-run] would stop the emulator (if running)"
    acb_step "[dry-run] would copy to $StageDir"
    acb_step "[dry-run] would exclude: $(printf '%s' "$ExcludeNames" | sed 's/ /, /g'); dirs: $(printf '%s' "$ExcludeDirs" | sed 's/ /, /g')"
    acb_step "[dry-run] would zip to $ZipPath"
    acb_step "[dry-run] free space needed ~$(awk -v b="$avd_size" 'BEGIN { printf "%.2f", (b * 2) / 1073741824 }') GB for staging + zip"
    for row in \
        "$(artifact_record apiJar "$ArtifactJar")" \
        "$(artifact_record serviceApk "$ArtifactService")" \
        "$(artifact_record appApk "$ArtifactApp")" \
        "$(artifact_record probeApk "$ArtifactProbe")"; do
        a_path=$(printf '%s\n' "$row" | cut -f2)
        a_exists=$(printf '%s\n' "$row" | cut -f3)
        a_size=$(printf '%s\n' "$row" | cut -f4)
        a_sha=$(printf '%s\n' "$row" | cut -f5)
        if [ "$a_exists" = "1" ]; then
            acb_step "[dry-run] artifact: $a_path  $a_size B  sha256=$a_sha"
        else
            acb_step "[dry-run] artifact missing: $a_path"
        fi
    done
    exit 0
fi

if [ "$NoStop" -eq 0 ]; then
    acb_stop_avd "$AvdName" "$TimeoutSec" "AVD $AvdName is not running"
else
    acb_warn "--no-stop: snapshot may be inconsistent while the emulator is running"
fi

if is_running; then
    acb_fail "AVD is still running; stop it first (run.sh --stop)"
fi

needed=$((avd_size * 2 + 200 * 1024 * 1024))
if [ "$free_bytes" -ge 0 ] && [ "$free_bytes" -lt "$needed" ]; then
    acb_fail "not enough free space on $(dirname "$ZipPath"): need ~$(awk -v b="$needed" 'BEGIN { printf "%.2f", b / 1073741824 }') GB, have $free_gb GB"
fi

if [ -d "$StageDir" ]; then rm -rf "$StageDir" || acb_fail "cannot remove stale staging directory: $StageDir"; fi
mkdir -p "$StageDir" || acb_fail "cannot create staging directory: $StageDir"
stage_avd="$StageDir/$AvdName.avd"
acb_step "copying $AvdDir -> $stage_avd"
# APFS clonefile 让 8 GB 稀疏镜像保持稀疏；失败再回退普通复制。
if ! cp -c -R "$AvdDir" "$stage_avd" 2>/dev/null; then
    cp -R "$AvdDir" "$stage_avd" || acb_fail "cannot copy $AvdDir"
fi
cp "$IniPath" "$StageDir/$AvdName.ini" || acb_fail "cannot copy $IniPath"

for dir in $ExcludeDirs; do
    if [ -e "$stage_avd/$dir" ]; then rm -rf "$stage_avd/$dir"; fi
done
for name in $ExcludeNames; do
    find "$stage_avd" -type f -name "$name" -print0 2>/dev/null | xargs -0 rm -f 2>/dev/null || true
done

emu_ver=$(acb_emulator_version "$SdkRoot")
image_rev=$(acb_image_revision "$SdkRoot" "$ACB_SYSTEM_IMAGE_REL" "$ACB_SYSTEM_IMAGE_PKG")
created=$(created_stamp)
max_sha_bytes=$((ChecksumMaxMB * 1024 * 1024))

tsv_files=$(mktemp "${TMPDIR:-/tmp}/psa-snapshot-files.XXXXXX") || acb_fail "mktemp failed"
tsv_artifacts=$(mktemp "${TMPDIR:-/tmp}/psa-snapshot-artifacts.XXXXXX") || acb_fail "mktemp failed"
tsv_config=$(mktemp "${TMPDIR:-/tmp}/psa-snapshot-config.XXXXXX") || acb_fail "mktemp failed"
cleanup_tmp() {
    rm -f "$tsv_files" "$tsv_artifacts" "$tsv_config"
}
trap cleanup_tmp EXIT

# 先算文件清单再写 manifest.json/RESTORE.md，清单不能包含它们自身。
find "$StageDir" -type f 2>/dev/null | sort |
while IFS= read -r f; do
    rel=${f#"$StageDir"/}
    size=$(acb_file_bytes "$f")
    sha=""
    if [ "$size" -le "$max_sha_bytes" ]; then
        sha=$(acb_sha256 "$f")
    fi
    printf '%s\t%s\t%s\n' "$rel" "$size" "$sha"
done > "$tsv_files"

artifact_record apiJar "$ArtifactJar" >> "$tsv_artifacts"
artifact_record serviceApk "$ArtifactService" >> "$tsv_artifacts"
artifact_record appApk "$ArtifactApp" >> "$tsv_artifacts"
artifact_record probeApk "$ArtifactProbe" >> "$tsv_artifacts"

stage_config="$stage_avd/config.ini"
if [ -f "$stage_config" ]; then
    WANTED_KEYS=" hw.lcd.width hw.lcd.height hw.lcd.density hw.ramSize disk.dataPartition.size hw.gpu.enabled hw.gpu.mode hw.audioInput hw.audioOutput hw.camera.back hw.keyboard showDeviceFrame hw.cpu.ncore image.sysdir.1 tag.id abi.type "
    while IFS= read -r line; do
        case "$line" in
            *=*) ;;
            *) continue ;;
        esac
        key=${line%%=*}
        case "$WANTED_KEYS" in
            *" $key "*) printf '%s\t%s\n' "$key" "${line#*=}" >> "$tsv_config" ;;
        esac
    done < "$stage_config"
fi

write_manifest || acb_fail "failed to write manifest.json"
write_restore || acb_fail "failed to write RESTORE.md"
acb_step "manifest.json and RESTORE.md written"

zip_dir=$(dirname "$ZipPath")
if [ ! -d "$zip_dir" ]; then mkdir -p "$zip_dir" || acb_fail "cannot create zip directory: $zip_dir"; fi
if [ -e "$ZipPath" ] && [ "$Force" -eq 1 ]; then rm -f "$ZipPath" || acb_fail "cannot remove existing zip: $ZipPath"; fi

acb_step "zipping $StageDir -> $ZipPath"
if ! write_zip; then
    rm -f "$ZipPath"
    acb_fail "python3 zipfile failed"
fi
if [ ! -f "$ZipPath" ]; then acb_fail "zip was not created"; fi

zip_size=$(acb_file_bytes "$ZipPath")
zip_size_gb=$(awk -v b="$zip_size" 'BEGIN { printf "%.2f", b / 1073741824 }')
zip_sha=$(acb_sha256 "$ZipPath")
acb_step "zip size : $zip_size_gb GB ($zip_size B)"
acb_step "zip sha256: $zip_sha"

if [ "$KeepStage" -eq 0 ]; then
    acb_step "removing staging directory (use --keep-stage to keep it)"
    rm -rf "$StageDir"
else
    acb_step "staging directory kept: $StageDir"
fi

acb_step "done"
exit 0
