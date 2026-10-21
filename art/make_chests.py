"""Draws the chests' still textures: designs of their own, in the style of the vanilla copper chest.

From the copper chest each chest keeps only how a vanilla chest is built: the light rim round every face, the dark
frame a pixel in, the grey lock and the black inside. Within each frame it gets a design of its material:

- iron: brushed plates, a riveted seam across them and a rivet in every corner;
- diamond: cut facets, a lattice of rhombuses lit from the top left, with the odd sparkle;
- netherite: heavy plating, bevelled bands laid like bricks, joints staggered;
- amethyst: cells of crystal of every size, as on the amethyst block, with dark cracks between;
- hay: straw woven like a basket, strands passing over and under each other;
- bone: two bones crossed corner to corner on plain bone, as the braces cross the copper chest's lid.

The colours are the material's vanilla block's, nine shades spread evenly from its lightest to a dark one for the
frame (the block's own colours where it has them, darkened towards violet where it does not), lit from the top
left as vanilla is. The textures go to art/textures/chest, from which make_effects.py makes the mod's animated ones:

    python art/make_chests.py && python art/make_effects.py
"""
import colorsys
import io
import math
import zipfile
from pathlib import Path
from PIL import Image

ART = Path(__file__).resolve().parent
OUT = ART / 'textures' / 'chest'
JAR = Path.home() / '.gradle' / 'caches' / 'fabric-loom' / '26.3' / 'minecraft-client.jar'

# The block whose colours each chest takes.
BLOCKS = {
    'iron': 'iron_block',
    'diamond': 'diamond_block',
    'netherite': 'netherite_block',
    'amethyst': 'amethyst_block',
    'hay': 'hay_block_top',
    'bone': 'bone_block_side',
}
# Copper darker than this, at a face's edge, is its frame.
FRAME_LUMINANCE = 105
# A face this dark all over is the inside of the lid, drawn plain in dark shades.
INSIDE_LUMINANCE = 90


def luminance(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def lightness(colour):
    return colorsys.rgb_to_hls(*(c / 255 for c in colour[:3]))[1]


def read(jar, path):
    return Image.open(io.BytesIO(jar.read(path))).convert('RGBA')


def shade(colour, target, hue_shift):
    """{@code colour} brought to lightness {@code target}, its hue turned a little towards violet the short way round."""
    h, _, s = colorsys.rgb_to_hls(*(c / 255 for c in colour[:3]))
    turn = (0.75 - h + 0.5) % 1.0 - 0.5
    r, g, b = colorsys.hls_to_rgb((h + turn * hue_shift) % 1.0, target, min(1.0, s * 1.15))
    return (round(r * 255), round(g * 255), round(b * 255))


def ramp(block):
    """Nine shades, darkest first, spread evenly from the block's lightest down to a quarter of it."""
    shades = sorted({block.getpixel((x, y))[:3] for y in range(block.height) for x in range(block.width) if block.getpixel((x, y))[3]}, key=lightness)
    top = lightness(shades[-1])
    colours = []
    for i in range(9):
        target = top * (0.25 + 0.75 * i / 8)
        nearest = min(shades, key=lambda c: abs(lightness(c) - target))
        colours.append(nearest if abs(lightness(nearest) - target) < 0.04 else shade(nearest, target, 0.15 * (1 - i / 8)))
    return colours


def noise(x, y, seed):
    """A steady pseudo-random number from 0 to 1 for a pixel, the same every run."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n ^ (n >> 16)) / 0xFFFFFFFF


def faces(width):
    """The faces of the chest's lid and base on the texture, as (x, y, width, height, seed), as ModelPart lays a box out."""
    out = []
    for u, v, h, part in ((0, 0, 5, 0), (0, 19, 10, 1)):
        d = 14
        rects = [(u + d, v, width, d), (u + d + width, v, width, d), (u, v + d, d, h), (u + d, v + d, width, h),
                 (u + d + width, v + d, d, h), (u + d + width + d, v + d, width, h)]
        out += [rect + (part * 10 + i,) for i, rect in enumerate(rects)]
    return out


# ------------------------------------------------------------------------------------ designs
# Each takes a pixel's place within the face's frame (x, y from the frame's top left corner, w, h its size) and the
# face's seed, and gives a shade from 0 (darkest) to 8 (lightest).

def iron(x, y, w, h, seed):
    # Brushed: each column of the plate a touch lighter or darker, top to bottom.
    tone = 5 + (1 if noise(x, seed, 1) > 0.6 else 0) - (1 if noise(x, seed, 2) > 0.8 else 0)
    if y == 0:
        tone += 1
    seam = h // 2 if h >= 8 else -1
    if y == seam:
        tone = 3
    elif y == seam + 1:
        tone = 7
    # A rivet in each corner and two along the seam: a lit head, its shadow below and to the right.
    rivets = [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)]
    if seam > 0:
        rivets += [(w // 3, seam), (w - 1 - w // 3, seam)]
    for rx, ry in rivets:
        if (x, y) == (rx, ry):
            return 8
        if (x, y) in ((rx + 1, ry), (rx, ry + 1), (rx + 1, ry + 1)):
            return 3
    return tone


def diamond(x, y, w, h, seed):
    # A lattice of rhombuses, each facet lit on its upper left and shaded on its lower right.
    size = 6
    a, b = (x + y + seed) % size, (x - y + 60 + seed) % size
    if a == 0 or b == 0:
        return 3
    if a < size // 2 and b < size // 2:
        return 8 if (a, b) == (1, 1) and noise(x // size, y // size, seed) > 0.55 else 7
    if a >= size // 2 and b >= size // 2:
        return 4
    return 6


def netherite(x, y, w, h, seed):
    # Bands three pixels high, bevelled, laid like bricks with their joints staggered.
    band, row = divmod(y, 3)
    if (x + band * 3 + seed) % 7 == 0:
        return 1
    tone = (5, 3, 2)[row]
    if row == 1 and noise(x, band, seed) > 0.85:
        tone = 4
    return tone


def amethyst(x, y, w, h, seed):
    # As the amethyst block: cells of crystal of every size, each lit on its upper left, with dark cracks between.
    points = [(noise(i, 1, seed) * w, noise(i, 2, seed) * h) for i in range(max(3, w * h // 14))]
    near = sorted((math.hypot(x - px, y - py), i) for i, (px, py) in enumerate(points))
    if near[1][0] - near[0][0] < 0.8:
        return 2
    i = near[0][1]
    px, py = points[i]
    lit = (x - px) + (y - py) < 0
    return (7 if lit else 5) if i % 2 else (6 if lit else 4)


def hay(x, y, w, h, seed):
    # Woven like a basket: strands two pixels wide passing over and under each other.
    if ((x // 2) + (y // 2)) % 2:
        return 7 if x % 2 == 0 else 5
    return 6 if y % 2 == 0 else 3


def bone(x, y, w, h, seed):
    # Two bones crossed corner to corner, knuckles at their ends, on plain bone with a crack here and there.
    slope = w / max(1, h)
    for d in (x - y * slope, (w - 1 - x) - y * slope):
        if abs(d) < 1.2:
            end = min(x, w - 1 - x) < 2 and (y < 2 or y > h - 3)
            return 8 if d < 0 or end else 6
        if abs(d) < 2.0:
            return 3
    if noise(x, y, seed) > 0.96:
        return 3
    return 5 if (x + y) % 2 else 6


DESIGNS = {'iron': iron, 'diamond': diamond, 'netherite': netherite, 'amethyst': amethyst, 'hay': hay, 'bone': bone}


def draw(copper, material, colours, width):
    out = copper.copy()
    lock = (6 if width == 14 else 4, 5)
    for fx, fy, fw, fh, seed in faces(width):
        pixels = [(x, y) for y in range(fy, fy + fh) for x in range(fx, fx + fw) if copper.getpixel((x, y))[3]]
        if not pixels:
            continue

        lums = sorted(luminance(copper.getpixel(p)) for p in pixels)
        inside = lums[len(lums) // 2] < INSIDE_LUMINANCE
        for x, y in pixels:
            p = copper.getpixel((x, y))
            if p[:3] == (0, 0, 0) or (x < lock[0] and y < lock[1]):
                continue

            lum = luminance(p)
            ring = min(x - fx, y - fy, fx + fw - 1 - x, fy + fh - 1 - y)
            if inside:
                # The inside of the lid: dark and plain, as on every chest.
                tone = 0 if lum < 70 else 1
            elif lum < FRAME_LUMINANCE and ring <= 1:
                tone = 0 if lum < 70 else 1
            elif ring == 0:
                # The rim, lit or shaded as the copper chest's is.
                tone = 6 if lum > 150 else 5
            else:
                tone = DESIGNS[material](x - fx - 2, y - fy - 2, fw - 4, fh - 4, seed)
            colour = colours[max(0, min(8, tone))]
            out.putpixel((x, y), colour + (p[3],))
    # Anything else the copper chest has outside the faces and the lock, darkest as the frame, the rest as the rim.
    covered = {(x, y) for fx, fy, fw, fh, _ in faces(width) for y in range(fy, fy + fh) for x in range(fx, fx + fw)}
    for y in range(copper.height):
        for x in range(copper.width):
            p = copper.getpixel((x, y))
            if p[3] and (x, y) not in covered and not (x < lock[0] and y < lock[1]):
                out.putpixel((x, y), colours[1 if luminance(p) < FRAME_LUMINANCE else 5] + (p[3],))
    return out


def main():
    with zipfile.ZipFile(JAR) as jar:
        for material, block in BLOCKS.items():
            colours = ramp(read(jar, f'assets/minecraft/textures/block/{block}.png'))
            for suffix, width in (('', 14), ('_left', 15), ('_right', 15)):
                copper = read(jar, f'assets/minecraft/textures/entity/chest/copper{suffix}.png')
                draw(copper, material, colours, width).save(OUT / f'{material}{suffix}.png')
            print('chest', material)


if __name__ == '__main__':
    main()
