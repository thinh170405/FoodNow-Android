"""Firestore persistence for the FoodNow API's in-process relational working set.

Firestore is authoritative: no SQLite file is opened in this mode. The existing
SQL routes run against a process-local memory index, which is loaded from
Firestore at startup and committed back to Firestore before each response.
Run a single backend process; the memory index does not watch external writers.
"""

import sqlite3
import threading

from firebase_admin import firestore

TABLES = (
    'users', 'sessions', 'restaurants', 'foods', 'orders', 'order_items',
    'uploaded_images', 'order_messages', 'delivery_positions', 'device_tokens',
    'firebase_accounts', 'notification_events',
)
KEYS = {
    'users': 'id', 'sessions': 'token_hash', 'restaurants': 'id', 'foods': 'id',
    'orders': 'id', 'order_items': 'id', 'uploaded_images': 'id',
    'order_messages': 'id', 'delivery_positions': 'order_id',
    'device_tokens': 'token', 'firebase_accounts': 'uid',
    'notification_events': 'id',
}
CHUNK_SIZE = 500_000


class Store:
    def __init__(self):
        self.db = firestore.client()
        self.sql = sqlite3.connect(':memory:', check_same_thread=False, isolation_level=None)
        self.sql.row_factory = sqlite3.Row
        self.sql.execute('PRAGMA foreign_keys=ON')
        self.lock = threading.RLock()
        self.loaded = False

    def connection(self):
        return Connection(self)

    def snapshot(self):
        result = {}
        for table in TABLES:
            try:
                result[table] = {str(row[KEYS[table]]): dict(row)
                                 for row in self.sql.execute(f'SELECT * FROM {table}')}
            except sqlite3.OperationalError:
                result[table] = {}
        return result

    def load(self):
        """Call once after schema creation and before seed data is inserted."""
        if self.loaded:
            return
        for table in TABLES:
            columns = {row['name'] for row in self.sql.execute(f'PRAGMA table_info({table})')}
            for doc in self.db.collection(table).stream():
                row = doc.to_dict()
                if table == 'uploaded_images' and row.get('chunk_count'):
                    row['data'] = b''.join(
                        self.db.collection(table).document(doc.id)
                        .collection('chunks').document(str(i)).get().to_dict()['data']
                        for i in range(row['chunk_count']))
                row = {k: v for k, v in row.items() if k in columns}
                fields = list(row)
                self.sql.execute(f"INSERT INTO {table} ({','.join(fields)}) VALUES ({','.join('?' for _ in fields)})",
                                 tuple(row.values()))
        self.loaded = True

    def commit_diff(self, before, after):
        writes = []
        for table in TABLES:
            old, new = before[table], after[table]
            for key in old.keys() - new.keys():
                ref = self.db.collection(table).document(key)
                writes.append(('delete', ref, None))
            for key, row in new.items():
                if old.get(key) == row:
                    continue
                ref = self.db.collection(table).document(key)
                if table == 'uploaded_images':
                    data = row['data']
                    parts = [data[i:i+CHUNK_SIZE] for i in range(0, len(data), CHUNK_SIZE)]
                    # Image payload lives in small chunk documents to stay below
                    # Firestore's per-document size limit.
                    writes.append(('set', ref, {'id': row['id'], 'created_at': row['created_at'], 'chunk_count': len(parts)}))
                    for i, part in enumerate(parts):
                        writes.append(('set', ref.collection('chunks').document(str(i)), {'data': part}))
                    old_parts = (len(old[key]['data']) + CHUNK_SIZE - 1) // CHUNK_SIZE if key in old else 0
                    for i in range(len(parts), old_parts):
                        writes.append(('delete', ref.collection('chunks').document(str(i)), None))
                else:
                    writes.append(('set', ref, row))
        # Normal API requests are well below the 500-write batch limit.
        if len(writes) > 500:
            raise RuntimeError('Too many Firestore writes in one request')
        if writes:
            batch = self.db.batch()
            for action, ref, row in writes:
                if action == 'set':
                    batch.set(ref, row)
                else:
                    batch.delete(ref)
            batch.commit()


class Connection:
    def __init__(self, store):
        self.store = store
        self.sql = store.sql

    def __enter__(self):
        self.store.lock.acquire()
        self.before = self.store.snapshot()
        return self

    def __exit__(self, typ, value, traceback):
        try:
            if typ is None:
                self.store.commit_diff(self.before, self.store.snapshot())
                self.sql.commit()
            else:
                self.sql.rollback()
        except Exception:
            self.sql.rollback()
            raise
        finally:
            self.store.lock.release()

    def __getattr__(self, name):
        return getattr(self.sql, name)
