import base64
import io
from unittest.mock import patch
from PIL import Image
import test_server
import server

class MobileFeatureTests(test_server.IntegrationTests):
    def test_delivery_login_requires_authorized_account(self):
        self.account('delivery-login-customer')
        admin=self.admin()
        with server.connection() as conn:
            before=conn.execute('SELECT count(*) FROM sessions').fetchone()[0]
        self.call('/api/auth/delivery-login','POST',{'email':'delivery-login-customer@test.local','password':'Customer123'},expected=403)
        self.call('/api/auth/delivery-login','POST',{'email':'admin@test.local','password':'Wrong123'},expected=401)
        self.call('/api/auth/delivery-login','POST',{'email':'admin@test.local','password':'Admin12345'},expected=403)
        with server.connection() as conn:
            self.assertEqual(before,conn.execute('SELECT count(*) FROM sessions').fetchone()[0])
        self.call('/api/admin/drivers','POST',{'name':'Driver Test','email':'driver@test.local','password':'Driver12345'},expected=401)
        created=self.call('/api/admin/drivers','POST',{'name':'Driver Test','email':'driver@test.local','password':'Driver12345'},admin)
        self.assertEqual('driver',created['user']['role'])
        self.call('/api/auth/login','POST',{'email':'driver@test.local','password':'Driver12345'},expected=403)
        result=self.call('/api/auth/delivery-login','POST',{'email':'driver@test.local','password':'Driver12345'})
        self.assertEqual('driver',result['user']['role'])
        customer=self.account('driver-order-customer')
        order=self.call('/api/orders','POST',self.payload('driver-order'),customer)['order']
        self.assertFalse(any(o['id']==order['id'] for o in self.call('/api/driver/orders',token=result['token'])['orders']))
        self.call('/api/admin/orders/'+order['id'],'PATCH',{'status':'confirmed'},admin)
        self.call('/api/admin/orders/'+order['id'],'PATCH',{'status':'delivering'},admin)
        self.assertTrue(any(o['id']==order['id'] for o in self.call('/api/driver/orders',token=result['token'])['orders']))
        self.call('/api/driver/orders/'+order['id']+'/location','POST',{'latitude':10.77,'longitude':106.7,'accuracy':10.0},result['token'])
        self.call('/api/driver/orders/'+order['id']+'/messages','POST',{'body':'Tôi đang giao hàng','clientKey':'driver-message'},result['token'])
        self.assertEqual(1,len(self.call('/api/orders/'+order['id']+'/messages',token=customer)['messages']))
        self.call('/api/admin/orders',token=result['token'],expected=403)
        self.call('/api/driver/orders',token=admin,expected=403)
        self.call('/api/auth/logout','POST',{},result['token'])
        self.call('/api/driver/orders',token=result['token'],expected=401)

    def test_firebase_verified_identity_and_no_email_autolink(self):
        claims={'uid':'firebase-test-uid','email':'google-new@example.com','email_verified':True,'name':'Google Test','firebase':{'sign_in_provider':'google.com'}}
        with patch('mobile_features.firebase_app'), patch('firebase_admin.auth.verify_id_token',return_value=claims) as verify:
            first=self.call('/api/auth/firebase','POST',{'idToken':'test-signed-token'})
            verify.assert_called_with('test-signed-token',check_revoked=True)
            self.assertEqual('customer',first['user']['role'])
            again=self.call('/api/auth/firebase','POST',{'idToken':'test-signed-token'})
            self.assertEqual(first['user']['id'],again['user']['id'])
            self.assertEqual(first['user']['id'],self.call('/api/me',token=again['token'])['user']['id'])
            claims['email_verified']=False
            self.call('/api/auth/firebase','POST',{'idToken':'test-signed-token'},expected=403)
            claims['email_verified']=True
            claims['uid']='different-identity'
            self.call('/api/auth/firebase','POST',{'idToken':'test-signed-token'},expected=409)
            verify.side_effect=ValueError('Invalid signature')
            self.call('/api/auth/firebase','POST',{'idToken':'tampered'},expected=401)

    def test_messages_private_idempotent_and_notifications(self):
        customer=self.account('chatcustomer');other=self.account('chatother');admin=self.admin()
        order=self.call('/api/orders','POST',self.payload('chat-order'),customer)['order'];path='/api/orders/'+order['id']+'/messages';adminpath='/api/admin/orders/'+order['id']+'/messages'
        self.call(path,token=other,expected=404)
        self.call(adminpath,token=customer,expected=403)
        for _ in range(2):self.call(path,'POST',{'body':'Giao tại cổng chính <b>test</b>','clientKey':'message-1'},customer)
        self.assertEqual(1,len(self.call(adminpath,token=admin)['messages']))
        self.call(adminpath,'POST',{'body':'Tôi đã nhận ghi chú','clientKey':'reply-1'},admin)
        self.assertEqual(2,len(self.call(path,token=customer)['messages']))
        self.assertEqual(1,len(self.call('/api/notifications',token=customer)['events']))
        self.assertEqual([],self.call('/api/notifications',token=other)['events'])
        self.call(path,'POST',{'body':' ','clientKey':'bad'},customer,expected=400)
    def test_delivery_position_permissions_validation_and_persistence(self):
        customer=self.account('gpscustomer');other=self.account('gpsother');admin=self.admin()
        order=self.call('/api/orders','POST',self.payload('gps-order'),customer)['order'];path='/api/orders/'+order['id']+'/location';adminpath='/api/admin/orders/'+order['id']
        self.assertIsNone(self.call(path,token=customer)['location'])
        point={'latitude':10.77,'longitude':106.7,'accuracy':12.5}
        self.call(path,'POST',point,customer,expected=403)
        self.call(path,token=other,expected=404)
        self.call(adminpath+'/location','POST',point,admin,expected=409)
        for status in ['confirmed','delivering']:self.call(adminpath,'PATCH',{'status':status},admin)
        for invalid in [dict(point,latitude=True),dict(point,longitude=181),dict(point,accuracy=-1),dict(point,latitude=float('nan'))]:self.call(adminpath+'/location','POST',invalid,admin,expected=400)
        self.call(adminpath+'/location','POST',point,admin)
        self.assertEqual(10.77,self.call(path,token=customer)['location']['latitude'])
        server.initialize(server.DB,bootstrap=False)
        self.assertEqual(106.7,self.call(path,token=customer)['location']['longitude'])
        self.call(adminpath,'PATCH',{'status':'completed'},admin)
        self.call(adminpath+'/location','POST',point,admin,expected=409)
    def test_avatar_and_firebase_config_guard(self):
        token=self.account('avatarcustomer');image=Image.new('RGB',(80,80),'green');buffer=io.BytesIO();image.save(buffer,format='PNG');data={'data':base64.b64encode(buffer.getvalue()).decode()}
        self.call('/api/me/avatar','POST',data,expected=401)
        result=self.call('/api/me/avatar','POST',data,token)
        self.assertEqual(result['image'],self.call('/api/me',token=token)['user']['avatar'])
        with patch.dict('os.environ',{'GOOGLE_APPLICATION_CREDENTIALS':''}):self.call('/api/auth/firebase','POST',{'idToken':'fake'},expected=503)
    def test_device_token_owned_by_current_session(self):
        token=self.account('notifycustomer')
        self.call('/api/devices','POST',{'token':'device-token'},token)
        with server.connection() as conn:self.assertEqual(1,conn.execute('SELECT COUNT(*) FROM device_tokens WHERE token=?',('device-token',)).fetchone()[0])
        self.call('/api/auth/logout','POST',{},token)
        with server.connection() as conn:self.assertEqual(0,conn.execute('SELECT COUNT(*) FROM device_tokens d JOIN sessions s ON s.token_hash=d.session_hash WHERE d.token=?',('device-token',)).fetchone()[0])

if __name__=='__main__':
    import unittest
    unittest.main()
