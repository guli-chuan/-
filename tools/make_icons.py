from PIL import Image, ImageDraw

SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
OUT = "app/src/main/res/mipmap-{density}/ic_launcher.png"
ROUND = "app/src/main/res/mipmap-{density}/ic_launcher_round.png"
BG = (11, 14, 19, 255)
WHITE = (238, 247, 244, 255)
ACCENT = (99, 245, 200, 255)
SCALE = 8


def draw_icon(size, round_icon=False):
    canvas = size * SCALE
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = int(canvas * 0.055)
    box = (pad, pad, canvas - pad, canvas - pad)
    radius = canvas // 2 - pad if round_icon else int(canvas * 0.235)
    d.rounded_rectangle(box, radius=radius, fill=BG)

    cx = cy = canvas / 2
    ring_r = canvas * 0.30
    d.ellipse((cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r),
              outline=WHITE, width=max(2, int(canvas * 0.035)))

    tick_len = canvas * 0.035
    tick_w = max(2, int(canvas * 0.018))
    for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        x1 = cx + dx * ring_r * 0.86
        y1 = cy + dy * ring_r * 0.86
        x2 = cx + dx * (ring_r * 0.86 + tick_len)
        y2 = cy + dy * (ring_r * 0.86 + tick_len)
        d.line((x1, y1, x2, y2), fill=WHITE, width=tick_w)

    d.line((cx, cy, cx, cy - ring_r * 0.55), fill=ACCENT,
           width=max(3, int(canvas * 0.042)))
    d.line((cx, cy, cx + ring_r * 0.46, cy + ring_r * 0.30), fill=ACCENT,
           width=max(3, int(canvas * 0.042)))
    pin = canvas * 0.045
    d.ellipse((cx - pin, cy - pin, cx + pin, cy + pin), fill=ACCENT)

    return img.resize((size, size), Image.LANCZOS)


for density, size in SIZES.items():
    draw_icon(size).save(OUT.format(density=density))
    draw_icon(size, round_icon=True).save(ROUND.format(density=density))
print("icons written")
