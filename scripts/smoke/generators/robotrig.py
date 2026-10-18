"""Shared helpers for the robot smoke-scenario generators (scripts/smoke/generators/gen_*.py).

Every helper returns scenario steps or server-command strings for scripts/smoke/Invoke-InClientSmoke.ps1;
coordinates are offsets from the scenario base, written with the runner's {X+n}/{Y-n}/{Z} placeholders.
A station is a pipe holder carrying a Docking Station on its top face and a gate on its north face, with
the block below (a chest by default) placed AFTER the pipe so the pipe connects to it.
"""
import json

def o(a, n):
    return "{%s%s}" % (a, ("+%d" % n) if n > 0 else ("-%d" % -n) if n < 0 else "")

def area(x0, y0, z0, x1, y1, z1):
    return ('{id:"buildcraftunofficial:map_location",count:1,components:{"minecraft:custom_data":{mapType:"AREA",'
            'xMin:%s,yMin:%s,zMin:%s,xMax:%s,yMax:%s,zMax:%s}}}'
            % (o('X', x0), o('Y', y0), o('Z', z0), o('X', x1), o('Y', y1), o('Z', z1)))

TRUE = '{s:{kind:"buildcraftunofficial:trigger.true",side:6b}}'

def gate(first, zone=None, zaction="buildcraft:robot.work_in_area"):
    a = '"trigger[0]":%s,"action[0]":{s:{kind:"%s",side:6b}}' % (TRUE, first)
    if zone:
        a += (',"trigger[1]":%s,"action[1]":{s:{kind:"%s",side:6b},'
              '"0":{kind:"buildcraft:maplocation",stack:%s}}' % (TRUE, zaction, area(*zone)))
        v = '{logic:0b,material:2b,modifier:2b}'
    else:
        v = '{logic:0b,material:0b,modifier:0b}'
    return '{id:"buildcraftunofficial:gate",data:{data:{variant:%s,%s}}}' % (v, a)

def station(x, z, items, action, zone=None, pipe="wood_item", below="minecraft:chest", beh=',beh:{currentDir:"DOWN"}', zaction="buildcraft:robot.work_in_area"):
    X, Z = o('X', x), o('Z', z)
    cmds = []
    cmds.append("setblock %s {Y+1} %s buildcraftunofficial:pipe_holder" % (X, Z))
    cmds.append(('data merge block %s {Y+1} %s {pipe:{def:"buildcraftunofficial:' + pipe + '"' + beh + '},'
                'plugs:{up:{id:"buildcraftunofficial:robot_station",data:{}},north:%s}}') % (X, Z, gate(action, zone, zaction)))
    cmds.insert(len(cmds), 'setblock %s {Y} %s %s' % (X, Z, below))
    for i, it in enumerate(items):
        cmds.append("item replace block %s {Y} %s container.%d with %s" % (X, Z, i, it))
    return cmds

def robot(board, energy):
    return ('buildcraftunofficial:robot[minecraft:custom_data={board:{id:"buildcraftunofficial:%s"},energy:%dL}]'
            % (board, energy))

def deploy(label, x, z, board, energy=5000000000):
    return [{"label": label, "do": "tp", "at": [x, 2, z], "yaw": 0, "pitch": 90},
            {"do": "hold", "item": robot(board, energy)}, {"do": "look", "yaw": 0, "pitch": 90},
            {"do": "use"}, {"do": "wait", "ms": 500}]

def echo(label, cmd):
    return {"label": label, "do": "cmd", "run": [cmd], "allowFail": True, "echo": True}


def write(path, d):
    with open(path, "w", newline=chr(10)) as f:
        f.write(json.dumps(d, indent=2) + chr(10))
