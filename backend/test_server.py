"""Integration tests against a real HTTP server and isolated temporary SQLite DB."""
import json
from pathlib import Path
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
import server


class IntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        server.initialize(Path(cls.temp.name) / 'test.db', bootstrap=False)
        with server.connection() as conn:
            conn.execute("INSERT INTO users(email,name,password_hash,role) VALUES(?,?,?,'admin')",
                         ('admin@test.local', 'Admin', server.password_hash('Admin12345')))
        cls.http = server.ThreadingHTTPServer(('127.0.0.1', 0), server.Handler)
        cls.thread = threading.Thread(target=cls.http.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = 'http://127.0.0.1:' + str(cls.http.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()
        cls.thread.join()
        cls.temp.cleanup()

    def setUp(self):
        server.Handler.attempts.clear()

    def call(self, path, method='GET', data=None, token='', expected=200):
        headers = {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token}
        req = urllib.request.Request(self.base + path, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method)
        try:
            response = urllib.request.urlopen(req)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            self.assertEqual(expected, response.status, response.read().decode() if expected != response.status else '')
            raw = response.read()
        return json.loads(raw) if path.startswith('/api/') else raw

    def account(self, name):
        email = name + '@test.local'
        self.call('/api/auth/register', 'POST', {'name': name, 'email': email, 'password': 'Customer123', 'role': 'admin'})
        result = self.call('/api/auth/login', 'POST', {'email': email, 'password': 'Customer123'})
        self.assertEqual('customer', result['user']['role'])
        return result['token']

    def admin(self):
        return self.call('/api/auth/login', 'POST', {'email': 'admin@test.local', 'password': 'Admin12345'})['token']

    def payload(self, key):
        shop = self.call('/api/catalog')['restaurants'][0]
        food = shop['menu'][0]
        return {'clientKey': key, 'restaurantId': shop['id'], 'recipient': 'Test User', 'phone': '0912345678',
                'address': '123 Test Street', 'note': '', 'paymentMethod': 'cod',
                'items': [{'foodId': food['id'], 'quantity': 2}], 'expectedTotal': 2 * food['price'] + 15000}

    def test_auth_profile_password_and_permissions(self):
        token = self.account('auth')
        self.call('/api/admin/users', token=token, expected=403)
        self.call('/api/me', expected=401)
        self.call('/api/auth/register', 'POST', {'name': 'Duplicate', 'email': 'AUTH@test.local', 'password': 'Customer123'}, expected=409)
        self.call('/api/auth/login', 'POST', {'email': 'auth@test.local', 'password': 'Wrong123'}, expected=401)
        user = self.call('/api/me', 'PATCH', {'name': 'Updated', 'phone': '0912345678', 'address': 'New address'}, token)['user']
        self.assertEqual('Updated', user['name'])
        self.call('/api/auth/password', 'POST', {'current': 'wrong', 'replacement': 'Changed123'}, token, expected=400)
        self.call('/api/auth/password', 'POST', {'current': 'Customer123', 'replacement': 'Changed123'}, token)
        self.call('/api/me', token=token, expected=401)
        result = self.call('/api/auth/login', 'POST', {'email': 'auth@test.local', 'password': 'Changed123'})
        self.call('/api/auth/logout', 'POST', {}, result['token'])
        self.call('/api/me', token=result['token'], expected=401)

    def test_order_idempotency_isolation_and_transitions(self):
        token, other, admin = self.account('orders'), self.account('other'), self.admin()
        payload = self.payload('repeat-order')
        first = self.call('/api/orders', 'POST', payload, token)['order']
        again = self.call('/api/orders', 'POST', payload, token)['order']
        self.assertEqual(first['id'], again['id'])
        self.assertEqual(1, len(self.call('/api/orders', token=token)['orders']))
        self.assertEqual([], self.call('/api/orders', token=other)['orders'])
        path = '/api/orders/' + first['id']
        self.call(path, 'PATCH', {'status': 'cancelled'}, other, expected=404)
        self.call(path, 'PATCH', {'status': 'confirmed'}, token, expected=403)
        self.call('/api/admin/orders/' + first['id'], 'PATCH', {'status': 'completed'}, admin, expected=409)
        for status in ['confirmed', 'delivering']:
            self.call('/api/admin/orders/' + first['id'], 'PATCH', {'status': status}, admin)
        self.call(path, 'PATCH', {'status': 'cancelled'}, token, expected=409)
        self.call(path, 'PATCH', {'status': 'completed'}, token)
        self.call(path, 'PATCH', {'status': 'cancelled'}, token, expected=409)
        self.assertGreaterEqual(self.call('/api/admin/summary', token=admin)['completedTotal'], first['total'])

    def test_price_validation_snapshot_and_persistence(self):
        token, admin = self.account('prices'), self.admin()
        payload = self.payload('prices1')
        original = self.call('/api/orders', 'POST', payload, token)['order']
        catalog = self.call('/api/admin/catalog', token=admin)
        food = catalog['foods'][0]
        food['price'] += 1000
        food['name'] = 'Updated food'
        self.call('/api/admin/foods/' + str(food['id']), 'PATCH', food, admin)
        payload['clientKey'] = 'prices2'
        self.call('/api/orders', 'POST', payload, token, expected=409)
        saved = self.call('/api/orders', token=token)['orders'][0]
        self.assertEqual(original['items'], saved['items'])
        self.assertEqual(original['total'], saved['total'])
        # Reinitializing the existing file must not erase stored accounts/orders.
        server.initialize(server.DB, bootstrap=False)
        self.assertEqual(original['id'], self.call('/api/orders', token=token)['orders'][0]['id'])
        food['active'] = 0
        self.call('/api/admin/foods/' + str(food['id']), 'PATCH', food, admin)
        self.assertNotIn(food['id'], [f['id'] for r in self.call('/api/catalog')['restaurants'] for f in r['menu']])
        self.call('/api/orders', 'POST', payload, token, expected=409)
        food['active'] = 1
        self.call('/api/admin/foods/' + str(food['id']), 'PATCH', food, admin)

    def test_cart_validation_and_customer_cancel(self):
        token = self.account('validation')
        payload = self.payload('validation')
        payload['items'][0]['quantity'] = -1
        self.call('/api/orders', 'POST', payload, token, expected=400)
        payload['items'][0]['quantity'] = 2
        payload['paymentMethod'] = []
        self.call('/api/orders', 'POST', payload, token, expected=400)
        payload['paymentMethod'] = 'bank_demo'
        order = self.call('/api/orders', 'POST', payload, token)['order']
        self.assertIn('mô phỏng', order['details'])
        self.call('/api/orders/' + order['id'], 'PATCH', {'status': 'cancelled'}, token)

    def test_admin_catalog_crud_and_static_boundaries(self):
        token = self.admin()
        shop = {'name': 'New shop', 'address': 'Test', 'hours': '08:00–22:00', 'image': 'ic_circlek', 'active': 1}
        rid = self.call('/api/admin/restaurants', 'POST', shop, token)['id']
        food = {'name': 'New food', 'restaurant_id': rid, 'price': 25000, 'image': 'ic_banh_mi', 'active': 1}
        self.call('/api/admin/foods', 'POST', food, token)
        self.assertEqual(1, len(next(r for r in self.call('/api/catalog')['restaurants'] if r['id'] == rid)['menu']))
        shop['active'] = 0
        self.call('/api/admin/restaurants/' + str(rid), 'PATCH', shop, token)
        self.assertNotIn(rid, [r['id'] for r in self.call('/api/catalog')['restaurants']])
        self.assertIn(b'FoodNow', self.call('/'))
        self.call('/data/admin-credentials.txt', expected=404)
        self.call('/../server.py', expected=404)

    def test_delivery_coordinates_validation_and_roundtrip(self):
        token,admin=self.account('map'),self.admin()
        payload=self.payload('map-valid')
        for lat,lng in [(91,106),(10,181),(True,106),(10,None),('10.5',106),(float('nan'),106),(10,float('inf'))]:
            payload.update(deliveryLatitude=lat,deliveryLongitude=lng)
            self.call('/api/orders','POST',payload,token,expected=400)
        payload.update(deliveryLatitude=10.7769,deliveryLongitude=106.7009)
        order=self.call('/api/orders','POST',payload,token)['order']
        self.assertEqual(10.7769,order['delivery_latitude'])
        self.assertEqual(106.7009,order['delivery_longitude'])
        again=self.call('/api/orders','POST',payload,token)['order']
        self.assertEqual(order['id'],again['id'])
        admin_order=next(o for o in self.call('/api/admin/orders',token=admin)['orders'] if o['id']==order['id'])
        self.assertEqual(order['delivery_latitude'],admin_order['delivery_latitude'])
        self.assertIn('10.776900, 106.700900',admin_order['details'])
        payload['clientKey']='map-zero';payload.update(deliveryLatitude=0,deliveryLongitude=0)
        self.assertEqual(0,self.call('/api/orders','POST',payload,token)['order']['delivery_latitude'])
        payload['clientKey']='map-none';payload.update(deliveryLatitude=None,deliveryLongitude=None)
        self.assertIsNone(self.call('/api/orders','POST',payload,token)['order']['delivery_latitude'])

    def test_migration_preserves_legacy_orders(self):
        token=self.account('migration')
        original=self.call('/api/orders','POST',self.payload('legacy'),token)['order']
        source=server.DB
        import sqlite3
        target=Path(self.temp.name)/'legacy.db'
        with server.connection() as conn:
            destination=sqlite3.connect(target)
            conn.backup(destination);destination.close()
        old=sqlite3.connect(target)
        old.execute('ALTER TABLE orders DROP COLUMN delivery_latitude')
        old.execute('ALTER TABLE orders DROP COLUMN delivery_longitude');old.commit();old.close()
        try:
            server.initialize(target,bootstrap=False)
            server.initialize(target,bootstrap=False)
            with server.connection() as conn:
                saved=server.order_json(conn,conn.execute('SELECT * FROM orders WHERE id=?',(original['id'],)).fetchone())
                self.assertEqual(original['total'],saved['total'])
                self.assertEqual(original['items'],saved['items'])
                self.assertIsNone(saved['delivery_latitude'])
        finally:
            server.DB=source

    def test_image_upload_permissions_validation_and_catalog(self):
        import base64,io
        from PIL import Image
        admin=self.admin();customer=self.account('images')
        buffer=io.BytesIO();Image.new('RGBA',(2000,1000),(30,150,80,120)).save(buffer,format='PNG')
        data={'data':base64.b64encode(buffer.getvalue()).decode()}
        self.call('/api/admin/images','POST',data,expected=401)
        self.call('/api/admin/images','POST',data,customer,expected=403)
        self.call('/api/admin/images','POST',{'data':'!bad'},admin,expected=400)
        self.call('/api/admin/images','POST',{'data':base64.b64encode(b'<svg/>').decode()},admin,expected=400)
        uploaded=self.call('/api/admin/images','POST',data,admin)
        self.assertEqual(uploaded,self.call('/api/admin/images','POST',data,admin))
        image=Image.open(io.BytesIO(self.call(uploaded['url'])))
        self.assertEqual('JPEG',image.format);self.assertEqual((1600,800),image.size)
        food=self.call('/api/admin/catalog',token=admin)['foods'][0]
        food['image']=uploaded['image']
        self.call('/api/admin/foods/'+str(food['id']),'PATCH',food,admin)
        shown=next(f for r in self.call('/api/catalog')['restaurants'] for f in r['menu'] if f['id']==food['id'])
        self.assertEqual(uploaded['image'],shown['image'])
        server.initialize(server.DB,bootstrap=False)
        self.assertEqual(self.call(uploaded['url'])[:2],b'\xff\xd8')
        food['image']='upload_'+'0'*64
        self.call('/api/admin/foods/'+str(food['id']),'PATCH',food,admin,expected=400)


if __name__ == '__main__':
    unittest.main(verbosity=2)
