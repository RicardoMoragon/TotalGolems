"""Builds the Blockbench projects in art/: one per golem (golem_<name>.bbmodel) with its own shape, from
golem_geometry.py; the copper golem's, for its Efficiency dots; and the chests'.

Each texture in a project points at its file, so Ctrl+S on a texture in Blockbench saves it: the golems'
straight into the mod, the chests' into art/textures/chest, from which make_effects.py makes the mod's
animated ones (run it again after changing a golem's texture too, for its glints, cracks or glow). Run this
again after adding textures or changing a shape: python art/make_bbmodels.py. A golem's shape in the game is
in GolemModelLayers.java, which has to be changed to match.
"""
import base64
import copy
import json
import math
import os
import uuid
from pathlib import Path

from golem_geometry import COPPER, GOLEMS

ART = Path(__file__).resolve().parent
TEXTURES = ART.parent / 'src' / 'main' / 'resources' / 'assets' / 'totalgolems' / 'textures'
CHESTS = ['iron', 'diamond', 'netherite', 'amethyst', 'hay', 'bone']


def new_id():
    return str(uuid.uuid4())


def png(path):
    return 'data:image/png;base64,' + base64.b64encode(path.read_bytes()).decode()


def texture(template, index, path, name=None, saved=True):
    """A texture entry of a project, linked to {@code path} when it is one of the mod's."""
    entry = copy.deepcopy(template)
    entry.update({
        'name': name or path.name,
        'id': str(index),
        'uuid': new_id(),
        'source': png(path),
        'saved': True,
    })
    if saved:
        # The path from art/, where the projects are: into the mod's textures, or to the chests' still ones here.
        entry['relative_path'] = Path(os.path.relpath(path, ART)).as_posix()
        entry['folder'] = path.parent.name
    else:
        # Only there to look at: not linked to any file, so it can never be saved over.
        entry['relative_path'] = ''
        entry['folder'] = ''
    return entry


def write(path, project):
    path.write_text(json.dumps(project, separators=(',', ':')), encoding='utf-8')
    print('wrote', path.relative_to(ART.parent))


template = {
    'namespace': 'totalgolems', 'group': '', 'scope': 0, 'width': 64, 'height': 64, 'uv_width': 64, 'uv_height': 64,
    'particle': False, 'use_as_default': False, 'layers_enabled': False, 'sync_to_project': '', 'file_format': 'png',
    'render_mode': 'default', 'render_sides': 'auto', 'wrap_mode': 'limited', 'pbr_channel': 'color', 'fps': 7,
    'force_translucent': False, 'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False,
    'visible': True, 'internal': True, 'saved': True,
}

# ------------------------------------------------------------------------------------ golems


def box_faces(u, v, w, h, d, mirror):
    """The faces of a box-UV cube, as Blockbench lists them."""
    faces = {
        'north': [u + d, v + d, u + d + w, v + d + h],
        'east': [u, v + d, u + d, v + d + h],
        'south': [u + d + w + d, v + d, u + d + w + d + w, v + d + h],
        'west': [u + d + w, v + d, u + d + w + d, v + d + h],
        'up': [u + d + w, v + d, u + d, v],
        'down': [u + d + w + w, v, u + d + w, v + d],
    }
    if mirror:
        faces['east'], faces['west'] = faces['west'], faces['east']
        faces = {k: [f[2], f[1], f[0], f[3]] for k, f in faces.items()}
    return {k: {'uv': f, 'texture': 0} for k, f in faces.items()}


def golem_project(name, parts, textures):
    """
    A Blockbench project of a golem built of {@code parts} (see golem_geometry.py). Blockbench has y up and the
    golem's right at +x, the other way round from the game on both counts; it works out the game's numbers again
    when it exports.
    """
    elements, groups = [], []

    def add(node, parent_pivot):
        px, py, pz = (parent_pivot[i] + node['pivot'][i] for i in range(3))
        origin = [-px, 24 - py, pz]
        # Turned round as the axes are (and + 0.0 so that no angle is written as -0).
        rx, ry, rz = (round(a * 180 / math.pi, 3) + 0.0 for a in node['rotation'])
        group = {
            'name': node['name'], 'uuid': new_id(), 'export': True, 'locked': False, 'scope': 0, 'selected': False,
            'visibility': True, 'origin': origin, 'rotation': [0.0 - rx, 0.0 - ry, rz], 'color': 0, 'children': [], 'reset': False,
            'shade': True, 'mirror_uv': False, 'autouv': 0, 'isOpen': True, 'primary_selected': False,
        }
        groups.append(group)
        children = []
        for c in node['cubes']:
            x, y, z, w, h, d = c['box']
            element = {
                'name': c['name'] or node['name'], 'box_uv': True, 'render_order': 'default', 'locked': False,
                'export': True, 'scope': 0, 'allow_mirror_modeling': True, 'mirror_uv': c['mirror'],
                'from': [-(px + x + w), 24 - (py + y + h), pz + z], 'to': [-(px + x), 24 - (py + y), pz + z + d],
                'inflate': c['inflate'], 'autouv': 0, 'color': len(elements) % 8, 'origin': origin,
                'uv_offset': list(c['uv']), 'faces': box_faces(*c['uv'], w, h, d, c['mirror']), 'type': 'cube', 'uuid': new_id(),
            }
            elements.append(element)
            children.append(element['uuid'])
        for child in node['children']:
            children.append(add(child, (px, py, pz)))
        return {'uuid': group['uuid'], 'isOpen': True, 'children': children}

    # The game raises the root's parts by 24, to stand on the ground.
    outliner = [add(node, (0, 24, 0)) for node in parts]
    return {
        'meta': {'format_version': '5.0', 'model_format': 'modded_entity', 'box_uv': True},
        'name': name, 'model_identifier': name, 'modded_entity_entity_class': '', 'modded_entity_version': '1.17',
        'modded_entity_flip_y': True, 'visible_box': [1, 1, 0], 'variable_placeholders': '', 'multi_file_ruleset': '',
        'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {},
        'resolution': {'width': 64, 'height': 64}, 'elements': elements, 'groups': groups, 'outliner': outliner,
        'textures': textures,
    }


# One project per golem, with its own shape, its texture and its lit eyes.
golem_textures = TEXTURES / 'entity' / 'mining_golem'
for name, parts in GOLEMS.items():
    paths = sorted(golem_textures.glob(f'{name}_*golem*.png'), key=lambda p: (p.name != f'{name}_golem.png', p.name))
    write(ART / f'golem_{name}.bbmodel', golem_project(f'{name}_golem', parts, [texture(template, i, p) for i, p in enumerate(paths)]))

# ------------------------------------------------------------------------------------ copper golem

# The copper golem with its antenna, for its Efficiency dots. The vanilla texture is there to see the dots
# against; it is not linked to a file.
vanilla = ART / 'reference' / 'copper_golem.png'
copper_textures = [texture(template, 0, vanilla, 'copper_golem (vanilla, solo referencia).png', saved=False)]
for level in range(1, 6):
    copper_textures.append(texture(template, level, TEXTURES / 'entity' / 'copper_golem' / f'efficiency_{level}.png'))
write(ART / 'copper_golem_efficiency.bbmodel', golem_project('copper_golem_efficiency', COPPER, copper_textures))

# ------------------------------------------------------------------------------------ chests

# Blockbench face corners in a block model: for each face, the corner where (u1, v1) goes and the corner
# where (u2, v2) goes, as (x, y, z) picks of min (0) or max (1); None where the axis is the face's own.
CORNERS = {
    'north': ((1, 1, None), (0, 0, None)),
    'south': ((0, 1, None), (1, 0, None)),
    'east': ((None, 1, 1), (None, 0, 0)),
    'west': ((None, 1, 0), (None, 0, 1)),
    'up': ((0, None, 0), (1, None, 1)),
    'down': ((0, None, 1), (1, None, 0)),
}


def mojang_faces(u, v, w, h, d):
    """
    The texture corners of each face of a box as Minecraft's ModelPart.Cube lays them out: each face as a
    list of ((x, y, z) picks of min 0 / max 1, (u, v)).
    """
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    t0, t1, t2, t3 = (0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)
    l0, l1, l2, l3 = (0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)

    def polygon(vertices, a, b, c, e):
        # As ModelPart.Polygon: the vertices get (c, b), (a, b), (a, e), (c, e) in that order.
        return list(zip(vertices, [(c, b), (a, b), (a, e), (c, e)]))

    return {
        'down': polygon([l1, l0, t0, t1], u1, v0, u2, v1),
        'up': polygon([t2, t3, l3, l2], u2, v1, u22, v0),
        'west': polygon([t0, l0, l3, t3], u0, v1, u1, v2),
        'north': polygon([t1, t0, t3, t2], u1, v1, u2, v2),
        'east': polygon([l1, t1, t2, l2], u2, v1, u3, v2),
        'south': polygon([l0, l1, l2, l3], u3, v1, u4, v2),
    }


def face_uv(corners, pick):
    for vertex, uv in corners:
        if all(p is None or p == q for p, q in zip(pick, vertex)):
            return uv
    raise ValueError(pick)


def cube(name, origin, box, tex, texture_id, skip=()):
    """A cube of the chest at origin + box (x, y, z, width, height, depth), its faces as the game maps them."""
    x, y, z, w, h, d = box
    ox, oy, oz = origin
    faces = {}
    mojang = mojang_faces(tex[0], tex[1], w, h, d)
    for face, (first, second) in CORNERS.items():
        if face in skip:
            # Blockbench wants all six faces on a cube, or it stops loading the project halfway: this one is
            # there, but with no texture, so it is not drawn.
            faces[face] = {'uv': [0, 0, 0, 0], 'texture': None}
            continue
        a = face_uv(mojang[face], first)
        b = face_uv(mojang[face], second)
        faces[face] = {'uv': [a[0], a[1], b[0], b[1]], 'texture': texture_id}
    return {
        'name': name, 'box_uv': False, 'render_order': 'default', 'locked': False, 'export': True, 'scope': 0,
        'allow_mirror_modeling': True, 'from': [ox + x, oy + y, oz + z], 'to': [ox + x + w, oy + y + h, oz + z + d],
        'autouv': 0, 'color': 0, 'origin': [ox + x, oy + y, oz + z], 'faces': faces, 'type': 'cube', 'uuid': new_id(),
    }


def chest_cubes(x_offset, half, texture_id):
    """The bottom, lid and lock of a single chest, or of the left or right half of a double one, as in ChestModel."""
    width = 14 if half is None else 15
    start = {None: 1, 'right': 1, 'left': 0}[half]
    lock = {None: (7, 2), 'right': (15, 1), 'left': (0, 1)}[half]
    # The faces where the two halves meet are not drawn.
    skip = {None: (), 'right': ('east',), 'left': ('west',)}[half]
    suffix = '' if half is None else '_' + half
    return [
        cube('bottom' + suffix, (x_offset, 0, 0), (start, 0, 1, width, 10, 14), (0, 19), texture_id, skip),
        cube('lid' + suffix, (x_offset, 9, 1), (start, 0, 0, width, 5, 14), (0, 0), texture_id, skip),
        cube('lock' + suffix, (x_offset, 9, 1), (lock[0], -2, 14, lock[1], 4, 1), (0, 0), texture_id, skip),
    ]


for material in CHESTS:
    # The still textures, which make_effects.py turns into the animated ones the mod uses.
    folder = ART / 'textures' / 'chest'
    textures = [texture(template, i, folder / f'{material}{suffix}.png') for i, suffix in enumerate(['', '_right', '_left'])]
    # The single chest on its own, and two blocks along the double chest: right half to the west, left to the east.
    parts = {
        'single': chest_cubes(0, None, 0),
        'double': chest_cubes(32, 'right', 1) + chest_cubes(48, 'left', 2),
    }
    elements, groups, outliner = [], [], []
    for name, cubes in parts.items():
        group = {
            'name': name, 'uuid': new_id(), 'export': True, 'locked': False, 'scope': 0, 'selected': False,
            'visibility': True, 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'color': 0, 'children': [],
            'reset': False, 'shade': True, 'mirror_uv': False, 'autouv': 0, 'isOpen': True, 'primary_selected': False,
        }
        groups.append(group)
        elements += cubes
        outliner.append({'uuid': group['uuid'], 'isOpen': True, 'children': [c['uuid'] for c in cubes]})
    write(ART / f'chest_{material}.bbmodel', {
        'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False},
        'name': f'chest_{material}',
        'model_identifier': '',
        'visible_box': [1, 1, 0],
        'variable_placeholders': '',
        'variable_placeholder_buttons': [],
        'timeline_setups': [],
        'unhandled_root_fields': {},
        'resolution': {'width': 64, 'height': 64},
        'elements': elements,
        'groups': groups,
        'outliner': outliner,
        'textures': textures,
    })
