"""Consistent SQLite backup, including while the local server is running."""
from datetime import datetime
from pathlib import Path
import sqlite3
root = Path(__file__).resolve().parent
source = root / 'data' / 'foodnow.db'
if not source.is_file():
    raise SystemExit('Chưa có database. Chạy server trước.')
target_dir = root / 'backups'
target_dir.mkdir(exist_ok=True)
target = target_dir / ('foodnow-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '.db')
src = sqlite3.connect(source.as_uri() + '?mode=ro', uri=True)
dst = sqlite3.connect(target)
try:
    src.backup(dst)
finally:
    dst.close()
    src.close()
print(target)
