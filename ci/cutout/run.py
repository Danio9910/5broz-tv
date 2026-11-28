# One-off test: can automatic cut-outs of tonight's hero posters look like the Deadpool card?
import io, json, sys, urllib.request
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from rembg import remove, new_session

def get(u):
    req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})
    return urllib.request.urlopen(req, timeout=60).read()

home = json.loads(get("https://www.5broz.com/api/home"))
heroes = home["heroes"][:5]
models = {m: new_session(m) for m in ["birefnet-general"]}

def tint(im):
    a = np.asarray(im.convert("RGB").resize((20, 20))).reshape(-1, 3).astype(float)
    mx, mn = a.max(1), a.min(1); s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1), 0)
    k = s ** 3 * np.where((mx > 50) & (mx < 240), 1, .05) + .002
    c = (a * k[:, None]).sum(0) / k.sum(); l = c @ [.3, .59, .11]
    return tuple(int(x) for x in (c * (95 / l if l > 95 else 1)))

def card(poster, cut, t):
    W, H = 430, 640; UP = 110
    bg = Image.new("RGB", (W + 60, H + UP + 40), (11, 10, 13))
    glow = Image.new("RGB", bg.size, t).filter(ImageFilter.GaussianBlur(1))
    m = Image.new("L", bg.size, 0); ImageDraw.Draw(m).ellipse((-100, -200, bg.size[0] + 100, 320), fill=150)
    bg.paste(glow, (0, 0), m.filter(ImageFilter.GaussianBlur(60)))
    x0, y0 = 30, UP
    cardim = Image.new("RGBA", (W, H), (20, 17, 22, 255))
    art = poster.convert("RGBA").resize((W, int(W * poster.height / poster.width)))
    cardim.alpha_composite(art.crop((0, UP, W, UP + 410)), (0, 0))
    fade = Image.new("L", (W, 410)); ImageDraw.Draw(fade).rectangle((0, 0, W, 410), fill=0)
    for y in range(410):
        fade.putpixel((0, y), 0)
    grad = Image.linear_gradient("L").resize((W, 410))  # 0 top -> 255 bottom
    dark = Image.new("RGBA", (W, 410), (20, 17, 22, 255)); dark.putalpha(grad.point(lambda v: max(0, (v - 110) * 2)))
    cardim.alpha_composite(dark, (0, 0))
    mask = Image.new("L", (W, H), 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, W - 1, H - 1), 40, fill=255)
    bg.paste(cardim, (x0, y0), mask)
    # the cut-out, same scale and place, allowed above the card
    c = cut.resize((W, int(W * cut.height / cut.width)))
    lay = Image.new("RGBA", bg.size, (0, 0, 0, 0)); lay.alpha_composite(c.crop((0, 0, W, UP + 360)), (x0, 0))
    # only the part of the subject that is above the card edge, or inside the art area
    keep = Image.new("L", bg.size, 0); ImageDraw.Draw(keep).rectangle((x0, 0, x0 + W, y0 + 300), fill=255)
    a = Image.composite(lay.getchannel("A"), Image.new("L", bg.size, 0), keep)
    lay.putalpha(a)
    out = bg.convert("RGBA"); out.alpha_composite(lay)
    d = ImageDraw.Draw(out); cx = x0 + W // 2; cy = y0 + 410 - 10
    d.ellipse((cx - 46, cy - 46, cx + 46, cy + 46), fill=t + (90,))
    d.ellipse((cx - 39, cy - 39, cx + 39, cy + 39), fill=t + (255,))
    return out.convert("RGB")

def stats(cut):
    a = np.asarray(cut.getchannel("A")) > 128
    area = a.mean()
    top = a[: a.shape[0] // 20].mean()
    # count separate blobs, roughly
    from scipy import ndimage
    lab, n = ndimage.label(a)
    sizes = ndimage.sum(a, lab, range(1, n + 1)) if n else []
    big = int(sum(1 for s in sizes if s > a.size * 0.01))
    return {"area": round(float(area), 3), "top": round(float(top), 3), "blobs": big}

report = []
json.dump(heroes, open("ci/cutout/out/heroes.json", "w"), ensure_ascii=False)
for i, h in enumerate(heroes):
    poster = Image.open(io.BytesIO(get(h["poster"].replace("/w500/", "/w780/")))).convert("RGB")
    t = tint(poster)
    row = [poster.resize((430, int(430 * poster.height / poster.width)))]
    for name, s in models.items():
        cut = remove(poster, session=s)
        cut.save(f"ci/cutout/out/cut{i}.webp", quality=88)
        poster.save(f"ci/cutout/out/poster{i}.jpg", quality=88)
        st = stats(cut); report.append({"film": h["name"], "model": name, **st})
        chk = Image.new("RGB", cut.size, (40, 40, 48)); chk.paste(cut, (0, 0), cut)
        row.append(chk.resize(row[0].size))
        row.append(card(poster, cut, t))
    H = max(r.height for r in row); Wt = sum(r.width for r in row) + 20 * (len(row) - 1)
    sheet = Image.new("RGB", (Wt, H), (0, 0, 0)); x = 0
    for r in row:
        sheet.paste(r, (x, 0)); x += r.width + 20
    sheet.save(f"ci/cutout/out/{i}.jpg", quality=82)
json.dump(report, open("ci/cutout/out/report.json", "w"), ensure_ascii=False, indent=1)
print(json.dumps(report, ensure_ascii=False, indent=1))
