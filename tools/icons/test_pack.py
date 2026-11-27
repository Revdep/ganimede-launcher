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

    def test_fixture_is_current(self):
        with open(os.path.join(FIXTURE, "icons-fixture.bin"), "rb") as f:
            self.assertEqual(f.read(), fixture_bytes(), "rewrite it: python3 -I tools/icons/test_pack.py write")


if __name__ == "__main__":
    if sys.argv[1:] == ["write"]:
        with open(os.path.join(FIXTURE, "icons-fixture.bin"), "wb") as f:
            f.write(fixture_bytes())
    else:
        unittest.main()
