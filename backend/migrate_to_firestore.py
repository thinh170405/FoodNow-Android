"""One-time copy of the FoodNow SQLite database into Cloud Firestore.

Requires GOOGLE_APPLICATION_CREDENTIALS and an existing Firestore database.
Never deletes or changes the source database. This copies data only; the API
must be switched to Firestore separately before retiring SQLite.
"""

import argparse
import sqlite3
from pathlib import Path

import firebase_admin
from firebase_admin import firestore

from firestore_storage import CHUNK_SIZE


TABLES = (
    "users", "sessions", "restaurants", "foods", "orders", "order_items",
    "uploaded_images", "order_messages", "delivery_positions", "device_tokens",
    "firebase_accounts", "notification_events",
)
KEYS = {
    "users": "id", "sessions": "token_hash", "restaurants": "id", "foods": "id",
    "orders": "id", "order_items": "id", "uploaded_images": "id",
    "order_messages": "id", "delivery_positions": "order_id",
    "device_tokens": "token", "firebase_accounts": "uid", "notification_events": "id",
}


def migrate(source: Path, apply: bool):
    if not source.is_file():
        raise FileNotFoundError(source)
    firebase_admin.initialize_app()
    db = firestore.client()
    if apply and any(next(db.collection(table).limit(1).stream(), None) for table in TABLES):
        raise RuntimeError('Firestore already contains FoodNow data; refusing to overwrite live records')
    with sqlite3.connect(f"file:{source.as_posix()}?mode=ro", uri=True) as conn:
        conn.row_factory = sqlite3.Row
        for table in TABLES:
            if not conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone():
                continue
            rows = [dict(row) for row in conn.execute(f"SELECT * FROM {table}")]
            print(f"{table}: {len(rows)} document(s)")
            if not apply:
                continue
            for row in rows:
                # SQLite BLOB maps to Firestore bytes. String IDs preserve the
                # existing API IDs, foreign keys and references in documents.
                ref = db.collection(table).document(str(row[KEYS[table]]))
                if table == 'uploaded_images':
                    payload = row.pop('data')
                    parts = [payload[i:i+CHUNK_SIZE] for i in range(0, len(payload), CHUNK_SIZE)]
                    row['chunk_count'] = len(parts)
                    ref.set(row)
                    for i, part in enumerate(parts):
                        ref.collection('chunks').document(str(i)).set({'data': part})
                else:
                    ref.set(row)
    if apply:
        print("Copy complete. Verify counts and API behavior before switching storage.")
    else:
        print("Dry run only. Add --apply to copy data after Firestore is created.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=Path(__file__).resolve().parent / "data" / "foodnow.db")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    migrate(args.source, args.apply)
