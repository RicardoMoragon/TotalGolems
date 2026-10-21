"""Draws the chests' still textures in the vanilla style, from the copper chest.

Each chest is the vanilla copper chest (single, left and right halves) with its copper palette swapped for one
taken from the material's own vanilla block: the frame round every face, the braces across the lid and sides
and the grain stay as Mojang drew them, only the colours change. The palette is kept short, as vanilla's:
nine shades spread evenly from the block's lightest colour to a dark one for the frame, the block's own colours
where it has them and its colours darkened, shifted towards violet as shadows are, where it does not.

The lock and the black inside of the chest are left as they are. The textures go to art/textures/chest, from
which make_effects.py makes the mod's animated ones: run both after changing anything here.

    python art/make_chests.py && python art/make_effects.py
"""
import colorsys
import io
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
# The lock, drawn at the top left of the texture: kept grey as on every vanilla chest.
LOCK = (6, 5)


def luminance(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def read(jar, path):
    return Image.open(io.BytesIO(jar.read(path))).convert('RGBA')


def shade(colour, lightness, hue_shift):
    """{@code colour} brought to {@code lightness} (0 to 1), its hue turned a little towards violet, the short way
    round, as shadows are: warm colours through red, cool ones through blue, never through green."""
    h, l, s = colorsys.rgb_to_hls(*(c / 255 for c in colour[:3]))
    turn = (0.75 - h + 0.5) % 1.0 - 0.5
    r, g, b = colorsys.hls_to_rgb((h + turn * hue_shift) % 1.0, lightness, min(1.0, s * 1.15))
    return (round(r * 255), round(g * 255), round(b * 255))


def lightness(colour):
    return colorsys.rgb_to_hls(*(c / 255 for c in colour[:3]))[1]


def ramp(block):
    """
    Nine shades, darkest first, evenly spread from the block's lightest down to a quarter of it, so that however
    pale the block the frame and braces read as clearly as the copper chest's. Where the block has a colour of about
    the right lightness that is the one used; between them, its nearest colour is darkened, shifted towards violet.
    """
    shades = sorted({block.getpixel((x, y))[:3] for y in range(block.height) for x in range(block.width) if block.getpixel((x, y))[3]}, key=lightness)
    top = lightness(shades[-1])
    colours = []
    for i in range(9):
        target = top * (0.25 + 0.75 * i / 8)
        nearest = min(shades, key=lambda c: abs(lightness(c) - target))
        colours.append(nearest if abs(lightness(nearest) - target) < 0.04 else shade(nearest, target, 0.15 * (1 - i / 8)))
    return colours


def recolour(chest, colours):
    """The chest with each copper shade swapped for the one of {@code colours} at the same place between darkest and lightest."""
    copper = {chest.getpixel((x, y))[:3] for y in range(chest.height) for x in range(chest.width)
              if chest.getpixel((x, y))[3] and chest.getpixel((x, y))[:3] != (0, 0, 0) and not (x < LOCK[0] and y < LOCK[1])}
    low, high = min(map(luminance, copper)), max(map(luminance, copper))
    swap = {c: colours[round((luminance(c) - low) / (high - low) * (len(colours) - 1))] for c in copper}
    out = chest.copy()
    for y in range(chest.height):
        for x in range(chest.width):
            p = chest.getpixel((x, y))
            if p[3] and p[:3] in swap and not (x < LOCK[0] and y < LOCK[1]):
                out.putpixel((x, y), swap[p[:3]] + (p[3],))
    return out


with zipfile.ZipFile(JAR) as jar:
    for material, block in BLOCKS.items():
        colours = ramp(read(jar, f'assets/minecraft/textures/block/{block}.png'))
        for suffix in ('', '_left', '_right'):
            chest = read(jar, f'assets/minecraft/textures/entity/chest/copper{suffix}.png')
            recolour(chest, colours).save(OUT / f'{material}{suffix}.png')
        print('chest', material)
