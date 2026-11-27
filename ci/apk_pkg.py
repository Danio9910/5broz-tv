# Package name of an APK without aapt: read it from the binary AndroidManifest.xml.
import sys, zipfile, struct

data = zipfile.ZipFile(sys.argv[1]).read("AndroidManifest.xml")


def strings(buf):
    # string pool chunk starts at 8
    off = 8
    _, hsize, size, count, _, flags, sstart, _ = struct.unpack_from("<HHIIIIII", buf, off)
    utf8 = flags & (1 << 8)
    offs = struct.unpack_from("<%dI" % count, buf, off + hsize)
    out = []
    base = off + sstart
    for o in offs:
        p = base + o
        if utf8:
            n = buf[p] & 0x7F if not buf[p] & 0x80 else ((buf[p] & 0x7F) << 8) | buf[p + 1]
            p += 1 if not buf[p] & 0x80 else 2
            ln = buf[p] & 0x7F if not buf[p] & 0x80 else ((buf[p] & 0x7F) << 8) | buf[p + 1]
            p += 1 if not buf[p] & 0x80 else 2
            out.append(buf[p:p + ln].decode("utf8", "replace"))
        else:
            ln = struct.unpack_from("<H", buf, p)[0]
            out.append(buf[p + 2:p + 2 + ln * 2].decode("utf16", "replace"))
    return out


s = strings(data)
# The "package" attribute value is typically the first dotted string that follows "package".
for i, x in enumerate(s):
    if x.count(".") >= 2 and " " not in x and "/" not in x and not x.startswith("android."):
        print(x)
        break
