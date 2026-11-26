# Prints the centre "x y" of the first UI node whose text or content-desc contains the query.
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
q = sys.argv[2]
for n in root.iter("node"):
    t = (n.get("text") or "") + " " + (n.get("content-desc") or "")
    if q in t:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
