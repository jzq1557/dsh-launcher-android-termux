#!/usr/bin/env python3
# DSH 启动器图标生成器（纯标准库，无第三方依赖）
# 设计：深蓝渐变圆角底 + 白色终端窗口 + 红黄绿圆点 + 绿色提示符 ">_"
import struct, zlib, math, os, sys

def make_png(w, h, pixels):
    def chunk(t, data):
        c = struct.pack('>I', len(data)) + t + data
        c += struct.pack('>I', zlib.crc32(t + data) & 0xffffffff)
        return c
    raw = b''.join(b'\x00' + b''.join(struct.pack('4B', *[int(round(v)) & 255 for v in p]) for p in row) for row in pixels)
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))

def draw_icon(size, ss=3):
    S = size * ss
    canvas = [[[0.0, 0.0, 0.0, 0.0] for _ in range(S)] for _ in range(S)]

    def blend(x, y, r, g, b, a):
        if 0 <= x < S and 0 <= y < S and a > 0:
            c = canvas[y][x]
            na = a + c[3] * (255 - a) / 255.0
            if na <= 0:
                return
            f = a / na
            c[0] = (r * f + c[0] * c[3] * (255 - a) / 255.0) / na
            c[1] = (g * f + c[1] * c[3] * (255 - a) / 255.0) / na
            c[2] = (b * f + c[2] * c[3] * (255 - a) / 255.0) / na
            c[3] = na

    def in_rounded(x, y, x0, y0, x1, y1, r):
        if x < x0 or x > x1 or y < y0 or y > y1:
            return False
        ix = min(max(x, x0 + r), x1 - r)
        iy = min(max(y, y0 + r), y1 - r)
        dx, dy = x - ix, y - iy
        return dx * dx + dy * dy <= r * r

    def fill_rounded_rect(x0, y0, x1, y1, r, color, soft=0.0):
        r_, g_, b_, a_ = color
        for y in range(max(0, int(y0 - 1)), min(S, int(y1 + 2))):
            for x in range(max(0, int(x0 - 1)), min(S, int(x1 + 2))):
                if in_rounded(x + 0.5, y + 0.5, x0, y0, x1, y1, r):
                    blend(x, y, r_, g_, b_, a_)

    def draw_line(px0, py0, px1, py1, w, color, limit=None):
        r_, g_, b_, a_ = color
        x0, y0, x1, y1 = px0 * ss, py0 * ss, px1 * ss, py1 * ss
        wpx = w * ss
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy
        xmin, xmax = int(min(x0, x1) - wpx / 2 - 1), int(max(x0, x1) + wpx / 2 + 2)
        ymin, ymax = int(min(y0, y1) - wpx / 2 - 1), int(max(y0, y1) + wpx / 2 + 2)
        for y in range(max(0, ymin), min(S, ymax)):
            for x in range(max(0, xmin), min(S, xmax)):
                t = 0.0
                if L2 > 0:
                    t = ((x + 0.5 - x0) * dx + (y + 0.5 - y0) * dy) / L2
                    t = max(0.0, min(1.0, t))
                qx, qy = x0 + t * dx, y0 + t * dy
                d = math.hypot(x + 0.5 - qx, y + 0.5 - qy)
                edge = wpx / 2
                if d <= edge:
                    alpha = a_
                    if d > edge - ss:
                        alpha = max(0.0, a_ * (edge - d) / ss)
                    blend(x, y, r_, g_, b_, alpha)

    def draw_circle(cx, cy, r, color):
        r_, g_, b_, a_ = color
        cxx, cyy, rr = cx * ss, cy * ss, r * ss
        for y in range(max(0, int(cyy - rr - 1)), min(S, int(cyy + rr + 2))):
            for x in range(max(0, int(cxx - rr - 1)), min(S, int(cxx + rr + 2))):
                d = math.hypot(x + 0.5 - cxx, y + 0.5 - cyy)
                if d <= rr:
                    alpha = a_
                    if d > rr - ss:
                        alpha = max(0.0, a_ * (rr - d) / ss)
                    blend(x, y, r_, g_, b_, alpha)

    # 1. 背景圆角矩形：垂直渐变 深蓝 -> 亮蓝
    c0 = (15, 23, 42)      # #0F172A
    c1 = (37, 99, 235)     # #2563EB
    bg_r, bg_b = 12, size - 12
    for y in range(0, S):
        t = y / max(1, S - 1)
        row = [c0[i] + (c1[i] - c0[i]) * t for i in range(3)]
        for x in range(0, S):
            if in_rounded(x + 0.5, y + 0.5, 0, 0, S, S, 40 * ss):
                blend(x, y, row[0], row[1], row[2], 255)

    # 2. 白色终端窗口（半透明白，微圆角）
    fill_rounded_rect(48 * ss, 60 * ss, 144 * ss, 152 * ss, 14 * ss, (255, 255, 255, 245))

    # 3. 红黄绿圆点
    dot_y = 76
    draw_circle(64, dot_y, 5.5, (255, 95, 86, 255))    # 红
    draw_circle(80, dot_y, 5.5, (255, 189, 46, 255))   # 黄
    draw_circle(96, dot_y, 5.5, (39, 201, 63, 255))    # 绿

    # 4. 绿色提示符 ">_"
    green = (34, 197, 94, 255)
    draw_line(68, 102, 92, 118, 8, green)   # > 上斜线
    draw_line(92, 118, 68, 134, 8, green)   # > 下斜线
    draw_line(68, 140, 122, 140, 8, green)  # _

    # 降采样到最终尺寸
    out = []
    for y in range(size):
        row = []
        for x in range(size):
            acc = [0.0] * 4
            for dy in range(ss):
                for dx in range(ss):
                    c = canvas[y * ss + dy][x * ss + dx]
                    for i in range(4):
                        acc[i] += c[i]
            n = ss * ss
            a = acc[3] / n
            if a <= 0:
                row.append((0, 0, 0, 0))
            else:
                row.append((acc[0] / n / (a / 255.0) if a > 0 else 0,
                            acc[1] / n / (a / 255.0),
                            acc[2] / n / (a / 255.0),
                            a))
        out.append(row)
    return out

def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else '.'
    sizes = {'mipmap-mdpi': 48, 'mipmap-hdpi': 72, 'mipmap-xhdpi': 96,
             'mipmap-xxhdpi': 144, 'mipmap-xxxhdpi': 192}
    for folder, size in sizes.items():
        d = os.path.join(outdir, folder)
        os.makedirs(d, exist_ok=True)
        path = os.path.join(d, 'ic_launcher.png')
        with open(path, 'wb') as f:
            f.write(make_png(size, size, draw_icon(size)))
        print(f'生成 {path} ({size}x{size})')

if __name__ == '__main__':
    main()
