#!/usr/bin/env python3
"""Draws the illustrated setup steps used in README.md (docs/guide/*.png).

These are schematic recreations of the Android screens involved, with the
control to tap highlighted. Run: python3 scripts/make_guide_images.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent.parent / "docs" / "guide"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

W, H = 540, 1080          # phone canvas
PAD = 28
BG = (250, 250, 252)
INK = (28, 28, 32)
MUTED = (110, 110, 120)
LINE = (225, 225, 232)
ACCENT = (26, 115, 232)
HL = (229, 57, 53)
OK = (27, 127, 59)
OFF = (189, 189, 196)


def f(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)


def wrap(draw, text, font, width):
    lines, line = [], ""
    for word in text.split():
        trial = (line + " " + word).strip()
        if draw.textlength(trial, font=font) <= width:
            line = trial
        else:
            if line:
                lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def toggle(d, x, y, on):
    w, h = 64, 34
    d.rounded_rectangle((x, y, x + w, y + h), radius=h // 2, fill=ACCENT if on else OFF)
    cx = x + w - h // 2 if on else x + h // 2
    d.ellipse((cx - 13, y + 4, cx + 13, y + h - 4), fill=(255, 255, 255))


def highlight(d, box, label=None):
    x0, y0, x1, y1 = box
    for i in range(4):
        d.rounded_rectangle((x0 - 6 - i, y0 - 6 - i, x1 + 6 + i, y1 + 6 + i), radius=14, outline=HL)
    if label:
        font = f(20, True)
        tw = d.textlength(label, font=font)
        lx = max(PAD, min(W - PAD - tw - 20, x1 - tw - 10))
        ly = y1 + 14
        d.rounded_rectangle((lx, ly, lx + tw + 20, ly + 34), radius=10, fill=HL)
        d.text((lx + 10, ly + 5), label, font=font, fill=(255, 255, 255))


def kebab(d, x, y, color=INK):
    for i in range(3):
        d.ellipse((x - 4, y - 14 + i * 12, x + 4, y - 6 + i * 12), fill=color)


def gear(d, x, y, color=INK):
    d.ellipse((x - 14, y - 14, x + 14, y + 14), outline=color, width=5)
    d.ellipse((x - 5, y - 5, x + 5, y + 5), fill=color)


def phone(step, caption, title, rows=(), top_icon=None, top_hl=False, dialog=None,
          bottom_button=None, subtitle=None, banner=None, back=True):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    # status bar
    d.rectangle((0, 0, W, 44), fill=(240, 240, 244))
    d.text((PAD, 10), "12:30", font=f(20, True), fill=INK)
    d.text((W - PAD - 70, 10), "▮▮▮ 85%", font=f(18), fill=INK)
    # app bar
    y = 64
    if back:
        d.text((PAD, y), "←", font=f(34), fill=INK)
    d.text((PAD + (52 if back else 0), y + 4), title, font=f(28, True), fill=INK)
    if top_icon:
        ix, iy = W - PAD - 16, y + 22
        (kebab if top_icon == "menu" else gear)(d, ix, iy)
        if top_hl:
            highlight(d, (ix - 22, iy - 24, ix + 22, iy + 24))
    y += 70
    if subtitle:
        for ln in wrap(d, subtitle, f(19), W - 2 * PAD):
            d.text((PAD, y), ln, font=f(19), fill=MUTED)
            y += 26
        y += 10
    if banner:
        d.rounded_rectangle((PAD, y, W - PAD, y + 120), radius=18, fill=(232, 240, 254))
        d.text((PAD + 20, y + 22), banner[0], font=f(24, True), fill=INK)
        d.text((PAD + 20, y + 62), banner[1], font=f(19), fill=MUTED)
        y += 140
    for r in rows:
        text, sub, tog, hl, label = (list(r) + [None] * 5)[:5]
        top = y
        tfont = f(23, bool(hl))
        yy = y + 14
        for ln in wrap(d, text, tfont, W - 2 * PAD - (90 if tog is not None else 0)):
            d.text((PAD, yy), ln, font=tfont, fill=INK)
            yy += 30
        yy += 2
        if sub:
            for ln in wrap(d, sub, f(18), W - 2 * PAD - (90 if tog is not None else 0)):
                d.text((PAD, yy), ln, font=f(18), fill=MUTED)
                yy += 24
        if tog is not None:
            toggle(d, W - PAD - 64, top + 18, tog)
        y = max(yy, top + 74) + 8
        d.line((PAD, y, W - PAD, y), fill=LINE, width=2)
        if hl:
            highlight(d, (PAD - 4, top + 4, W - PAD + 4, y - 6), label)
            y += 40 if label else 0
        y += 4
    if dialog:
        d.rectangle((0, 0, W, H), fill=None)
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 110))
        img.paste(Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB"))
        d = ImageDraw.Draw(img)
        dtitle, dtext, buttons, hl_btn = dialog
        bx0, bx1 = PAD + 10, W - PAD - 10
        lines = wrap(d, dtext, f(20), bx1 - bx0 - 48)
        bh = 120 + 28 * len(lines) + 80
        by0 = H // 2 - bh // 2
        d.rounded_rectangle((bx0, by0, bx1, by0 + bh), radius=26, fill=(255, 255, 255))
        d.text((bx0 + 24, by0 + 26), dtitle, font=f(25, True), fill=INK)
        ty = by0 + 74
        for ln in lines:
            d.text((bx0 + 24, ty), ln, font=f(20), fill=MUTED)
            ty += 28
        bx = bx1 - 24
        for i, b in enumerate(reversed(buttons)):
            font = f(21, True)
            tw = d.textlength(b, font=font)
            x0 = bx - tw
            d.text((x0, by0 + bh - 56), b, font=font, fill=ACCENT)
            if len(buttons) - 1 - i == hl_btn:
                highlight(d, (x0 - 8, by0 + bh - 62, bx + 8, by0 + bh - 24))
            bx = x0 - 40
    if bottom_button:
        text, hl = bottom_button
        by = H - 150
        d.rounded_rectangle((PAD, by, W - PAD, by + 64), radius=32, fill=ACCENT)
        tw = d.textlength(text, font=f(23, True))
        d.text(((W - tw) / 2, by + 17), text, font=f(23, True), fill=(255, 255, 255))
        if hl:
            highlight(d, (PAD, by, W - PAD, by + 64))
    return frame(img, step, caption)


def frame(screen, step, caption, landscape=False):
    """Phone bezel + step badge + caption under it."""
    sw, sh = screen.size
    bez = 18
    cap_font = f(24, True)
    canvas_w = sw + 2 * bez + 40
    tmp = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    lines = wrap(tmp, caption, cap_font, canvas_w - 40)
    cap_h = 34 * len(lines) + 30
    out = Image.new("RGB", (canvas_w, sh + 2 * bez + 40 + cap_h), (255, 255, 255))
    d = ImageDraw.Draw(out)
    d.rounded_rectangle((20, 20, 20 + sw + 2 * bez, 20 + sh + 2 * bez), radius=48, fill=(30, 30, 34))
    out.paste(screen, (20 + bez, 20 + bez))
    d.ellipse((14, 4, 74, 64), fill=ACCENT)
    num = str(step)
    tw = d.textlength(num, font=f(30, True))
    d.text((44 - tw / 2, 15), num, font=f(30, True), fill=(255, 255, 255))
    y = sh + 2 * bez + 40
    for ln in lines:
        tw = d.textlength(ln, font=cap_font)
        d.text(((canvas_w - tw) / 2, y), ln, font=cap_font, fill=INK)
        y += 34
    return out


def car_screen(step, caption):
    cw, ch = 900, 420
    img = Image.new("RGB", (cw, ch), (18, 18, 22))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 80, ch), fill=(30, 30, 36))
    for i, s in enumerate(["◉", "♪", "☎", "▦"]):
        d.text((26, 40 + i * 90), s, font=f(30), fill=(200, 200, 210))
    d.text((120, 30), "OgAuto", font=f(30, True), fill=(255, 255, 255))
    gear(d, cw - 50, 50, (220, 220, 230))
    tiles = [("Yansıtma", "▣"), ("Uygulamalar", "▦"), ("Video", "▶"), ("Tarayıcı", "◍"), ("YouTube", "▷")]
    for i, (t, ic) in enumerate(tiles):
        x = 120 + i * 152
        d.rounded_rectangle((x, 120, x + 136, 300), radius=18, fill=(44, 44, 52))
        d.text((x + 50, 150), ic, font=f(46), fill=(138, 180, 248))
        tw = d.textlength(t, font=f(19, True))
        d.text((x + 68 - tw / 2, 250), t, font=f(19, True), fill=(255, 255, 255))
        if i == 0:
            highlight(d, (x, 120, x + 136, 300))
    return frame(img, step, caption)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    shots = {
        "01-download": phone(1, "APK’yı indir: downloads klasöründeki son OgAuto-x.y.z.apk",
            "github.com", back=False,
            subtitle="og-auto / downloads",
            rows=[("OgAuto-0.1.0.apk", "eski sürüm"),
                  ("OgAuto-0.5.0-debug.apk", "en yeni — dokun → View raw / İndir", None, True, "BUNU İNDİR"),
                  ("SHA256SUMS", "dosya doğrulama")]),
        "02-play-protect": phone(2, "Play Store → profil → Play Protect → ⚙ → taramayı KAPAT",
            "Play Protect", top_icon="gear", top_hl=True,
            banner=("Uygulamalar tarandı", "Zararlı uygulama bulunamadı"),
            rows=[("Uygulamaları Play Protect ile tara", "Dışarıdan kurulan OgAuto’yu engellememesi için kapat", False, True, "KAPAT"),
                  ("Zararlı uygulama algılamayı iyileştir", None, False)]),
        "03-install-apk": phone(3, "İndirilenler’de APK’ya dokun: “Bu kaynaktan izin ver” → Yükle",
            "Bilinmeyen uygulamalar",
            subtitle="Chrome / Dosyalar",
            rows=[("Bu kaynaktan izin ver", "APK’yı açtığın uygulama için bir kez", True, True, "AÇ")],
            dialog=("OgAuto", "Bu uygulamayı yüklemek istiyor musunuz?", ["İptal", "Yükle"], 1)),
        "04-open-consent": phone(4, "OgAuto’yu aç, kullanım onayını kabul et",
            "OgAuto", back=False,
            dialog=("Kullanım onayı", "Sürücü sürüş sırasında kullanmamalıdır. Ekranınızdaki bilgiler araçta görünür.", ["İptal", "Onayla"], 1)),
        "05-checklist": phone(5, "Uygulamadaki numaralı adımlar: ✗ olanların düğmesine sırayla bas",
            "OgAuto", back=False,
            subtitle="Kurulum adımları (sırayla)",
            rows=[("○ 1. Play Protect taramasını kapat", "Play Protect ayarlarını aç"),
                  ("✓ 2. Bilinmeyen uygulama yükleme izni", None),
                  ("✗ 3. Android Auto için kur", "Play yükleyici adıyla yeniden kurar", None, True, "DOKUN"),
                  ("✗ 4. Kısıtlanmış ayarlara izin ver", None),
                  ("✗ 5. Dokunmatik servisi", None)]),
        "06-aa-install": phone(6, "“Android Auto için kur” → sistem ekranında Güncelle. OgAuto kapanır, yeniden aç",
            "OgAuto",
            dialog=("OgAuto", "Bu uygulamayı güncellemek istiyor musunuz? Mevcut verileriniz kaybolmaz.", ["İptal", "Güncelle"], 1)),
        "07-restricted": phone(7, "Android 13+: Uygulama bilgisi → sağ üst ⋮ → “Kısıtlanmış ayarlara izin ver”",
            "Uygulama bilgisi", top_icon="menu", top_hl=True,
            rows=[("OgAuto", "sürüm 0.5.0"),
                  ("Kısıtlanmış ayarlara izin ver", "⋮ menüsünde görünür", None, True, "DOKUN"),
                  ("Bildirimler", None), ("İzinler", None), ("Pil", None)]),
        "08-accessibility": phone(8, "Erişilebilirlik → Yüklü uygulamalar → OgAuto → AÇ",
            "OgAuto",
            subtitle="Araç ekranından telefona dokunma, Geri ve Ana ekran komutları gönderir",
            rows=[("OgAuto kullan", "Dokunmatik servisi", True, True, "AÇ"),
                  ("OgAuto kısayolu", None, False)]),
        "09-overlay": phone(9, "Diğer uygulamaların üzerinde göster → OgAuto → AÇ (yatay kilit)",
            "Üzerinde göster",
            rows=[("OgAuto", "Diğer uygulamaların üzerinde göstermeye izin ver", True, True, "AÇ")]),
        "10-write-settings": phone(10, "Sistem ayarlarını değiştirme → OgAuto → AÇ (parlaklık + döndürme)",
            "Sistem ayarlarını değiştir",
            rows=[("OgAuto", "Sistem ayarlarını değiştirmeye izin ver", True, True, "AÇ")]),
        "11-battery": phone(11, "Pil kısıtlaması: “İzin ver” / “Kısıtlanmamış”",
            "Pil kullanımı",
            rows=[("Kısıtlanmamış", "Arka planda kısıtlama yok", None, True, "SEÇ"),
                  ("Optimize edilmiş", None), ("Kısıtlanmış", None)]),
        "12-aa-dev": phone(12, "Android Auto ayarları → “Sürüm”e 10 kez dokun → ⋮ → Geliştirici ayarları",
            "Android Auto", top_icon="menu", top_hl=True,
            rows=[("Başlatıcıyı özelleştir", None), ("Bildirimler", None),
                  ("Sürüm", "10 kez dokun → geliştirici modu", None, True, "10×")]),
        "13-unknown-sources": phone(13, "Geliştirici ayarları → “Bilinmeyen kaynaklar” → AÇ",
            "Geliştirici ayarları",
            rows=[("Uygulama modu", "Normal"),
                  ("Bilinmeyen kaynaklar", "Play dışından kurulan araç uygulamaları", True, True, "AÇ"),
                  ("Video çözünürlüğü", "Otomatik")]),
        "14-launcher": phone(14, "Android Auto → Başlatıcıyı özelleştir → OgAuto işaretli",
            "Başlatıcıyı özelleştir",
            rows=[("Haritalar", None, True), ("Spotify", None, True),
                  ("OgAuto", "Yansıtma · Uygulamalar · Video", True, True, "İŞARETLİ"),
                  ("Telefon", None, True)]),
        "15-share": phone(15, "Telefonda “Ekran paylaşımını başlat” → TÜM EKRAN → Başlat",
            "OgAuto", back=False,
            dialog=("OgAuto ile paylaşılsın mı?", "Tüm ekran paylaşılır. Ekranınızdaki her şey araçta görünür.", ["İptal", "Başlat"], 1)),
        "16-car": car_screen(16, "Araçta OgAuto: Yansıtma, Uygulamalar veya Video"),
        "17-iptv": phone(17, "VYPN / IPTV uygulaması → Ayarlar → Harici oynatıcı → OgAuto",
            "Oynatıcı seç",
            rows=[("Dahili oynatıcı", None),
                  ("OgAuto (araçta oynat)", "Yayın doğrudan araç ekranında oynar", None, True, "SEÇ"),
                  ("VLC", None), ("MX Player", None)]),
    }
    for name, image in shots.items():
        image.save(OUT / f"{name}.png", optimize=True)
        print(OUT / f"{name}.png")


if __name__ == "__main__":
    main()
