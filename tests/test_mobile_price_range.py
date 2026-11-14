"""Exercise the Android search SQL with SQLite, including cross-retailer ranges."""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/dev/intelisystems/potrosackakorpa/Catalog.kt'


class PriceRangeTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.addCleanup(self.db.close)
        self.db.executescript((ROOT / 'app/src/main/assets/catalog-schema.sql').read_text())
        source = SOURCE.read_text().split('fun offers(')[1].split('fun shoppingList')[0]
        self.sql = re.search(r'rawQuery\("""(.*?)"""', source, re.S)[1]
        self.sql = self.sql.replace('$operator', 'IN').replace('$placeholders', '?').replace('$beverageFilter', '1=1')

    def offer(self, identifier, price, key, retailer='one'):
        self.db.execute('INSERT INTO offers VALUES (?,?,?,?,?,?,?,?,?,?)',
                        (identifier, identifier, retailer, 'kom', price, 'food', 'Food', identifier, retailer, key))

    def search(self, query='%'):
        return self.db.execute(self.sql, ('food', query, query, '0')).fetchall()

    def test_range_includes_other_retailers_despite_search_filter(self):
        self.offer('a', 10000, 'gtin:1', 'one')
        self.offer('b', 15000, 'gtin:1', 'two')
        row = self.search('one')[0]
        self.assertEqual(row[4:], (10000, 10000, 15000))

    def test_unknown_codes_do_not_merge(self):
        self.offer('a', 10000, None)
        self.offer('b', 20000, None)
        self.assertEqual([r[5:] for r in self.search()], [(10000, 10000), (20000, 20000)])

    def test_single_equal_prices_and_removal(self):
        self.offer('a', 10000, 'gtin:1')
        self.offer('b', 10000, 'gtin:1')
        self.offer('c', 20000, 'gtin:1')
        self.db.execute("DELETE FROM offers WHERE id='c'")
        self.assertEqual([r[5:] for r in self.search()], [(10000, 10000), (10000, 10000)])

    def test_migration_preserves_shopping_list(self):
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        db.executescript('CREATE TABLE offers(id TEXT, price INTEGER); CREATE TABLE catalog_version(id INTEGER, version TEXT); CREATE TABLE shopping(id TEXT, quantity INTEGER); INSERT INTO shopping VALUES ("saved",3);')
        upgrade = SOURCE.read_text().split('override fun onUpgrade')[1].split('fun version()')[0]
        for sql in re.findall(r'db.execSQL\("([^"]+)"\)', upgrade):
            db.execute(sql)
        self.assertEqual(db.execute('SELECT * FROM shopping').fetchall(), [('saved', 3)])
        self.assertIn('product_key', [r[1] for r in db.execute('PRAGMA table_info(offers)')])
