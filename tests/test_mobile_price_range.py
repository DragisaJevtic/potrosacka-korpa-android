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
        self.sql = self.sql.replace('$categoryFilter', 'category IN (?)').replace('$operator', 'IN').replace('$placeholders', '?').replace('$beverageFilter', '1=1')

    def offer(self, identifier, price, key, retailer='one'):
        self.db.execute('INSERT INTO offers VALUES (?,?,?,?,?,?,?,?,?,?)',
                        (identifier, identifier, retailer, 'kom', price, 'food', 'Food', identifier, retailer, key))

    def search(self, query='%'):
        return self.db.execute(self.sql, ('food', query, query, '0')).fetchall()

    def test_range_includes_other_retailers_despite_search_filter(self):
        self.offer('a', 10000, 'gtin:1', 'one')
        self.offer('b', 15000, 'gtin:1', 'two')
        row = self.search('one')[0]
        self.assertEqual(row[4:7], (10000, 10000, 15000))

    def test_unknown_codes_do_not_merge(self):
        self.offer('a', 10000, None)
        self.offer('b', 20000, None)
        self.assertEqual([r[5:7] for r in self.search()], [(10000, 10000), (20000, 20000)])

    def test_single_equal_prices_and_removal(self):
        self.offer('a', 10000, 'gtin:1')
        self.offer('b', 10000, 'gtin:1')
        self.offer('c', 20000, 'gtin:1')
        self.db.execute("DELETE FROM offers WHERE id='c'")
        self.assertEqual([r[5:7] for r in self.search()], [(10000, 10000), (10000, 10000)])

    def test_migration_preserves_shopping_list(self):
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        db.executescript('CREATE TABLE offers(id TEXT, price INTEGER); CREATE TABLE catalog_version(id INTEGER, version TEXT); CREATE TABLE shopping(id TEXT, quantity INTEGER); INSERT INTO shopping VALUES ("saved",3);')
        upgrade = SOURCE.read_text().split('override fun onUpgrade')[1].split('fun version()')[0]
        for sql in re.findall(r'db.execSQL\("([^"]+)"\)', upgrade):
            db.execute(sql)
        self.assertEqual(db.execute('SELECT * FROM shopping').fetchall(), [('saved', 3)])
        self.assertIn('product_key', [r[1] for r in db.execute('PRAGMA table_info(offers)')])

    def test_scanned_product_uses_catalog_ids_and_keeps_retailers_separate(self):
        self.offer('expensive', 15000, 'gtin:08601500126362', 'two')
        self.offer('cheap', 10000, 'gtin:08601500126362', 'one')
        self.offer('other', 1, 'gtin:08606017431428')
        self.offer('internal', 1, None)
        source = SOURCE.read_text().split('fun offersForBarcode(')[1].split('fun shoppingList')[0]
        sql = re.search(r'rawQuery\("""(.*?)"""', source, re.S)[1]
        rows = self.db.execute(sql, ('gtin:08601500126362',)).fetchall()
        self.assertEqual([r[0] for r in rows], ['cheap', 'expensive'])
        selected = rows[1]
        add_source = SOURCE.read_text().split('fun add(offer:')[1].split('fun setQuantity')[0]
        insert = re.search(r'execSQL\("""(.*?)"""', add_source, re.S)[1]
        update = re.search(r'execSQL\("(UPDATE shopping.*?)"', add_source)[1]
        for _ in range(2):
            self.db.execute(insert, selected[:4])
            self.db.execute(update, (selected[0],))
        self.assertEqual(self.db.execute('SELECT id,quantity FROM shopping').fetchall(), [('expensive', 2)])
        self.assertEqual(self.db.execute(sql, ('gtin:00000000000000',)).fetchall(), [])

    def test_global_search_crosses_categories_and_keeps_query_filter(self):
        self.offer('milk', 10000, 'gtin:1')
        self.offer('soap', 20000, 'gtin:2')
        self.db.execute("UPDATE offers SET category='hygiene' WHERE id='soap'")
        source = SOURCE.read_text().split('fun offers(')[1].split('fun offersForBarcode')[0]
        sql = re.search(r'rawQuery\("""(.*?)"""', source, re.S)[1]
        sql = sql.replace('$categoryFilter', '1=1').replace('$beverageFilter', '1=1')
        self.assertEqual([r[0] for r in self.db.execute(sql, ('%', '%', '0'))], ['milk', 'soap'])
        self.assertEqual([r[0] for r in self.db.execute(sql, ('%soap%', '%soap%', '0'))], ['soap'])
        self.assertEqual([r[0] for r in self.search()], ['milk'])

    def test_page_limit_keeps_price_range_from_off_page_offers(self):
        for index in range(40):
            self.offer(f'item{index:02}', 10000 + index, 'gtin:1')
        first = self.db.execute(self.sql, ('food', '%', '%', '0')).fetchall()
        second = self.db.execute(self.sql, ('food', '%', '%', '30')).fetchall()
        self.assertEqual(len(first), 31)
        self.assertEqual([r[0] for r in second], [f'item{i:02}' for i in range(30, 40)])
        self.assertEqual(first[0][5:7], (10000, 10039))

    def test_search_counts_include_all_matches_not_only_first_page(self):
        for index in range(45):
            self.offer(f'milk{index:02}', 10000, 'gtin:1')
        self.offer('soap', 20000, 'gtin:2')
        self.db.execute("UPDATE offers SET category='hygiene' WHERE id='soap'")
        source = SOURCE.read_text().split('fun searchOffers(')[1].split('fun offers(')[0]
        total = re.search(r'rawQuery\("(SELECT count[^"\n]+)"', source)[1]
        matched = re.search(r'rawQuery\("""(.*?)"""', source, re.S)[1]
        def query(sql, scope):
            return sql.replace('$categoryFilter', scope).replace('$beverageFilter', '1=1')
        self.assertEqual(self.db.execute(query(total, '1=1')).fetchone()[0], 46)
        self.assertEqual(self.db.execute(query(matched, '1=1'), ('%milk%', '%milk%')).fetchone()[0], 45)
        self.assertEqual(self.db.execute(query(total, 'category IN (?)'), ('food',)).fetchone()[0], 45)
        self.assertEqual(self.db.execute(query(matched, 'category IN (?)'), ('food', '%soap%', '%soap%')).fetchone()[0], 0)
