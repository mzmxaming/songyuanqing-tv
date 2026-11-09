import ctypes, os, sys, time

sys.stdout.reconfigure(line_buffering=True)

base = "/opt/songyuanqing-tv/lib/app/resources/vlc"
lib_path = os.path.join(base, "lib", "libvlc.so.5")
plugins = os.path.join(base, "plugins")
url = sys.argv[1] if len(sys.argv) > 1 else "https://v2.maowushi.com/"

vlc = ctypes.CDLL(lib_path)
os.environ["VLC_PLUGIN_PATH"] = plugins
data_dir = os.path.join(base, "share", "vlc")
if os.path.isdir(data_dir):
    os.environ["VLC_DATA_PATH"] = data_dir

args = [b"-vv"]
argv = (ctypes.c_char_p * len(args))(*args)
vlc.libvlc_new.restype = ctypes.c_void_p
vlc.libvlc_new.argtypes = [ctypes.c_int, ctypes.POINTER(ctypes.c_char_p)]
inst = vlc.libvlc_new(len(args), argv)
print("[cb] instance", inst)

W, H = 640, 360
frames = {"lock": 0, "unlock": 0, "display": 0}
buf = ctypes.create_string_buffer(W * H * 4)

LOCK = ctypes.CFUNCTYPE(ctypes.c_void_p, ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p))
UNLOCK = ctypes.CFUNCTYPE(None, ctypes.c_void_p, ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p))
DISPLAY = ctypes.CFUNCTYPE(None, ctypes.c_void_p, ctypes.c_void_p)


@LOCK
def on_lock(opaque, planes):
    frames["lock"] += 1
    planes[0] = ctypes.cast(buf, ctypes.c_void_p)
    return ctypes.c_void_p(0)


@UNLOCK
def on_unlock(opaque, picture, planes):
    frames["unlock"] += 1


@DISPLAY
def on_display(opaque, picture):
    frames["display"] += 1


vlc.libvlc_media_new_location.restype = ctypes.c_void_p
vlc.libvlc_media_new_location.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
media = vlc.libvlc_media_new_location(inst, url.encode())

vlc.libvlc_media_player_new_from_media.restype = ctypes.c_void_p
vlc.libvlc_media_player_new_from_media.argtypes = [ctypes.c_void_p]
mp = vlc.libvlc_media_player_new_from_media(media)

vlc.libvlc_video_set_callbacks.argtypes = [ctypes.c_void_p, LOCK, UNLOCK, DISPLAY, ctypes.c_void_p]
vlc.libvlc_video_set_callbacks(mp, on_lock, on_unlock, on_display, None)
vlc.libvlc_video_set_format.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_uint, ctypes.c_uint, ctypes.c_uint]
vlc.libvlc_video_set_format(mp, b"RV32", W, H, W * 4)

vlc.libvlc_media_player_play.argtypes = [ctypes.c_void_p]
print("[cb] play ->", vlc.libvlc_media_player_play(mp))
vlc.libvlc_media_player_get_state.argtypes = [ctypes.c_void_p]
vlc.libvlc_media_player_get_time.argtypes = [ctypes.c_void_p]
for i in range(10):
    time.sleep(2)
    print("[cb] t=%ds state=%s time=%s lock=%d unlock=%d display=%d" % (
        (i + 1) * 2, vlc.libvlc_media_player_get_state(mp), vlc.libvlc_media_player_get_time(mp),
        frames["lock"], frames["unlock"], frames["display"]))
vlc.libvlc_media_player_stop(mp)
