#!/usr/bin/env python3
"""Genera el icono de la app (PNG) sin dependencias externas."""
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SS = 4  # supermuestreo


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def dist_seg(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    if l2 == 0:
        return ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
    t = ((px - ax) * dx + (py - ay) * dy) / l2
    t = max(0.0, min(1.0, t))
    cx, cy = ax + t * dx, ay + t * dy
    return ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5


def render(size):
    k = size * SS
    top = (0x18, 0x2C, 0x38)
    bot = (0x0A, 0x10, 0x16)
    accent = (0x35, 0xD0, 0xBA)
    radius = 0.235 * k

    # línea de pulso (coordenadas relativas)
    pts = [(0.16, 0.56), (0.34, 0.56), (0.43, 0.30), (0.54, 0.76),
           (0.63, 0.45), (0.70, 0.56), (0.84, 0.56)]
    pts = [(x * k, y * k) for x, y in pts]
    stroke = 0.075 * k

    # máscara de la zona del pulso para acelerar
    ymin = int(0.20 * k)
    ymax = int(0.86 * k)

    buf = bytearray(k * k * 4)
    for y in range(k):
        ty = y / (k - 1)
        base = lerp(top, bot, ty)
        for x in range(k):
            # esquinas redondeadas
            cx = min(max(x, radius), k - radius)
            cy = min(max(y, radius), k - radius)
            ddx, ddy = x - cx, y - cy
            if ddx * ddx + ddy * ddy > radius * radius:
                continue
            r, g, b = base
            if ymin <= y <= ymax:
                best = min(dist_seg(x, y, pts[i][0], pts[i][1],
                                    pts[i + 1][0], pts[i + 1][1])
                           for i in range(len(pts) - 1))
                if best <= stroke / 2:
                    r, g, b = accent
            o = (y * k + x) * 4
            buf[o] = r
            buf[o + 1] = g
            buf[o + 2] = b
            buf[o + 3] = 255

    # reducción por promedio (antialiasing)
    out = bytearray(size * size * 4)
    n = SS * SS
    for y in range(size):
        for x in range(size):
            ar = ag = ab = aa = 0
            for sy in range(SS):
                for sx in range(SS):
                    o = ((y * SS + sy) * k + (x * SS + sx)) * 4
                    ar += buf[o]
                    ag += buf[o + 1]
                    ab += buf[o + 2]
                    aa += buf[o + 3]
            o = (y * size + x) * 4
            out[o] = ar // n
            out[o + 1] = ag // n
            out[o + 2] = ab // n
            out[o + 3] = aa // n
    return out


def write_png(path, size, rgba):
    raw = b"".join(b"\x00" + bytes(rgba[y * size * 4:(y + 1) * size * 4])
                   for y in range(size))

    def chunk(t, d):
        return (struct.pack(">I", len(d)) + t + d +
                struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n" +
           chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) +
           chunk(b"IDAT", zlib.compress(raw, 9)) +
           chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    for name, size in SIZES.items():
        data = render(size)
        path = os.path.join(OUT, "mipmap-" + name, "ic_launcher.png")
        write_png(path, size, data)
        print("icono", name, size, "px ->", path)
