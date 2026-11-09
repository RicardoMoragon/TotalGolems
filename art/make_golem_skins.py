"""Paints golem skins in the style of the vanilla copper golem: its own design carried over onto each golem's body.

The copper golem is painted with ten colours in flat panels, bevelled edges, seams and rivets. Each face of a golem
here takes the copper golem's matching face (head for head, arm for arm; its body for parts the copper golem has
not got), stretched to the new size by repeating its middle rows and columns so that its edges and details stay
crisp, with the ten copper shades swapped for ten of the material's. Then the material's own touches go on top.

The eyes are where the copper golem has them; the eyes texture is redrawn to match.

    python art/make_golem_skins.py && python art/make_aging.py && python art/make_effects.py
"""
import colorsys
import math
import sys
import zipfile
import io
from pathlib import Path
from PIL import Image

ART = Path(__file__).resolve().parent
sys.path.insert(0, str(ART))
from golem_geometry import COPPER, GOLEMS  # noqa: E402

TEXTURES = ART.parent / 'src' / 'main' / 'resources' / 'assets' / 'totalgolems' / 'textures' / 'entity' / 'mining_golem'
JAR = Path.home() / '.gradle' / 'caches' / 'fabric-loom' / '26.3' / 'minecraft-client.jar'


def lightness(c):
    return colorsys.rgb_to_hls(*(v / 255 for v in c[:3]))[1]


def read(jar, path):
    return Image.open(io.BytesIO(jar.read(path))).convert('RGBA')


def ramp(block, steps=10, high=1.0, low=0.22, hue_shift=0.12, lift=1.0):
    """
    {@code steps} shades of the block, lightest first: from the colour {@code high} of the way up its own (by
    brightness, 1 its lightest) down to {@code low} of that.
    """
    shades = sorted({block.getpixel((x, y))[:3] for y in range(block.height) for x in range(block.width) if block.getpixel((x, y))[3]}, key=lightness)
    top = min(0.95, lightness(shades[round(high * (len(shades) - 1))]) * lift)
    out = []
    for i in range(steps):
        target = top * (1 - (1 - low) * i / (steps - 1))
        near = min(shades, key=lambda c: abs(lightness(c) - target))
        if abs(lightness(near) - target) < 0.035:
            out.append(near)
            continue
        h, _, s = colorsys.rgb_to_hls(*(v / 255 for v in near))
        turn = (0.75 - h + 0.5) % 1.0 - 0.5
        r, g, b = colorsys.hls_to_rgb((h + turn * hue_shift * i / (steps - 1)) % 1.0, target, min(1.0, s * 1.1))
        out.append((round(r * 255), round(g * 255), round(b * 255)))
    return out


def cubes(parts):
    for p in parts:
        for c in p['cubes']:
            yield p['name'], c
        yield from cubes(p['children'])


def faces(c):
    u, v = c['uv']
    w, h, d = (math.ceil(n) for n in c['box'][3:])
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'east': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}


def stretch(face, width, height, border=2):
    """{@code face} at a new size, its outer {@code border} rows and columns kept and its middle repeated or dropped."""
    def mapping(old, new):
        b = min(border, old // 2, new // 2)
        out = []
        for i in range(new):
            if i < b:
                out.append(i)
            elif i >= new - b:
                out.append(old - (new - i))
            else:
                inner_old, inner_new = old - 2 * b, new - 2 * b
                out.append(b + min(inner_old - 1, (i - b) * inner_old // max(1, inner_new)))
        return out

    xs, ys = mapping(len(face[0]), width), mapping(len(face), height)
    return [[face[y][x] for x in xs] for y in ys]


def noise(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n ^ (n >> 16)) / 0xFFFFFFFF


# ------------------------------------------------------------------------------------ touches of each material
# Each gets a face as rows of shade numbers (0 lightest, 9 darkest; GOLD and on, gold), which side of which cube it is, and changes it.

# Shade numbers from here on are the material's gold, where it has any: lit, then in shadow; then pure white, for glints.
GOLD = 10
WHITE = 13

# The diamond golem's back gem, row by row as seen from behind (see golem_geometry): lit from the upper left, darker
# towards its point, and catching the light in a couple of glints.
W = WHITE
BACK_GEM = {
    'back_gem_top': [[1, 0, W, 1]],
    'back_gem_middle': [[1, W, 0, 1, 1, 2], [0, 1, 1, 1, 2, 3]],
    'back_gem_lower': [[1, 1, 2, 3]],
    'back_gem_bottom': [[2, 3]],
}
# Where it sits on the back of the body, in that face's columns and rows, so that the back can darken right round it.
BACK_GEM_ROWS = {1: range(3, 7), 2: range(2, 8), 3: range(2, 8), 4: range(3, 7), 5: range(4, 6)}


def gem(face, cx, cy, rx, ry):
    """A cut gem centred on (cx, cy): a rhombus lit on its upper left, shaded on its lower right, a dark girdle round it."""
    for y in range(len(face)):
        for x in range(len(face[0])):
            d = abs(x - cx) / rx + abs(y - cy) / ry
            if d <= 1.0:
                up, left = y <= cy, x <= cx
                face[y][x] = 0 if up and left else 1 if up else 2 if left else 4
            elif d <= 1.0 + 1.2 / min(rx, ry):
                face[y][x] = 7
    face[round(cy - ry / 2)][round(cx - rx / 2)] = 0


def sparkle(face, seed, chance):
    h, w = len(face), len(face[0])
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            if face[y][x] <= 2 and face[y - 1][x - 1] <= 2 and face[y + 1][x + 1] <= 3 and noise(x, y, seed) > chance:
                face[y - 1][x - 1], face[y][x] = 0, 0
                face[y + 1][x + 1] = max(face[y + 1][x + 1], 4)


def diamond_touch(face, name, cube, seed):
    """
    Cut, as a diamond is: a large gem set in its chest, a small one on its crown and a bright one set in its back,
    cleavage lines slanting across arms and legs with the light above them, bright tops on its lumps, and glints here
    and there.
    """
    h, w = len(face), len(face[0])
    if cube in BACK_GEM:
        # Brighter than anything round it: its back as drawn, its top in the light, its sides and underside less so.
        for y in range(h):
            for x in range(w):
                face[y][x] = BACK_GEM[cube][y][x] if name == 'south' else 0 if name == 'up' else 4 if name == 'down' else 2
        return
    if cube == 'body' and name == 'north':
        gem(face, (w - 1) / 2, (h - 1) / 2, 3.0, 2.4)
    elif cube == 'body' and name == 'south':
        # The setting: the back darkens right round the gem, darkest where it touches it.
        sparkle(face, seed, 0.88)
        for y in range(h):
            for x in range(w):
                touching = [(x + dx in BACK_GEM_ROWS.get(y + dy, ())) for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1))]
                corner = any(x + dx in BACK_GEM_ROWS.get(y + dy, ()) for dx, dy in ((1, 1), (1, -1), (-1, 1), (-1, -1)))
                if any(touching):
                    face[y][x] = 8
                elif corner:
                    face[y][x] = max(face[y][x], 7)
        return
    elif cube == 'head' and name in ('south', 'up') and w >= 7 and h >= 5:
        gem(face, (w - 1) / 2, (h - 1) / 2, 2.0, 1.6)
    elif cube in ('right_arm', 'left_arm', 'right_leg', 'left_leg') and name not in ('up', 'down'):
        # A cleavage line from the top right down to the bottom left, lit above and shaded below.
        for y in range(h):
            x = round((w - 1) * (1 - y / max(1, h - 1)))
            if 0 < y < h - 1:
                face[y][x] = 6
                if x > 0:
                    face[y][x - 1] = min(face[y][x - 1], 1)
                if x < w - 1:
                    face[y][x + 1] = max(face[y][x + 1], 4)
    elif cube.endswith('lump') or cube.endswith('shoulder'):
        if name == 'up':
            for y in range(h):
                for x in range(w):
                    face[y][x] = 0 if (x + y) % 4 == 0 else 1 if x + y < (w + h) / 2 else 2
    sparkle(face, seed, 0.88)


def netherite_touch(face, name, cube, seed):
    """
    Forged, as netherite is: a riveted breastplate with a seam down its middle, a buckle on the belt, layered plates on
    the shoulders, knee plates, a bright ridge on the crest, the faint layer lines of the netherite block, and dents.
    """
    h, w = len(face), len(face[0])
    if cube == 'body' and name == 'north':
        for y in range(h):
            for x in range(w):
                ring = min(x, y, w - 1 - x, h - 1 - y)
                if ring == 1:
                    face[y][x] = 2 if y == 1 else 6
                elif ring >= 2:
                    face[y][x] = 7 if x in (w // 2 - 1, w // 2) and w % 2 == 0 and x == w // 2 else 4
        # Gold rivets, as netherite is forged with gold.
        for rx in (2, w - 3):
            face[2][rx], face[3][rx] = GOLD, 8
    elif cube == 'belt' and name == 'north':
        for y in range(h):
            for x in range(w):
                face[y][x] = 1 if y == 0 else 5
        # A gold buckle.
        mid = w // 2
        for x in range(mid - 1, mid + 2):
            for y in range(h):
                face[y][x] = GOLD if y == 0 else GOLD + 1
        face[h - 1][mid] = 8
    elif cube.endswith('pauldron'):
        for y in range(h):
            for x in range(w):
                if name == 'up':
                    face[y][x] = 2 if (y // 2) % 2 == 0 else 5
                    if x in (0, w - 1):
                        face[y][x] = 6
                elif y == 0:
                    face[y][x] = 1
                elif y == h - 1:
                    face[y][x] = 7
        if name not in ('up', 'down') and w >= 3 and h >= 3:
            face[1][1], face[1][w - 2] = GOLD, GOLD
    elif cube == 'crest':
        for y in range(h):
            for x in range(w):
                face[y][x] = 0 if (name == 'up' or y == 0) else 3 if y < h - 1 else 6
    elif cube in ('right_leg', 'left_leg') and name == 'north':
        for y in (1, 2):
            for x in range(1, w - 1):
                face[y][x] = 1 if y == 1 else 3
        face[1][0] = face[1][w - 1] = 6
    else:
        # The netherite block's layers: a faint line every third row across the broad panels.
        for y in range(h):
            for x in range(1, w - 1):
                if name not in ('up', 'down') and y % 3 == 2 and 1 <= face[y][x] <= 3:
                    face[y][x] += 1
    # Dents, and the odd bright scratch.
    for y in range(h):
        for x in range(w):
            r = noise(x, y, seed)
            if r > 0.96 and face[y][x] <= 4:
                face[y][x] = 0
            elif r < 0.03 and 2 <= face[y][x] <= 5:
                face[y][x] = 7


MATERIALS = {
    # The golem, the block its colours come from, how far up the block's colours its lightest is, how much lighter
    # than that to make it (the netherite block is too dark to read on a golem otherwise), and its own touch.
    # The diamond's lightest is a bright cyan rather than the block's white, kept for the facets' glints.
    'diamond': ('diamond_block', 0.9, 1.0, diamond_touch),
    'netherite': ('netherite_block', 1.0, 1.35, netherite_touch),
}


def paint(golem, copper, copper_eyes, colours, touch):
    shades = sorted({copper.getpixel((x, y))[:3] for y in range(64) for x in range(64) if copper.getpixel((x, y))[3]}, key=lightness, reverse=True)
    index = {c: i for i, c in enumerate(shades)}
    source = {name: c for name, c in cubes(COPPER)}
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    eyes = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    eye_colour = max((copper_eyes.getpixel((x, y)) for y in range(64) for x in range(64) if copper_eyes.getpixel((x, y))[3]), key=lightness)
    for seed, (part, c) in enumerate(cubes(GOLEMS[golem])):
        from_cube = source.get(c['name']) or source.get(part) or source['body']
        if c['name'] in ('left_arm', 'left_leg') and c['mirror']:
            from_cube = source[c['name'].replace('left', 'right')]
        for name, (fx, fy, fw, fh) in faces(c).items():
            sx, sy, sw, sh = faces(from_cube)[name]
            face = [[index[copper.getpixel((sx + x, sy + y))[:3]] if copper.getpixel((sx + x, sy + y))[3] else 9 for x in range(sw)] for y in range(sh)]
            face = stretch(face, fw, fh)
            eyed = c['name'] == 'head' and name == 'north'
            if eyed:
                # The eyes go where the copper golem's are, carried over by the same stretch.
                where = [[(x, y) in {(ex - sx, ey - sy) for ex in range(64) for ey in range(64)
                                     if copper_eyes.getpixel((ex, ey))[3]} for x in range(sw)] for y in range(sh)]
                where = stretch(where, fw, fh)
            touch(face, name, c['name'] or part, seed * 7 + len(name))
            for y in range(fh):
                for x in range(fw):
                    if fx + x < 64 and fy + y < 64:
                        out.putpixel((fx + x, fy + y), colours[face[y][x]] + (255,))
                        if eyed and where[y][x]:
                            out.putpixel((fx + x, fy + y), colours[8] + (255,))
                            eyes.putpixel((fx + x, fy + y), eye_colour)
    return out, eyes


def colours(jar, block, high, lift):
    """The shades a skin is painted with: the material's ten, the gold's three, and white."""
    gold = ramp(read(jar, 'assets/minecraft/textures/block/gold_block.png'), steps=3, high=0.8, low=0.6)
    return ramp(read(jar, f'assets/minecraft/textures/block/{block}.png'), high=high, lift=lift) + gold + [(255, 255, 255)]


def main(names=None):
    with zipfile.ZipFile(JAR) as jar:
        copper = read(jar, 'assets/minecraft/textures/entity/copper_golem/copper_golem.png')
        copper_eyes = read(jar, 'assets/minecraft/textures/entity/copper_golem/copper_golem_eyes.png')
        for golem, (block, high, lift, touch) in MATERIALS.items():
            if names and golem not in names:
                continue
            old_eyes = Image.open(TEXTURES / f'{golem}_golem_eyes.png').convert('RGBA')
            skin, eyes = paint(golem, copper, copper_eyes, colours(jar, block, high, lift), touch)
            # The eyes keep the colour they had.
            lit = [old_eyes.getpixel((x, y)) for y in range(64) for x in range(64) if old_eyes.getpixel((x, y))[3]]
            colour = max(lit, key=lightness) if lit else None
            if colour:
                for y in range(64):
                    for x in range(64):
                        if eyes.getpixel((x, y))[3]:
                            eyes.putpixel((x, y), colour)
            skin.save(TEXTURES / f'{golem}_golem.png')
            eyes.save(TEXTURES / f'{golem}_golem_eyes.png')
            print('skin', golem)


if __name__ == '__main__':
    main(sys.argv[1:])
