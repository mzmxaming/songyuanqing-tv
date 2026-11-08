#!/usr/bin/env bash
# 为 Linux 安装包准备内置 libVLC。
# 在 Ubuntu 20.04（glibc 2.31，与银河麒麟 V10 SP1 同代）容器内执行：
#   docker run --rm -v "$PWD:/ws" -w /ws ubuntu:20.04 bash scripts/prepare-linux-vlc.sh
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq --no-install-recommends \
    libvlc5 libvlccore9 vlc-data vlc-plugin-base vlc-plugin-video-output vlc-plugin-access-extra \
    patchelf >/dev/null

# 音频输出插件在不同发行版里拆包方式不一致，装了就用，装不上不影响视频
for pkg in vlc-plugin-alsa vlc-plugin-pulse vlc-plugin-jack; do
    apt-get install -y -qq --no-install-recommends "$pkg" >/dev/null 2>&1 || true
done

OUT="desktop/build/appResources/linux/vlc"
rm -rf "$OUT"
mkdir -p "$OUT/lib" "$OUT/plugins" "$OUT/share"

cp -a /usr/lib/*/vlc/plugins/. "$OUT/plugins/"
cp -a /usr/lib/*/libvlc.so.5* "$OUT/lib/" 2>/dev/null || true
cp -a /usr/lib/*/libvlccore.so.9* "$OUT/lib/" 2>/dev/null || true
# 插件依赖的辅助库（libvlc_xcb_events / libvlc_pulse / libvlc_vdpau）也在 vlc 目录下，
# 缺了会导致 xcb_x11 视频输出加载失败——有声音没画面。
cp -a /usr/lib/*/vlc/*.so* "$OUT/lib/" 2>/dev/null || true
cp -a /usr/share/vlc "$OUT/share/vlc" 2>/dev/null || true

# 删除与播放无关的界面类插件，以及系统上生成的插件缓存（换目录后必须重建）
rm -rf "$OUT/plugins/gui" "$OUT/plugins/control" "$OUT/plugins/visualization" \
       "$OUT/plugins/mux" "$OUT/plugins/stream_out" "$OUT/plugins/access_output"
rm -f "$OUT/plugins/plugins.dat"

# JNA 以 "vlc" 为名查找动态库，需要 libvlc.so
ln -sf libvlc.so.5 "$OUT/lib/libvlc.so"

# 收集核心库与全部插件的第三方依赖（glibc / X11 / GL 等由系统提供，不重复打包）
: > /tmp/vlc-deps.txt
for f in "$OUT"/lib/libvlc.so.5 "$OUT"/lib/libvlccore.so.9 "$OUT"/plugins/*/*.so; do
    ldd "$f" 2>/dev/null | awk '{print $3}' | grep '^/' >> /tmp/vlc-deps.txt || true
done

sort -u /tmp/vlc-deps.txt | while read -r lib; do
    case "$lib" in
        /lib/*/libc.so*|/lib/*/libm.so*|*/libpthread.so*|*/libdl.so*|*/librt.so*|*/libgcc_s.so*|\
        */libstdc++.so*|*/libresolv.so*|*/libutil.so*|*/libnsl.so*|*/libanl.so*|*/libmvec.so*) continue ;;
        */libX11.so*|*/libXext.so*|*/libXau.so*|*/libXdmcp.so*|*/libxcb*.so*|*/libGL.so*|\
        */libGLX*|*/libEGL*|*/libGLdispatch*|*/libGLESv2*|*/libdrm.so*) continue ;;
        "$OUT"/*) continue ;;
    esac
    cp -L "$lib" "$OUT/lib/" || true
done

# 动态库搜索路径：同目录互相查找；插件指回 ../../lib
for f in "$OUT"/lib/*.so*; do
    [ -f "$f" ] || continue
    patchelf --set-rpath '$ORIGIN' "$f" 2>/dev/null || true
done
for f in "$OUT"/plugins/*/*.so; do
    patchelf --set-rpath '$ORIGIN/../../lib' "$f" 2>/dev/null || true
done

# 自检：只依赖本目录与系统基础库时不应有 not found
echo "--- 依赖自检 ---"
LD_LIBRARY_PATH="$OUT/lib" ldd "$OUT/lib/libvlc.so.5" | grep 'not found' && exit 1 || true
LD_LIBRARY_PATH="$OUT/lib" ldd "$OUT/lib/libvlccore.so.9" | grep 'not found' && exit 1 || true

echo "--- 内置 libVLC 体积 ---"
du -sh "$OUT"
ls "$OUT/plugins" | head -20
