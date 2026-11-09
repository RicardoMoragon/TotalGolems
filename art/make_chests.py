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
left as vanilla is. Each piece of a design (a straw, a facet, a brick, a cell, a column of the plate) has a shade of
its own, mixed between two of the nine where it falls between them, so no chest is only a few flat colours. The textures go to art/textures/chest, from which make_effects.py makes the mod's animated ones:

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
    """
    The faces of the chest's lid and base on the texture, as (x, y, width, height, face), as ModelPart lays a box out:
    face is 0 to 5 on the lid (inside, top, then the sides: west, front, east, back), 10 to 15 on the base.
    """
    out = []
    for u, v, h, part in ((0, 0, 5, 0), (0, 19, 10, 1)):
        d = 14
        rects = [(u + d, v, width, d), (u + d + width, v, width, d), (u, v + d, d, h), (u + d, v + d, width, h),
                 (u + d + width, v + d, d, h), (u + d + width + d, v + d, width, h)]
        out += [rect + (part * 10 + i,) for i, rect in enumerate(rects)]
    return out


# ------------------------------------------------------------------------------------ designs
# Each takes a pixel's place within the face's frame (x, y from the frame's top left corner, w, h its size) and the
# face's seed, and gives a shade from 0 (darkest) to 8 (lightest). Shades between two of the nine are allowed: the
# colour is mixed from those two. Each piece of a design (a straw, a facet, a brick, a cell) has a shade of its own,
# lighter or darker than the next, so that no material is only two or three flat colours.

def vary(seed, *key, spread=1.0):
    """A steady shift of up to {@code spread} either way for one piece of a design, the same every run."""
    a = key[0] if key else 0
    b = key[1] if len(key) > 1 else 0
    return (noise(a * 31 + 7, b * 17 + 3, seed + 101) - 0.5) * 2 * spread


def iron(x, y, w, h, seed):
    # Brushed: each column a shade of its own, the plate lighter towards its top, the odd bright scratch.
    tone = 5.2 + vary(seed, x, 0, spread=0.9) + 0.7 * (1 - y / max(1, h - 1)) - 0.35
    if noise(x, y, seed + 5) > 0.95:
        tone += 1.2
    seam = h // 2 if h >= 8 else -1
    if y == seam:
        tone = 3 + vary(seed, x, 9, spread=0.4)
    elif y == seam + 1:
        tone = 7 + vary(seed, x, 8, spread=0.4)
    # A rivet in each corner and two along the seam: a bright head, lit round its upper left, its shadow below right.
    rivets = [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)]
    if seam > 0:
        rivets += [(w // 3, seam), (w - 1 - w // 3, seam)]
    for rx, ry in rivets:
        if (x, y) == (rx, ry):
            return 8
        if (x, y) in ((rx + 1, ry + 1),):
            return 2.5
        if (x, y) in ((rx + 1, ry), (rx, ry + 1)):
            return 3.5
    return tone


def diamond(x, y, w, h, seed):
    # A lattice of rhombuses, each facet a shade of its own: lit on its upper left, a bright edge where the light
    # catches it, shaded on its lower right, and the odd sparkle.
    size = 6
    a, b = (x + y + seed) % size, (x - y + 60 + seed) % size
    facet = ((x + y + seed) // size, (x - y + 60 + seed) // size)
    shift = vary(seed, *facet, spread=0.8)
    if a == 0 or b == 0:
        return 3 + vary(seed, x, y, spread=0.4)
    if a < size // 2 and b < size // 2:
        if (a, b) == (1, 1) and noise(*facet, seed) > 0.55:
            return 8
        return 7 + shift + (0.6 if a == 1 or b == 1 else 0)
    if a >= size // 2 and b >= size // 2:
        return 4 + shift - (0.5 if a == size - 1 or b == size - 1 else 0)
    return (6 if a < b else 5.2) + shift


def netherite(x, y, w, h, seed):
    # Bands three pixels high, bevelled, laid like bricks with their joints staggered: each brick a shade of its own,
    # lit along its top and its left end, and flecked lighter as the netherite block is.
    band, row = divmod(y, 3)
    along = x + band * 3 + seed
    if along % 7 == 0:
        return 1 + vary(seed, x, y, spread=0.3)
    brick = (along // 7, band)
    tone = (5, 3.4, 2.3)[row] + vary(seed, *brick, spread=0.7)
    if along % 7 == 1:
        tone += 0.5
    if row == 1 and noise(x, band, seed) > 0.8:
        tone += 1.2
    return tone


def amethyst(x, y, w, h, seed):
    # As the amethyst block: cells of crystal of every size and shade, each lit on its upper left with a bright
    # point, and dark cracks between them, darkest where three meet.
    points = [(noise(i, 1, seed) * w, noise(i, 2, seed) * h) for i in range(max(3, w * h // 14))]
    near = sorted((math.hypot(x - px, y - py), i) for i, (px, py) in enumerate(points))
    if near[1][0] - near[0][0] < 0.8:
        return 1.5 if len(near) > 2 and near[2][0] - near[0][0] < 1.2 else 2.3
    i = near[0][1]
    px, py = points[i]
    base = 4.2 + 2.6 * noise(i, 3, seed)
    if round(px - 1) == x and round(py - 1) == y:
        return 8
    lit = (x - px) + (y - py) < 0
    return base + (1 if lit else -0.4)


def hay(x, y, w, h, seed):
    # Woven like a basket: strands two pixels wide passing over and under each other, each strand a shade of its own,
    # each straw in it lit on one side, and here and there a pale ear of wheat or a dark seed.
    cell = (x // 2, y // 2)
    shift = vary(seed, *cell, spread=0.6)
    if (cell[0] + cell[1]) % 2:
        # Passing over: two straws side by side, the left one in the light.
        tone = 6.4 + shift + (0.7 if x % 2 == 0 else -0.3)
    else:
        # Passing under, in the shadow of those over it: lit along its top, darkest along its bottom.
        tone = 4.0 + shift * 0.7 + (0.4 if y % 2 == 0 else -1.0)
    speck = noise(x, y, seed + 9)
    if speck > 0.97 and tone > 5:
        tone = 8
    elif speck < 0.025:
        tone -= 1.2
    return tone


def bone(x, y, w, h, seed):
    # Two bones crossed corner to corner, rounded, lit along one side and shaded along the other, knuckles at their
    # ends, on bone mottled lighter and darker, with a crack here and there.
    slope = w / max(1, h)
    for d in (x - y * slope, (w - 1 - x) - y * slope):
        if abs(d) < 1.2:
            end = min(x, w - 1 - x) < 2 and (y < 2 or y > h - 3)
            if end:
                return 8
            return 8 if d < -0.4 else 7 if d < 0.4 else 5.8
        if abs(d) < 2.0:
            return 3 + vary(seed, x, y, spread=0.4)
    if noise(x, y, seed) > 0.96:
        return 3
    return 5.6 + vary(seed, x // 2, y // 2, spread=0.7) + (0.35 if (x + y) % 2 else -0.35)


DESIGNS = {'iron': iron, 'diamond': diamond, 'netherite': netherite, 'amethyst': amethyst, 'hay': hay, 'bone': bone}


def tone_colour(colours, tone):
    """The colour of a shade from 0 to 8, mixed from the two nearest of the nine when it falls between them."""
    tone = max(0.0, min(8.0, tone))
    low = int(tone)
    high = min(8, low + 1)
    f = tone - low
    return tuple(round(a + (b - a) * f) for a, b in zip(colours[low], colours[high]))


def frame_colour(colours, u, v, width, height, face):
    """
    The frame's colour at a pixel of it: not one flat line but short runs of two to four pixels in its own dark shade,
    a shade a third of the way to the next, and the next, so that it has some grain to it.
    """
    # How far round the frame, clockwise from its top left corner.
    if v == 1:
        along = u
    elif u == width - 2:
        along = width + v
    elif v == height - 2:
        along = width + height + (width - 1 - u)
    else:
        along = 2 * width + height + (height - 1 - v)
    run = 0
    start = 0
    while True:
        length = 2 + int(noise(run, face, 11) * 3)
        if along < start + length:
            break
        start += length
        run += 1
    pick = noise(run, face, 13)
    dark, light = colours[1], colours[2]
    if pick < 0.3:
        return dark
    if pick < 0.7:
        return tuple(round(a + (b - a) / 3) for a, b in zip(dark, light))
    return light


def join_edge(half, face):
    """
    Which edge of a face of a double chest's half meets the other half: 'left' or 'right' as on the texture, or None.
    Each half leaves out its side facing the other (west on the left half, east on the right); the faces beside it in
    the texture's layout are the ones that meet: the front, top and bottom on that side, the back on the other.
    """
    if not half:
        return None
    near = 'left' if half == '_left' else 'right'
    far = 'right' if near == 'left' else 'left'
    return {0: near, 1: near, 3: near, 5: far}.get(face % 10)


def draw(copper, material, colours, half):
    """
    The chest built as vanilla builds one (a rim round each face and a dark frame a pixel in), its design within.
    The two halves of a double chest are one chest: no rim or frame where they meet, and the design runs on from
    one into the other. The faces of the sides are upside down on the texture, so their rows are counted from the
    bottom; the lock sits on a plate at the middle of the front, where lid and base meet, in a little shadow.
    """
    width = 15 if half else 14
    out = copper.copy()
    lock = (6 if width == 14 else 4, 5)
    for fx, fy, fw, fh, face in faces(width):
        pixels = [(x, y) for y in range(fy, fy + fh) for x in range(fx, fx + fw) if copper.getpixel((x, y))[3]]
        if not pixels:
            continue

        lums = sorted(luminance(copper.getpixel(p)) for p in pixels)
        inside = lums[len(lums) // 2] < INSIDE_LUMINANCE
        join = join_edge(half, face)
        whole = fw * 2 if join else fw
        side = face % 10 >= 2
        lid = face < 10
        for x, y in pixels:
            p = copper.getpixel((x, y))
            if p[:3] == (0, 0, 0) or (x < lock[0] and y < lock[1]):
                continue

            # Where the pixel is on the whole face, both halves of a double chest together, rows from the top.
            u = x - fx + (fw if join == 'left' else 0)
            v = fh - 1 - (y - fy) if side else y - fy
            ring = min(u, v, whole - 1 - u, fh - 1 - v)
            # The lid comes a pixel down over the base: the row where they overlap is drawn on both, and has to be
            # the same on both, or the two flicker against each other. It is left plain, shadow and lock plate only.
            seam = side and ((lid and v == fh - 1) or (not lid and v == 0))
            # Distance, across and up or down from where lid and base meet, from the lock at the middle of the front.
            lock_dx = abs(u + 0.5 - whole / 2)
            lock_dy = fh - 1 - v if lid else v
            front = face % 10 == 3
            plate = front and lock_dx <= 2 and lock_dy <= 1
            if inside:
                # The inside of the lid: dark and plain, as on every chest.
                colour = colours[0 if luminance(p) < 70 else 1]
            elif plate:
                colour = colours[2]
            elif ring == 0:
                # The rim: lit along the top, in shadow where lid and base meet.
                colour = colours[3 if seam else 6 if v == 0 else 5]
            elif ring == 1:
                colour = frame_colour(colours, u, v, whole, fh, face)
            else:
                tone = DESIGNS[material](u - 2, v - 2, whole - 4, fh - 4, face)
                if front and lock_dx <= 3 and lock_dy <= 2:
                    # A little shadow round the lock.
                    tone -= 1
                colour = tone_colour(colours, tone)
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
            for suffix in ('', '_left', '_right'):
                copper = read(jar, f'assets/minecraft/textures/entity/chest/copper{suffix}.png')
                draw(copper, material, colours, suffix).save(OUT / f'{material}{suffix}.png')
            print('chest', material)


if __name__ == '__main__':
    main()
