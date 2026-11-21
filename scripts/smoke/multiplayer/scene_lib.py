"""Helpers the two-client scene scripts share (scene_swing_particles.py, scene_machines.py), on top of mp.py.

  S = Scene(node)       RCON connection, shot folder build/smoke-mp/<node>/shots, scene.log
  S.cmd(*rcon_cmds)     server console commands, `{X+1}` style coordinates around S.base
  S.b(side, method, path, body)   one bridge call on client A or B
  S.shot / tp / look / aim / hold / burst   what the scenes do with a client
  S.check(name, ok, detail)       a recorded pass/fail; S.finish() prints the tally and exits 1 on any failure
"""
import json, math, os, re, socket, sys, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mp


class Scene:
    def __init__(self, node, base=(0, -60, 0)):
        self.node = node
        self.X, self.Y, self.Z = base
        self.out = os.path.join(mp.HERE, node, 'shots')
        os.makedirs(self.out, exist_ok=True)
        self.R = mp.Rcon(node)
        self.log = open(os.path.join(mp.HERE, node, 'scene.log'), 'a', encoding='utf-8')
        self.results = []

    def say(self, *a):
        s = ' '.join(str(x) for x in a)
        print(s)
        self.log.write(time.strftime('%H:%M:%S ') + s + '\n')
        self.log.flush()

    def coords(self, t):
        def rep(m):
            base = {'X': self.X, 'Y': self.Y, 'Z': self.Z}[m.group(1)]
            return str(base + int(m.group(2) or 0))
        return re.sub(r'\{([XYZ])([+-]\d+)?\}', rep, t)

    def cmd(self, *cs):
        """Each command through RCON; the replies are logged and the last one returned."""
        r = ''
        for c in cs:
            c = self.coords(c)
            # `gamemode <mode> <player>` on a player already in that mode answers nothing at all on the 26.3 servers measured
            # (Forge: the RCON reply never comes), so it gets a short wait and a fresh connection instead of a 30 s stall.
            quiet = c.startswith('gamemode ')
            try:
                r = self.R.cmd(c, timeout=4 if quiet else None)
            except (TimeoutError, socket.timeout):
                if not quiet:
                    raise
                r = '(no reply)'
                self.R = mp.Rcon(self.node)
            self.say('rcon>', c[:160], '=>', r[:200])
        return r

    def b(self, side, method, path, body=None, timeout=60):
        return mp.bridge(self.node, side, method, path, body, timeout)

    def shot(self, side, name, look=''):
        p = os.path.join(self.out, f'{side}-{name}.png').replace('\\', '/')
        self.b(side, 'POST', '/screenshot', {'path': p})
        self.say('shot', side, name, '-', look)
        return p

    def tp(self, user, x, y, z, yaw=0, pitch=0):
        self.cmd(f'tp {user} {x} {y} {z} {yaw} {pitch}')

    def look(self, side, yaw, pitch):
        self.b(side, 'POST', '/look', {'mode': 'absolute', 'yaw': yaw, 'pitch': pitch})
        time.sleep(0.2)

    def aim(self, side, tx, ty, tz):
        p = self.b(side, 'GET', '/state')['player']['pos']
        dx, dy, dz = tx - p['x'], ty - (p['y'] + 1.62), tz - p['z']
        yaw = math.degrees(math.atan2(-dx, dz))
        pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
        self.look(side, yaw, pitch)
        hit = self.b(side, 'GET', '/state').get('hit')
        self.say('aim', side, round(yaw, 1), round(pitch, 1), json.dumps(hit)[:200])
        return hit

    def hold(self, user, side, item):
        self.cmd(f'item replace entity {user} weapon.mainhand with {item}')
        want = item.split('[')[0]
        for _ in range(50):
            h = self.b(side, 'GET', '/state')['player'].get('selectedItem') or {}
            if (want == 'minecraft:air' and h.get('empty')) or h.get('id') == want:
                return
            time.sleep(0.1)
        self.say('WARN hold not seen client-side', side, item)

    def burst(self, side, name, n, gap):
        for i in range(n):
            self.shot(side, f'{name}-{i}')
            time.sleep(gap)

    def screen_class(self, side):
        """The class name of the screen the client has open ('' for none)."""
        s = self.b(side, 'GET', '/state')
        scr = s.get('screen') if isinstance(s, dict) else None
        return (scr or {}).get('class', '') if isinstance(scr, dict) else ''

    def wait_screen(self, side, contains, seconds=8):
        """True once the client's open screen class contains `contains`."""
        end = time.time() + seconds
        while time.time() < end:
            if contains in self.screen_class(side):
                return True
            time.sleep(0.25)
        return False

    def check(self, name, ok, detail=''):
        self.results.append((name, bool(ok), detail))
        self.say(('PASS ' if ok else 'FAIL ') + name, '-', detail)
        return ok

    def finish(self):
        bad = [r for r in self.results if not r[1]]
        self.say('%d checks, %d failed%s' % (len(self.results), len(bad), (': ' + ', '.join(r[0] for r in bad)) if bad else ''))
        sys.exit(1 if bad else 0)
