"""Packs Arcticons' line icons into Ganimede's built-in icon set (issue 076), read by core/IconSet.

usage, from app/: python3 -I tools/icons/pack.py <Arcticons checkout at ARCTICONS_COMMIT> src/main/assets/icons.bin <android-app-categories checkout at CATEGORIES_COMMIT>
It reads <checkout>/icons/white/*.svg and <checkout>/app/src/main/assets/appfilter.xml.

Format (little endian), all geometry on Arcticons' 48x48 grid:
  header   'ICN1' (format 1), 20 B Arcticons commit (SHA-1), u32 iconCount, u32 indexCount, u32 namesOff, u32 indexOff
  offsets  u32[iconCount + 1], icon i is data[off[i]:off[i+1]], data starts right after this table
  icon     varint pathCount; per path: u8 style (1 stroke, 2 fill, 4 evenodd, bits 3-4 cap 0 round 1 butt 2 square,
           bits 5-6 join 0 round 1 miter 2 bevel), u8 stroke width * 16, u8 alpha * 255,
           varint opCount, then ops: u8 op (0 M, 1 L, 2 C, 3 Z, 4 OVAL x0 y0 x1 y1) followed by its points, each
           x and y a zigzag varint delta from the previous point of the icon, in 1/32 of a unit
  names    per icon, ids in name order: varint length + UTF-8
  index    indexCount * (u48 low bits of FNV-1a 64 of the key, u16 icon id), sorted by hash; the key is the package
           when every component of it in the appfilter draws one icon, otherwise "package/full.ClassName"
Format 2 ('ICN2', issue 087) adds the app categories after the icon index: the header grows by u32 categoryKeys, u32 categoryIndexOff,
u32 categoryTableOff (52 B in all). The category index has the icon index's layout (u48 hash of the package, u16 category id =
position in the table); the table is u16 n, n * u16 glyph icon id, then n category names (varint length + UTF-8).
Arcs become cubics, dashes become subpaths (a zero-length dash is a zero-length line, drawn by its cap), transforms,
CSS classes and the viewBox are applied here, so the reader only draws.
Whether a subpath is closed (for fills, issue 080) is read from the geometry by the decoder, not stored.
"""
import bisect
import math
import os
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET

ARCTICONS_COMMIT = "da2fa5694ccfea800a9b88f556131a20908e95c1"  # also in README.md; bump both and repack
NS = "{http://www.w3.org/2000/svg}"
Q = 32  # coordinates are stored in 1/32 of a unit
OPS = {"M": 0, "L": 1, "C": 2, "Z": 3, "O": 4}
STROKE, FILL, EVEN_ODD = 1, 2, 4
CAPS, JOINS = {"round": 0, "butt": 1, "square": 2}, {"round": 0, "miter": 1, "bevel": 2}  # style bits 3-4 and 5-6
NUMBER = r"-?[\d.]+(?:e-?\d+)?"


def css(text):
    """'.a,.b{fill:none}' -> {'a': {'fill': 'none'}, 'b': {...}}: Arcticons styles only by class."""
    out = {}
    for sel, body in re.findall(r"([^{}]+)\{([^}]*)\}", text or ""):
        props = dict((k.strip(), v.strip()) for k, v in (p.split(":", 1) for p in body.split(";") if ":" in p))
        for s in sel.split(","):
            s = s.strip()
            if s.startswith("."):
                out.setdefault(s[1:], {}).update(props)
    return out


def num(v, d=0.0):
    if v is None:
        return d
    m = re.match(NUMBER, v.strip())
    return float(m.group(0)) if m else d


def painted(v):
    return v is not None and v.strip().lower() not in ("none", "transparent")


def style_of(el, classes, inherited):
    """Inherited, then presentation attributes, then class rules, then style="": the later wins, as in CSS.

    opacity is not inherited: collect multiplies it down the tree.
    """
    s = {k: v for k, v in inherited.items() if k != "opacity"}
    for k in ("fill", "stroke", "stroke-width", "fill-rule", "opacity", "fill-opacity", "stroke-opacity", "stroke-linecap",
              "stroke-linejoin", "stroke-dasharray", "stroke-dashoffset"):
        if el.get(k) is not None:
            s[k] = el.get(k)
    for c in (el.get("class") or "").split():
        s.update(classes.get(c, {}))
    for p in (el.get("style") or "").split(";"):
        if ":" in p:
            k, v = p.split(":", 1)
            s[k.strip()] = v.strip()
    return s


def shape_path(tag, a):
    """Basic shapes as path data; circles and ellipses go through collect's OVAL when they can."""
    def f(x):
        return repr(float(x))
    if tag == "path":
        return a.get("d")
    if tag == "line":
        return "M%s,%sL%s,%s" % tuple(f(num(a.get(k))) for k in ("x1", "y1", "x2", "y2"))
    if tag in ("polyline", "polygon"):
        pts = [float(p) for p in re.findall(NUMBER, a.get("points", ""))]
        if len(pts) < 4:
            return None
        d = "M" + " L".join("%s,%s" % (f(pts[i]), f(pts[i + 1])) for i in range(0, len(pts) - 1, 2))
        return d + ("Z" if tag == "polygon" else "")
    if tag in ("circle", "ellipse"):
        cx, cy = num(a.get("cx")), num(a.get("cy"))
        rx = num(a.get("r")) if tag == "circle" else num(a.get("rx"))
        ry = num(a.get("r")) if tag == "circle" else num(a.get("ry"))
        if rx <= 0 or ry <= 0:
            return None
        # SVG starts at (cx + rx, cy) and goes clockwise: where a dash pattern begins
        return "M%s,%sA%s,%s 0 0 1 %s,%sA%s,%s 0 0 1 %s,%sA%s,%s 0 0 1 %s,%sA%s,%s 0 0 1 %s,%sZ" % (
            f(cx + rx), f(cy), f(rx), f(ry), f(cx), f(cy + ry), f(rx), f(ry), f(cx - rx), f(cy),
            f(rx), f(ry), f(cx), f(cy - ry), f(rx), f(ry), f(cx + rx), f(cy))
    if tag == "rect":
        x, y, w, h = (num(a.get(k)) for k in ("x", "y", "width", "height"))
        rx, ry = a.get("rx"), a.get("ry")
        rx, ry = num(rx if rx is not None else ry), num(ry if ry is not None else rx)
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx <= 0 or ry <= 0:
            return "M%s,%sh%sv%sh%sZ" % (f(x), f(y), f(w), f(h), f(-w))
        return ("M%s,%sh%sa%s,%s 0 0 1 %s,%sv%sa%s,%s 0 0 1 %s,%sh%sa%s,%s 0 0 1 %s,%sv%sa%s,%s 0 0 1 %s,%sZ" % (
            f(x + rx), f(y), f(w - 2 * rx), f(rx), f(ry), f(rx), f(ry), f(h - 2 * ry), f(rx), f(ry), f(-rx), f(ry),
            f(-(w - 2 * rx)), f(rx), f(ry), f(-rx), f(-ry), f(-(h - 2 * ry)), f(rx), f(ry), f(rx), f(-ry)))
    return None


def mul(a, b):
    """Affine (a b c d e f) product, SVG order: mul(parent, child)."""
    return (a[0] * b[0] + a[2] * b[1], a[1] * b[0] + a[3] * b[1], a[0] * b[2] + a[2] * b[3],
            a[1] * b[2] + a[3] * b[3], a[0] * b[4] + a[2] * b[5] + a[4], a[1] * b[4] + a[3] * b[5] + a[5])


def matrix(t):
    m = (1, 0, 0, 1, 0, 0)
    for fn, args in re.findall(r"(\w+)\s*\(([^)]*)\)", t or ""):
        v = [float(x) for x in re.findall(NUMBER, args)]
        if fn == "translate":
            n = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif fn == "scale":
            n = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif fn == "rotate":
            r = math.radians(v[0])
            n = (math.cos(r), math.sin(r), -math.sin(r), math.cos(r), 0, 0)
            if len(v) == 3:
                n = mul(mul((1, 0, 0, 1, v[1], v[2]), n), (1, 0, 0, 1, -v[1], -v[2]))
        elif fn == "matrix" and len(v) == 6:
            n = tuple(v)
        elif fn == "skewX":
            n = (1, 0, math.tan(math.radians(v[0])), 1, 0, 0)
        elif fn == "skewY":
            n = (1, math.tan(math.radians(v[0])), 0, 1, 0, 0)
        else:
            continue
        m = mul(m, n)
    return m


def arc_to_cubics(x1, y1, rx, ry, phi, large, sweep, x2, y2):
    """SVG elliptical arc (implementation notes F.6) as cubics of at most 90 degrees each."""
    if rx == 0 or ry == 0 or (x1 == x2 and y1 == y2):
        return [(x1, y1, x2, y2, x2, y2)]
    rx, ry = abs(rx), abs(ry)
    c, s = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    xp, yp = c * dx + s * dy, -s * dx + c * dy
    lam = xp * xp / (rx * rx) + yp * yp / (ry * ry)
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num_ = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp
    den = rx * rx * yp * yp + ry * ry * xp * xp
    k = math.sqrt(max(0, num_ / den)) * (-1 if large == sweep else 1)
    cxp, cyp = k * rx * yp / ry, -k * ry * xp / rx
    cx, cy = c * cxp - s * cyp + (x1 + x2) / 2, s * cxp + c * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        return math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
    t1 = ang(1, 0, (xp - cxp) / rx, (yp - cyp) / ry)
    dt = ang((xp - cxp) / rx, (yp - cyp) / ry, (-xp - cxp) / rx, (-yp - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    n = max(1, int(math.ceil(abs(dt) / (math.pi / 2) - 1e-9)))
    d = dt / n
    a = 4 / 3 * math.tan(d / 4)

    def pt(t):
        return (cx + rx * math.cos(t) * c - ry * math.sin(t) * s, cy + rx * math.cos(t) * s + ry * math.sin(t) * c)

    def dv(t):
        return (-rx * math.sin(t) * c - ry * math.cos(t) * s, -rx * math.sin(t) * s + ry * math.cos(t) * c)
    out = []
    for i in range(n):
        ta, tb = t1 + i * d, t1 + (i + 1) * d
        p0, p3, d0, d3 = pt(ta), pt(tb), dv(ta), dv(tb)
        out.append((p0[0] + a * d0[0], p0[1] + a * d0[1], p3[0] - a * d3[0], p3[1] - a * d3[1], p3[0], p3[1]))
    out[-1] = out[-1][:4] + (x2, y2)  # land exactly on the arc's end point
    return out


TOK = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def parse_d(d):
    """SVG path data -> absolute ops ('M',x,y) ('L',x,y) ('C',x1,y1,x2,y2,x,y) ('Z',).

    Every subpath starts with M: a drawing command right after Z gets the M that SVG implies (back to the start).
    """
    toks = TOK.findall(d)
    ops, i, cmd = [], 0, None
    x = y = sx = sy = 0.0
    lc = lq = None  # last control points, for S and T
    after_z = False

    def nums(n):
        nonlocal i
        v = [float(t) for t in toks[i:i + n]]
        if len(v) < n:
            raise ValueError("short")
        i += n
        return v
    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
            if cmd in "Zz":
                ops.append(("Z",))
                x, y = sx, sy
                lc = lq = None
                after_z = True
                continue
        elif cmd is None:
            break
        rel = cmd.islower()
        C = cmd.upper()
        if after_z and C != "M":
            ops.append(("M", x, y))
        after_z = False
        ox, oy = (x, y) if rel else (0, 0)
        try:
            if C == "M":
                a = nums(2)
                x, y = a[0] + ox, a[1] + oy
                sx, sy = x, y
                ops.append(("M", x, y))
                cmd = "l" if rel else "L"
                lc = lq = None
            elif C == "L":
                a = nums(2)
                x, y = a[0] + ox, a[1] + oy
                ops.append(("L", x, y))
                lc = lq = None
            elif C == "H":
                x = nums(1)[0] + ox
                ops.append(("L", x, y))
                lc = lq = None
            elif C == "V":
                y = nums(1)[0] + oy
                ops.append(("L", x, y))
                lc = lq = None
            elif C in "CS":
                if C == "C":
                    a = nums(6)
                    c1 = (a[0] + ox, a[1] + oy)
                    c2, e = (a[2] + ox, a[3] + oy), (a[4] + ox, a[5] + oy)
                else:
                    a = nums(4)
                    c1 = (2 * x - lc[0], 2 * y - lc[1]) if lc else (x, y)
                    c2, e = (a[0] + ox, a[1] + oy), (a[2] + ox, a[3] + oy)
                ops.append(("C",) + c1 + c2 + e)
                lc, lq = c2, None
                x, y = e
            elif C in "QT":
                if C == "Q":
                    a = nums(4)
                    q, e = (a[0] + ox, a[1] + oy), (a[2] + ox, a[3] + oy)
                else:
                    a = nums(2)
                    q = (2 * x - lq[0], 2 * y - lq[1]) if lq else (x, y)
                    e = (a[0] + ox, a[1] + oy)
                ops.append(("C", x + 2 / 3 * (q[0] - x), y + 2 / 3 * (q[1] - y),
                            e[0] + 2 / 3 * (q[0] - e[0]), e[1] + 2 / 3 * (q[1] - e[1]), e[0], e[1]))
                lq, lc = q, None
                x, y = e
            elif C == "A":
                a = nums(7)
                ex, ey = a[5] + ox, a[6] + oy
                for cu in arc_to_cubics(x, y, a[0], a[1], a[2], int(a[3]), int(a[4]), ex, ey):
                    ops.append(("C",) + cu)
                x, y = ex, ey
                lc = lq = None
            else:
                i += 1
        except ValueError:
            break  # SVG: render up to the first error
    return ops


def dash_pattern(st):
    """stroke-dasharray as lengths, or None for a solid stroke: none, negative or all zero (SVG); odd lists repeat."""
    v = [float(x) for x in re.findall(NUMBER, st.get("stroke-dasharray") or "")]
    if not v or min(v) < 0 or sum(v) <= 0:
        return None
    return v * 2 if len(v) % 2 else v


def cubic_at(p, t):
    """Point at t of the cubic p = (x0, y0, x1, y1, x2, y2, x3, y3)."""
    u = 1 - t
    return tuple(u * u * u * p[k] + 3 * u * u * t * p[k + 2] + 3 * u * t * t * p[k + 4] + t * t * t * p[k + 6] for k in (0, 1))


def cubic_split(p, t):
    """de Casteljau: the cubic p cut at t -> (first part, second part)."""
    def lerp(a, b):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
    p0, p1, p2, p3 = p[0:2], p[2:4], p[4:6], p[6:8]
    a, b, c = lerp(p0, p1), lerp(p1, p2), lerp(p2, p3)
    d, e = lerp(a, b), lerp(b, c)
    f = lerp(d, e)
    return p0 + a + d + f, f + e + c + p3


class Segment:
    """A line (4 numbers) or cubic (8) of a subpath, with its arc length (cubics: 32 chords, far below 1/32 off)."""

    def __init__(self, p):
        self.p = p
        if len(p) == 4:
            self.table = [0.0, math.hypot(p[2] - p[0], p[3] - p[1])]
        else:
            pts = [cubic_at(p, i / 32) for i in range(33)]
            self.table = [0.0]
            for a, b in zip(pts, pts[1:]):
                self.table.append(self.table[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
        self.length = self.table[-1]

    def t(self, s):
        """The parameter at arc length s, linear between the table's samples."""
        if self.length == 0:
            return 0.0
        tb = self.table
        i = min(max(bisect.bisect_left(tb, s), 1), len(tb) - 1)
        span = tb[i] - tb[i - 1]
        return ((i - 1) + ((s - tb[i - 1]) / span if span else 0)) / (len(tb) - 1)

    def at(self, t):
        p = self.p
        return (p[0] + (p[2] - p[0]) * t, p[1] + (p[3] - p[1]) * t) if len(p) == 4 else cubic_at(p, t)

    def cut(self, a, b):
        """-> (start point, op) of this segment between arc lengths a <= b; a zero-length piece is a line."""
        ta, tb = self.t(a), self.t(b)
        if len(self.p) == 4 or b <= a:
            return self.at(ta), ("L",) + self.at(tb)
        head = cubic_split(self.p, tb)[0]
        part = cubic_split(head, ta / tb)[1] if ta > 0 else head
        return part[0:2], ("C",) + part[2:]


def subpaths(ops):
    """Absolute ops (parse_d) -> [(its ops, [Segment], closed)]; Z adds the closing line."""
    out = []
    for op in ops:
        if op[0] == "M":
            out.append([[], [], False])
            first = last = op[1:]
        elif op[0] == "Z":
            if last != first:
                out[-1][1].append(Segment(last + first))
            out[-1][2] = True
        else:
            out[-1][1].append(Segment(last + op[1:]))
            last = op[-2:]
        out[-1][0].append(op)
    return out


def dash_ranges(length, pattern, offset):
    """The dashes along one subpath, [start, end] in arc length; dashes that touch (a zero gap) are one."""
    total, out, i = sum(pattern), [], 0
    s = -(offset % total)
    while s <= length + 1e-6:
        e = s + pattern[i]
        if i % 2 == 0 and e >= 0:
            a, b = min(max(s, 0), length), min(e, length)
            if out and a <= out[-1][1] + 1e-9:
                out[-1][1] = max(out[-1][1], b)
            else:
                out.append([a, b])
        s, i = e, (i + 1) % len(pattern)
    return out


def dashed(ops, pattern, offset):
    """SVG dashing, per subpath from its start: each dash becomes its own open subpath."""
    out = []
    for own, segs, closed in subpaths(ops):
        length = sum(g.length for g in segs)
        ranges = dash_ranges(length, pattern, offset) if length > 0 else [[0, length]]
        if ranges == [[0, length]]:  # solid, or a dot that is drawn as written: keep the original
            out += own
            continue

        def piece(a, b):
            res, c = [], 0.0
            for g in segs:
                lo, hi = max(a, c), min(b, c + g.length)
                if hi > lo or (a == b and not res and c <= a <= c + g.length):
                    p0, op = g.cut(lo - c, hi - c)
                    res += ([("M",) + p0] if not res else []) + [op]
                c += g.length
                if c > b:
                    break
            return res
        pieces = [piece(a, b) for a, b in ranges]
        if closed and len(ranges) > 1 and ranges[0][0] <= 1e-9 and ranges[-1][1] >= length - 1e-9:
            pieces[0] = pieces.pop() + pieces[0][1:]  # a dash across the closing point is one dash
        for pc in pieces:
            out += pc
    return out


def collect(el, classes, inherited, m, alpha, out):
    """Appends (style, width, alpha, ops) for each painted shape under el, in document order, transforms applied.

    alpha is the product of the ancestors' opacity; a shape whose fill and stroke differ in opacity becomes two paths.
    """
    tag = el.tag.replace(NS, "")
    if tag in ("defs", "style", "title", "desc", "metadata", "clipPath", "mask") or tag.startswith("{"):
        return
    st = style_of(el, classes, inherited)
    m = mul(m, matrix(el.get("transform")))
    alpha *= num(st.get("opacity"), 1.0)
    if tag in ("g", "svg"):
        for ch in el:
            collect(ch, classes, st, m, alpha, out)
        return
    width = num(st.get("stroke-width"), 1.0)
    stroked = painted(st.get("stroke")) and width > 0
    style = (STROKE if stroked else 0) | (FILL if painted(st.get("fill", "#000")) else 0)
    if not style:
        return
    if st.get("fill-rule") == "evenodd":
        style |= EVEN_ODD
    ends = 0
    if stroked:  # SVG's defaults are butt and miter; unknown values (miter-clip, arcs) draw as miter
        ends = CAPS.get(st.get("stroke-linecap"), 1) << 3 | JOINS.get(st.get("stroke-linejoin"), 1) << 5
    style |= ends
    pattern = dash_pattern(st) if stroked else None
    fill_alpha, stroke_alpha = alpha * num(st.get("fill-opacity"), 1.0), alpha * num(st.get("stroke-opacity"), 1.0)
    if style & FILL and stroked and (fill_alpha != stroke_alpha or pattern):
        parts = [(style & (FILL | EVEN_ODD), fill_alpha), (STROKE | ends, stroke_alpha)]  # SVG paints the fill, then the stroke
    else:
        parts = [(style, stroke_alpha if stroked else fill_alpha)]
    rigid = abs(m[1]) < 1e-9 and abs(m[2]) < 1e-9
    if tag in ("circle", "ellipse") and rigid and not pattern:
        a = el.attrib
        cx, cy = num(a.get("cx")), num(a.get("cy"))
        rx = num(a.get("r") if tag == "circle" else a.get("rx"))
        ry = num(a.get("r") if tag == "circle" else a.get("ry"))
        if rx > 0 and ry > 0:
            box = (m[0] * (cx - rx) + m[4], m[3] * (cy - ry) + m[5], m[0] * (cx + rx) + m[4], m[3] * (cy + ry) + m[5])
            box = (min(box[0], box[2]), min(box[1], box[3]), max(box[0], box[2]), max(box[1], box[3]))
            out += [(paint, width * math.sqrt(abs(m[0] * m[3])), a, [("O",) + box]) for paint, a in parts]
        return
    d = shape_path(tag, el.attrib)
    if not d:
        return
    raw = parse_d(d)
    if not raw:
        return
    offset = num(st.get("stroke-dashoffset"))
    for paint, a in parts:
        ops = []
        for op in dashed(raw, pattern, offset) if pattern and paint & STROKE else raw:  # dashes in user units
            pts = op[1:]
            tp = []
            for k in range(0, len(pts), 2):
                tp += [m[0] * pts[k] + m[2] * pts[k + 1] + m[4], m[1] * pts[k] + m[3] * pts[k + 1] + m[5]]
            ops.append((op[0],) + tuple(tp))
        out.append((paint, width * math.sqrt(abs(m[0] * m[3] - m[1] * m[2])), a, ops))


def icon_paths(svg_file):
    root = ET.parse(svg_file).getroot()
    classes = {}
    for s in root.iter(NS + "style"):
        classes.update(css(s.text))
    vb = [float(v) for v in (root.get("viewBox") or "0 0 48 48").replace(",", " ").split()]
    sx, sy = 48 / vb[2], 48 / vb[3]  # a few icons are drawn on 43x43 or 44x44
    paths = []
    collect(root, classes, {}, (sx, 0, 0, sy, -vb[0] * sx, -vb[1] * sy), 1.0, paths)
    return paths


def varint(out, v):
    while v >= 0x80:
        out.append((v & 0x7F) | 0x80)
        v >>= 7
    out.append(v)


def encode_icon(paths):
    b = bytearray()
    varint(b, len(paths))
    lx = ly = 0
    for style, width, alpha, ops in paths:
        b += bytes((style, min(255, round(width * 16)), max(0, min(255, round(alpha * 255)))))
        varint(b, len(ops))
        for op in ops:
            b.append(OPS[op[0]])
            for k in range(1, len(op), 2):
                qx, qy = round(op[k] * Q), round(op[k + 1] * Q)
                for dv in (qx - lx, qy - ly):
                    varint(b, (dv << 1) ^ (dv >> 63))  # zigzag: small negatives stay small
                lx, ly = qx, qy
    return bytes(b)


def fnv48(key):
    """Low 48 bits of FNV-1a 64 over the UTF-8 bytes; core/IconIndex.fnv48 must give the same."""
    h = 0xcbf29ce484222325
    for c in key.encode():
        h = ((h ^ c) * 0x100000001b3) & 0xFFFFFFFFFFFFFFFF
    return h & 0xFFFFFFFFFFFF


def components(appfilter, ids):
    """{package: {"package/full.Class": icon id}} from the appfilter's <item>s whose drawable exists."""
    comps = {}
    for it in ET.parse(appfilter).getroot().iter("item"):
        m = re.match(r"ComponentInfo\{([^/]+)/([^}]+)\}", it.get("component") or "")
        drawable = it.get("drawable")
        if m and drawable in ids:
            pkg, cls = m.group(1), m.group(2)
            comps.setdefault(pkg, {})[pkg + "/" + (pkg + cls if cls.startswith(".") else cls)] = ids[drawable]
    return comps


def keys(comps):
    """One key per package that draws one icon everywhere, else one per component (core/IconIndex.find)."""
    out = {}
    for pkg, cs in comps.items():
        if len(set(cs.values())) == 1:
            out[pkg] = next(iter(cs.values()))
        else:
            out.update(cs)
    return out


def index(key_ids, hash48=fnv48):
    """Sorted (hash, id); hash48 is a parameter only so the test can force the collision no real key set has."""
    entries = sorted((hash48(k), v) for k, v in key_ids.items())
    for a, b in zip(entries, entries[1:]):
        if a[0] == b[0]:
            sys.exit("hash collision %012x: two keys would share an icon" % a[0])
    return entries


# Play Store categories in the order of core/AppCategory, with the built-in icon that stands for each (issue 087)
GAMES = ["ACTION", "ADVENTURE", "ARCADE", "BOARD", "CARD", "CASINO", "CASUAL", "EDUCATIONAL", "MUSIC", "PUZZLE", "RACING",
         "ROLE_PLAYING", "SIMULATION", "SPORTS", "STRATEGY", "TRIVIA", "WORD"]
GLYPHS = dict([
    ("ART_AND_DESIGN", "emoji_artist_palette"), ("AUTO_AND_VEHICLES", "car"), ("BEAUTY", "emoji_dress"),
    ("BOOKS_AND_REFERENCE", "books"), ("BUSINESS", "emoji_briefcase"), ("COMICS", "emoji_collision_symbol"),
    ("COMMUNICATION", "chat"), ("DATING", "emoji_two_hearts"), ("EDUCATION", "school"), ("ENTERTAINMENT", "tv"),
    ("EVENTS", "emoji_party_popper"), ("FINANCE", "emoji_classical_building"), ("FOOD_AND_DRINK", "emoji_pot_of_food"),
    ("HEALTH_AND_FITNESS", "workout"), ("HOUSE_AND_HOME", "home"), ("LIBRARIES_AND_DEMO", "emoji_package"),
    ("LIFESTYLE", "emoji_potted_plant"), ("MAPS_AND_NAVIGATION", "maps"), ("MEDICAL", "pill"), ("MUSIC_AND_AUDIO", "music"),
    ("NEWS_AND_MAGAZINES", "emoji_newspaper"), ("PARENTING", "emoji_baby_bottle"), ("PERSONALIZATION", "themes"),
    ("PHOTOGRAPHY", "camera"), ("PRODUCTIVITY", "emoji_clipboard"), ("SHOPPING", "shopping"),
    ("SOCIAL", "emoji_busts_in_silhouette"), ("SPORTS", "emoji_soccer_ball"), ("TOOLS", "emoji_hammer_and_wrench"),
    ("TRAVEL_AND_LOCAL", "emoji_airplane"), ("VIDEO_PLAYERS", "emoji_clapper_board"), ("WEATHER", "weather")]
    + [("GAME_" + g, "games") for g in GAMES])
CATEGORIES_COMMIT = "05ba4fb166ae3e5dd66ee50645ac8a6186259216"  # heysupratim/android-app-categories (Unlicense)
# the dataset's 67 `type` values that are not a category's own name once "&" is "AND": typos, old names, games without "GAME_"
ALIASES = {
    "Offic": "PRODUCTIVITY", "Office": "PRODUCTIVITY", "Development": "TOOLS", "Utilities": "TOOLS",
    "Transport": "MAPS_AND_NAVIGATION", "Transportation": "MAPS_AND_NAVIGATION", "Sport": "SPORTS",
    "Libraries & Demo": "LIBRARIES_AND_DEMO", "Library & Demos": "LIBRARIES_AND_DEMO",
    "Media & Audio": "MUSIC_AND_AUDIO", "Music & AUdio": "MUSIC_AND_AUDIO",
    "Media & Video": "VIDEO_PLAYERS", "Media & Videos": "VIDEO_PLAYERS", "Media & Vide0": "VIDEO_PLAYERS",
    "Video Players & Editors": "VIDEO_PLAYERS", "Travel & Locale": "TRAVEL_AND_LOCAL",
    "Brain Games": "GAME_PUZZLE", "Puzles": "GAME_PUZZLE", "Puzzle": "GAME_PUZZLE", "Board Games": "GAME_BOARD",
    "Racing Games": "GAME_RACING", "Quiz": "GAME_TRIVIA", "General Culture": "GAME_TRIVIA", "Music": "GAME_MUSIC",
    "Educational": "GAME_EDUCATIONAL", "Role Playing": "GAME_ROLE_PLAYING"}
for _g in ("Action", "Adventure", "Arcade", "Board", "Card", "Casino", "Casual", "Racing", "Simulation", "Strategy", "Trivia", "Word"):
    ALIASES[_g] = "GAME_" + _g.upper()


def category_of(type_):
    """The Play category of a dataset `type` value, or None for the one empty value (every other value must map)."""
    if not type_.strip():
        return None
    name = ALIASES.get(type_) or type_.upper().replace(" & ", "_AND_").replace(" ", "_")
    if name not in GLYPHS:
        sys.exit("dataset type %r maps to no category" % type_)
    return name


def read_curated(path):
    """{package: category} from curated.tsv: package TAB CATEGORY TAB source and date; blank and # lines skipped."""
    out = {}
    for n, line in enumerate(open(path, encoding="utf-8"), 1):
        if not line.strip() or line.startswith("#"):
            continue
        cols = line.rstrip("\n").split("\t")
        if len(cols) < 3 or cols[1] not in GLYPHS or not cols[2].strip():
            sys.exit("%s:%d: want package, a category and its source" % (path, n))
        out[cols[0]] = cols[1]
    return out


def read_dataset(path):
    """{package: category} from heysupratim's apps_with_category.txt."""
    import json
    out = {}
    with open(path, encoding="utf-8") as f:
        for app in json.load(f)["apps"]:
            c = category_of(app["type"])
            if c:
                out[app["packagename"]] = c
    return out


def category_keys(curated, dataset, taken):
    """{package: category}: the curated table beats the dataset; a package that already has an Arcticons icon is dropped."""
    merged = dict(dataset)
    merged.update(curated)
    return {p: c for p, c in merged.items() if p not in taken}


def pack(svg_dir, appfilter, commit, categories=None, glyphs=GLYPHS):
    """-> (icons.bin bytes, summary line). categories = (curated.tsv, dataset or None): format 2, else format 1."""
    names = sorted(n[:-4] for n in os.listdir(svg_dir) if n.endswith(".svg"))
    if len(names) > 0xFFFF:
        sys.exit("%d icons: ids are u16" % len(names))
    blobs = []
    for n in names:
        try:
            blobs.append(encode_icon(icon_paths(os.path.join(svg_dir, n + ".svg"))))
        except ET.ParseError as e:
            sys.exit("%s.svg: %s" % (n, e))
    icon_keys = keys(components(appfilter, {n: i for i, n in enumerate(names)}))
    entries = index(icon_keys)
    if categories:
        glyph_ids = []
        for c, g in glyphs.items():
            if g not in names:
                sys.exit("category %s: glyph %r is not among the icons" % (c, g))
            glyph_ids.append(names.index(g))
        cats = list(glyphs)
        cat_entries = index({p: cats.index(c) for p, c in category_keys(
            read_curated(categories[0]), read_dataset(categories[1]) if categories[1] else {}, icon_keys).items()})
    offs, data = [0], bytearray()
    for bl in blobs:
        data += bl
        offs.append(len(data))
    namesec = bytearray()
    for n in names:
        e = n.encode()
        varint(namesec, len(e))
        namesec += e
    fixed = 52 if categories else 40
    head = fixed + 4 * len(offs)
    out = bytearray((b"ICN2" if categories else b"ICN1") + bytes.fromhex(commit))
    out += struct.pack("<IIII", len(names), len(entries), head + len(data), head + len(data) + len(namesec))
    if categories:
        cat_at = head + len(data) + len(namesec) + 8 * len(entries)
        out += struct.pack("<III", len(cat_entries), cat_at, cat_at + 8 * len(cat_entries))
    out += struct.pack("<%dI" % len(offs), *offs) + data + namesec
    for h, v in entries:
        out += struct.pack("<Q", h)[:6] + struct.pack("<H", v)
    if categories:
        for h, v in cat_entries:
            out += struct.pack("<Q", h)[:6] + struct.pack("<H", v)
        out += struct.pack("<H", len(glyph_ids)) + struct.pack("<%dH" % len(glyph_ids), *glyph_ids)
        for c in glyphs:
            varint(out, len(c))
            out += c.encode()
    empty = sum(1 for bl in blobs if bl == b"\x00")
    return bytes(out), ("icons %d (empty %d), index %d keys, %d category keys, geometry %d B, names %d B, total %d B"
                        % (len(names), empty, len(entries), len(cat_entries) if categories else 0, len(data), len(namesec), len(out)))


HERE = os.path.dirname(os.path.abspath(__file__))
CURATED = os.path.join(HERE, "categories", "curated.tsv")
COVERAGE = os.path.join(HERE, "categories", "coverage")


def checked_out(checkout, pinned, what):
    head = subprocess.run(["git", "-C", checkout, "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
    if head != pinned:
        sys.exit("%s checkout is at %r, the pinned commit is %s" % (what, head, pinned))


def coverage(icon_keys, category_packages):
    """Per public list (tools/icons/categories/coverage/*.txt, one package per line): % with an icon / a category / neither."""
    for name in sorted(os.listdir(COVERAGE)):
        pkgs = [l.split()[0] for l in open(os.path.join(COVERAGE, name), encoding="utf-8") if l.strip() and not l.startswith("#")]
        icon = sum(1 for p in pkgs if p in icon_keys)
        cat = sum(1 for p in pkgs if p not in icon_keys and p in category_packages)
        print("%s: %d apps, icon %d%%, category only %d%%, neither %d%%" % (
            name, len(pkgs), 100 * icon // len(pkgs), 100 * cat // len(pkgs), 100 * (len(pkgs) - icon - cat) // len(pkgs)))


def main(checkout, dst, dataset_checkout=None):
    checked_out(checkout, ARCTICONS_COMMIT, "Arcticons")
    appfilter = os.path.join(checkout, "app", "src", "main", "assets", "appfilter.xml")
    dataset = None
    if dataset_checkout:
        checked_out(dataset_checkout, CATEGORIES_COMMIT, "Category dataset")
        dataset = os.path.join(dataset_checkout, "apps_with_category.txt")
    data, summary = pack(os.path.join(checkout, "icons", "white"), appfilter, ARCTICONS_COMMIT, (CURATED, dataset))
    with open(dst, "wb") as f:
        f.write(data)
    print(summary)
    names = sorted(n[:-4] for n in os.listdir(os.path.join(checkout, "icons", "white")) if n.endswith(".svg"))
    coverage(keys(components(appfilter, {n: i for i, n in enumerate(names)})),
             (read_dataset(dataset) if dataset else {}) | read_curated(CURATED))


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    main(*sys.argv[1:])
