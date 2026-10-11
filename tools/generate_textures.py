"""Generates every texture of the mod. Run from the repository root:

    python tools/generate_textures.py

Textures are painted per cuboid face using the same box-UV layout Minecraft's ModelPart uses, so the
shapes below mirror the cuboids of the vanilla copper golem and chest models.
"""

import hashlib
import os

from PIL import Image

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
}


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
        target = os.path.join(ASSETS, *path)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        self.image.save(target)


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

    def wall(seed, w, h, x, y, e, band_row):
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
    face.resize((128, 128), Image.NEAREST).save(os.path.join(ASSETS, "icon.png"))


if __name__ == "__main__":
    for tier in PALETTES:
        golem(tier)
        for kind in ("single", "left", "right"):
            chest(tier, kind)
    icon()
