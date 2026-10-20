"""The shape of each golem, the same as GolemModelLayers builds in the game, for make_bbmodels.py.

Coordinates are the game's model coordinates: y points down, the golem's right is -x, and a part's pivot is
relative to its parent's (the root's parts are 24 higher, at the feet). Each cube is (u, v) on the texture
and (x, y, z, width, height, depth) from its part's pivot; rotations are in radians.
"""


def cube(uv, box, mirror=False, inflate=0.0, name=None):
    return {'uv': uv, 'box': box, 'mirror': mirror, 'inflate': inflate, 'name': name}


def part(name, pivot, cubes=(), children=(), rotation=(0.0, 0.0, 0.0)):
    return {'name': name, 'pivot': pivot, 'rotation': rotation, 'cubes': list(cubes), 'children': list(children)}


def base(head=(), body=(), right_arm=None, left_arm=None, right_leg=(), left_leg=(), head_children=(), body_children=(),
         right_arm_children=(), left_arm_children=()):
    """The copper golem's body without its antenna, with whatever a golem adds to it."""
    right_arm = right_arm or [cube((36, 16), (-3, -1, -2, 3, 10, 4), name='right_arm')]
    left_arm = left_arm or [cube((50, 16), (0, -1, -2, 3, 10, 4), name='left_arm')]
    return [
        part('body', (0, -5, 0), [cube((0, 15), (-4, -6, -3, 8, 6, 6), name='body')] + list(body), [
            part('head', (0, -6, 0), [
                cube((0, 0), (-4, -5, -5, 8, 5, 10), inflate=0.015, name='head'),
                cube((56, 0), (-1, -2, -6, 2, 3, 2), name='nose'),
            ] + list(head), head_children),
            part('right_arm', (-4, -6, 0), right_arm, right_arm_children),
            part('left_arm', (4, -6, 0), left_arm, left_arm_children),
        ] + list(body_children)),
        part('right_leg', (0, -5, 0), [cube((0, 27), (-4, 0, -2, 4, 5, 4), name='right_leg')] + list(right_leg)),
        part('left_leg', (0, -5, 0), [cube((16, 27), (0, 0, -2, 4, 5, 4), name='left_leg')] + list(left_leg)),
    ]


def crystal(name, pivot, rotation, big):
    return part(name, pivot, [cube((0, 36), (-1, -3, -1, 2, 3, 2)) if big else cube((8, 36), (-0.5, -2, -0.5, 1, 2, 1))], rotation=rotation)


GOLEMS = {
    # Long, heavy arms that nearly reach the ground, like the iron golem's.
    'iron': base(
        right_arm=[cube((0, 36), (-4, -2, -2.5, 4, 12, 5), name='right_arm')],
        left_arm=[cube((18, 36), (0, -2, -2.5, 4, 12, 5), name='left_arm')],
    ),
    # Rough and chunky: everything a size bigger, and lumps of stone on its shoulders, head and back.
    'diamond': [
        part('body', (0, -5, 0), [
            cube((0, 16), (-5, -7, -3.5, 10, 7, 7), name='body'),
            cube((14, 41), (-2.5, -6, 3.5, 5, 4, 1), inflate=0.05, name='back_lump'),
        ], [
            part('head', (0, -7, 0), [
                cube((0, 0), (-4.5, -5, -5, 9, 5, 10), inflate=0.015, name='head'),
                cube((56, 0), (-1, -2, -6, 2, 3, 2), name='nose'),
                cube((0, 41), (-1.5, -6, -3, 3, 1, 4), inflate=0.05, name='head_lump'),
            ]),
            part('right_arm', (-5, -7, 0), [
                cube((38, 0), (-4, -1, -2.5, 4, 10, 5), name='right_arm'),
                cube((40, 31), (-4.5, -2, -3, 5, 2, 6), inflate=0.05, name='right_shoulder'),
            ]),
            part('left_arm', (5, -7, 0), [
                cube((38, 0), (0, -1, -2.5, 4, 10, 5), mirror=True, name='left_arm'),
                cube((40, 31), (-0.5, -2, -3, 5, 2, 6), mirror=True, inflate=0.05, name='left_shoulder'),
            ]),
        ]),
        part('right_leg', (0, -5, 0), [cube((0, 31), (-5, 0, -2.5, 5, 5, 5), name='right_leg')]),
        part('left_leg', (0, -5, 0), [cube((0, 31), (0, 0, -2.5, 5, 5, 5), mirror=True, name='left_leg')]),
    ],
    # Forged like armour: plated shoulders, a crest along the head and a belt.
    'netherite': base(
        head=[cube((22, 36), (-0.5, -7, -4, 1, 2, 8), name='crest')],
        body=[cube((0, 46), (-4.5, -1.5, -3.5, 9, 2, 7), name='belt')],
        right_arm=[
            cube((36, 16), (-3, -1, -2, 3, 10, 4), name='right_arm'),
            cube((0, 36), (-4, -2, -3, 5, 3, 6), inflate=0.05, name='right_pauldron'),
        ],
        left_arm=[
            cube((50, 16), (0, -1, -2, 3, 10, 4), name='left_arm'),
            cube((0, 36), (-1, -2, -3, 5, 3, 6), mirror=True, inflate=0.05, name='left_pauldron'),
        ],
    ),
    # Little amethyst crystals growing out of its head and arms.
    'amethyst': base(
        head_children=[
            crystal('crystal_head_0', (2, -5, -1), (0.35, 0.0, 0.45), True),
            crystal('crystal_head_1', (-2.5, -5, -3), (-0.3, 0.0, -0.4), False),
            crystal('crystal_head_2', (4, -3, 1.5), (0.0, 0.0, 1.1), False),
        ],
        right_arm_children=[
            crystal('crystal_right_0', (-3, 2, 0), (0.2, 0.0, -0.9), True),
            crystal('crystal_right_1', (-3, 5, 1), (-0.3, 0.0, -1.2), False),
        ],
        left_arm_children=[
            crystal('crystal_left_0', (3, 2, 0), (0.2, 0.0, 0.9), True),
            crystal('crystal_left_1', (3, 5, 1), (-0.3, 0.0, 1.2), False),
        ],
    ),
    # A straw hat, its own part so that it can hop.
    'hay': base(head_children=[
        part('hat', (0, -5, 0), [
            cube((0, 36), (-7, -1, -7, 14, 1, 14), name='brim'),
            cube((0, 51), (-3, -4, -3, 6, 3, 6), name='crown'),
        ]),
    ]),
    # A ribcage, see-through between the ribs (in the texture), with its spine inside.
    'bone': base(body=[cube((28, 15), (-1, -6, 1, 2, 6, 2), inflate=-0.1, name='spine')]),
    # A shell with holes in it and a glowing core inside each part.
    'coal': base(
        head=[cube((20, 36), (-3, -4, -4, 6, 3, 8), name='head_core')],
        body=[cube((0, 36), (-3, -5, -2, 6, 4, 4), name='body_core')],
        right_arm=[
            cube((36, 16), (-3, -1, -2, 3, 10, 4), name='right_arm'),
            cube((48, 36), (-2, 0, -1, 1, 8, 2), name='right_arm_core'),
        ],
        left_arm=[
            cube((50, 16), (0, -1, -2, 3, 10, 4), name='left_arm'),
            cube((54, 36), (1, 0, -1, 1, 8, 2), name='left_arm_core'),
        ],
        right_leg=[cube((0, 47), (-3, 1, -1, 2, 3, 2), name='right_leg_core')],
        left_leg=[cube((8, 47), (1, 1, -1, 2, 3, 2), name='left_leg_core')],
    ),
}

# The copper golem's own, antenna and all, for the Efficiency dots.
COPPER = base(head=[
    cube((37, 8), (-1, -9, -1, 2, 4, 2), inflate=-0.015, name='antenna_rod'),
    cube((37, 0), (-2, -13, -2, 4, 4, 4), inflate=-0.015, name='antenna_knob'),
])
