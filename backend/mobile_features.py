"""Order conversations, delivery position and authenticated Firebase bridge."""
import hashlib
import math
import os
import re
import secrets
import time
import threading


def initialize(conn):
    conn.executescript('''
    CREATE TABLE IF NOT EXISTS order_messages(
      id INTEGER PRIMARY KEY, order_id TEXT NOT NULL REFERENCES orders(id),
      sender_id INTEGER NOT NULL REFERENCES users(id), body TEXT NOT NULL,
      client_key TEXT NOT NULL, created_at INTEGER NOT NULL,
      UNIQUE(sender_id,client_key));
    CREATE INDEX IF NOT EXISTS messages_order ON order_messages(order_id,id);
    CREATE TABLE IF NOT EXISTS delivery_positions(
      order_id TEXT PRIMARY KEY REFERENCES orders(id), latitude REAL NOT NULL,
      longitude REAL NOT NULL, accuracy REAL NOT NULL, updated_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS device_tokens(
      token TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
      session_hash TEXT NOT NULL, updated_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS firebase_accounts(
      uid TEXT PRIMARY KEY, user_id INTEGER UNIQUE NOT NULL REFERENCES users(id));
    CREATE TABLE IF NOT EXISTS notification_events(
      id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id),
      order_id TEXT NOT NULL REFERENCES orders(id),title TEXT NOT NULL,body TEXT NOT NULL,
      created_at INTEGER NOT NULL,sent INTEGER NOT NULL DEFAULT 0);
    ''')
    columns={r['name'] for r in conn.execute('PRAGMA table_info(users)')}
    if 'avatar' not in columns:conn.execute("ALTER TABLE users ADD COLUMN avatar TEXT NOT NULL DEFAULT ''")


_firebase_lock=threading.Lock()
def firebase_app():
    if not os.environ.get('GOOGLE_APPLICATION_CREDENTIALS'):
        raise RuntimeError('Firebase Admin chưa được cấu hình trên máy chủ')
    import firebase_admin
    with _firebase_lock:
        try:return firebase_admin.get_app()
        except ValueError:return firebase_admin.initialize_app()


def firebase_login(conn,data,server):
    try:
        firebase_app()
    except Exception:
        raise server.ApiError(503,'Máy chủ chưa cấu hình Firebase Admin')
    raw=server.text(data,'idToken',10000)
    try:
        from firebase_admin import auth
        claims=auth.verify_id_token(raw,check_revoked=True)
    except Exception:
        raise server.ApiError(401,'Phiên Firebase không hợp lệ hoặc đã hết hạn')
    email=claims.get('email','').strip().lower()
    if not claims.get('email_verified') or not email:
        raise server.ApiError(403,'Tài khoản cần email đã xác minh')
    if claims.get('firebase',{}).get('sign_in_provider') not in ('google.com','facebook.com'):
        raise server.ApiError(403,'Nhà cung cấp đăng nhập chưa được hỗ trợ')
    row=conn.execute('SELECT user_id FROM firebase_accounts WHERE uid=?',(claims['uid'],)).fetchone()
    if row:
        user=conn.execute('SELECT * FROM users WHERE id=?',(row['user_id'],)).fetchone()
    else:
        # Never silently link an existing local/admin identity by email.
        if conn.execute('SELECT 1 FROM users WHERE email=?',(email,)).fetchone():
            raise server.ApiError(409,'Email đã có tài khoản FoodNow. Hãy đăng nhập bằng mật khẩu hiện tại.')
        name=str(claims.get('name') or email.split('@')[0])[:100]
        uid=conn.execute('INSERT INTO users(email,name,password_hash) VALUES(?,?,?)',
                         (email,name,server.password_hash(secrets.token_urlsafe(40)))).lastrowid
        conn.execute('INSERT INTO firebase_accounts VALUES(?,?)',(claims['uid'],uid))
        user=conn.execute('SELECT * FROM users WHERE id=?',(uid,)).fetchone()
    token=secrets.token_urlsafe(32)
    conn.execute('INSERT INTO sessions VALUES(?,?,?)',(hashlib.sha256(token.encode()).hexdigest(),user['id'],int(time.time())+7*86400))
    return {'token':token,'user':server.public_user(user)}


def event(conn,user_id,order_id,title,body):
    conn.execute('INSERT INTO notification_events(user_id,order_id,title,body,created_at) VALUES(?,?,?,?,?)',
                 (user_id,order_id,title,body,int(time.time())))


def route(handler,conn,path,data,user,server):
    method=handler.command
    if path=='/api/devices' and method=='POST':
        token=server.text(data,'token',4096)
        session=handler.headers.get('Authorization','').removeprefix('Bearer ')
        conn.execute('INSERT INTO device_tokens VALUES(?,?,?,?) ON CONFLICT(token) DO UPDATE SET user_id=excluded.user_id,session_hash=excluded.session_hash,updated_at=excluded.updated_at',
                     (token,user['id'],hashlib.sha256(session.encode()).hexdigest(),int(time.time())))
        return {'ok':True}
    if path=='/api/notifications' and method=='GET':
        return {'events':[dict(r) for r in conn.execute('SELECT id,order_id,title,body,created_at FROM notification_events WHERE user_id=? ORDER BY id DESC LIMIT 50',(user['id'],))]}
    m=re.fullmatch(r'/api/(admin/|driver/)?orders/([a-zA-Z0-9]+)/(messages|location)',path)
    if not m:return None
    order=conn.execute('SELECT * FROM orders WHERE id=?',(m[2],)).fetchone()
    if (not order or (not m[1] and order['user_id']!=user['id'])
            or (m[1]=='driver/' and order['status']!='delivering')):
        raise server.ApiError(404,'Không tìm thấy đơn hàng')
    if m[3]=='messages':
        if method=='GET':
            return {'messages':[dict(r) for r in conn.execute('''SELECT m.id,m.body,m.created_at,u.name,u.role
              FROM order_messages m JOIN users u ON u.id=m.sender_id WHERE order_id=? ORDER BY m.id DESC LIMIT 200''',(order['id'],))][::-1]}
        if method=='POST':
            handler.rate_limit()
            body=server.text(data,'body',2000);key=server.text(data,'clientKey',100)
            old=conn.execute('SELECT order_id FROM order_messages WHERE sender_id=? AND client_key=?',(user['id'],key)).fetchone()
            if old:
                if old['order_id']!=order['id']:raise server.ApiError(409,'Mã tin nhắn đã được sử dụng')
                return {'ok':True}
            conn.execute('INSERT INTO order_messages(order_id,sender_id,body,client_key,created_at) VALUES(?,?,?,?,?)',
                         (order['id'],user['id'],body,key,int(time.time())))
            if m[1]:event(conn,order['user_id'],order['id'],'Tin nhắn mới','Bạn có tin nhắn mới về đơn hàng.')
            return {'ok':True}
    if m[3]=='location':
        if method=='GET':
            pos=conn.execute('SELECT latitude,longitude,accuracy,updated_at FROM delivery_positions WHERE order_id=?',(order['id'],)).fetchone()
            return {'location':dict(pos) if pos else None,'status':order['status']}
        if method=='POST':
            if not m[1]:raise server.ApiError(403,'Chỉ người giao hàng được cập nhật')
            if order['status']!='delivering':raise server.ApiError(409,'Chỉ chia sẻ vị trí khi đơn đang giao')
            lat,lng,accuracy=data.get('latitude'),data.get('longitude'),data.get('accuracy')
            if any(type(v) not in (int,float) or not math.isfinite(v) for v in (lat,lng,accuracy)) or not(-90<=lat<=90 and -180<=lng<=180 and 0<=accuracy<=10000):
                raise server.ApiError(400,'Vị trí hoặc độ chính xác không hợp lệ')
            conn.execute('INSERT INTO delivery_positions VALUES(?,?,?,?,?) ON CONFLICT(order_id) DO UPDATE SET latitude=excluded.latitude,longitude=excluded.longitude,accuracy=excluded.accuracy,updated_at=excluded.updated_at',
                         (order['id'],lat,lng,accuracy,int(time.time())))
            return {'ok':True}
    raise server.ApiError(405,'Phương thức không hỗ trợ')


def start_push_worker(server):
    """Send only committed events; revoked sessions cannot receive pushes."""
    if not os.environ.get('GOOGLE_APPLICATION_CREDENTIALS'):return
    def work():
        while True:
            try:
                firebase_app()
                from firebase_admin import messaging
                with server.connection() as conn:
                    events=[dict(r) for r in conn.execute('SELECT * FROM notification_events WHERE sent=0 ORDER BY id LIMIT 20')]
                for e in events:
                    with server.connection() as conn:
                        tokens=[r['token'] for r in conn.execute('SELECT d.token FROM device_tokens d JOIN sessions s ON s.token_hash=d.session_hash WHERE d.user_id=? AND s.expires>?',(e['user_id'],int(time.time())))]
                    for token in tokens:
                        try:
                            messaging.send(messaging.Message(token=token,data={'title':e['title'],'body':e['body'],'orderId':e['order_id'],'eventId':str(e['id']),'userId':str(e['user_id'])}))
                        except messaging.UnregisteredError:
                            with server.connection() as conn:conn.execute('DELETE FROM device_tokens WHERE token=?',(token,))
                    with server.connection() as conn:conn.execute('UPDATE notification_events SET sent=1 WHERE id=?',(e['id'],))
            except Exception as exc:
                print('FCM delivery pending:',type(exc).__name__,flush=True)
            time.sleep(5)
    threading.Thread(target=work,daemon=True,name='foodnow-fcm').start()
