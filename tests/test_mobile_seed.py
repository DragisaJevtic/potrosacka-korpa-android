import gzip
import io
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch

from tools.build_catalog_seed import build, search_key


class SeedTests(unittest.TestCase):
    def test_seed_contains_version_and_searchable_offers_with_empty_list(self):
        offer = dict(id="one", name="Čačanski đevrek", retailer="Radnja", unit="kom",
                     para=12345, category="Pekara", format="Market", chain="Trgovac", product_key="gtin:036000291452")
        pages = [
            dict(version="generation:1", reset=True, removed=[], updates=[offer], next="one"),
            dict(version="generation:1", reset=True, removed=[], updates=[dict(offer, id="two")], next=None),
        ]
        with tempfile.TemporaryDirectory() as directory, patch(
            "tools.build_catalog_seed.build_opener"
        ) as opener:
            opener.return_value.open.side_effect = [io.BytesIO(json.dumps(page).encode()) for page in pages]
            output = Path(directory) / "seed.gz"
            build("http://fixture", output)
            database = Path(directory) / "seed.db"
            database.write_bytes(gzip.decompress(output.read_bytes()))
            with sqlite3.connect(database) as db:
                self.assertEqual(db.execute("PRAGMA user_version").fetchone()[0], 2)
                self.assertEqual(db.execute("SELECT product_key FROM offers LIMIT 1").fetchone()[0], "gtin:00036000291452")
                self.assertEqual(db.execute("SELECT version FROM catalog_version").fetchone()[0], "generation:1")
                self.assertEqual(db.execute("SELECT count(*) FROM shopping").fetchone()[0], 0)
                self.assertEqual(db.execute("SELECT count(*) FROM offers").fetchone()[0], 2)
                self.assertEqual(db.execute("SELECT name_key, price FROM offers LIMIT 1").fetchone(),
                                 ("cacanski devrek", 12345))
            self.assertIn("target=generation%3A1", opener.return_value.open.call_args_list[1].args[0])

    def test_inconsistent_snapshot_preserves_previous_asset(self):
        pages = [
            dict(version="v1", reset=True, removed=[], updates=[], next="one"),
            dict(version="v2", reset=True, removed=[], updates=[], next=None),
        ]
        with tempfile.TemporaryDirectory() as directory, patch(
            "tools.build_catalog_seed.build_opener"
        ) as opener:
            opener.return_value.open.side_effect = [io.BytesIO(json.dumps(page).encode()) for page in pages]
            output = Path(directory) / "seed.gz"
            output.write_bytes(b"previous asset")
            with self.assertRaises(ValueError):
                build("http://fixture", output)
            self.assertEqual(output.read_bytes(), b"previous asset")

    def test_search_normalization(self):
        self.assertEqual(search_key("ČĆŠŽĐ MLEKO"), "ccszd mleko")
