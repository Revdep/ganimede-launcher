"""Packer checks: python3 -I tools/icons/test_pack.py (from app/).

The Java decoder tests (IconSetTest, IconIndexTest) read the fixture packed from src/test/resources/icons;
after changing the packer or those SVGs, rewrite it with: python3 -I tools/icons/test_pack.py write
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)  # -I leaves the script's own folder out of sys.path
import pack  # noqa: E402

FIXTURE = os.path.join(HERE, "..", "..", "src", "test", "resources", "icons")


def fixture_bytes():
    return pack.pack(FIXTURE, os.path.join(FIXTURE, "appfilter.xml"), pack.ARCTICONS_COMMIT)[0]


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

    def test_fixture_is_current(self):
        with open(os.path.join(FIXTURE, "icons-fixture.bin"), "rb") as f:
            self.assertEqual(f.read(), fixture_bytes(), "rewrite it: python3 -I tools/icons/test_pack.py write")


if __name__ == "__main__":
    if sys.argv[1:] == ["write"]:
        with open(os.path.join(FIXTURE, "icons-fixture.bin"), "wb") as f:
            f.write(fixture_bytes())
    else:
        unittest.main()
