"""Draws the worn skins of the golems, which wear like the copper golem: three stages, each further gone.

- Iron rusts, diamond clouds over, netherite and coal turn to ash, amethyst whitens like calcite,
  hay rots brown with spots of mould, and bone yellows and grows moss.

Each stage covers more of the golem: patches grow from the same spots, so a golem scraped back a stage
loses the newest wear first. Run after changing a golem's texture: python art/make_aging.py
"""
import math
import random
from pathlib import Path
from PIL import Image

ART = Path(__file__).resolve().parent
GOLEMS = ART.parent / 'src' / 'main' / 'resources' / 'assets' / 'totalgolems' / 'textures' / 'entity' / 'mining_golem'

# For each golem: the colour all of it drifts towards, the colour of its patches, and the colour of the specks
# that come with the last stage (None for none).
WEAR = {
    'iron': ((170, 150, 140), (150, 78, 38), (95, 48, 28)),
    'diamond': ((160, 185, 185), (205, 215, 210), (90, 105, 105)),
    'netherite': ((105, 98, 98), (140, 132, 128), None),
    'coal': ((95, 92, 90), (150, 146, 140), None),
    'amethyst': ((160, 140, 170), (215, 208, 210), None),
    'hay': ((150, 130, 80), (115, 95, 60), (70, 82, 45)),
    'bone': ((215, 200, 150), (195, 170, 100), (85, 115, 55)),
}
# How much of the golem the patches cover, and how far all of it has drifted, at each stage.
COVER = (0.22, 0.45, 0.72)
DRIFT = (0.12, 0.25, 0.4)


def noise(width, height, seed, scale=6.0):
    """Smooth value noise from 0 to 1, a few octaves of it."""
    rng = random.Random(seed)
    total = [[0.0] * width for _ in range(height)]
    weight = 0.0
    for octave, amplitude in ((1, 1.0), (2, 0.5), (4, 0.25)):
        size = scale / octave
        cols, rows = int(width / size) + 2, int(height / size) + 2
        grid = [[rng.random() for _ in range(cols)] for _ in range(rows)]
        for y in range(height):
            for x in range(width):
                gx, gy = x / size, y / size
                x0, y0 = int(gx), int(gy)
                tx, ty = gx - x0, gy - y0
                tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
                top = grid[y0][x0] * (1 - tx) + grid[y0][x0 + 1] * tx
                bottom = grid[y0 + 1][x0] * (1 - tx) + grid[y0 + 1][x0 + 1] * tx
                total[y][x] += (top * (1 - ty) + bottom * ty) * amplitude
        weight += amplitude
    return [[v / weight for v in row] for row in total]


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def luminance(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


for golem, (drift, patch, speck) in WEAR.items():
    source = GOLEMS / f'{golem}_golem.png'
    image = Image.open(source).convert('RGBA')
    field = noise(image.width, image.height, golem + '_wear')
    # The noise is ranked, so that a stage covering a fifth of the golem covers a fifth of its pixels.
    ranked = sorted(v for row in field for v in row)
    specks = random.Random(golem + '_specks')
    speckled = {(x, y) for y in range(image.height) for x in range(image.width) if specks.random() < 0.035}
    for stage, (cover, amount) in enumerate(zip(COVER, DRIFT), start=1):
        cut = ranked[int(cover * (len(ranked) - 1))]
        out = image.copy()
        for y in range(image.height):
            for x in range(image.width):
                p = image.getpixel((x, y))
                if p[3] == 0:
                    continue
                # Keep the shading: the worn colour is brought to the pixel's own brightness.
                shade = luminance(p) / 160.0
                colour = mix(p, tuple(min(255, int(c * shade)) for c in drift), amount)
                v = field[y][x]
                if v < cut:
                    # Full wear in the middle of a patch, fading out to its edge.
                    depth = min(1.0, (cut - v) / 0.06)
                    colour = mix(colour, tuple(min(255, int(c * (0.6 + 0.4 * shade))) for c in patch), 0.75 * depth)
                    if speck is not None and stage == 3 and (x, y) in speckled:
                        colour = speck
                out.putpixel((x, y), colour + (p[3],))
        out.save(GOLEMS / f'{golem}_golem_aged_{stage}.png')
    print('aged', golem)
