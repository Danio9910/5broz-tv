# Prints the centre "x y" of the first UI node whose text or content-desc contains the query.
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
q = sys.argv[2]
if q == "@poster":
    # First image with a description in the lower part of the screen (a result poster).
    h = max(int(re.findall(r"\d+", n.get("bounds"))[3]) for n in root.iter("node"))
    for n in root.iter("node"):
        d = n.get("content-desc") or ""
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        if d and y1 > h * 0.18 and (x2 - x1) > 40 and (y2 - y1) > 60:
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
    sys.exit(0)
for n in root.iter("node"):
    t = (n.get("text") or "") + " " + (n.get("content-desc") or "")
    if q in t:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
