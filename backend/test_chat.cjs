const {chromium}=require('playwright');const assert=require('node:assert/strict');
(async()=>{const browser=await chromium.launch({headless:true,channel:'msedge'});try{
 const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const base=process.env.FOODNOW_TEST_URL||'http://127.0.0.1:8081',email='chat'+Date.now()+'@test.local';
 const request=async(path,body,token,method=body?'POST':'GET')=>{const r=await page.request.fetch(base+'/api'+path,{method,...(body?{data:body}:{}),headers:token?{Authorization:'Bearer '+token}:{}});assert.equal(r.status(),200,await r.text());return r.json();};
 await request('/auth/register',{name:'Chat QA',email,password:'Customer123'});
 const token=(await request('/auth/login',{email,password:'Customer123'})).token;
 const shop=(await request('/catalog')).restaurants[0],food=shop.menu[0];
 const order=(await request('/orders',{clientKey:'web-chat',restaurantId:shop.id,recipient:'Chat QA',phone:'0900000000',address:'Demo',note:'',paymentMethod:'cod',items:[{foodId:food.id,quantity:1}],expectedTotal:food.price+15000},token)).order;
 await request('/orders/'+order.id+'/messages',{clientKey:'c1',body:'Xin giao tại cổng. <img src=x onerror=alert(1)>'},token);
 await page.goto(base);await page.getByLabel('Email',{exact:true}).fill('admin@test.local');await page.getByLabel('Mật khẩu',{exact:true}).fill('Admin12345');await page.getByRole('button',{name:'Đăng nhập quản trị'}).click();
 await page.getByRole('button',{name:'Chi tiết',exact:true}).first().click();await page.getByRole('button',{name:'Nhắn tin với khách',exact:true}).click();
 await page.getByText('Xin giao tại cổng. <img src=x onerror=alert(1)>',{exact:true}).waitFor();assert.equal(await page.locator('.chat-list img').count(),0);
 await page.getByLabel('Tin nhắn',{exact:true}).fill('Đã nhận ghi chú.');await page.getByRole('button',{name:'Gửi tin nhắn',exact:true}).click();await page.getByText('Đã nhận ghi chú.',{exact:true}).waitFor();
 const messages=(await request('/orders/'+order.id+'/messages',null,token)).messages;assert.equal(messages.length,2);assert.equal(messages[1].role,'admin');
 assert.equal((await request('/notifications',null,token)).events.length,1);assert.deepEqual(errors,[]);
 await page.getByRole('button',{name:'Đóng',exact:true}).click();console.log('PASS web chat: customer message, escaped HTML, admin reply, customer readback, notification event, no JS errors');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
