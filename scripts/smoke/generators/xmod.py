"""Shared step builders for the cross-mod scenario generators (gen_crossmod_*.py, issue #186).

A scenario is a list of runner steps (scripts/smoke/README.md); `Scenario` collects them, `at(x, y, z)` spells an offset
from the scenario base as the runner's {X+n}/{Y+n}/{Z+n} placeholders, and `write` saves the JSON next to the others.
"""
import json
import os

BC = "buildcraftunofficial:"
DEV_UUID = "380df991-f603-344c-a090-369bad2a924a"   # the dev client's offline UUID for "Dev", the same on every node
SCENARIOS = os.path.join(os.path.dirname(__file__), "..", "scenarios")


def o(axis, n):
    return "{%s%s}" % (axis, ("+%d" % n) if n > 0 else ("-%d" % -n) if n < 0 else "")


def at(x, y, z):
    return "%s %s %s" % (o('X', x), o('Y', y), o('Z', z))


def pipe(x, y, z, pdef, extra=""):
    """A pipe holder with a pipe. `extra` is appended inside the top-level compound (plugs, ...)."""
    return ["setblock %s %spipe_holder" % (at(x, y, z), BC),
            'data merge block %s {pipe:{def:"%s%s"}%s}' % (at(x, y, z), BC, pdef, extra)]


class Scenario:
    def __init__(self, name, base, description="", **top):
        self.name = name
        self.base = base
        self.description = description
        self.top = top
        self.steps = []
        self.only = None          # while set, every step added is limited to these nodes (see only_on)
        self.only_loaders = None  # and to these loaders

    def only_on(self, nodes=None, loaders=None):
        """`with S.only_on(["26.1.2"]):` limits the steps added inside to those Minecraft lines (a step's "nodes"),
        `with S.only_on(loaders=["fabric"]):` to that loader's nodes (a step's "loaders"). Scopes nest and narrow."""
        scenario = self

        class _Scope:
            def __enter__(self):
                self.before = (scenario.only, scenario.only_loaders)
                if nodes is not None:
                    scenario.only = [n for n in nodes if scenario.only is None or n in scenario.only]
                if loaders is not None:
                    scenario.only_loaders = [ld for ld in loaders if scenario.only_loaders is None or ld in scenario.only_loaders]

            def __exit__(self, *exc):
                scenario.only, scenario.only_loaders = self.before
        return _Scope()

    def step(self, **kw):
        if self.only is not None:
            kw["nodes"] = [n for n in kw["nodes"] if n in self.only] if "nodes" in kw else self.only
        if self.only_loaders is not None:
            kw["loaders"] = [ld for ld in kw["loaders"] if ld in self.only_loaders] if "loaders" in kw else self.only_loaders
        if kw.get("nodes") == [] or kw.get("loaders") == []:
            return    # the scopes leave it nowhere to run
        self.steps.append(kw)

    def cmd(self, label, *run, **kw):
        self.step(label=label, do="cmd", run=list(run), **kw)

    def echo(self, label, c, **kw):
        """Run one command that may legitimately fail and put its reply in the report."""
        self.step(label=label, do="cmd", run=[c], allowFail=True, echo=True, **kw)

    def wait_cmd(self, label, c, match, timeout=60, poll=1000, **kw):
        self.step(label=label, do="waitCmd", run=c, match=match, timeoutSec=timeout, pollMs=poll, **kw)

    def tp(self, x, y, z, yaw=0, pitch=0):
        self.step(do="tp", at=[x, y, z], yaw=yaw, pitch=pitch)

    def aim(self, x, y, z):
        self.step(do="aim", at=[x, y, z])

    def shot(self, name, look):
        self.step(name=name, do="shot", look=look)

    def wait(self, ms, label=None):
        if label:
            self.step(label=label, do="wait", ms=ms)
        else:
            self.step(do="wait", ms=ms)

    def hold(self, item):
        self.step(do="hold", item=item)

    def write(self, filename=None):
        d = {"name": self.name, "description": self.description}
        d.update(self.top)
        d["base"] = self.base
        d["steps"] = self.steps
        path = os.path.join(SCENARIOS, filename or (self.name + ".json"))
        with open(path, "w", newline=chr(10)) as f:
            f.write(json.dumps(d, indent=1) + chr(10))
        print("wrote %s (%d steps)" % (os.path.normpath(path), len(self.steps)))
