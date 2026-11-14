#!/usr/bin/env python3
"""Build a compressed, versioned Android SQLite catalog from a consistent API snapshot."""
import argparse
import gzip
import json
import os
from pathlib import Path
import shutil
import sqlite3
import tempfile
import unicodedata
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import ProxyHandler, build_opener

ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets"


def search_key(value):
    decomposed = unicodedata.normalize("NFD", value.lower())
    return "".join(c for c in decomposed if not unicodedata.category(c).startswith("M")).replace("đ", "d")


def build(base_url, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    opener = build_opener(ProxyHandler({}))
    with tempfile.TemporaryDirectory(dir=output.parent) as directory:
        database = Path(directory) / "catalog.db"
        with sqlite3.connect(database) as db:
            db.executescript((ASSETS / "catalog-schema.sql").read_text())
            version, after, total, page_number = "", "", 0, 0
            while True:
                parameters = urlencode({k: v for k, v in {"target": version, "after": after}.items() if v})
                with opener.open(base_url.rstrip("/") + "/api/mobile/catalog?" + parameters, timeout=30) as response:
                    page = json.load(response)
                version = version or page["version"]
                if page["version"] != version or not page["reset"] or page["removed"]:
                    raise ValueError("Server returned an inconsistent initial snapshot")
                rows = []
                for offer in page["updates"]:
                    category = offer["category"]
                    product = offer.get("product_key")
                    product = "gtin:" + product[5:].zfill(14) if product and product.startswith("gtin:") else None
                    rows.append((
                        offer["id"], offer["name"], offer["retailer"], offer["unit"], offer["para"],
                        search_key(category), category[:1].title() + category[1:],
                        search_key(offer["name"]),
                        search_key(f'{offer["retailer"]} {offer["format"]} {offer["chain"]}'), product,
                    ))
                db.executemany("INSERT INTO offers VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows)
                total += len(rows)
                page_number += 1
                if page_number % 20 == 0:
                    print(f"Downloaded {total} offers ({page_number} pages)", flush=True)
                following = page["next"]
                if following is None:
                    break
                if following <= after:
                    raise ValueError("Server returned a non-advancing cursor")
                after = following
            db.execute("INSERT INTO catalog_version VALUES (1, ?)", (version,))
            db.execute("PRAGMA user_version = 2")
            db.commit()
            db.execute("VACUUM")
            if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                raise ValueError("SQLite integrity check failed")
            assert db.execute("SELECT count(*) FROM shopping").fetchone()[0] == 0
        compressed = Path(directory) / "catalog.db.gzip"
        with database.open("rb") as source, compressed.open("wb") as target:
            with gzip.GzipFile(fileobj=target, mode="wb", filename="", mtime=0, compresslevel=6) as zipped:
                shutil.copyfileobj(source, zipped)
        compressed_size = compressed.stat().st_size
        os.replace(compressed, output)
        print(json.dumps(dict(offers=total, version=version, database_bytes=database.stat().st_size,
                              compressed_bytes=compressed_size, output=str(output)), ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-url", required=True)
    parser.add_argument("--output", type=Path, default=ASSETS / "catalog.db.gzip")
    args = parser.parse_args()
    for attempt in range(3):
        try:
            build(args.api_url, args.output)
            break
        except HTTPError as error:
            if error.code != 409 or attempt == 2:
                raise
            print("Catalog changed during download; restarting.", flush=True)
