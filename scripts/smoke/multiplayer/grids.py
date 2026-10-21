"""Tile scene_swing_particles.py shots per node: python grids.py <node> -> build/smoke-mp/<node>/*-grid.png (needs Pillow)."""
import sys, os
from PIL import Image
node = sys.argv[1]
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'build', 'smoke-mp')
S = os.path.join(ROOT, node, 'shots')
def grid(out, names, crop=None, cols=3, size=(427, 240)):
    names = [n for n in names if os.path.exists(os.path.join(S, n + '.png'))]
    w, h = (crop[2]-crop[0], crop[3]-crop[1]) if crop else size
    g = Image.new('RGB', (w*cols, h*((len(names)+cols-1)//cols)), 'white')
    for i, n in enumerate(names):
        im = Image.open(os.path.join(S, n + '.png')).convert('RGB')
        im = im.crop(crop) if crop else im.resize(size)
        g.paste(im, ((i % cols)*w, (i//cols)*h))
    g.save(os.path.join(ROOT, node, out))
grid('robots-grid.png', ['A-robots-02s','A-robots-10s','A-robots-20s','A-robots-30s'], cols=2)
grid('wrench-grid.png', ['A-wrench-before'] + [f'A-wrench-swing-{i}' for i in range(5)] + [f'A-wrench-plain-{i}' for i in range(3)], crop=(277,70,577,370), cols=5)
grid('helmet-grid.png', ['B-robotclick-aim'] + [f'A-helmet-swing-{i}' for i in range(4)])
grid('dismantle-grid.png', [f'A-dismantle-swing-{i}' for i in range(4)], cols=2)
grid('mine-grid.png', ['A-mine-before','A-mine-progress-0','A-mine-progress-2','A-mine-progress-4','A-mine-break-0','A-mine-break-1'], crop=(480,100,840,340))
