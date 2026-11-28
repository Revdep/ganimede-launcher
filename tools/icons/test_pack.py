"""Packer checks: python3 -I tools/icons/test_pack.py (from app/).

The Java decoder tests (IconSetTest, IconIndexTest) read the fixture packed from src/test/resources/icons;
after changing the packer or those SVGs, rewrite it with: python3 -I tools/icons/test_pack.py write
"""
import os
import struct
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)  # -I leaves the script's own folder out of sys.path
import pack  # noqa: E402

FIXTURE = os.path.join(HERE, "..", "..", "src", "test", "resources", "icons")


CATS = (os.path.join(FIXTURE, "categories", "curated.tsv"), os.path.join(FIXTURE, "categories", "apps_with_category.txt"))
GLYPHS = dict.fromkeys(pack.GLYPHS, "square") | {"FINANCE": "arc"}  # the fixture has no emoji icons


def fixture_bytes(categories=None, glyphs=pack.GLYPHS):
    return pack.pack(FIXTURE, os.path.join(FIXTURE, "appfilter.xml"), pack.ARCTICONS_COMMIT, categories, glyphs)[0]


def category_of(blob, package):
    """What a reader finds: (category name, glyph icon id) of a package in an ICN2 asset, or None."""
    n, index_at, table_at = struct.unpack_from("<III", blob, 40)
    keys = {int.from_bytes(blob[o:o + 6], "little"): struct.unpack_from("<H", blob, o + 6)[0]
            for o in range(index_at, index_at + 8 * n, 8)}
    cat = keys.get(pack.fnv48(package))
    if cat is None:
        return None
    count = struct.unpack_from("<H", blob, table_at)[0]
    glyph = struct.unpack_from("<H", blob, table_at + 2 + 2 * cat)[0]
    at = table_at + 2 + 2 * count
    for i in range(cat + 1):
        length = blob[at]
        name = blob[at + 1:at + 1 + length].decode()
        at += 1 + length
    return name, glyph


class PackTest(unittest.TestCase):
    def test_hash_collision_stops_the_build(self):
        # two keys with one 48-bit hash would make one app show the other's icon: the packer must refuse
        with self.assertRaises(SystemExit) as e:
            pack.index({"pkgA": 0, "pkgB": 1}, hash48=lambda key: 42)
        self.assertIn("collision", str(e.exception.code))

    def test_dash_across_the_closing_point_is_one_dash(self):
        # a closed outline's last dash runs on into its first: one subpath with a corner, not two ends at the start
        square = pack.parse_d("M4 4 L44 4 L44 44 L4 44 Z")  # 160 units, "10 10" from 5 in: 9 ranges, 8 dashes
        ops = pack.dashed(square, [10, 10], 5)
        moves = [op for op in ops if op[0] == "M"]
        self.assertEqual(8, len(moves))
        self.assertEqual([("M", 4, 9), ("L", 4, 4), ("L", 9, 4)], [(o[0], round(o[1], 6), round(o[2], 6)) for o in ops[:3]])

    def test_category_round_trip(self):
        blob = fixture_bytes(CATS, GLYPHS)
        self.assertEqual(b"ICN2", blob[:4])
        self.assertEqual(("FINANCE", self.glyph("arc")), category_of(blob, "com.example.bank"))
        self.assertIsNone(category_of(blob, "com.example.nothing"), "the empty type is dropped")

    def glyph(self, name):
        return sorted(n[:-4] for n in os.listdir(FIXTURE) if n.endswith(".svg")).index(name)

    def test_curated_beats_dataset(self):
        self.assertEqual("SHOPPING", category_of(fixture_bytes(CATS, GLYPHS), "com.example.shop")[0])

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
            fixture_bytes(CATS, GLYPHS | {"WEATHER": "no_such_svg"})
        self.assertIn("WEATHER", str(e.exception.code))
        with self.assertRaises(SystemExit):
            fixture_bytes(CATS)  # the real glyphs are not among the fixture's SVGs

    def test_arcticons_packages_dropped(self):
        self.assertIsNone(category_of(fixture_bytes(CATS, GLYPHS), "pkgA"), "pkgA has an Arcticons icon: the category is never read")

    def test_other_dataset_commit_refused(self):
        with self.assertRaises(SystemExit) as e:
            pack.checked_out(os.path.dirname(HERE), pack.CATEGORIES_COMMIT, "Category dataset")
        self.assertIn("pinned commit", str(e.exception.code))

    def test_fixture_is_current(self):
        with open(os.path.join(FIXTURE, "icons-fixture.bin"), "rb") as f:
            self.assertEqual(f.read(), fixture_bytes(), "rewrite it: python3 -I tools/icons/test_pack.py write")
        with open(os.path.join(FIXTURE, "icons-fixture2.bin"), "rb") as f:
            self.assertEqual(f.read(), fixture_bytes(CATS, GLYPHS), "rewrite it: python3 -I tools/icons/test_pack.py write")


if __name__ == "__main__":
    if sys.argv[1:] == ["write"]:
        with open(os.path.join(FIXTURE, "icons-fixture.bin"), "wb") as f:
            f.write(fixture_bytes())
        with open(os.path.join(FIXTURE, "icons-fixture2.bin"), "wb") as f:
            f.write(fixture_bytes(CATS, GLYPHS))
    else:
        unittest.main()
