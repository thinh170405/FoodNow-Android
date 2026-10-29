"""FoodNow local development API + admin website. Python 3.11+ and Pillow."""
import argparse
import base64
import binascii
import io
from PIL import Image, ImageOps, UnidentifiedImageError
import hashlib
import hmac
import json
import mimetypes
import os
from pathlib import Path
import re
import secrets
import sqlite3
import threading
import time
import sys
import mobile_features
import firestore_storage
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
DB = ROOT / 'data' / 'foodnow.db'
_firestore_store = None
LABELS = {'pending': 'Chờ xác nhận', 'confirmed': 'Đã xác nhận', 'delivering': 'Đang giao', 'completed': 'Hoàn thành', 'cancelled': 'Đã hủy'}
TRANSITIONS = {'pending': {'confirmed', 'cancelled'}, 'confirmed': {'delivering', 'cancelled'}, 'delivering': {'completed'}, 'completed': set(), 'cancelled': set()}
PAYMENTS = {'cod': 'Tiền mặt khi nhận hàng', 'wallet_demo': 'Ví điện tử (mô phỏng)', 'bank_demo': 'Chuyển khoản (mô phỏng)'}
IMAGES = {'ic_black_coffee', 'ic_banh_mi', 'ic_milk_tea', 'ic_circlek', 'ic_ministop', 'ic_seveneleven', 'ic_vinmart', 'cover_menu_1'}


class ApiError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


class ClosingConnection(sqlite3.Connection):
    def __exit__(self, *args):
        try:
            return super().__exit__(*args)
        finally:
            self.close()


def connection():
    if _firestore_store is not None:
        return _firestore_store.connection()
    conn = sqlite3.connect(DB, timeout=15, factory=ClosingConnection)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA foreign_keys=ON')
    return conn


def password_hash(password):
    salt = secrets.token_hex(16)
    return salt + ':' + hashlib.pbkdf2_hmac('sha256', password.encode(), bytes.fromhex(salt), 600000).hex()


def password_matches(password, encoded):
    salt, digest = encoded.split(':')
    return hmac.compare_digest(digest, hashlib.pbkdf2_hmac('sha256', password.encode(), bytes.fromhex(salt), 600000).hex())


def text(data, key, maximum=200, required=True):
    value = data.get(key, '')
    if not isinstance(value, str) or len(value.strip()) > maximum or (required and not value.strip()):
        raise ApiError(400, f'Thông tin {key} không hợp lệ')
    return value.strip()


def password(data, key='password'):
    value = data.get(key, '')
    if not isinstance(value, str) or not 8 <= len(value) <= 128 or not any(c.isalpha() for c in value) or not any(c.isdigit() for c in value):
        raise ApiError(400, 'Mật khẩu 8–128 ký tự, gồm chữ và số')
    return value


def email_value(data):
    email = text(data, 'email', 254).lower()
    if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', email):
        raise ApiError(400, 'Email không hợp lệ')
    return email


def integer(data, key, low=1, high=100000000):
    value = data.get(key)
    if type(value) is not int or not low <= value <= high:
        raise ApiError(400, f'Giá trị {key} không hợp lệ')
    return value


def public_user(row):
    return {key: row[key] for key in ('id', 'name', 'email', 'phone', 'address', 'role', 'avatar')}


def initialize(path=None, bootstrap=True):
    global DB, _firestore_store
    if path is not None:
        DB = Path(path)
        _firestore_store = None
    elif os.environ.get('FOODNOW_STORAGE') == 'firestore':
        mobile_features.firebase_app()
        _firestore_store = firestore_storage.Store()
    DB.parent.mkdir(parents=True, exist_ok=True)
    with connection() as conn:
        conn.execute('PRAGMA journal_mode=WAL')
        conn.executescript('''
        CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY, email TEXT UNIQUE NOT NULL COLLATE NOCASE,
          name TEXT NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'customer' CHECK(role IN ('customer','admin','driver')),
          phone TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '');
        CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), expires INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS restaurants(id INTEGER PRIMARY KEY, name TEXT NOT NULL, address TEXT NOT NULL,
          hours TEXT NOT NULL, image TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)));
        CREATE TABLE IF NOT EXISTS foods(id INTEGER PRIMARY KEY, restaurant_id INTEGER NOT NULL REFERENCES restaurants(id),
          name TEXT NOT NULL, price INTEGER NOT NULL CHECK(price>0), image TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)));
        CREATE TABLE IF NOT EXISTS orders(id TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
          restaurant_id INTEGER NOT NULL REFERENCES restaurants(id), restaurant_name TEXT NOT NULL, client_key TEXT NOT NULL,
          recipient TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, note TEXT NOT NULL,
          subtotal INTEGER NOT NULL, delivery_fee INTEGER NOT NULL, total INTEGER NOT NULL,
          payment_method TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', created_at INTEGER NOT NULL,
          UNIQUE(user_id,client_key));
        CREATE TABLE IF NOT EXISTS order_items(id INTEGER PRIMARY KEY, order_id TEXT NOT NULL REFERENCES orders(id),
          food_id INTEGER NOT NULL REFERENCES foods(id), name TEXT NOT NULL, unit_price INTEGER NOT NULL, quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 99));
        CREATE INDEX IF NOT EXISTS orders_user ON orders(user_id,created_at);
        CREATE TABLE IF NOT EXISTS uploaded_images(id TEXT PRIMARY KEY, data BLOB NOT NULL, created_at INTEGER NOT NULL);
        ''')
        mobile_features.initialize(conn)
        # Add nullable coordinates without altering existing orders on older databases.
        columns = {row['name'] for row in conn.execute('PRAGMA table_info(orders)')}
        for column in ('delivery_latitude', 'delivery_longitude'):
            if column not in columns:
                conn.execute(f'ALTER TABLE orders ADD COLUMN {column} REAL')
        if _firestore_store is not None:
            _firestore_store.load()
        if not conn.execute('SELECT 1 FROM restaurants').fetchone():
            shops = [('Circle K', '40 Hùng Vương', '00:00 – 24:00', 'ic_circlek'),
                     ('MiniStop', '70 Lý Thái Tổ', '06:00 – 23:00', 'ic_ministop'),
                     ('7 Eleven', '82 Nguyễn Thị Minh Khai', '00:00 – 24:00', 'ic_seveneleven'),
                     ('VinMart', '98 Nguyễn Văn Cừ', '07:00 – 22:00', 'ic_vinmart')]
            for index, shop in enumerate(shops):
                rid = conn.execute('INSERT INTO restaurants(name,address,hours,image) VALUES(?,?,?,?)', shop).lastrowid
                menu = [('Cà phê đen', 49000, 'ic_black_coffee'), ('Bánh mì trứng', 25000, 'ic_banh_mi'),
                        ('Trà sữa truyền thống', 35000, 'ic_milk_tea'), ('Cà phê sữa', 35000, 'ic_black_coffee'),
                        ('Bánh mì gà', 45000, 'ic_banh_mi'), ('Trà sữa trân châu', 49000, 'ic_milk_tea'),
                        [('Combo bánh mì và cà phê', 59000, 'ic_banh_mi'), ('Trà sữa matcha', 42000, 'ic_milk_tea'),
                         ('Bánh mì phô mai', 32000, 'ic_banh_mi'), ('Cà phê đen đá', 25000, 'ic_black_coffee')][index]]
                conn.executemany('INSERT INTO foods(restaurant_id,name,price,image) VALUES(?,?,?,?)', [(rid, *food) for food in menu])
        if bootstrap and not conn.execute("SELECT 1 FROM users WHERE role='admin'").fetchone():
            secret = secrets.token_urlsafe(18) + '9a'
            conn.execute("INSERT INTO users(email,name,password_hash,role) VALUES(?,?,?,'admin')", ('admin@foodnow.local', 'Quản trị FoodNow', password_hash(secret)))
            credentials = DB.parent / 'admin-credentials.txt'
            credentials.write_text(f'Email: admin@foodnow.local\nPassword: {secret}\nĐổi mật khẩu sau lần đăng nhập đầu tiên.\n', encoding='utf-8')


def order_json(conn, row):
    result = dict(row)
    result['items'] = [dict(item) for item in conn.execute('SELECT food_id,name,unit_price,quantity FROM order_items WHERE order_id=?', (row['id'],))]
    result['statusLabel'] = LABELS[row['status']]
    money = lambda n: f'{n:,}'.replace(',', '.') + ' VND'
    items = '\n'.join(f"{i['quantity']} × {i['name']} — {money(i['quantity']*i['unit_price'])}" for i in result['items'])
    payment_state = ('Đã thu tiền mặt' if row['status'] == 'completed' else 'Chưa thu tiền mặt') if row['payment_method'] == 'cod' else 'Thanh toán mô phỏng, không thu tiền thật'
    result['details'] = f"Mã đơn: {row['id']}\n{row['restaurant_name']}\n\n{items}\n\nPhí giao hàng: {money(row['delivery_fee'])}\nTổng: {money(row['total'])}\n{PAYMENTS[row['payment_method']]}\n{payment_state}\n\nNgười nhận: {row['recipient']}\nĐiện thoại: {row['phone']}\nĐịa chỉ: {row['address']}\nGhi chú: {row['note']}"
    if row['delivery_latitude'] is not None and row['delivery_longitude'] is not None:
        result['details'] += f"\nĐiểm giao: {row['delivery_latitude']:.6f}, {row['delivery_longitude']:.6f}"
    return result


class Handler(BaseHTTPRequestHandler):
    server_version = 'FoodNow/1.0'
    protocol_version = 'HTTP/1.1'
    attempts = {}
    lock = threading.Lock()

    def send_json(self, status, body):
        payload = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def authenticate(self, conn, admin=False):
        token = self.headers.get('Authorization', '').removeprefix('Bearer ')
        user = conn.execute('SELECT users.* FROM sessions JOIN users ON users.id=sessions.user_id WHERE token_hash=? AND expires>?',
                            (hashlib.sha256(token.encode()).hexdigest(), int(time.time()))).fetchone()
        if not user:
            raise ApiError(401, 'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.')
        if admin and user['role'] != 'admin':
            raise ApiError(403, 'Chỉ quản trị viên được thực hiện thao tác này')
        return user

    def rate_limit(self):
        ip = self.client_address[0]
        now = time.time()
        with self.lock:
            # Short-lived local-development throttle, bounded by expiry.
            expired = [key for key, entries in self.attempts.items() if not entries or now - entries[-1] >= 60]
            for key in expired:
                del self.attempts[key]
            entries = [t for t in self.attempts.get(ip, []) if now-t < 60]
            if len(entries) >= 30:
                raise ApiError(429, 'Quá nhiều lần thử. Vui lòng đợi một phút.')
            self.attempts[ip] = entries + [now]

    def do_GET(self): self.handle_request()
    def do_POST(self): self.handle_request()
    def do_PATCH(self): self.handle_request()

    def handle_request(self):
        try:
            path = urlsplit(self.path).path
            if not path.startswith('/api/'):
                if self.command != 'GET': raise ApiError(405, 'Phương thức không hỗ trợ')
                return self.static(path)
            data = {}
            if self.command in ('POST', 'PATCH'):
                length = int(self.headers.get('Content-Length', '0'))
                if path in ('/api/admin/images','/api/me/avatar'):
                    with connection() as conn: self.authenticate(conn, admin=path.startswith('/api/admin/'))
                limit = 7*1024*1024 if path in ('/api/admin/images','/api/me/avatar') else 65536
                if not 0 <= length <= limit: raise ApiError(413, 'Dữ liệu quá lớn')
                if self.headers.get('Content-Type', '').split(';')[0] != 'application/json': raise ApiError(415, 'Yêu cầu JSON')
                try: data = json.loads(self.rfile.read(length) or b'{}')
                except (ValueError, UnicodeDecodeError): raise ApiError(400, 'JSON không hợp lệ')
                if not isinstance(data, dict): raise ApiError(400, 'Yêu cầu JSON object')
            with connection() as conn:
                # Serialize mutations, including idempotent order creation.
                if self.command != 'GET': conn.execute('BEGIN IMMEDIATE')
                result = self.route(conn, path, data)
            self.send_json(200, result)
        except ApiError as error:
            self.close_connection = True
            self.send_json(error.status, {'error': error.message})
        except sqlite3.IntegrityError:
            self.send_json(409, {'error': 'Dữ liệu trùng hoặc không hợp lệ'})
        except (ValueError, TypeError):
            self.send_json(400, {'error': 'Dữ liệu không hợp lệ'})
        except Exception:
            import traceback
            traceback.print_exc()
            self.send_json(500, {'error': 'Lỗi máy chủ. Vui lòng thử lại.'})

    def route(self, conn, path, data):
        method = self.command
        if path == '/api/health' and method == 'GET': return {'ok': True, 'database': 'Firestore' if _firestore_store else 'SQLite', 'version': 1}
        if path == '/api/auth/firebase' and method == 'POST':
            self.rate_limit()
            return mobile_features.firebase_login(conn,data,sys.modules[__name__])
        if path == '/api/auth/register' and method == 'POST':
            self.rate_limit()
            email, name, secret = email_value(data), text(data, 'name', 100), password(data)
            if conn.execute('SELECT 1 FROM users WHERE email=?', (email,)).fetchone(): raise ApiError(409, 'Email đã được đăng ký')
            conn.execute('INSERT INTO users(email,name,password_hash) VALUES(?,?,?)', (email, name, password_hash(secret)))
            return {'ok': True}
        if path in ('/api/auth/login', '/api/auth/delivery-login') and method == 'POST':
            self.rate_limit()
            email = email_value(data)
            secret = data.get('password', '')
            if not isinstance(secret, str) or len(secret) > 128: raise ApiError(400, 'Mật khẩu không hợp lệ')
            user = conn.execute('SELECT * FROM users WHERE email=?', (email,)).fetchone()
            if not user or not password_matches(secret, user['password_hash']): raise ApiError(401, 'Email hoặc mật khẩu không đúng')
            if path == '/api/auth/delivery-login' and user['role'] != 'driver':
                raise ApiError(403, 'Chỉ tài khoản tài xế được đăng nhập tại đây')
            if path == '/api/auth/login' and user['role'] == 'driver':
                raise ApiError(403, 'Tài xế hãy dùng trang đăng nhập tài xế')
            token = secrets.token_urlsafe(32)
            conn.execute('DELETE FROM sessions WHERE expires<=?', (int(time.time()),))
            conn.execute('INSERT INTO sessions VALUES(?,?,?)', (hashlib.sha256(token.encode()).hexdigest(), user['id'], int(time.time())+7*86400))
            return {'token': token, 'user': public_user(user)}
        if path == '/api/catalog' and method == 'GET':
            shops = []
            for row in conn.execute('SELECT * FROM restaurants WHERE active=1 ORDER BY id'):
                shop = dict(row)
                shop['menu'] = [dict(f) for f in conn.execute('SELECT * FROM foods WHERE restaurant_id=? AND active=1 ORDER BY id', (row['id'],))]
                shops.append(shop)
            return {'restaurants': shops, 'deliveryFee': 15000}
        user = self.authenticate(conn, path.startswith('/api/admin/'))
        if path.startswith('/api/driver/') and user['role'] != 'driver':
            raise ApiError(403, 'Chỉ tài xế được truy cập')
        if user['role'] == 'driver' and not (path.startswith('/api/driver/') or path in
                ('/api/me', '/api/auth/logout', '/api/auth/password')):
            raise ApiError(403, 'Tài khoản tài xế chỉ dùng giao diện giao hàng')
        extra = mobile_features.route(self,conn,path,data,user,sys.modules[__name__])
        if extra is not None: return extra
        if path in ('/api/admin/images','/api/me/avatar') and method == 'POST':
            encoded = data.get('data')
            if not isinstance(encoded, str): raise ApiError(400, 'Chưa chọn ảnh')
            try: raw = base64.b64decode(encoded, validate=True)
            except (ValueError, binascii.Error): raise ApiError(400, 'Dữ liệu ảnh không hợp lệ')
            if not 1 <= len(raw) <= 5*1024*1024: raise ApiError(413, 'Ảnh tối đa 5 MB')
            try:
                with Image.open(io.BytesIO(raw)) as source:
                    if source.format not in ('JPEG', 'PNG', 'WEBP'): raise ApiError(400, 'Chỉ hỗ trợ JPG, PNG, WebP')
                    if source.width*source.height > 16000000 or max(source.size)>8192: raise ApiError(400, 'Ảnh tối đa 16 triệu điểm ảnh và 8192 px mỗi chiều')
                    source.verify()
                with Image.open(io.BytesIO(raw)) as source:
                    picture = ImageOps.exif_transpose(source)
                    picture.thumbnail((1600,1600))
                    rgba = picture.convert('RGBA')
                    normalized = Image.new('RGB', rgba.size, 'white')
                    normalized.paste(rgba, mask=rgba.getchannel('A'))
                    output = io.BytesIO()
                    normalized.save(output, format='JPEG', quality=85)
                    payload = output.getvalue()
            except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
                raise ApiError(400, 'Không đọc được ảnh. Hãy chọn JPG, PNG hoặc WebP hợp lệ.')
            key = 'upload_' + hashlib.sha256(payload).hexdigest()
            conn.execute('INSERT OR IGNORE INTO uploaded_images VALUES(?,?,?)', (key,payload,int(time.time())))
            if path == '/api/me/avatar':conn.execute('UPDATE users SET avatar=? WHERE id=?',(key,user['id']))
            return {'image': key, 'url': '/uploads/'+key+'.jpg'}
        if path == '/api/auth/logout' and method == 'POST':
            token = self.headers.get('Authorization', '').removeprefix('Bearer ')
            conn.execute('DELETE FROM sessions WHERE token_hash=?', (hashlib.sha256(token.encode()).hexdigest(),))
            return {'ok': True}
        if path == '/api/auth/password' and method == 'POST':
            old = data.get('current', '')
            if not isinstance(old, str) or len(old) > 128 or not password_matches(old, user['password_hash']): raise ApiError(400, 'Mật khẩu hiện tại không đúng')
            secret = password(data, 'replacement')
            if old == secret: raise ApiError(400, 'Mật khẩu mới phải khác mật khẩu cũ')
            conn.execute('UPDATE users SET password_hash=? WHERE id=?', (password_hash(secret), user['id']))
            conn.execute('DELETE FROM sessions WHERE user_id=?', (user['id'],))
            return {'ok': True}
        if path == '/api/me':
            if method == 'GET': return {'user': public_user(user)}
            if method == 'PATCH':
                name, phone, address = text(data, 'name', 100), text(data, 'phone', 30, False), text(data, 'address', 300, False)
                if phone and not re.fullmatch(r'(0\d{9}|\+84\d{9})', re.sub(r'[\s()-]', '', phone)): raise ApiError(400, 'Điện thoại không hợp lệ')
                conn.execute('UPDATE users SET name=?,phone=?,address=? WHERE id=?', (name, phone, address, user['id']))
                return {'user': public_user(conn.execute('SELECT * FROM users WHERE id=?', (user['id'],)).fetchone())}
        if path in ('/api/orders', '/api/admin/orders', '/api/driver/orders') and method == 'GET':
            if path == '/api/driver/orders':
                rows = conn.execute("SELECT * FROM orders WHERE status='delivering' ORDER BY created_at DESC,rowid DESC")
            else:
                rows = conn.execute('SELECT * FROM orders ORDER BY created_at DESC,rowid DESC') if user['role'] == 'admin' and path.startswith('/api/admin') else conn.execute('SELECT * FROM orders WHERE user_id=? ORDER BY created_at DESC,rowid DESC', (user['id'],))
            return {'orders': [order_json(conn, row) for row in rows]}
        if path == '/api/orders' and method == 'POST':
            key = text(data, 'clientKey', 100)
            existing = conn.execute('SELECT * FROM orders WHERE user_id=? AND client_key=?', (user['id'], key)).fetchone()
            if existing: return {'order': order_json(conn, existing)}
            rid = integer(data, 'restaurantId')
            shop = conn.execute('SELECT * FROM restaurants WHERE id=? AND active=1', (rid,)).fetchone()
            if not shop: raise ApiError(409, 'Nhà hàng đã ngừng phục vụ')
            recipient, phone, address = text(data, 'recipient', 100), text(data, 'phone', 30), text(data, 'address', 300)
            note = text(data, 'note', 500, False)
            latitude, longitude = data.get('deliveryLatitude'), data.get('deliveryLongitude')
            if latitude is not None or longitude is not None:
                if (type(latitude) not in (int, float) or type(longitude) not in (int, float)
                        or not -90 <= latitude <= 90 or not -180 <= longitude <= 180):
                    raise ApiError(400, 'Tọa độ giao hàng không hợp lệ')
            if not re.fullmatch(r'(0\d{9}|\+84\d{9})', re.sub(r'[\s()-]', '', phone)): raise ApiError(400, 'Điện thoại không hợp lệ')
            payment = data.get('paymentMethod')
            if not isinstance(payment, str) or payment not in PAYMENTS: raise ApiError(400, 'Phương thức thanh toán không hợp lệ')
            items = data.get('items')
            if not isinstance(items, list) or not 1 <= len(items) <= 100: raise ApiError(400, 'Giỏ hàng trống hoặc quá lớn')
            selected, seen, subtotal = [], set(), 0
            for item in items:
                if not isinstance(item, dict): raise ApiError(400, 'Món không hợp lệ')
                fid, qty = integer(item, 'foodId'), integer(item, 'quantity', 1, 99)
                if fid in seen: raise ApiError(400, 'Món bị lặp')
                seen.add(fid)
                food = conn.execute('SELECT * FROM foods WHERE id=? AND restaurant_id=? AND active=1', (fid, rid)).fetchone()
                if not food: raise ApiError(409, 'Có món đã ngừng bán. Vui lòng tải lại thực đơn.')
                subtotal += food['price']*qty
                selected.append((fid, food['name'], food['price'], qty))
            total = subtotal + 15000
            if total > 1000000000: raise ApiError(400, 'Giá trị đơn hàng quá lớn')
            if data.get('expectedTotal') != total: raise ApiError(409, 'Giá món đã thay đổi. Quay về Trang chủ và tải lại thực đơn trước khi đặt.')
            oid = secrets.token_hex(8)
            conn.execute('''INSERT INTO orders(id,user_id,restaurant_id,restaurant_name,client_key,recipient,phone,address,note,subtotal,delivery_fee,total,payment_method,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)''', (oid, user['id'], rid, shop['name'], key, recipient, phone, address, note, subtotal, 15000, total, payment, int(time.time())))
            conn.executemany('INSERT INTO order_items(order_id,food_id,name,unit_price,quantity) VALUES(?,?,?,?,?)', [(oid, *item) for item in selected])
            conn.execute('UPDATE orders SET delivery_latitude=?,delivery_longitude=? WHERE id=?', (latitude, longitude, oid))
            return {'order': order_json(conn, conn.execute('SELECT * FROM orders WHERE id=?', (oid,)).fetchone())}
        match = re.fullmatch(r'/api/(admin/)?orders/([a-zA-Z0-9]+)', path)
        if match and method == 'PATCH':
            row = conn.execute('SELECT * FROM orders WHERE id=?', (match[2],)).fetchone()
            if not row or (not match[1] and row['user_id'] != user['id']): raise ApiError(404, 'Không tìm thấy đơn hàng')
            status = data.get('status')
            if not isinstance(status, str) or status not in TRANSITIONS[row['status']]: raise ApiError(409, 'Không thể chuyển trạng thái đơn này')
            if not match[1] and status not in ('cancelled', 'completed'): raise ApiError(403, 'Không được thay đổi trạng thái này')
            conn.execute('UPDATE orders SET status=? WHERE id=?', (status, row['id']))
            mobile_features.event(conn,row['user_id'],row['id'],'Cập nhật đơn hàng',LABELS[status])
            return {'order': order_json(conn, conn.execute('SELECT * FROM orders WHERE id=?', (row['id'],)).fetchone())}
        if path == '/api/admin/summary' and method == 'GET':
            return {'users': conn.execute("SELECT COUNT(*) FROM users WHERE role='customer'").fetchone()[0],
                    'orders': conn.execute('SELECT COUNT(*) FROM orders').fetchone()[0],
                    'pending': conn.execute("SELECT COUNT(*) FROM orders WHERE status='pending'").fetchone()[0],
                    'completedTotal': conn.execute("SELECT COALESCE(SUM(total),0) FROM orders WHERE status='completed'").fetchone()[0]}
        if path == '/api/admin/users' and method == 'GET': return {'users': [public_user(row) for row in conn.execute('SELECT * FROM users ORDER BY id DESC')]}
        if path == '/api/admin/drivers' and method == 'POST':
            email, name, secret = email_value(data), text(data, 'name', 100), password(data)
            if conn.execute('SELECT 1 FROM users WHERE email=?', (email,)).fetchone():
                raise ApiError(409, 'Email đã được đăng ký')
            ident = conn.execute("INSERT INTO users(email,name,password_hash,role) VALUES(?,?,?,'driver')",
                                 (email, name, password_hash(secret))).lastrowid
            return {'user': public_user(conn.execute('SELECT * FROM users WHERE id=?', (ident,)).fetchone())}
        if path == '/api/admin/catalog' and method == 'GET':
            return {'restaurants': [dict(row) for row in conn.execute('SELECT * FROM restaurants ORDER BY id')], 'foods': [dict(row) for row in conn.execute('SELECT * FROM foods ORDER BY id')]}
        match = re.fullmatch(r'/api/admin/(restaurants|foods)(?:/(\d+))?', path)
        if match and method in ('POST', 'PATCH'):
            table, ident = match[1], match[2]
            if (method == 'POST') != (ident is None): raise ApiError(405, 'Sai phương thức')
            name = text(data, 'name', 100)
            image = text(data, 'image', 96)
            uploaded = table == 'foods' and conn.execute('SELECT 1 FROM uploaded_images WHERE id=?',(image,)).fetchone()
            if image not in IMAGES and not uploaded: raise ApiError(400, 'Ảnh không hợp lệ')
            active = integer(data, 'active', 0, 1)
            if table == 'restaurants':
                fields = ('name', 'address', 'hours', 'image', 'active')
                values = (name, text(data, 'address', 300), text(data, 'hours', 60), image, active)
            else:
                fields = ('name', 'restaurant_id', 'price', 'image', 'active')
                rid = integer(data, 'restaurant_id')
                if not conn.execute('SELECT 1 FROM restaurants WHERE id=?', (rid,)).fetchone(): raise ApiError(400, 'Nhà hàng không tồn tại')
                values = (name, rid, integer(data, 'price', 1000, 10000000), image, active)
            # table and columns are chosen above, never taken directly from arbitrary input.
            if ident:
                if not conn.execute(f'SELECT 1 FROM {table} WHERE id=?', (ident,)).fetchone(): raise ApiError(404, 'Không tìm thấy dữ liệu')
                conn.execute(f'UPDATE {table} SET '+','.join(f'{f}=?' for f in fields)+' WHERE id=?', (*values, ident))
            else:
                ident = conn.execute(f'INSERT INTO {table}('+','.join(fields)+') VALUES('+','.join('?' for _ in fields)+')', values).lastrowid
            return {'id': int(ident), 'ok': True}
        raise ApiError(404, 'Không tìm thấy API')

    def static(self, path):
        match = re.fullmatch(r'/uploads/(upload_[a-f0-9]{64})\.jpg', path)
        if match:
            with connection() as conn:
                row = conn.execute('SELECT data FROM uploaded_images WHERE id=?',(match[1],)).fetchone()
            if not row: raise ApiError(404, 'Không tìm thấy ảnh')
            self.send_response(200)
            self.send_header('Content-Type','image/jpeg')
            self.send_header('X-Content-Type-Options','nosniff')
            self.send_header('Cache-Control','public, max-age=31536000, immutable')
            self.send_header('Content-Length',str(len(row['data'])))
            self.end_headers(); self.wfile.write(row['data']); return
        routes = {'/': ROOT/'web/index.html', '/admin': ROOT/'web/index.html', '/app.js': ROOT/'web/app.js', '/style.css': ROOT/'web/style.css'}
        file = routes.get(path)
        if path.startswith('/images/'):
            name = path.rsplit('/', 1)[-1]
            if name.split('.')[0] in IMAGES and name == Path(name).name:
                candidate = ROOT.parent/'img'/name
                if candidate.suffix in ('.png', '.jpg'): file = candidate
        if not file or not file.is_file(): raise ApiError(404, 'Không tìm thấy tài nguyên')
        payload = file.read_bytes()
        self.send_response(200)
        self.send_header('Content-Type', (mimetypes.guess_type(file.name)[0] or 'application/octet-stream') + ('; charset=utf-8' if file.suffix in ('.html', '.js', '.css') else ''))
        self.send_header('Content-Security-Policy', "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; frame-ancestors 'none'")
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8080)
    parser.add_argument('--db', type=Path, help='Use a SQLite database for isolated tests or legacy data')
    args = parser.parse_args()
    if args.db is None:
        os.environ['FOODNOW_STORAGE'] = 'firestore'
    initialize(args.db)
    mobile_features.start_push_worker(sys.modules[__name__])
    print(f'FoodNow: http://localhost:{args.port} | Storage: {"Firestore" if _firestore_store else DB}', flush=True)
    print(f'Initial admin credentials: {DB.parent / "admin-credentials.txt"}', flush=True)
    ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()
