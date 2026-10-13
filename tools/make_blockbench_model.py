"""Writes art/mining_golem.bbmodel: the golem model with its textures, to paint on in Blockbench.

    python tools/make_blockbench_model.py

The cuboids are the copper golem's (the model the mod reuses). Textures are linked to the PNGs in
src/main/resources, so saving a texture in Blockbench writes straight to the file the mod uses.
"""

import base64
import json
import os
import uuid

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "totalgolems", "textures", "entity", "mining_golem")

# name, pivot (x, y, z) in Minecraft model space, parent, and boxes as (u, v, x, y, z, w, h, d).
PARTS = [
    ("body", (0, 19, 0), None, [(0, 15, -4, -6, -3, 8, 6, 6)]),
    ("head", (0, 13, 0), "body", [(0, 0, -4, -5, -5, 8, 5, 10), (56, 0, -1, -2, -6, 2, 3, 2), (37, 8, -1, -9, -1, 2, 4, 2), (37, 0, -2, -13, -2, 4, 4, 4)]),
    ("right_arm", (-4, 13, 0), "body", [(36, 16, -3, -1, -2, 3, 10, 4)]),
    ("left_arm", (4, 13, 0), "body", [(50, 16, 0, -1, -2, 3, 10, 4)]),
    ("right_leg", (0, 19, 0), None, [(0, 27, -4, 0, -2, 4, 5, 4)]),
    ("left_leg", (0, 19, 0), None, [(16, 27, 0, 0, -2, 4, 5, 4)]),
]
BOX_NAMES = {"head": ["head", "nose", "antenna_rod", "antenna_knob"]}


def new_id():
    return str(uuid.uuid4())


def main():
    elements = []
    groups = {}
    outliner = []
    for name, (px, py, pz), parent, boxes in PARTS:
        # Blockbench has X and Y flipped with respect to Minecraft's model space, and the floor at 0.
        origin = [-px, 24 - py, pz]
        group = {"name": name, "origin": origin, "rotation": [0, 0, 0], "uuid": new_id(), "export": True, "isOpen": True, "visibility": True, "children": []}
        for index, (u, v, x, y, z, w, h, d) in enumerate(boxes):
            cube = {
                "name": BOX_NAMES.get(name, [name])[index],
                "type": "cube",
                "box_uv": True,
                "from": [-(px + x + w), 24 - (py + y + h), pz + z],
                "to": [-(px + x), 24 - (py + y), pz + z + d],
                "origin": origin,
                "uv_offset": [u, v],
                "inflate": 0,
                "autouv": 0,
                "uuid": new_id(),
            }
            elements.append(cube)
            group["children"].append(cube["uuid"])
        groups[name] = group
        (groups[parent]["children"] if parent else outliner).append(group)

    textures = []
    for file in sorted(os.listdir(TEXTURES)):
        if not file.endswith("_golem.png") and not file.endswith("_golem_eyes.png"):
            continue
        path = os.path.join(TEXTURES, file)
        with open(path, "rb") as image:
            source = "data:image/png;base64," + base64.b64encode(image.read()).decode()
        textures.append({
            "name": file,
            "path": path,
            "folder": "entity/mining_golem",
            "namespace": "totalgolems",
            "id": str(len(textures)),
            "width": 64,
            "height": 64,
            "uv_width": 64,
            "uv_height": 64,
            "particle": False,
            "visible": True,
            "mode": "bitmap",
            "saved": True,
            "uuid": new_id(),
            "source": source,
        })

    model = {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": "mining_golem",
        "model_identifier": "mining_golem",
        "visible_box": [1, 1, 0],
        "resolution": {"width": 64, "height": 64},
        "elements": elements,
        "outliner": outliner,
        "textures": textures,
    }
    target = os.path.join(ROOT, "art", "mining_golem.bbmodel")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="utf-8") as output:
        json.dump(model, output)
    print(target, len(elements), "cubes,", len(textures), "textures")


if __name__ == "__main__":
    main()
