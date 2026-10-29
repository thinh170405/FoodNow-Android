// Requires Playwright and the isolated run_test_server.py fixture on port 8081.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}}), errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('dialog',d=>d.accept());
  await page.goto('http://127.0.0.1:8081');
  await page.getByLabel('Email',{exact:true}).fill('admin@test.local');
  await page.getByLabel('Mật khẩu',{exact:true}).fill('Admin12345');
  await page.getByRole('button',{name:'Đăng nhập quản trị'}).click();
  await page.locator('#dashboard').waitFor({state:'visible'});
  await page.getByRole('button',{name:'Khách hàng',exact:true}).click();
  await page.getByRole('button',{name:'+ Tạo tài khoản tài xế'}).click();
  await page.getByLabel('Họ tên tài xế').fill('Tài xế kiểm thử');
  await page.getByLabel('Email đăng nhập').fill('driver-browser@test.local');
  await page.getByLabel('Mật khẩu (8–128 ký tự, chữ và số)').fill('Driver12345');
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await page.getByRole('cell',{name:'driver-browser@test.local'}).waitFor();
  const driverResponse=await page.request.post('http://127.0.0.1:8081/api/auth/delivery-login',{data:{email:'driver-browser@test.local',password:'Driver12345'}});
  assert.equal(driverResponse.status(),200,await driverResponse.text());
  assert.equal((await driverResponse.json()).user.role,'driver');
  if(process.env.EXPECT_ANDROID_ORDER==='1') {
    const androidOrder=page.getByRole('row').filter({hasText:'Android Test'});
    await androidOrder.locator('.status.cancelled').waitFor();
    console.log('PASS: order placed and cancelled by Android is visible on admin web.');
  }
  await page.getByRole('button',{name:'Thực đơn & nhà hàng',exact:true}).click();
  await page.getByRole('button',{name:'+ Món ăn',exact:true}).waitFor({state:'visible'});
  await page.getByRole('button',{name:'+ Món ăn',exact:true}).click();
  await page.getByLabel('Tên',{exact:true}).fill('Món kiểm thử trình duyệt');
  await page.getByLabel('Giá (VND)',{exact:true}).fill('27000');
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await page.getByRole('cell',{name:'Món kiểm thử trình duyệt',exact:true}).waitFor();
  const api=async(url,method='GET',body=null,token='')=>{
    const res=await page.request.fetch('http://127.0.0.1:8081/api'+url,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},...(body?{data:body}:{})});
    assert.equal(res.status(),200,await res.text());return res.json();
  };
  const catalog=await api('/catalog'),food=catalog.restaurants[0].menu.find(f=>f.name==='Món kiểm thử trình duyệt');assert.equal(food.price,27000);
  const email='browser'+Date.now()+'@test.local';
  await api('/auth/register','POST',{name:'Browser QA',email,password:'Customer123'});
  const {token}=await api('/auth/login','POST',{email,password:'Customer123'});
  const {order}=await api('/orders','POST',{clientKey:'web-'+Date.now(),restaurantId:food.restaurant_id,recipient:'Browser QA',phone:'0912345678',address:'123 Test',deliveryLatitude:10.7769,deliveryLongitude:106.7009,note:'Web integration test',paymentMethod:'cod',items:[{foodId:food.id,quantity:1}],expectedTotal:42000},token);
  await page.getByRole('button',{name:'Đơn hàng',exact:true}).click();
  await page.getByRole('cell',{name:'Browser QA'}).waitFor();
  let row=page.getByRole('row').filter({hasText:'Browser QA'});
  await row.getByRole('button',{name:'Chi tiết',exact:true}).click();
  const mapLink=page.getByRole('link',{name:'Mở điểm giao hàng trên bản đồ'});
  await mapLink.waitFor();
  assert.equal(await mapLink.getAttribute('href'),'https://www.openstreetmap.org/?mlat=10.7769&mlon=106.7009#map=17/10.7769/106.7009');
  await page.screenshot({path:path.join(__dirname,'data','order-map-link.png'),fullPage:true});
  await page.getByRole('button',{name:'Đóng',exact:true}).click();
  await row.getByRole('button',{name:'Đã xác nhận',exact:true}).click();
  await row.getByRole('button',{name:'Đang giao',exact:true}).waitFor();
  await row.getByRole('button',{name:'Đang giao',exact:true}).click();
  await row.getByRole('button',{name:'Hoàn thành',exact:true}).waitFor();
  await row.getByRole('button',{name:'Hoàn thành',exact:true}).click();
  await row.locator('.status.completed').waitFor();
  assert.equal((await api('/orders','GET',null,token)).orders.find(o=>o.id===order.id).status,'completed');
  await page.screenshot({path:path.join(__dirname,'data','web-desktop.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
  await page.screenshot({path:path.join(__dirname,'data','web-mobile.png'),fullPage:true});
  assert.deepEqual(errors,[]);
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();
  await page.locator('#loginPage').waitFor({state:'visible'});
  console.log('PASS: admin login, add food, catalog API sync, order lifecycle, customer status sync, responsive layout, logout; no browser JS errors.');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
