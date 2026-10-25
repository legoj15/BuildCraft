"""Facade smooth shading: do facades cast and receive ambient-occlusion creases like the blocks they copy?

Run: python scripts/smoke/generators/gen_facade_shading.py   (rewrites scripts/smoke/scenarios/facade-shading.json)
     pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3 -Scenario scripts/smoke/scenarios/facade-shading.json

Three identical L-shaped rigs (a 4x4 floor with a 4x3 wall rising from its back edge), left to right:
  A  real white concrete                    the reference
  B  pipes wearing a white-concrete facade on all six sides
  C  a real concrete floor with a facade wall
Plus a fourth, D: the B rig in HOLLOW facades, which must stay uncreased (see-through, like glass).
With the fix, B and C show the same dark crease where wall meets floor as A, and the same soft darkening at the wall's
foot. Before it, B's crease was missing (every facade lit as an isolated flat square) and C's floor had none either.
"""
import json
import os

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, "..", "scenarios", "facade-shading.json")
BC = "buildcraftunofficial:"
BLOCK = "minecraft:white_concrete"
SIDES = ["north", "south", "east", "west", "up", "down"]


def o(a, n):
    return "{%s%s}" % (a, ("+%d" % n) if n > 0 else ("-%d" % -n) if n < 0 else "")


def at(x, y, z):
    return "%s %s %s" % (o('X', x), o('Y', y), o('Z', z))


def facade_pipe(x, y, z, hollow=False):
    f = '{id:"%sfacade",data:{facade:{isHollow:%db,states:[{state:{Name:"%s"}}]},activeState:0}}' % (
        BC, 1 if hollow else 0, BLOCK)
    plugs = ",".join("%s:%s" % (s, f) for s in SIDES)
    return ["setblock %s %spipe_holder" % (at(x, y, z), BC),
            'data merge block %s {pipe:{def:"%sstone_item"},plugs:{%s}}' % (at(x, y, z), BC, plugs)]


def real(x, y, z):
    return ["setblock %s %s" % (at(x, y, z), BLOCK)]


def rig(x0, floor, wall):
    cmds = []
    for dx in range(4):
        for dz in range(-7, -3):
            cmds += floor(x0 + dx, 0, dz)
        for dy in range(1, 4):
            cmds += wall(x0 + dx, dy, -8)
    return cmds


steps = [
    {"label": "clear the area, noon, clear weather", "do": "cmd", "run": [
        "fill {X-14} {Y-1} {Z-10} {X+14} {Y+6} {Z+4} minecraft:air",
        "?time set noon",
        "?weather clear",
        "?gamerule doDaylightCycle false",
        "?gamerule advance_time false",
        # a walkway for the camera, a block clear of the rigs so it never shades them
        "fill {X-14} {Y} {Z-2} {X+14} {Y} {Z+4} minecraft:barrier",
    ]},
    {"do": "hold", "item": "minecraft:air"},
    {"label": "A: real concrete floor and wall", "do": "cmd", "run": rig(-12, real, real)},
    {"label": "B: facade floor and wall", "do": "cmd", "run": rig(-6, facade_pipe, facade_pipe)},
    {"label": "C: real floor, facade wall", "do": "cmd", "run": rig(0, real, facade_pipe)},
    {"label": "D: hollow-facade floor and wall", "do": "cmd",
     "run": rig(6, lambda x, y, z: facade_pipe(x, y, z, True), lambda x, y, z: facade_pipe(x, y, z, True))},
    {"do": "tp", "at": [-1, 1, 3], "yaw": 180, "pitch": 12},
    {"do": "wait", "ms": 3000},
    {"do": "shot", "name": "overview",
     "look": "four L rigs, left to right: A real concrete, B facades, C real floor + facade wall, D hollow facades. "
             "A, B and C must all show the same dark crease where the wall meets the floor; D shows none"},
]
for name, x in (("a-real", -11), ("b-facade", -5), ("c-mixed", 1), ("d-hollow", 7)):
    steps += [
        {"do": "tp", "at": [x, 1, -1], "yaw": 180, "pitch": 18},
        {"do": "wait", "ms": 2500},
        {"do": "shot", "name": name,
         "look": "close-up of the wall/floor crease; compare a-real, b-facade and c-mixed: the gradients must match (d-hollow: no crease, see-through)"},
    ]

with open(OUT, "w", newline="\n") as fh:
    json.dump({"name": "facade-shading", "base": [0, 150, 0], "steps": steps}, fh, indent=1)
    fh.write("\n")
print("wrote", os.path.normpath(OUT))
