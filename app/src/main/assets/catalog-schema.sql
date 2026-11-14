CREATE TABLE offers (
    id TEXT PRIMARY KEY, name TEXT NOT NULL, retailer TEXT NOT NULL, unit TEXT,
    price INTEGER NOT NULL, category TEXT NOT NULL, category_label TEXT NOT NULL,
    name_key TEXT NOT NULL, retailer_key TEXT NOT NULL, product_key TEXT
);
CREATE INDEX offers_category_name ON offers(category, name_key, id);
CREATE INDEX offers_product_price ON offers(product_key, price);
CREATE TABLE shopping (
    id TEXT PRIMARY KEY, name TEXT NOT NULL, retailer TEXT NOT NULL, unit TEXT,
    quantity INTEGER NOT NULL
);
CREATE TABLE catalog_version (id INTEGER PRIMARY KEY, version TEXT NOT NULL);
