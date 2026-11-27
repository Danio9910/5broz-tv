# Prints the centre "x y" of the first clickable-looking UI node whose text or description
# contains one of the given words. "@poster": the first tall image-like node in the middle band.
import re, sys, xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
words = sys.argv[2:]
nodes = list(root.iter("node"))


def box(n):
    return list(map(int, re.findall(r"\d+", n.get("bounds") or "[0,0][0,0]")))


if words == ["@poster"]:
    H = max((box(n)[3] for n in nodes), default=0)
    for n in nodes:
        x1, y1, x2, y2 = box(n)
        w, h = x2 - x1, y2 - y1
        if y1 > H * 0.15 and y2 < H * 0.9 and w > 120 and h > w * 1.2 and h < H * 0.5:
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
    sys.exit(0)

for word in words:
    for n in nodes:
        t = ((n.get("text") or "") + " " + (n.get("content-desc") or "")).strip()
        if not t:
            continue
        # whole-label match for short words so "OK" does not hit "Book"
        hit = (t == word or t.lower() == word.lower()) if len(word) <= 4 else (word in t)
        if hit:
            x1, y1, x2, y2 = box(n)
            if x2 > x1 and y2 > y1:
                print((x1 + x2) // 2, (y1 + y2) // 2)
                sys.exit(0)
