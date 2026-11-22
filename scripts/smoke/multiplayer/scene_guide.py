"""The guide book's crafting panels on a client of a REMOTE server (#218): the Forge recipe sync's client receiver, observed.

  python -I scene_guide.py <node> <phase>
phases: prep (a floor and open sky for Worker), guide (Worker opens the book, searches, opens a page; the log check), log (the log
check alone, to re-run it), all (prep, guide).

Why a remote client: a single-player guide falls back to the integrated server's RecipeManager (ClientGuideRecipeCache), so a
recipe payload that never arrived looks identical to one that did. A client of a dedicated server has no such fallback: its
crafting and smelting panels can only come from the sync. On the Forge nodes that carry ForgeRecipeSync (MinecraftForge 1.21.10
and later: 26.3, 26.2, 26.1.2 and 1.21.11) the receiver logs one line when the last batch of a run lands,
`[recipe sync] received <n> recipes in <m> batches`; the scene greps each client's latest.log for it (every joined client gets the
run, so Watcher's log is checked as well as Worker's) and fails on none or on zero recipes. Other nodes get the guide walk and the
shots but no log check: a NeoForge node has no such line and 1.21.1-forge uses vanilla's own full recipe sync.

The walk (Worker, B): hold the guide, look at the sky, use it, wait for GuiGuide, open the cover, focus the search tab, type
`Stirling Engine`, and click down the first result column until a page opens (the Back button turns visible: the guide keeps a
history, and the contents page has none). The bridge sees no guide content, so a rendered crafting panel is a screenshot for a
human (B-guide-page.png), and only "a page opened" is programmatic. The book is centred in the window, so the click points are
computed from the GUI size `/screenclick list` reports.
Every `check` is a recorded pass/fail (exit 1 when one fails); shots land in build/smoke-mp/<node>/shots as <A|B>-<name>.png.
"""
import os, re, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mp
from scene_lib import Scene

NODE = sys.argv[1]
mp.refuse_unsupported_node(NODE)
PHASE = sys.argv[2]
S = Scene(NODE)
X, Y, Z = S.X, S.Y, S.Z
cmd, b, shot, tp, look, hold = S.cmd, S.b, S.shot, S.tp, S.look, S.hold
A, B = 'Watcher', 'Worker'
# ForgeRecipeSync (and its log line) exists on MinecraftForge from 1.21.10 on only.
HAS_SYNC_LINE = mp.node_loader(NODE) == 'forge' and mp.mc_version(NODE) >= (1, 21, 10)
SYNC_LINE = re.compile(r'\[recipe sync\] received (\d+) recipes in (\d+) batches')
# The open book's layout, from GuiGuide: two pages of 193 by 248 centred in the window; the search tab sits at (38, 12) from the
# book's corner (inside the tab's click rectangle, which spans 21..61 by -9..25 there); a contents page's text starts at (23, 25).
BOOK_W, BOOK_H = 2 * 193, 248
TAB = (38, 12)
TEXT = (23, 25)


def screen(side, body):
    """One /screenclick call: the reply, or the error text as a dict (a click the bridge refuses must not end the scene)."""
    try:
        return b(side, 'POST', '/screenclick', body) or {}
    except Exception as e:
        return {'success': False, 'message': str(e)[:160]}


def book_corner(side):
    """The open book's top-left corner in GUI units, from the GUI size (Java's int division truncates toward zero)."""
    r = screen(side, {'list': True})
    return int((r.get('width', 427) - BOOK_W) / 2), int((r.get('height', 240) - BOOK_H) / 2)


def back_visible(side):
    """True when the guide's Back button shows: the book has a page history, i.e. it has left the contents page."""
    for w in screen(side, {'list': True}).get('widgets', []):
        if w.get('text') == 'Back':
            return bool(w.get('visible'))
    return False


def prep():
    cmd('forceload add {X-12} {Z-12} {X+12} {Z+12}')
    for c in ['difficulty peaceful', 'time set 6000', 'weather clear', 'gamemode creative Watcher', 'gamemode creative Worker',
              'kill @e[type=minecraft:item]', 'fill {X-12} {Y} {Z-12} {X+12} {Y+8} {Z+12} minecraft:air',
              'fill {X-12} {Y-1} {Z-12} {X+12} {Y-1} {Z+12} minecraft:smooth_stone',
              'gamerule advance_time false', 'gamerule doDaylightCycle false']:  # one of the two fails per node
        cmd(c)
    time.sleep(1.5)


def sync_log(side, seconds=15):
    """(recipes, batches) of the last `[recipe sync] received` line in the client's latest.log, waiting for one; None if none."""
    path = os.path.join(mp.node_dir(NODE), 'run-smoke', 'mp-' + side.lower(), 'logs', 'latest.log')
    end = time.time() + seconds
    while True:
        try:
            with open(path, encoding='utf-8', errors='replace') as f:
                found = SYNC_LINE.findall(f.read())
        except OSError:
            found = []
        if found or time.time() >= end:
            return tuple(int(n) for n in found[-1]) if found else None
        time.sleep(1.0)


def sync_check():
    if not HAS_SYNC_LINE:
        S.say('SKIP recipe sync log check: %s has no ForgeRecipeSync (a NeoForge node, or 1.21.1-forge on vanilla\'s sync)' % NODE)
        return
    for side, who in (('B', B), ('A', A)):
        got = sync_log(side)
        S.check('recipe sync: %s\'s latest.log holds the received-run line with recipes' % who, got is not None and got[0] > 0,
                'no line' if got is None else '%d recipes in %d batches' % got)


def guide():
    tp(B, X + 0.5, Y, Z + 0.5, 0, -90)
    time.sleep(1.5)
    hold(B, 'B', 'buildcraftunofficial:guide')
    look('B', 0, -90)  # at the sky: no block or entity in the way of the use
    S.say('use the guide (Worker)', b('B', 'POST', '/worldclick', {'action': 'right_click'}))
    # The first open of a joined world pays the guide rebuild, which on this node's first recipe run is the slow part.
    S.check('guide: the book opens on the remote client', S.wait_screen('B', 'GuiGuide', 30), S.screen_class('B'))
    time.sleep(1.0)
    shot('B', 'guide-cover', 'the guide book cover on a client of a remote server')
    screen('B', {'fx': 0.5, 'fy': 0.5})  # open the cover
    time.sleep(2.5)
    left, top = book_corner('B')
    S.say('book corner (GUI units)', left, top)
    S.check('guide: the contents page has no history yet (Back hidden)', not back_visible('B'))
    shot('B', 'guide-contents', 'the contents spread')
    screen('B', {'x': left + TAB[0], 'y': top + TAB[1]})  # focus the search tab
    time.sleep(0.5)
    S.say('type', b('B', 'POST', '/screenkeys', {'type': 'text', 'value': 'Stirling Engine'}))
    time.sleep(1.5)
    shot('B', 'guide-search', 'search results for Stirling Engine')
    # The results are a flat list, 16 GUI units a row, under the left page's top; which row holds the item entry depends on the
    # chapter headings above it, so click down the column (x well inside an entry's icon and name) until a page opens.
    x, opened = left + TEXT[0] + 50, False
    for y in range(top + TEXT[1] + 4, top + TEXT[1] + 4 + 16 * 9, 8):
        screen('B', {'x': x, 'y': y})
        time.sleep(0.4)  # also keeps two clicks outside vanilla's 250 ms double-click window
        if back_visible('B'):
            opened = True
            S.say('a search result opened at', x, y)
            break
    S.check('guide: a search result opens a page (Back shows)', opened, 'GuiGuide still open: %s' % S.screen_class('B'))
    time.sleep(1.5)
    shot('B', 'guide-page', 'the opened page: a crafting recipe panel (item icons in a grid) must be there, drawn from the synced recipes')
    S.check('guide: the book is still open on the page', 'GuiGuide' in S.screen_class('B'), S.screen_class('B'))
    sync_check()
    b('B', 'POST', '/screenclose')


if PHASE in ('prep', 'all'):
    prep()
if PHASE in ('guide', 'all'):
    guide()
if PHASE == 'log':
    sync_check()
S.finish()
