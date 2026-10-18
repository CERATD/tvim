"""Build OFL-derived Nothing fonts with native square zero outlines.
Run with fonttools and Pillow installed; original font resources are retained.
"""
from pathlib import Path
from copy import deepcopy
from array import array
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph, GlyphCoordinates
from fontTools.varLib.instancer import instantiateVariableFont
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONTS = ROOT / 'app/src/main/res/font'

def contours(g, glyf):
    coords, ends, flags = g.getCoordinates(glyf)
    start = 0
    for end in ends:
        yield list(coords[start:end+1]), list(flags[start:end+1])
        start = end + 1

def build(parts):
    g = Glyph()
    points, flags, ends = [], [], []
    for coords, f, dx, dy in parts:
        points.extend((x+dx, y+dy) for x,y in coords)
        flags.extend(f)
        ends.append(len(points)-1)
    g.numberOfContours = len(ends)
    g.coordinates = GlyphCoordinates(points)
    g.flags = array('B', flags)
    g.endPtsOfContours = ends
    from fontTools.ttLib.tables.ttProgram import Program
    g.program = Program()
    g.program.fromBytecode([])
    return g

outputs = []
for src, dst, family in [('nothing_doto', 'tvim_display_square', 'TVIM Display Square'),
                          ('matrix_nothing', 'tvim_text_square', 'TVIM Text Square')]:
    font = TTFont(FONTS / (src+'.ttf'))
    if 'fvar' in font:
        font = instantiateVariableFont(font, {a.axisTag:a.defaultValue for a in font['fvar'].axes}, inplace=True)
    cmap = font.getBestCmap()
    zero, five = cmap[48], cmap[53]
    original = deepcopy(font['glyf'][zero])
    original.recalcBounds(font['glyf'])
    original_metrics = font['hmtx'][zero]
    parts = []
    if src == 'nothing_doto':
        coords, flags = list(contours(font['glyf'][five], font['glyf']))[0]
        for row in range(7):
            for col in range(5):
                if row in (0,6) or col in (0,4):
                    parts.append((coords, flags, col*100, row*100-600))
    else:
        coords, flags = list(contours(font['glyf'][five], font['glyf']))[-1]
        parts.extend([(coords,flags,0,0), (coords,flags,0,-600)])
        coords, flags = list(contours(original, font['glyf']))[2]
        for row in range(5):
            for dx in (0,400):
                parts.append((coords,flags,dx,row*100))
    font['glyf'][zero] = build(parts)
    font['glyf'][zero].recalcBounds(font['glyf'])
    g = font['glyf'][zero]
    assert (g.xMin,g.yMin,g.xMax,g.yMax) == (original.xMin,original.yMin,original.xMax,original.yMax)
    assert font['hmtx'][zero] == original_metrics
    for record in font['name'].names:
        if record.nameID in (1,3,4,6,16,17):
            value = 'Regular' if record.nameID == 17 else family.replace(' ','') if record.nameID == 6 else family
            record.string = value.encode(record.getEncoding())
    font.save(FONTS / (dst+'.ttf'))
    outputs.append(FONTS / (dst+'.ttf'))
    print(dst, 'zero bounds', (g.xMin,g.yMin,g.xMax,g.yMax), 'metrics', original_metrics)

# Render actual font outlines together, at clock and input-field sizes.
image = Image.new('RGB',(1100,680),'#191919')
draw = ImageDraw.Draw(image)
for path, y in zip(outputs, [30,340]):
    for size, offset, sample in [(90,0,'00:05:00  07:30'), (28,150,'Hours 0   Minutes 5   Seconds 0   10 min')]:
        f = ImageFont.truetype(str(path),size)
        draw.text((35,y+offset),sample,font=f,fill='white')
preview = ROOT / 'design/square-zero-font-preview.png'
image.save(preview)
print(preview)
