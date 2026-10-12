"""Smoke tests of production migration/DAO SQL, independent of the Android SDK.

Run: python tools/verify_sqlite.py
This does not replace Room schema validation or Android device tests.
"""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "app/src/main/java/com/usuario/finanzas/data/local"


def migrated_database():
    db = sqlite3.connect(":memory:")
    db.executescript("""
        CREATE TABLE transacciones (id INTEGER PRIMARY KEY, monto REAL NOT NULL,
            fecha INTEGER NOT NULL, descripcion TEXT NOT NULL);
        CREATE TABLE transacciones_pendientes (id INTEGER PRIMARY KEY,
            monto REAL NOT NULL, tipo TEXT NOT NULL);
        INSERT INTO transacciones VALUES (7, 123.45, 1000, 'Histórico existente');
        INSERT INTO transacciones_pendientes VALUES (1, 500, 'ingreso');
        INSERT INTO transacciones_pendientes VALUES (2, 900, 'transferencia');
    """)
    source = (DATA / "FinanzasDatabase.kt").read_text(encoding="utf-8-sig")
    migration = source.split("val MIGRATION_3_4", 1)[1].split("val MIGRATION_1_2", 1)[0]
    statements = re.findall(r'db\.execSQL\("([^"\n]+)"\)', migration)
    if len(statements) != 8:
        raise AssertionError("Migration changed: inspect/update this SQL smoke test")
    for sql in statements:
        db.execute(sql)
    return db


class DatabaseSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = migrated_database()

    def tearDown(self):
        self.db.close()

    def test_migration_preserves_existing_values_and_assigns_defaults(self):
        self.assertEqual(self.db.execute("SELECT * FROM transacciones").fetchone(),
                         (7, 123.45, 1000, "Histórico existente", "salida", "compra", ""))
        self.assertEqual(self.db.execute(
            "SELECT id, direccion FROM transacciones_pendientes ORDER BY id").fetchall(),
            [(1, "entrada"), (2, "salida")])
        indexes = {row[1] for row in self.db.execute("PRAGMA index_list(transacciones)")}
        self.assertIn("index_transacciones_fecha", indexes)

    def test_cross_channel_match_is_one_to_one(self):
        source = (DATA / "dao/RegistroDeteccionDao.kt").read_text(encoding="utf-8-sig")
        queries = re.findall(r'@Query\("([^"\n]+)"\)', source)
        match = next(sql for sql in queries if sql.startswith("SELECT clave"))
        pair = next(sql for sql in queries if sql.startswith("UPDATE"))
        self.db.executemany("INSERT INTO registro_detecciones VALUES (?, ?, ?, ?, ?)", [
            ("sms-1", "same-payment", "SMS", 100000, 0),
            ("sms-2", "same-payment", "SMS", 100001, 0),
            ("old", "same-payment", "SMS", 1, 0),
            ("other", "other-payment", "SMS", 100002, 0),
        ])
        args = dict(coincidencia="same-payment", canal="NOTIFICACION", desde=10000, hasta=190000)
        self.assertEqual(self.db.execute(match, {**args, "canal": "SMS"}).fetchall(), [])
        for expected in ("sms-1", "sms-2"):
            actual = self.db.execute(match, args).fetchone()
            self.assertEqual(actual, (expected,))
            self.db.execute(pair, {"clave": expected})
        self.assertIsNone(self.db.execute(match, args).fetchone())

    def test_totals_and_categories_exclude_incoming_and_own_transfers(self):
        self.db.execute("ALTER TABLE transacciones ADD COLUMN categoriaId INTEGER NOT NULL DEFAULT 1")
        self.db.execute("CREATE TABLE categorias (id INTEGER, nombre TEXT, color TEXT, icono TEXT)")
        self.db.execute("INSERT INTO categorias VALUES (1, 'Otros', '#000000', 'Category')")
        self.db.executemany("""INSERT INTO transacciones
            (id, monto, fecha, descripcion, direccion, tipo, banco, categoriaId)
            VALUES (?, ?, ?, '', ?, ?, '', 1)""", [
            (8, 1000, 1000, "entrada", "ingreso"),
            (9, 500, 1000, "entrada", "transferencia_propia"),
            (10, 500, 1000, "salida", "transferencia_propia"),
            (11, 50, 1000, "salida", "transferencia"),
            (12, 9999, 99999, "salida", "compra"),
        ])
        source = (DATA / "dao/TransaccionDao.kt").read_text(encoding="utf-8-sig")
        totals = re.findall(r'@Query\("(SELECT COALESCE[^"\n]+)"\)', source)
        for sql in totals:
            expected = 1000 if "direccion = 'entrada'" in sql else 173.45
            self.assertAlmostEqual(self.db.execute(sql, {"inicio": 0, "fin": 2000}).fetchone()[0], expected)
            self.assertEqual(self.db.execute(sql, {"inicio": 5000, "fin": 6000}).fetchone()[0], 0)
        category_queries = re.findall(r'@Query\(\s*"""(.*?)"""\s*\)', source, re.S)
        self.assertEqual(len(category_queries), 2)
        for sql in category_queries:
            rows = self.db.execute(sql, {"inicio": 0, "fin": 2000}).fetchall()
            self.assertEqual(len(rows), 1)
            self.assertAlmostEqual(rows[0][-1], 173.45)


if __name__ == "__main__":
    unittest.main(verbosity=2)
