"""Generates the textures of the mod. Run from the repository root:

    python tools/generate_textures.py

Textures that already exist are left alone, so ones edited by hand are safe; pass --force to
regenerate everything.

Textures are painted per cuboid face using the same box-UV layout Minecraft's ModelPart uses, so the
shapes below mirror the cuboids of the vanilla copper golem and chest models.
"""

import hashlib
import os
import sys

from PIL import Image

FORCE = "--force" in sys.argv
ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "totalgolems")

PALETTES = {
    "iron": {
        "light": (236, 236, 236),
        "base": (206, 206, 206),
        "mid": (170, 170, 170),
        "dark": (124, 124, 124),
        "shadow": (84, 84, 84),
        "inside": (38, 38, 42),
        "accent": (255, 190, 70),
        "accent_dark": (190, 110, 30),
    },
    "diamond": {
        "light": (214, 255, 250),
        "base": (118, 236, 226),
        "mid": (74, 208, 200),
        "dark": (38, 160, 158),
        "shadow": (20, 104, 110),
        "inside": (12, 44, 52),
        "accent": (255, 255, 255),
        "accent_dark": (150, 240, 255),
    },
    "netherite": {
        "light": (112, 104, 110),
        "base": (78, 72, 78),
        "mid": (62, 57, 62),
        "dark": (46, 42, 46),
        "shadow": (28, 25, 29),
        "inside": (12, 10, 12),
        "accent": (255, 150, 60),
        "accent_dark": (190, 80, 30),
    },
    "amethyst": {
        "light": (200, 144, 240),
        "base": (141, 106, 204),
        "mid": (122, 91, 181),
        "dark": (100, 71, 158),
        "shadow": (72, 45, 122),
        "inside": (30, 18, 52),
        "accent": (254, 203, 230),
        "accent_dark": (200, 144, 240),
    },
    "sponge": {
        "light": (225, 227, 81),
        "base": (205, 206, 74),
        "mid": (193, 188, 78),
        "dark": (183, 172, 79),
        "shadow": (157, 143, 57),
        "inside": (70, 62, 24),
        "accent": (240, 240, 150),
        "accent_dark": (157, 143, 57),
    },
    "hay": {
        "light": (203, 182, 48),
        "base": (191, 171, 49),
        "mid": (171, 146, 37),
        "dark": (148, 128, 30),
        # The frame takes the colour of the twine round a bale.
        "shadow": (146, 65, 35),
        "inside": (60, 40, 14),
        "accent": (230, 205, 70),
        "accent_dark": (164, 81, 43),
    },
    "bone": {
        "light": (240, 238, 224),
        "base": (233, 230, 212),
        "mid": (225, 221, 202),
        "dark": (205, 200, 176),
        "shadow": (160, 154, 128),
        "inside": (60, 56, 44),
        "accent": (250, 250, 240),
        "accent_dark": (190, 184, 160),
    },
}

# Golems painted like the block they are built from: the block's own colours, darkest first, with no
# outlines. "glint" is the occasional bright speck, "off" the unlit eyes and "lit" the eyes' glow.
BLOCK_GOLEMS = {
    "coal": {
        "colors": [(5, 5, 5), (13, 13, 13), (21, 21, 21), (31, 30, 30), (41, 40, 40)],
        "glint": None,
        "off": (58, 56, 60),
        "lit": [(214, 110, 30), (240, 160, 70), (255, 225, 170)],
    },
    "amethyst": {
        "colors": [(93, 58, 154), (100, 71, 158), (122, 91, 181), (141, 106, 204), (166, 120, 241), (200, 144, 240)],
        "glint": (254, 203, 230),
        "off": (58, 34, 100),
        "lit": [(214, 160, 245), (236, 196, 248), (255, 238, 252)],
    },
    "sponge": {
        "colors": [(157, 143, 57), (183, 172, 79), (193, 188, 78), (205, 206, 74), (225, 227, 81)],
        "glint": None,
        # The holes of the sponge.
        "pores": (122, 108, 40),
        "off": (104, 94, 38),
        "lit": [(90, 170, 230), (140, 205, 245), (215, 240, 255)],
    },
    "bone": {
        "colors": [(205, 200, 176), (222, 218, 197), (225, 221, 202), (230, 226, 210), (233, 230, 212)],
        "glint": None,
        "off": (118, 114, 98),
        "lit": [(200, 60, 60), (235, 110, 100), (255, 200, 190)],
    },
}


UPGRADED_EYES = [(226, 148, 42), (250, 202, 92), (255, 242, 196)]


def save_image(image, *path):
    target = os.path.join(ASSETS, *path)
    if os.path.exists(target) and not FORCE:
        return
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target)


def noise(*key):
    return hashlib.md5(repr(key).encode()).digest()[0] / 255.0


def metal(pal, seed, x, y):
    """Brushed metal: mostly the base colour with short horizontal streaks of its neighbours."""
    n = noise(seed, x // 2, y)
    if n < 0.16:
        return pal["light"]
    if n > 0.80:
        return pal["mid"]
    return pal["base"]


def plate(pal, seed, x, y, w, h, edges="lrtb"):
    """A bevelled plate. `edges` lists which sides are real edges (halves of a double chest lack one)."""
    left, right, top, bottom = ("l" in edges and x == 0), ("r" in edges and x == w - 1), ("t" in edges and y == 0), ("b" in edges and y == h - 1)
    if right or bottom:
        return pal["dark"]
    if left or top:
        return pal["light"]
    return metal(pal, seed, x, y)


class Canvas:
    def __init__(self, size=64):
        self.image = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    def put(self, x, y, color):
        if color is not None:
            self.image.putpixel((x, y), tuple(color) + (255,) if len(color) == 3 else tuple(color))

    def box(self, u, v, w, h, d, painters, hidden=()):
        """Paints a cuboid's faces. Painters get indices along the model axes, as ModelPart.Cube maps them:
        down/up -> (x, z), north/south -> (x, y), west/east -> (z, y), all counted from the min corner."""
        faces = {
            "down": (u + d, v, w, d, lambda i, j: (i, d - 1 - j)),
            "up": (u + d + w, v, w, d, lambda i, j: (i, d - 1 - j)),
            "west": (u, v + d, d, h, lambda i, j: (d - 1 - i, j)),
            "north": (u + d, v + d, w, h, lambda i, j: (i, j)),
            "east": (u + d + w, v + d, d, h, lambda i, j: (i, j)),
            "south": (u + d + w + d, v + d, w, h, lambda i, j: (w - 1 - i, j)),
        }
        for name, (fu, fv, fw, fh, to_model) in faces.items():
            painter = painters.get(name, painters.get("*"))
            if name in hidden or painter is None:
                continue
            for j in range(fh):
                for i in range(fw):
                    self.put(fu + i, fv + j, painter(*to_model(i, j)))

    def save(self, *path):
        save_image(self.image, *path)


# --------------------------------------------------------------------------------------------- golems
# Entity models are drawn with Y pointing down, so y = 0 is the top of each cuboid and north is the front.

EYES = [(1, 1), (2, 1), (1, 2), (2, 2), (5, 1), (6, 1), (5, 2), (6, 2)]
CORE = [(3, 2), (4, 2), (3, 3), (4, 3)]


def golem(tier):
    pal = PALETTES[tier]
    c = Canvas()

    def simple(seed, w, h, d):
        return {
            "north": lambda x, y: plate(pal, seed + "n", x, y, w, h),
            "south": lambda x, y: plate(pal, seed + "s", x, y, w, h),
            "west": lambda z, y: plate(pal, seed + "w", z, y, d, h),
            "east": lambda z, y: plate(pal, seed + "e", z, y, d, h),
            "down": lambda x, z: plate(pal, seed + "d", x, z, w, d),
            "up": lambda x, z: pal["shadow"],
        }

    # Head: 8 x 5 x 10.
    head = simple("head", 8, 5, 10)

    def head_front(x, y):
        if (x, y) in EYES:
            return pal["accent"] if y == 2 else pal["accent_dark"]
        if y == 0 and x in (1, 2, 5, 6):
            return pal["shadow"]
        return plate(pal, "headn", x, y, 8, 5)

    def head_side(z, y):
        if 4 <= z <= 5 and 1 <= y <= 2:
            return pal["shadow"] if (z, y) == (5, 2) else pal["dark"]
        return plate(pal, "heads", z, y, 10, 5)

    def head_back(x, y):
        if y in (1, 3) and 2 <= x <= 5:
            return pal["dark"]
        return plate(pal, "headb", x, y, 8, 5)

    def head_top(x, z):
        if 3 <= x <= 4 and 4 <= z <= 5:
            return pal["shadow"]
        if 2 <= x <= 5 and 3 <= z <= 6:
            return pal["mid"]
        return plate(pal, "headt", x, z, 8, 10)

    head.update(north=head_front, south=head_back, west=head_side, east=head_side, down=head_top)
    c.box(0, 0, 8, 5, 10, head)

    # Nose: 2 x 3 x 2.
    c.box(56, 0, 2, 3, 2, {"*": lambda a, b: pal["dark"] if b == 2 else pal["mid"], "down": lambda x, z: pal["base"]})
    # Antenna rod (2 x 4 x 2) and knob (4 x 4 x 4).
    c.box(37, 8, 2, 4, 2, {"*": lambda a, b: pal["shadow"] if a == 1 else pal["dark"]})
    knob = simple("knob", 4, 4, 4)
    knob["down"] = lambda x, z: pal["accent"] if 1 <= x <= 2 and 1 <= z <= 2 else plate(pal, "knobt", x, z, 4, 4)
    c.box(37, 0, 4, 4, 4, knob)

    # Body: 8 x 6 x 6.
    body = simple("body", 8, 6, 6)

    def body_front(x, y):
        if (x, y) in CORE:
            return pal["accent"] if y == 2 else pal["accent_dark"]
        if 2 <= x <= 5 and 1 <= y <= 4:
            return pal["shadow"] if x in (2, 5) or y in (1, 4) else pal["dark"]
        return plate(pal, "bodyn", x, y, 8, 6)

    def body_back(x, y):
        if (x, y) in ((1, 1), (6, 1), (1, 4), (6, 4)):
            return pal["shadow"]
        return plate(pal, "bodys", x, y, 8, 6)

    body.update(north=body_front, south=body_back)
    c.box(0, 15, 8, 6, 6, body)

    # Arms: 3 x 10 x 4. A lighter shoulder, a wrist band and a darker hand.
    def arm_side(seed, width):
        def paint(a, y):
            if y == 7:
                return pal["shadow"]
            if y >= 8:
                return pal["dark"] if a < width - 1 else pal["shadow"]
            if y <= 1:
                return pal["light"] if a < width - 1 else pal["base"]
            return plate(pal, seed, a, y, width, 10)

        return paint

    for seed, u in (("rarm", 36), ("larm", 50)):
        arm = {name: arm_side(seed + name, 3 if name in ("north", "south") else 4) for name in ("north", "south", "west", "east")}
        arm["down"] = lambda x, z: pal["light"]
        arm["up"] = lambda x, z: pal["shadow"]
        c.box(u, 16, 3, 10, 4, arm)

    # Legs: 4 x 5 x 4 with a dark foot.
    for seed, u in (("rleg", 0), ("lleg", 16)):
        leg = simple(seed, 4, 5, 4)
        for name in ("north", "south", "west", "east"):
            leg[name] = (lambda s: lambda a, y: pal["shadow"] if y == 4 else plate(pal, s, a, y, 4, 5))(seed + name)
        c.box(u, 27, 4, 5, 4, leg)

    c.save("textures", "entity", "mining_golem", f"{tier}_golem.png")

    # Emissive layer: only the eyes and the core glow.
    glow = Canvas()
    for x, y in EYES:
        glow.put(10 + x, 10 + y, pal["accent"])
    for x, y in CORE:
        glow.put(6 + x, 21 + y, pal["accent"])
    glow.save("textures", "entity", "mining_golem", f"{tier}_golem_eyes.png")


def block_golem(name):
    """A golem with the look of its block: blotches of the block's colours over every face."""
    style = BLOCK_GOLEMS[name]
    colors = style["colors"]
    c = Canvas()

    def surface(seed, darker=0):
        def paint(a, b):
            if style["glint"] and darker == 0 and noise(name, seed, "glint", a, b) > 0.95:
                return style["glint"]
            if style.get("pores") and noise(name, seed, "pore", a, b) > 0.87:
                return style["pores"]
            value = 0.55 * noise(name, seed, a // 2, b // 2) + 0.45 * noise(name, seed, "fine", a, b)
            return colors[max(0, min(len(colors) - 1, int(value * len(colors))) - darker)]

        return paint

    def part(seed, darker=0):
        painters = {face: surface(seed + face, darker) for face in ("north", "south", "west", "east", "down")}
        # The underside is never lit.
        painters["up"] = lambda a, b: colors[0]
        return painters

    head = part("head")
    front = head["north"]
    head["north"] = lambda x, y: style["off"] if (x, y) in EYES else front(x, y)
    c.box(0, 0, 8, 5, 10, head)
    c.box(56, 0, 2, 3, 2, part("nose", 1))
    c.box(37, 8, 2, 4, 2, part("rod", 2))
    c.box(37, 0, 4, 4, 4, part("knob"))

    body = part("body")
    chest_front = body["north"]
    body["north"] = lambda x, y: style["off"] if (x, y) in CORE else chest_front(x, y)
    c.box(0, 15, 8, 6, 6, body)

    # Hands and feet a shade darker than the limb.
    for seed, u in (("rarm", 36), ("larm", 50)):
        arm = part(seed)
        for face in ("north", "south", "west", "east"):
            arm[face] = (lambda limb, hand: lambda a, y: hand(a, y) if y >= 8 else limb(a, y))(arm[face], surface(seed + face, 1))
        c.box(u, 16, 3, 10, 4, arm)
    for seed, u in (("rleg", 0), ("lleg", 16)):
        leg = part(seed)
        for face in ("north", "south", "west", "east"):
            leg[face] = (lambda limb, foot: lambda a, y: foot(a, y) if y == 4 else limb(a, y))(leg[face], surface(seed + face, 1))
        c.box(u, 27, 4, 5, 4, leg)
    c.save("textures", "entity", "mining_golem", f"{name}_golem.png")
    glow_layer(name, style["lit"])


def glow_layer(name, lit):
    """The eyes texture, only drawn while the golem is working: the eyes and the core, brightest at one corner."""
    glow = Canvas()
    dim, medium, bright = lit
    for u, v in ((11, 11), (15, 11)):
        glow.put(u, v, dim)
        glow.put(u + 1, v, medium)
        glow.put(u, v + 1, medium)
        glow.put(u + 1, v + 1, bright)
    glow.put(9, 23, bright)
    glow.put(10, 23, medium)
    glow.put(9, 24, medium)
    glow.put(10, 24, dim)
    glow.save("textures", "entity", "mining_golem", f"{name}_golem_eyes.png")


def scarecrow_golem():
    """The hay golem: a sackcloth head, a checked shirt, patched trousers and straw sticking out everywhere."""
    name = "hay"
    sack = [(150, 122, 74), (172, 142, 88), (188, 158, 100), (202, 174, 116)]
    straw = [(171, 146, 37), (191, 171, 49), (203, 182, 48), (230, 205, 70)]
    shirt = [(120, 44, 34), (146, 58, 42), (168, 74, 52)]
    trousers = [(52, 66, 104), (64, 82, 126), (78, 98, 146)]
    wood = [(92, 64, 38), (112, 80, 48)]
    c = Canvas()

    def cloth(colors, seed):
        return lambda a, b: colors[int(noise(name, seed, a, b) * len(colors)) % len(colors)]

    def strawy(seed):
        return cloth(straw, seed)

    def part(painter):
        painters = {face: painter for face in ("north", "south", "west", "east", "down")}
        painters["up"] = lambda a, b: sack[0]
        return painters

    head = part(cloth(sack, "head"))
    weave = head["north"]

    def face(x, y):
        if (x, y) in EYES:
            return (48, 34, 22)
        # A stitched mouth under the nose.
        if y == 4 and x in (1, 2, 5, 6):
            return sack[0]
        return weave(x, y)

    head["north"] = face
    # Straw hair showing under the hat, all round the top of the head.
    for side in ("north", "south", "west", "east"):
        head[side] = (lambda base: lambda a, y: straw[int(noise(name, "hair", a) * 4)] if y == 0 and (a, y) not in EYES else base(a, y))(head[side])
    head["down"] = strawy("hat")
    c.box(0, 0, 8, 5, 10, head)
    # A carrot for a nose, a stick for the antenna and a straw knob on top.
    c.box(56, 0, 2, 3, 2, part(cloth([(214, 118, 30), (232, 140, 44)], "nose")))
    c.box(37, 8, 2, 4, 2, part(cloth(wood, "rod")))
    c.box(37, 0, 4, 4, 4, part(strawy("knob")))

    def checked(a, b):
        return shirt[((a // 2) + (b // 2)) % 2 + (1 if noise(name, "shirt", a, b) > 0.7 else 0)]

    body = part(checked)

    def chest_front(x, y):
        if (x, y) in CORE:
            return (48, 34, 22)
        # Straw at the collar.
        return straw[int(noise(name, "collar", x) * 4)] if y == 0 and x % 2 == 0 else checked(x, y)

    body["north"] = chest_front
    c.box(0, 15, 8, 6, 6, body)

    # Sleeves ending in bunches of straw for hands.
    for seed, u in (("rarm", 36), ("larm", 50)):
        arm = part(lambda a, y: strawy(seed)(a, y) if y >= 7 else checked(a, y))
        c.box(u, 16, 3, 10, 4, arm)
    for seed, u in (("rleg", 0), ("lleg", 16)):
        leg = part((lambda s: lambda a, y: strawy(s)(a, y) if y == 4 else cloth(trousers, s)(a, y))(seed))
        c.box(u, 27, 4, 5, 4, leg)
    c.save("textures", "entity", "mining_golem", f"{name}_golem.png")
    glow_layer(name, [(230, 170, 40), (250, 215, 90), (255, 245, 190)])


# --------------------------------------------------------------------------------------------- chests
# Chest models are drawn with Y pointing up, so y = 0 is the bottom of each cuboid. South is the front.
# A double chest is painted as one 30 pixel wide chest and cut in two: the "right" model is the low-X
# half and the "left" model the high-X half.


def chest(tier, kind):
    pal = PALETTES[tier]
    c = Canvas()
    total, offset, width = {"single": (14, 0, 14), "right": (30, 0, 15), "left": (30, 15, 15)}[kind]

    def framed(seed, w, h, x, y, e, body):
        """Dark outer frame, then whatever `body` paints inside it."""
        if ("l" in e and x == 0) or ("r" in e and x == w - 1) or ("t" in e and y == 0) or ("b" in e and y == h - 1):
            return pal["shadow"]
        return body(x, y)

    def rivets(w, h, x, y):
        return x in (2, w - 3) and y in (2, h - 3)

    def soaked(color):
        """Darker and a touch greener, the way a sponge goes when it is wet."""
        return (int(color[0] * 0.68), int(color[1] * 0.74), int(color[2] * 0.62))

    def straw(seed, x):
        """Ears of wheat, mostly golden with the odd green stalk, or None where there is none."""
        n = noise("straw", seed, x)
        return (236, 208, 72) if n < 0.5 else (112, 152, 50) if n < 0.66 else None

    def wall(seed, w, h, x, y, e, band_row):
        color = plain_wall(seed, w, h, x, y, e, band_row)
        lid = seed.startswith("lid")
        if tier == "sponge" and not lid and y >= h // 2:
            # The bottom half of the sponge chest is waterlogged.
            return soaked(color)
        if tier == "hay":
            # Wheat poking out through the gap between lid and base, a few ears standing or hanging further.
            at_gap = y == h - 1 if lid else y == 0
            near_gap = y == h - 2 if lid else y == 1
            if at_gap and straw(seed[:3], x):
                return straw(seed[:3], x)
            if near_gap and noise("ear", seed, x) < 0.3 and straw(seed[:3], x):
                return straw(seed[:3], x)
        return color

    def plain_wall(seed, w, h, x, y, e, band_row):
        def body(x, y):
            if y == band_row:
                return pal["dark"]
            if rivets(w, h, x, y) and h > 6:
                return pal["shadow"]
            inner_left, inner_right = ("l" in e and x == 1), ("r" in e and x == w - 2)
            if inner_right or y == 1:
                return pal["mid"]
            if inner_left or y == h - 2:
                return pal["light"]
            return metal(pal, seed, x, y)

        return framed(seed, w, h, x, y, e, body)

    def lid_top(x, z):
        def body(x, z):
            if rivets(total, 14, x, z):
                return pal["shadow"]
            if 4 <= z <= 9 and 4 <= x <= total - 5:
                edge = z in (4, 9) or x in (4, total - 5)
                return pal["dark"] if edge else pal["mid"]
            return plate(pal, "lid", x - 1, z - 1, total - 2, 12)

        return framed("lid", total, 14, x, z, "lrtb", body)

    def inside(x, z):
        return framed("in", total, 14, x, z, "lrtb", lambda x, z: pal["mid"] if x in (1, total - 2) or z in (1, 12) else pal["inside"])

    def underside(x, z):
        return framed("under", total, 14, x, z, "lrtb", lambda x, z: metal(pal, "under", x, z))

    def shifted(painter):
        return lambda x, other: painter(x + offset, other)

    hidden = {"single": (), "right": ("east",), "left": ("west",)}[kind]
    # Lid: its bottom row meets the base, so the seam band is at y = 0.
    c.box(0, 0, width, 5, 14, {
        "up": shifted(lid_top),
        "down": shifted(inside),
        "north": shifted(lambda x, y: wall("lidn", total, 5, x, 4 - y, "lrt", -1)),
        "south": shifted(lambda x, y: wall("lids", total, 5, x, 4 - y, "lrt", -1)),
        "west": lambda z, y: wall("lidw", 14, 5, z, 4 - y, "lrt", -1),
        "east": lambda z, y: wall("lide", 14, 5, z, 4 - y, "lrt", -1),
    }, hidden)
    # Base: 10 tall, with a darker band just under the seam.
    c.box(0, 19, width, 10, 14, {
        "up": shifted(inside),
        "down": shifted(underside),
        "north": shifted(lambda x, y: wall("basen", total, 10, x, 9 - y, "lrb", 1)),
        "south": shifted(lambda x, y: wall("bases", total, 10, x, 9 - y, "lrb", 1)),
        "west": lambda z, y: wall("basew", 14, 10, z, 9 - y, "lrb", 1),
        "east": lambda z, y: wall("basee", 14, 10, z, 9 - y, "lrb", 1),
    }, hidden)
    # Lock, drawn last because its UVs sit in the unused corner of the lid's layout.
    lock_width = 2 if kind == "single" else 1
    c.box(0, 0, lock_width, 4, 1, {"*": lambda a, y: pal["accent_dark"] if y in (0, 3) else pal["accent"]})

    c.save("textures", "entity", "chest", tier + ("" if kind == "single" else "_" + kind) + ".png")


def copper_efficiency_dots():
    """The copper golem's Efficiency: one lit copper dot per level along the top row of its face.

    Drawn over the vanilla copper golem model with its 64x64 layout, so only the head's front face is
    used (u 10-17, v 10-14); everything else is transparent. Rendered like the eyes, at full brightness.
    """
    bright, glow = (255, 196, 140), (255, 150, 84)
    for level in range(1, 6):
        image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        for i in range(level):
            image.putpixel((11 + i, 10), (bright if i % 2 == 0 else glow) + (255,))
        save_image(image, "textures", "entity", "copper_golem", f"efficiency_{level}.png")


def icon():
    """Mod icon: the iron golem's face, scaled up."""
    pal = PALETTES["iron"]
    face = Image.new("RGBA", (8, 8), pal["base"] + (255,))
    for y in range(8):
        for x in range(8):
            face.putpixel((x, y), plate(pal, "icon", x, y, 8, 8) + (255,))
    for x, y in EYES:
        face.putpixel((x, y + 2), (pal["accent"] if y == 2 else pal["accent_dark"]) + (255,))
    for x, y in ((3, 5), (4, 5), (3, 6), (4, 6)):
        face.putpixel((x, y), pal["dark"] + (255,))
    save_image(face.resize((128, 128), Image.NEAREST), "icon.png")


if __name__ == "__main__":
    for tier in ("iron", "diamond", "netherite"):
        golem(tier)
    for name in BLOCK_GOLEMS:
        block_golem(name)
    scarecrow_golem()
    for tier in PALETTES:
        for kind in ("single", "left", "right"):
            chest(tier, kind)
    # The amethyst golem's eyes once a spyglass has upgraded it: brass instead of lilac.
    glow_layer("amethyst_upgraded", UPGRADED_EYES)
    copper_efficiency_dots()
    icon()
