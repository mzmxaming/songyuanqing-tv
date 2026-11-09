import ctypes, os, sys, time

sys.stdout.reconfigure(line_buffering=True)

base = "/opt/songyuanqing-tv/lib/app/resources/vlc"
lib_path = os.environ.get("PROBE_LIB", os.path.join(base, "lib", "libvlc.so.5"))
plugins = os.environ.get("PROBE_PLUGINS", os.path.join(base, "plugins"))
url = sys.argv[1] if len(sys.argv) > 1 else "https://v.lfthirtytwo.com/20260328/3536_03ebf6e4/index.m3u8"

vlc = ctypes.CDLL(lib_path)
vlc.libvlc_get_version.restype = ctypes.c_char_p
print("[probe] libvlc =", vlc.libvlc_get_version().decode())

# 3.0.9.2 已移除 --plugin-path 选项（传入会导致 libvlc_new 返回 NULL），改用环境变量
if plugins:
    os.environ["VLC_PLUGIN_PATH"] = plugins
data_dir = os.path.join(base, "share", "vlc")
if os.path.isdir(data_dir):
    os.environ["VLC_DATA_PATH"] = data_dir

args = [b"-vv"]
extra = os.environ.get("PROBE_EXTRA", "--vout=dummy --aout=dummy").strip()
if extra:
    args += [a.encode() for a in extra.split()]
argv = (ctypes.c_char_p * len(args))(*args)
vlc.libvlc_new.restype = ctypes.c_void_p
vlc.libvlc_new.argtypes = [ctypes.c_int, ctypes.POINTER(ctypes.c_char_p)]
inst = vlc.libvlc_new(len(args), argv)
print("[probe] instance =", inst)
if not inst:
    sys.exit(1)

print("[probe] creating media...")
vlc.libvlc_media_new_location.restype = ctypes.c_void_p
vlc.libvlc_media_new_location.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
media = vlc.libvlc_media_new_location(inst, url.encode())
print("[probe] media =", media)

print("[probe] adding options...")
vlc.libvlc_media_add_option.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
vlc.libvlc_media_add_option.restype = None
ua = os.environ.get("PROBE_UA", "")
referer = os.environ.get("PROBE_REFERER", "")
if ua:
    vlc.libvlc_media_add_option(media, (":http-user-agent=" + ua).encode())
if referer:
    vlc.libvlc_media_add_option(media, (":http-referrer=" + referer).encode())
print("[probe] options ok ua=%r referer=%r" % (ua[:20], referer[:30]))

print("[probe] creating player...")
vlc.libvlc_media_player_new_from_media.restype = ctypes.c_void_p
vlc.libvlc_media_player_new_from_media.argtypes = [ctypes.c_void_p]
mp = vlc.libvlc_media_player_new_from_media(media)
print("[probe] player =", mp)
vlc.libvlc_media_player_play.argtypes = [ctypes.c_void_p]
vlc.libvlc_media_player_play.restype = ctypes.c_int
print("[probe] play ->", vlc.libvlc_media_player_play(mp))
print("[probe] url =", url)

vlc.libvlc_media_player_get_state.argtypes = [ctypes.c_void_p]
vlc.libvlc_media_player_get_time.argtypes = [ctypes.c_void_p]
vlc.libvlc_media_player_get_length.argtypes = [ctypes.c_void_p]
for i in range(12):
    time.sleep(2)
    print("[probe] state=%s time=%s length=%s" % (
        vlc.libvlc_media_player_get_state(mp),
        vlc.libvlc_media_player_get_time(mp),
        vlc.libvlc_media_player_get_length(mp),
    ))
vlc.libvlc_media_player_stop(mp)
