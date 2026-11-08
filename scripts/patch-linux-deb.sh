#!/usr/bin/env bash
# 修正 Linux deb 包：
#   1) 依赖名兼容 Debian 10 / 银河麒麟 V10（Ubuntu 24.04 的 t64 包名在这些系统上不存在）
#   2) 补上中文菜单项与品牌图标
#   3) 安装后在各用户桌面（桌面 / Desktop）放一份快捷方式
set -euo pipefail

shopt -s nullglob
for deb in desktop/build/compose/binaries/main/deb/*.deb; do
    work=$(mktemp -d)
    dpkg-deb -R "$deb" "$work/pkg"

    # 1) 依赖修正：t64 包名改写成二选一；libmd0 无任何 ELF 引用，移除
    ctl="$work/pkg/DEBIAN/control"
    sed -i 's/libasound2t64/libasound2t64 | libasound2/g' "$ctl"
    sed -i 's/libpng16-16t64/libpng16-16t64 | libpng16-16/g' "$ctl"
    sed -i 's/libmd0, //g; s/, libmd0//g' "$ctl"

    # 2) 菜单项
    mkdir -p "$work/pkg/usr/share/applications"
    cat > "$work/pkg/usr/share/applications/songyuanqing-tv.desktop" <<'DESKTOP_EOF'
[Desktop Entry]
Type=Application
Name=松源情·TV
GenericName=影视点播
Comment=多源聚合影视点播客户端
Exec=/opt/songyuanqing-tv/bin/SongyuanqingTV
Icon=/opt/songyuanqing-tv/lib/SongyuanqingTV.png
Terminal=false
Categories=AudioVideo;Video;Player;
StartupWMClass=SongyuanqingTV
DESKTOP_EOF
    chmod 644 "$work/pkg/usr/share/applications/songyuanqing-tv.desktop"

    # 3) 安装后把快捷方式放到各用户桌面（银河麒麟中文目录名为「桌面」）
    cat > "$work/pkg/DEBIAN/postinst" <<'POSTINST_EOF'
#!/bin/sh
set -e
SRC=/usr/share/applications/songyuanqing-tv.desktop
for home in /home/* /root; do
    [ -d "$home" ] || continue
    for dir in "$home/桌面" "$home/Desktop"; do
        [ -d "$dir" ] || continue
        cp "$SRC" "$dir/" 2>/dev/null || continue
        chmod 755 "$dir/songyuanqing-tv.desktop" 2>/dev/null || true
        owner=$(stat -c '%U:%G' "$dir" 2>/dev/null || echo root:root)
        chown "$owner" "$dir/songyuanqing-tv.desktop" 2>/dev/null || true
    done
done
exit 0
POSTINST_EOF
    chmod 755 "$work/pkg/DEBIAN/postinst"

    dpkg-deb -b "$work/pkg" "$work/patched.deb" > /dev/null
    mv "$work/patched.deb" "$deb"
    echo "--- $(basename "$deb") ---"
    dpkg-deb -f "$deb" Depends
    rm -rf "$work"
done
