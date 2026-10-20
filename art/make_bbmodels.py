"""Builds the Blockbench projects in art/ for the textures that had none: the chests and the copper golem's
Efficiency dots, and adds any golem texture missing from mining_golem.bbmodel.

Each texture in a project points at its file, so Ctrl+S on a texture in Blockbench saves it: the golems'
straight into the mod, the chests' into art/textures/chest, from which make_effects.py makes the mod's
animated ones. Run again after adding textures: python art/make_bbmodels.py
"""
import base64
import copy
import json
import os
import uuid
from pathlib import Path

ART = Path(__file__).resolve().parent
TEXTURES = ART.parent / 'src' / 'main' / 'resources' / 'assets' / 'totalgolems' / 'textures'
GOLEM = ART / 'mining_golem.bbmodel'
CHESTS = ['iron', 'diamond', 'netherite', 'amethyst', 'hay', 'bone']


def new_id():
    return str(uuid.uuid4())


def png(path):
    return 'data:image/png;base64,' + base64.b64encode(path.read_bytes()).decode()


def texture(template, index, path, name=None, saved=True):
    """A texture entry like those of mining_golem.bbmodel, linked to {@code path} when it is one of the mod's."""
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


golem = json.loads(GOLEM.read_text(encoding='utf-8'))
template = {k: v for k, v in golem['textures'][0].items()}

# ------------------------------------------------------------------------------------ golems

known = {t['name'] for t in golem['textures']}
for path in sorted((TEXTURES / 'entity' / 'mining_golem').glob('*.png')):
    if path.name not in known:
        golem['textures'].append(texture(template, len(golem['textures']), path))
        print('added', path.name, 'to', GOLEM.name)
write(GOLEM, golem)

# ------------------------------------------------------------------------------------ copper golem

# The copper golem has the same body as the mod's golems, so the same cubes. The vanilla texture is there
# to see the dots against; it is not linked to a file.
vanilla = ART / 'reference' / 'copper_golem.png'
copper = copy.deepcopy(golem)
copper['name'] = copper['model_identifier'] = 'copper_golem_efficiency'
copper['textures'] = [texture(template, 0, vanilla, 'copper_golem (vanilla, solo referencia).png', saved=False)]
for level in range(1, 6):
    copper['textures'].append(texture(template, level, TEXTURES / 'entity' / 'copper_golem' / f'efficiency_{level}.png'))
for element in copper['elements']:
    element['uuid'] = new_id()
uuids = {old['uuid']: new['uuid'] for old, new in zip(golem['elements'], copper['elements'])}
groups = {}
for group in copper['groups']:
    groups[group['uuid']] = group['uuid'] = new_id()


def renumber(node):
    if isinstance(node, str):
        return uuids[node]
    node['uuid'] = groups[node['uuid']]
    node['children'] = [renumber(child) for child in node['children']]
    return node


copper['outliner'] = [renumber(node) for node in copper['outliner']]
write(ART / 'copper_golem_efficiency.bbmodel', copper)

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
