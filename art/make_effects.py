"""Builds the animated textures of the chests and the golems from their still ones.

- Iron, diamond, netherite and amethyst: glints that light up a pixel or a small cross here and there and fade.
- Bone: cracks that open a pixel at a time, stay a moment and close again.
- Coal golem: the core seen through the holes in its shell glows, each pixel brightening and dimming at its
  own pace.

The chests are animated the way vanilla animates lava: their texture in the mod is a strip of frames with a
.mcmeta beside it. Their still textures, the ones to edit (in Blockbench, see make_bbmodels.py), are in
art/textures/chest. The golems keep their textures and get a layer drawn over them, a picture per frame, in
textures/entity/mining_golem/effect, which the renderer steps through.

Run after changing any of those textures: python art/make_effects.py
"""
import json
import math
import random
import sys
from pathlib import Path
from PIL import Image

ART = Path(__file__).resolve().parent
ASSETS = ART.parent / 'src' / 'main' / 'resources' / 'assets' / 'totalgolems' / 'textures' / 'entity'
CHEST_SOURCES = ART / 'textures' / 'chest'
GOLEMS = ASSETS / 'mining_golem'
EFFECTS = GOLEMS / 'effect'

# Frames in a loop and ticks per frame, for each kind of effect. The golem renderer has the same numbers.
GLINT = (48, 5)
CRACK = (64, 3)
EMBER = (32, 5)

GLINT_TINT = {
    'iron': (255, 255, 255),
    'diamond': (220, 255, 255),
    'netherite': (235, 225, 245),
    'amethyst': (255, 225, 255),
}


def luminance(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def surface(image, inside=None):
    """
    The pixels the effects go on: drawn, and not among the darkest third, which are lines and insides; and, if
    {@code inside} is given, only those in it.
    """
    pixels = [(x, y) for y in range(image.height) for x in range(image.width) if image.getpixel((x, y))[3] > 0]
    levels = sorted(luminance(image.getpixel(p)) for p in pixels)
    cut = levels[len(levels) // 3] if levels else 0
    return [p for p in pixels if luminance(image.getpixel(p)) >= cut and (inside is None or p in inside)]


def glints(image, frames, seed, inside=None):
    """For each frame, how bright each glinting pixel is, from 0 to 1."""
    rng = random.Random(seed)
    spots = surface(image, inside)
    allowed = set(spots)
    lit = [dict() for _ in range(frames)]
    count = max(4, len(spots) // 45)
    # Each glint comes up, peaks and dies away over six frames.
    shape = (0.2, 0.5, 1.0, 0.7, 0.4, 0.15)
    for _ in range(count):
        x, y = rng.choice(spots)
        start = rng.randrange(frames)
        cross = rng.random() < 0.4
        for step, level in enumerate(shape):
            frame = lit[(start + step) % frames]
            frame[(x, y)] = max(frame.get((x, y), 0.0), level)
            if cross and level == 1.0:
                for near in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if near in allowed:
                        frame[near] = max(frame.get(near, 0.0), 0.5)
    return lit


DIRECTIONS = ((1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1))


def cracks(image, frames, seed, inside=None):
    """For each frame, the pixels that are cracked."""
    rng = random.Random(seed)
    spots = surface(image, inside)
    allowed = set(spots)
    cracked = [set() for _ in range(frames)]
    for _ in range(max(2, len(spots) // 450)):
        path = [rng.choice(spots)]
        heading = rng.randrange(8)
        for _ in range(rng.randint(3, 6)):
            x, y = path[-1]
            # Mostly on in one direction, now and then a kink to one side.
            if rng.random() < 0.4:
                heading = (heading + rng.choice((-1, 1))) % 8
            dx, dy = DIRECTIONS[heading]
            step = (x + dx, y + dy)
            if step not in allowed or step in path:
                break
            path.append(step)
        start = rng.randrange(frames)
        length = len(path)
        # Opens a pixel a frame, stays four frames, closes from where it began.
        for t in range(2 * length + 4):
            if t < length:
                shown = path[:t + 1]
            elif t < length + 4:
                shown = path
            else:
                shown = path[t - length - 4 + 1:]
            cracked[(start + t) % frames].update(shown)
    return cracked


def crack_colour(p):
    return (int(p[0] * 0.45), int(p[1] * 0.42), int(p[2] * 0.38), 255)


def write_strip(frames, path, frametime, interpolate):
    strip = Image.new('RGBA', (frames[0].width, frames[0].height * len(frames)))
    for i, frame in enumerate(frames):
        strip.paste(frame, (0, i * frames[0].height))
    strip.save(path)
    Path(str(path) + '.mcmeta').write_text(json.dumps({'animation': {'frametime': frametime, 'interpolate': interpolate}}, indent=2) + '\n')


# ------------------------------------------------------------------------------------ chests

def face_insides(name):
    """
    The pixels of a chest texture inside its faces, leaving out each face's outline (its outermost ring of
    pixels, where the dark border lines are) and the lock: the only ones the effects may change.
    """
    elements = json.loads((ART / 'chest_iron.bbmodel').read_text(encoding='utf-8'))['elements']
    texture = 1 if name.endswith('_right') else 2 if name.endswith('_left') else 0
    inside = set()
    for element in elements:
        if element['name'].startswith('lock'):
            continue
        for face in element['faces'].values():
            if face.get('texture') != texture:
                continue
            u1, v1, u2, v2 = face['uv']
            left, right, top, bottom = int(min(u1, u2)), int(max(u1, u2)), int(min(v1, v2)), int(max(v1, v2))
            for y in range(top + 1, bottom - 1):
                for x in range(left + 1, right - 1):
                    inside.add((x, y))
    return inside


for source in sorted(CHEST_SOURCES.glob('*.png')):
    name = source.stem
    material = name.split('_')[0]
    image = Image.open(source).convert('RGBA')
    inside = face_insides(name)
    target = ASSETS / 'chest' / source.name
    meta = Path(str(target) + '.mcmeta')
    if material in GLINT_TINT:
        frames, ticks = GLINT
        tint = GLINT_TINT[material]
        out = []
        for lit in glints(image, frames, name, inside):
            frame = image.copy()
            for (x, y), level in lit.items():
                p = frame.getpixel((x, y))
                frame.putpixel((x, y), tuple(int(p[i] + (tint[i] - p[i]) * 0.85 * level) for i in range(3)) + (p[3],))
            out.append(frame)
        write_strip(out, target, ticks, True)
    elif material == 'bone':
        frames, ticks = CRACK
        out = []
        for cracked in cracks(image, frames, name, inside):
            frame = image.copy()
            for x, y in cracked:
                frame.putpixel((x, y), crack_colour(frame.getpixel((x, y))))
            out.append(frame)
        write_strip(out, target, ticks, False)
    else:
        image.save(target)
        meta.unlink(missing_ok=True)
    print('chest', name)

# ------------------------------------------------------------------------------------ golems

EFFECTS.mkdir(exist_ok=True)
for old in EFFECTS.glob('*.png'):
    old.unlink()

def back_gem(image):
    """
    The diamond golem's back gem: its pixels, which glow softly so that it shows bright on whatever side the light
    is, and among them its white glints, which pulse.
    """
    sys.path.insert(0, str(ART))
    from golem_geometry import GOLEMS as SHAPES  # noqa: E402
    from make_golem_skins import cubes, faces  # noqa: E402
    pixels = set()
    for _, c in cubes(SHAPES['diamond']):
        if (c['name'] or '').startswith('back_gem'):
            for name in ('south', 'up'):
                fx, fy, fw, fh = faces(c)[name]
                pixels.update((x, y) for y in range(fy, fy + fh) for x in range(fx, fx + fw))
    white = {p for p in pixels if image.getpixel(p)[:3] == (255, 255, 255)}
    return pixels, white


for golem in ('iron', 'diamond', 'netherite', 'amethyst'):
    image = Image.open(GOLEMS / f'{golem}_golem.png').convert('RGBA')
    tint = GLINT_TINT[golem]
    gem, gem_glints = back_gem(image) if golem == 'diamond' else (set(), set())
    for i, lit in enumerate(glints(image, GLINT[0], golem + '_golem')):
        for p in gem:
            # Its glints come and go twice round the loop, one after the other.
            pulse = 0.5 + 0.5 * math.sin(2 * math.pi * (2 * i / GLINT[0] + 0.5 * sorted(gem_glints).index(p))) if p in gem_glints else 0.0
            lit[p] = max(lit.get(p, 0.0), 0.55 + 0.45 * pulse if p in gem_glints else 0.55)
        # Drawn lit, like the eyes, and blended over the golem by its alpha: the glint's full colour, as strong as
        # it is bright. (Not added: a dimmed colour at full alpha would darken the pixel instead.)
        frame = Image.new('RGBA', image.size, (0, 0, 0, 0))
        for (x, y), level in lit.items():
            p = image.getpixel((x, y))
            frame.putpixel((x, y), tuple(int(p[c] * 0.4 + tint[c] * 0.6) for c in range(3)) + (int(255 * 0.85 * level),))
        frame.save(EFFECTS / f'{golem}_{i}.png')
    print('golem', golem)

image = Image.open(GOLEMS / 'bone_golem.png').convert('RGBA')
for i, cracked in enumerate(cracks(image, CRACK[0], 'bone_golem')):
    # Drawn over the golem: only the cracks show.
    frame = Image.new('RGBA', image.size, (0, 0, 0, 0))
    for x, y in cracked:
        frame.putpixel((x, y), crack_colour(image.getpixel((x, y))))
    frame.save(EFFECTS / f'bone_{i}.png')
print('golem bone')

image = Image.open(GOLEMS / 'coal_golem.png').convert('RGBA')
embers = [(x, y) for y in range(image.height) for x in range(image.width)
          if image.getpixel((x, y))[3] > 0 and image.getpixel((x, y))[0] > image.getpixel((x, y))[2] + 60]
rng = random.Random('coal_golem')
paces = {p: (rng.randint(1, 3), rng.random() * 2 * math.pi, rng.randint(2, 5), rng.random() * 2 * math.pi) for p in embers}
frames = EMBER[0]
for i in range(frames):
    frame = Image.new('RGBA', image.size, (0, 0, 0, 0))
    for (x, y), (slow, phase, fast, flicker) in paces.items():
        t = 2 * math.pi * i / frames
        # A slow swell and a quicker flicker, both looping with the frames.
        level = 0.55 + 0.3 * math.sin(slow * t + phase) + 0.15 * math.sin(fast * t + flicker)
        level = max(0.0, min(1.0, level))
        frame.putpixel((x, y), (int(255 * level), int((120 + 60 * level) * level), int(30 * level), 255))
    frame.save(EFFECTS / f'coal_{i}.png')
print('golem coal', len(embers), 'embers')
