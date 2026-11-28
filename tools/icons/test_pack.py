"""Packer checks: python3 -I tools/icons/test_pack.py (from app/).

The Java decoder tests (IconSetTest, IconIndexTest) read the fixture packed from src/test/resources/icons;
after changing the packer or those SVGs, rewrite it with: python3 -I tools/icons/test_pack.py write
"""
import os
import struct
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)  # -I leaves the script's own folder out of sys.path
import pack  # noqa: E402

FIXTURE = os.path.join(HERE, "..", "..", "src", "test", "resources", "icons")


CATS = (os.path.join(FIXTURE, "categories", "curated.tsv"), os.path.join(FIXTURE, "categories", "apps_with_category.txt"))
GLYPHS = dict.fromkeys(pack.GLYPHS, "square") | {"FINANCE": "arc"}  # the fixture has no emoji icons


def fixture_bytes(categories=CATS, glyphs=GLYPHS):
    return pack.pack(FIXTURE, os.path.join(FIXTURE, "appfilter.xml"), pack.ARCTICONS_COMMIT, categories, glyphs)[0]


def category_of(blob, package):
    """What a reader finds: (category, glyph icon id) of a package in an ICN2 asset, or None; categories are in GLYPHS' order."""
    n, index_at, table_at = struct.unpack_from("<III", blob, 40)
    keys = {int.from_bytes(blob[o:o + 6], "little"): struct.unpack_from("<H", blob, o + 6)[0]
            for o in range(index_at, index_at + 8 * n, 8)}
    cat = keys.get(pack.fnv48(package))
    if cat is None:
        return None
    glyph = struct.unpack_from("<H", blob, table_at + 2 + 2 * cat)[0]
    return list(pack.GLYPHS)[cat], glyph


def rounded(ops):
    return [tuple(round(v, 4) if isinstance(v, float) else v for v in op) for op in ops]


class PathDataTest(unittest.TestCase):
    """Every path command, absolute and relative, against ops worked out by hand."""

    def d(self, text):
        return rounded(pack.parse_d(text))

    def test_move_and_line(self):
        self.assertEqual([("M", 1, 2), ("L", 4, 6)], self.d("M1 2 L4 6"))
        self.assertEqual([("M", 5, 5), ("L", 7, 8)], self.d("m5 5 l2 3"))
        self.assertEqual([("M", 1, 2), ("L", 3, 4), ("L", 5, 6)], self.d("M1 2 3 4 5 6"), "extra pairs after M are lines")
        self.assertEqual([("M", 1, 2), ("L", 4, 6), ("L", 5, 8)], self.d("m1 2 3 4 1 2"), "relative: after the first pair, lines")

    def test_horizontal_and_vertical(self):
        self.assertEqual([("M", 1, 2), ("L", 9, 2), ("L", 9, 7)], self.d("M1 2 H9 V7"))
        self.assertEqual([("M", 1, 2), ("L", 4, 2), ("L", 4, 0)], self.d("M1 2 h3 v-2"))

    def test_cubic_and_smooth_cubic(self):
        self.assertEqual([("M", 0, 0), ("C", 1, 2, 3, 4, 5, 6)], self.d("M0 0 C1 2 3 4 5 6"))
        self.assertEqual([("M", 1, 1), ("C", 2, 3, 4, 5, 6, 7)], self.d("M1 1 c1 2 3 4 5 6"))
        # S reflects the previous second control point about the current point; with no previous cubic it is the point
        self.assertEqual([("M", 0, 0), ("C", 1, 2, 3, 4, 5, 6), ("C", 7, 8, 9, 9, 10, 10)], self.d("M0 0 C1 2 3 4 5 6 S9 9 10 10"))
        self.assertEqual([("M", 2, 2), ("C", 2, 2, 5, 6, 7, 8)], self.d("M2 2 S5 6 7 8"))
        self.assertEqual([("M", 1, 1), ("C", 1, 1, 2, 2, 3, 3)], self.d("M1 1 s1 1 2 2"))

    def test_quadratic_and_smooth_quadratic(self):
        # a quadratic with control q is the cubic with controls p + 2/3 (q - p) and e + 2/3 (q - e)
        self.assertEqual([("M", 0, 0), ("C", 2, 0, 4, 2, 6, 6)], self.d("M0 0 Q3 0 6 6"))
        self.assertEqual([("M", 1, 1), ("C", 1.6667, 1, 2.3333, 1.6667, 3, 3)], self.d("M1 1 q1 0 2 2"))
        # T reflects the previous control (3,0) about (6,6): (9,12); with no previous quadratic it is the point itself
        self.assertEqual([("M", 0, 0), ("C", 2, 0, 4, 2, 6, 6), ("C", 8, 10, 10, 8, 12, 0)], self.d("M0 0 Q3 0 6 6 T12 0"))
        self.assertEqual([("M", 1, 1), ("C", 1, 1, 2, 2, 4, 4)], self.d("M1 1 T4 4"))
        self.assertEqual([("M", 1, 1), ("C", 1, 1, 2, 2, 4, 4)], self.d("M1 1 t3 3"))

    def test_arc_flags_may_touch_the_next_number(self):
        # SVGO writes "a1 1 0 00.5.5": large=0 sweep=0 x=.5 y=.5
        spaced = self.d("M0 0a1 1 0 0 0 .5 .5")
        self.assertEqual(spaced, self.d("M0 0a1 1 0 00.5.5"))
        self.assertEqual(self.d("M0 0A5 5 0 1 1 10 0"), self.d("M0 0A5 5 0 1110 0"))
        self.assertEqual(self.d("M0 0A5 5 0 1 1 10 0"), self.d("M0 0a5 5 0 1,1,10,0"))
        self.assertEqual(self.d("M0 0A1 1 0 0 0 .5 .5A1 1 0 0 0 1 1"), self.d("M0 0A1 1 0 00.5.5 1 1 0 0 0 1 1"), "repeated arc")

    def test_arc_ends_where_it_says(self):
        self.assertEqual((10, 0), self.d("M0 0 A5 5 0 0 1 10 0")[-1][5:])
        self.assertEqual((11, 1), self.d("M1 1 a5 5 0 0 1 10 0")[-1][5:])
        self.assertEqual(("C", 1, 1, 1, 1, 1, 1)[0], self.d("M1 1 A0 5 0 0 1 1 1")[-1][0], "degenerate arcs stay a (zero) curve")

    def test_close_then_draw_restarts_at_the_subpath_start(self):
        self.assertEqual([("M", 1, 1), ("L", 5, 1), ("Z",), ("M", 1, 1), ("L", 2, 2)], self.d("M1 1 L5 1 Z L2 2"))
        self.assertEqual([("M", 1, 1), ("Z",)], self.d("M1 1z"))

    def test_malformed_path_is_an_error(self):
        for bad in ("M1 2 L3", "M1 2 C1 2 3 4 5", "M1 2 A1 1 0 2 0 3 3", "M1 2 A1 1 0 0", "M1 2 X 3 4", "1 2 L3 4", "M1 2 L3 4 L5 x"):
            with self.assertRaises(ValueError, msg=bad):
                pack.parse_d(bad)

    def test_empty_path_has_no_ops(self):
        self.assertEqual([], pack.parse_d(""))


class MatrixTest(unittest.TestCase):
    def check(self, expected, text):
        self.assertEqual([round(v, 6) for v in expected], [round(v, 6) for v in pack.matrix(text)])

    def test_each_transform(self):
        self.check((1, 0, 0, 1, 3, 4), "translate(3 4)")
        self.check((1, 0, 0, 1, 3, 0), "translate(3)")
        self.check((2, 0, 0, 3, 0, 0), "scale(2,3)")
        self.check((2, 0, 0, 2, 0, 0), "scale(2)")
        self.check((0, 1, -1, 0, 0, 0), "rotate(90)")
        m = pack.matrix("rotate(90 1 1)")  # the centre stays put, (2,1) goes to (1,2)
        self.assertEqual([(1, 1), (1, 2)], [(round(m[0] * x + m[2] * y + m[4], 6), round(m[1] * x + m[3] * y + m[5], 6))
                                            for x, y in ((1, 1), (2, 1))])
        self.check((1, 2, 3, 4, 5, 6), "matrix(1 2 3 4 5 6)")
        self.check((1, 0, 1, 1, 0, 0), "skewX(45)")
        self.check((1, 1, 0, 1, 0, 0), "skewY(45)")

    def test_list_applies_left_to_right_and_garbage_is_skipped(self):
        self.check((2, 0, 0, 2, 10, 20), "translate(10 20) scale(2)")
        self.check((2, 0, 0, 2, 0, 0), "foo(1 2) scale(2)")
        self.check((1, 0, 0, 1, 0, 0), "")
        self.check((1, 0, 0, 1, 0, 0), None)


class ShapeTest(unittest.TestCase):
    def d(self, tag, **a):
        return rounded(pack.parse_d(pack.shape_path(tag, {k: str(v) for k, v in a.items()})))

    def test_line(self):
        self.assertEqual([("M", 1, 2), ("L", 3, 4)], self.d("line", x1=1, y1=2, x2=3, y2=4))

    def test_polyline_and_polygon(self):
        self.assertEqual([("M", 1, 2), ("L", 3, 4), ("L", 5, 0)], self.d("polyline", points="1,2 3,4 5,0"))
        self.assertEqual([("M", 1, 2), ("L", 3, 4), ("L", 5, 0), ("Z",)], self.d("polygon", points="1 2 3 4 5 0"))
        self.assertIsNone(pack.shape_path("polyline", {"points": "1 2"}), "one point draws nothing")
        self.assertIsNone(pack.shape_path("polyline", {}))

    def test_rect(self):
        self.assertEqual([("M", 1, 2), ("L", 5, 2), ("L", 5, 5), ("L", 1, 5), ("Z",)], self.d("rect", x=1, y=2, width=4, height=3))

    def test_rounded_rect_starts_after_the_corner_and_closes(self):
        ops = self.d("rect", x=0, y=0, width=10, height=10, rx=2)
        self.assertEqual([("M", 2, 0), ("L", 8, 0)], ops[:2])
        self.assertEqual(["C"] * 4, [o[0] for o in ops if o[0] == "C"])
        self.assertIn(("L", 10, 8), ops)
        self.assertEqual((2, 0), ops[-2][5:])
        self.assertEqual(("Z",), ops[-1])

    def test_circle_is_four_arcs_from_the_right(self):
        ops = self.d("circle", cx=5, cy=5, r=2)
        self.assertEqual(("M", 7, 5), ops[0])
        self.assertEqual((5, 7), ops[1][5:])
        self.assertIsNone(pack.shape_path("circle", {"r": "0"}))

    def test_unknown_tag(self):
        self.assertIsNone(pack.shape_path("text", {}))


class BadSvgTest(unittest.TestCase):
    def pack_one(self, svg):
        with tempfile.TemporaryDirectory() as d:
            os.mkdir(os.path.join(d, "icons"))
            with open(os.path.join(d, "icons", "x.svg"), "w") as f:
                f.write(svg)
            with open(os.path.join(d, "appfilter.xml"), "w") as f:
                f.write("<resources/>")
            return pack.pack(os.path.join(d, "icons"), os.path.join(d, "appfilter.xml"), pack.ARCTICONS_COMMIT)

    def test_a_good_icon_packs(self):
        self.pack_one('<svg xmlns="http://www.w3.org/2000/svg"><path stroke="#fff" d="M1 2 L3 4"/></svg>')

    def test_truncated_path_names_the_file(self):
        with self.assertRaises(SystemExit) as e:
            self.pack_one('<svg xmlns="http://www.w3.org/2000/svg"><path stroke="#fff" d="M1 2 L3"/></svg>')
        self.assertIn("x.svg", str(e.exception.code))

    def test_icon_without_paths_fails(self):
        for svg in ('<svg xmlns="http://www.w3.org/2000/svg"/>',
                    '<svg xmlns="http://www.w3.org/2000/svg"><path fill="none" stroke="none" d="M1 2 L3 4"/></svg>',
                    '<svg xmlns="http://www.w3.org/2000/svg"><path stroke="#fff" d=""/></svg>'):
            with self.assertRaises(SystemExit, msg=svg) as e:
                self.pack_one(svg)
            self.assertIn("x.svg", str(e.exception.code))


    def test_unsupported_svg_features_name_the_file(self):
        svg = '<svg xmlns="http://www.w3.org/2000/svg">%s<path stroke="#fff" d="M1 2 L3 4"/></svg>'
        for extra in ('<use href="#a"/>', '<text>a</text>', '<image href="a.png"/>', '<path display="none" d="M1 2 L3 4" stroke="#fff"/>',
                      '<path style="display:none" d="M1 2 L3 4" stroke="#fff"/>', '<path transform="translate()" d="M1 2 L3 4" stroke="#fff"/>',
                      '<path clip-path="url(#a)" d="M1 2 L3 4" stroke="#fff"/>', '<g mask="url(#a)"><path d="M1 2 L3 4" stroke="#fff"/></g>',
                      '<path style="clip-path:url(#a)" d="M1 2 L3 4" stroke="#fff"/>'):
            with self.assertRaises(SystemExit, msg=extra) as e:
                self.pack_one(svg % extra)
            self.assertIn("x.svg", str(e.exception.code), extra)


class PackTest(unittest.TestCase):
    def test_hash_collision_stops_the_build(self):
        # two keys with one 48-bit hash would make one app show the other's icon: the packer must refuse
        with self.assertRaises(SystemExit) as e:
            pack.index({"pkgA": 0, "pkgB": 1}, hash48=lambda key: 42)
        self.assertIn("collision", str(e.exception.code))

    def test_keys_are_listed_components_only(self):
        # Fable c3 m6: a package-wide key would hand a glyph to an activity the appfilter never listed
        names = sorted(n[:-4] for n in os.listdir(FIXTURE) if n.endswith(".svg"))
        keys = pack.keys(pack.components(os.path.join(FIXTURE, "appfilter.xml"), {n: i for i, n in enumerate(names)}))
        self.assertEqual({"pkgA/pkgA.X", "pkgA/pkgA.Y", "pkgB/pkgB.X", "pkgB/pkgB.Y"}, set(keys))

    def test_dash_across_the_closing_point_is_one_dash(self):
        # a closed outline's last dash runs on into its first: one subpath with a corner, not two ends at the start
        square = pack.parse_d("M4 4 L44 4 L44 44 L4 44 Z")  # 160 units, "10 10" from 5 in: 9 ranges, 8 dashes
        ops = pack.dashed(square, [10, 10], 5)
        moves = [op for op in ops if op[0] == "M"]
        self.assertEqual(8, len(moves))
        self.assertEqual([("M", 4, 9), ("L", 4, 4), ("L", 9, 4)], [(o[0], round(o[1], 6), round(o[2], 6)) for o in ops[:3]])

    def test_category_round_trip(self):
        blob = fixture_bytes()
        self.assertEqual(b"ICN2", blob[:4])
        self.assertEqual(("FINANCE", self.glyph("arc")), category_of(blob, "com.example.bank"))
        self.assertIsNone(category_of(blob, "com.example.nothing"), "the empty type is dropped")

    def glyph(self, name):
        return sorted(n[:-4] for n in os.listdir(FIXTURE) if n.endswith(".svg")).index(name)

    def test_curated_beats_dataset(self):
        self.assertEqual("SHOPPING", category_of(fixture_bytes(), "com.example.shop")[0])

    def test_every_dataset_type_has_a_category(self):
        types = ("Action|Adventure|Arcade|Art & Design|Auto & Vehicles|Beauty|Board|Board Games|Books & Reference|Brain Games|"
                 "Business|Card|Casino|Casual|Comics|Communication|Dating|Development|Education|Educational|Entertainment|Events|"
                 "Finance|Food & Drink|General Culture|Health & Fitness|Libraries & Demo|Library & Demos|Lifestyle|"
                 "Maps & Navigation|Media & Audio|Media & Vide0|Media & Video|Media & Videos|Medical|Music|Music & AUdio|"
                 "Music & Audio|News & Magazines|Offic|Office|Personalization|Photography|Productivity|Puzles|Puzzle|Quiz|"
                 "Racing|Racing Games|Role Playing|Shopping|Simulation|Social|Sport|Sports|Strategy|Tools|Transport|"
                 "Transportation|Travel & Local|Travel & Locale|Trivia|Utilities|Video Players & Editors|Weather|Word").split("|")
        self.assertEqual(66, len(types), "the dataset's 67 values: these and the empty one")
        for t in types:
            self.assertIn(pack.category_of(t), pack.GLYPHS, t)
        self.assertIsNone(pack.category_of(""))
        self.assertEqual("VIDEO_PLAYERS", pack.category_of("Media & Vide0"))
        self.assertEqual("MAPS_AND_NAVIGATION", pack.category_of("Transportation"))
        self.assertEqual("GAME_PUZZLE", pack.category_of("Brain Games"))
        self.assertEqual("GAME_MUSIC", pack.category_of("Music"))
        self.assertEqual("SPORTS", pack.category_of("Sports"))
        self.assertEqual(49, len(pack.GLYPHS))

    def test_missing_glyph_fails(self):
        with self.assertRaises(SystemExit) as e:
            fixture_bytes(glyphs=GLYPHS | {"WEATHER": "no_such_svg"})
        self.assertIn("WEATHER", str(e.exception.code))
        with self.assertRaises(SystemExit):
            fixture_bytes(glyphs=pack.GLYPHS)  # the real glyphs are not among the fixture's SVGs

    def test_arcticons_packages_dropped(self):
        self.assertIsNone(category_of(fixture_bytes(), "pkgA"), "pkgA has an Arcticons icon: the category is never read")

    def test_other_dataset_commit_refused(self):
        with self.assertRaises(SystemExit) as e:
            pack.checked_out(os.path.dirname(HERE), pack.CATEGORIES_COMMIT, "Category dataset")
        self.assertIn("pinned commit", str(e.exception.code))

    def test_fixture_is_current(self):
        with open(os.path.join(FIXTURE, "icons-fixture.bin"), "rb") as f:
            self.assertEqual(f.read(), fixture_bytes(), "rewrite it: python3 -I tools/icons/test_pack.py write")


if __name__ == "__main__":
    if sys.argv[1:] == ["write"]:
        with open(os.path.join(FIXTURE, "icons-fixture.bin"), "wb") as f:
            f.write(fixture_bytes())
    else:
        unittest.main()
