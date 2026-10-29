'use strict';
const $=id=>document.getElementById(id);
let token=sessionStorage.getItem('foodnow_admin')||'', page='orders', catalog={restaurants:[],foods:[]}, orders=[], users=[], editing=null;
const labels={pending:'Chờ xác nhận',confirmed:'Đã xác nhận',delivering:'Đang giao',completed:'Hoàn thành',cancelled:'Đã hủy'};
const next={pending:['confirmed','cancelled'],confirmed:['delivering','cancelled'],delivering:['completed'],completed:[],cancelled:[]};
const images=['ic_black_coffee','ic_banh_mi','ic_milk_tea','ic_circlek','ic_ministop','ic_seveneleven','ic_vinmart','cover_menu_1'];
const imageUrl=key=>/^upload_[a-f0-9]{64}$/.test(key)?'/uploads/'+key+'.jpg':'/images/'+key+(key==='ic_vinmart'?'.jpg':'.png');
let pendingImage=null;
function foodImagePicker(){
 const selection=$('editorFields').querySelector('[name=image]');
 const preview=el('img',undefined,'image-preview');preview.alt='Xem trước ảnh món ăn';preview.src=imageUrl(selection.value);
 const label=el('label','Chọn ảnh từ máy (JPG, PNG, WebP · tối đa 5 MB)'),input=el('input');input.type='file';input.accept='image/jpeg,image/png,image/webp';label.append(input);
 const owner=editing;let version=0;
 selection.onchange=()=>{version++;pendingImage=null;input.value='';preview.src=imageUrl(selection.value);};
 input.onchange=async()=>{
  const current=++version,file=input.files[0];pendingImage=null;
  if(!file){preview.src=imageUrl(selection.value);return;}
  if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>5*1024*1024){input.value='';preview.src=imageUrl(selection.value);$('editorError').textContent='Chọn JPG, PNG hoặc WebP tối đa 5 MB.';return;}
  const submit=$('editorForm').querySelector('[type=submit]');submit.disabled=true;$('editorError').textContent='';
  try{const url=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=()=>reject(Error('Không đọc được tệp ảnh'));reader.readAsDataURL(file);});if(owner!==editing||current!==version)return;pendingImage=url.split(',')[1];preview.src=url;}
  catch(error){if(owner===editing)$('editorError').textContent=error.message;}
  finally{if(owner===editing)submit.disabled=false;}
 };
 $('editorFields').append(label,preview);
}
const money=value=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value);
const el=(tag,text,cls)=>{const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(cls)node.className=cls;return node;};
function notice(message){$('notice').textContent=message;$('notice').hidden=false;setTimeout(()=>$('notice').hidden=true,7000);}
async function api(path,method='GET',body){const response=await fetch('/api'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});const result=await response.json();if(!response.ok){if(response.status===401){token='';sessionStorage.removeItem('foodnow_admin');showLogin();}throw Error(result.error||'Không thể kết nối');}return result;}
function showLogin(){$('loginPage').hidden=false;$('dashboard').hidden=true;}
async function enter(){const {user}=await api('/me');if(user.role!=='admin')throw Error('Tài khoản này không có quyền quản trị');$('adminName').textContent=user.name;$('loginPage').hidden=true;$('dashboard').hidden=false;await refresh();}
$('loginForm').onsubmit=async e=>{e.preventDefault();const button=e.submitter;button.disabled=true;try{const form=new FormData(e.target),data=await api('/auth/login','POST',Object.fromEntries(form));if(data.user.role!=='admin'){await fetch('/api/auth/logout',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+data.token},body:'{}'});throw Error('Chỉ quản trị viên được đăng nhập tại đây');}token=data.token;sessionStorage.setItem('foodnow_admin',token);e.target.reset();await enter();}catch(error){notice(error.message);}finally{button.disabled=false;}};
$('logoutButton').onclick=async()=>{try{await api('/auth/logout','POST',{});}catch(error){notice(error.message);}finally{token='';sessionStorage.removeItem('foodnow_admin');showLogin();}};
document.querySelectorAll('[data-page]').forEach(button=>button.onclick=()=>{page=button.dataset.page;document.querySelectorAll('[data-page]').forEach(b=>b.classList.toggle('selected',b===button));$('search').value='';refresh().catch(e=>notice(e.message));});
$('refreshButton').onclick=()=>refresh().catch(e=>notice(e.message));
$('search').oninput=render;$('statusFilter').onchange=render;
let refreshing=false;
async function refresh(){if(refreshing)return;refreshing=true;$('refreshButton').disabled=true;try{const summary=await api('/admin/summary');$('stats').replaceChildren();for(const [label,value] of [['ĐƠN HÀNG',summary.orders],['CHỜ XÁC NHẬN',summary.pending],['KHÁCH HÀNG',summary.users],['GIÁ TRỊ ĐƠN HOÀN THÀNH',money(summary.completedTotal)]]){const card=el('div',undefined,'stat');card.append(el('span',label),el('strong',value));$('stats').append(card);}if(page==='orders')orders=(await api('/admin/orders')).orders;if(page==='catalog')catalog=await api('/admin/catalog');if(page==='users')users=(await api('/admin/users')).users;render();}finally{refreshing=false;$('refreshButton').disabled=false;}}
function table(headers){const wrap=el('div',undefined,'table-wrap'),table=el('table'),head=el('thead'),row=el('tr'),body=el('tbody');headers.forEach(h=>row.append(el('th',h)));head.append(row);table.append(head,body);wrap.append(table);$('content').append(wrap);return body;}
function button(label,action,cls='secondary'){const b=el('button',label,cls);b.type='button';b.onclick=action;return b;}
function normalize(text){return String(text).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d');}
function render(){const titles={orders:['Đơn hàng','Đơn hàng mới nhất','Đơn hàng từ ứng dụng Android. Tự làm mới mỗi 15 giây.'],catalog:['Thực đơn & nhà hàng','Quản lý thực đơn','Thay đổi được áp dụng khi khách tải lại thực đơn.'],users:['Tài khoản','Khách hàng và tài xế','Quản trị viên cấp tài khoản tài xế tại đây.']};const title=titles[page];$('pageTitle').textContent=title[0];$('sectionTitle').textContent=title[1];$('sectionSubtitle').textContent=title[2];$('catalogActions').hidden=page!=='catalog';$('driverActions').hidden=page!=='users';$('statusFilter').hidden=page!=='orders';$('content').replaceChildren();const query=normalize($('search').value);let count=0;
 if(page==='orders'){const body=table(['Đơn / thời gian','Khách hàng','Tổng tiền','Trạng thái','Thao tác']);for(const order of orders){if(!normalize(order.details).includes(query)||($('statusFilter').value&&order.status!==$('statusFilter').value))continue;count++;const row=el('tr'),identity=el('td','#'+order.id.slice(0,8));identity.append(el('small',new Date(order.created_at*1000).toLocaleString('vi-VN')),el('small',order.restaurant_name));const customer=el('td',order.recipient);customer.append(el('small',order.phone));const status=el('td');status.append(el('span',labels[order.status],'status '+order.status));const actions=el('td'),group=el('div',undefined,'actions');group.append(button('Chi tiết',()=>details(order)));for(const state of next[order.status]||[]){group.append(button(labels[state],async()=>{if(!confirm('Chuyển đơn sang '+labels[state]+'?'))return;try{await api('/admin/orders/'+order.id,'PATCH',{status:state});await refresh();}catch(e){notice(e.message);}}));}actions.append(group);row.append(identity,customer,el('td',money(order.total)),status,actions);body.append(row);}}
 if(page==='catalog'){const body=table(['Loại / ảnh','Tên','Thông tin','Hiển thị','']);for(const type of ['restaurants','foods'])for(const item of catalog[type]){if(!normalize(item.name+' '+(item.address||'')).includes(query))continue;count++;const row=el('tr'),imageCell=el('td'),image=el('img');image.src=imageUrl(item.image);image.alt='';imageCell.append(image,el('span',type==='foods'?'Món ăn':'Nhà hàng'));const info=type==='foods'?money(item.price)+' · '+(catalog.restaurants.find(r=>r.id===item.restaurant_id)?.name||''):item.address+' · '+item.hours;const action=el('td');action.append(button('Chỉnh sửa',()=>edit(type,item)));row.append(imageCell,el('td',item.name),el('td',info),el('td',item.active?'Đang hiển thị':'Đã ẩn'),action);body.append(row);}}
 if(page==='users'){const body=table(['Họ tên','Email','Điện thoại','Địa chỉ','Vai trò']);for(const user of users){if(!normalize(user.name+' '+user.email).includes(query))continue;count++;const row=el('tr');for(const field of ['name','email','phone','address','role'])row.append(el('td',user[field]||'—'));body.append(row);}}
 if(!count)$('content').append(el('div','Chưa có dữ liệu phù hợp.', 'empty'));
}
function field(name,label,value='',options=null,type='text'){const wrapper=el('label',label),input=el(options?'select':'input');input.name=name;if(options){for(const [v,t] of options){const option=el('option',t);option.value=v;input.append(option);}}else input.type=type;input.value=value;input.required=true;if(type==='number'){input.min='1000';input.max='10000000';input.step='1000';}wrapper.append(input);$('editorFields').append(wrapper);}
function openEditor(title){pendingImage=null;$('editorForm').querySelector('[type=submit]').disabled=false;$('editorTitle').textContent=title;$('editorError').textContent='';$('editorFields').replaceChildren();$('editorForm').querySelector('[type=submit]').hidden=false;$('editor').showModal();}
function edit(type,item={}){editing={type,id:item.id};openEditor(item.id?'Chỉnh sửa thông tin':type==='foods'?'Thêm món ăn':'Thêm nhà hàng');field('name','Tên',item.name||'');if(type==='foods'){field('restaurant_id','Nhà hàng',item.restaurant_id||catalog.restaurants[0]?.id,catalog.restaurants.map(r=>[r.id,r.name]));field('price','Giá (VND)',item.price||25000,null,'number');}else{field('address','Địa chỉ',item.address||'');field('hours','Giờ mở cửa',item.hours||'06:00 – 23:00');}field('image','Ảnh minh họa',item.image||(type==='foods'?'ic_banh_mi':'ic_circlek'),[...images.map(i=>[i,i]),...(/^upload_[a-f0-9]{64}$/.test(item.image)?[[item.image,'Ảnh đã tải lên']]:[])]);if(type==='foods')foodImagePicker();field('active','Hiển thị',item.active??1,[[1,'Đang hiển thị / bán'],[0,'Ẩn / ngừng bán']]);}
$('addRestaurant').onclick=()=>edit('restaurants');$('addFood').onclick=()=>catalog.restaurants.length?edit('foods'):notice('Thêm nhà hàng trước.');
$('addDriver').onclick=()=>{editing={type:'driver'};openEditor('Tạo tài khoản tài xế');field('name','Họ tên tài xế');field('email','Email đăng nhập','',null,'email');field('password','Mật khẩu (8–128 ký tự, chữ và số)','',null,'password');};
$('passwordButton').onclick=()=>{editing={type:'password'};openEditor('Đổi mật khẩu quản trị');field('current','Mật khẩu hiện tại','',null,'password');field('replacement','Mật khẩu mới (8–128 ký tự, chữ và số)','',null,'password');field('repeat','Nhập lại mật khẩu','',null,'password');};
function details(order){editing=null;openEditor('Chi tiết đơn #'+order.id.slice(0,8));$('editorFields').append(el('pre','Trạng thái: '+labels[order.status]+'\n\n'+order.details));
 $('editorFields').append(button('Nhắn tin với khách',()=>openChat(order)));
 const lat=order.delivery_latitude,lng=order.delivery_longitude;
 if(Number.isFinite(lat)&&Number.isFinite(lng)&&Math.abs(lat)<=90&&Math.abs(lng)<=180){const link=el('a','Mở điểm giao hàng trên bản đồ');link.href='https://www.openstreetmap.org/?mlat='+lat+'&mlon='+lng+'#map=17/'+lat+'/'+lng;link.target='_blank';link.rel='noopener noreferrer';$('editorFields').append(link);}
 $('editorForm').querySelector('[type=submit]').hidden=true;}
$('closeEditor').onclick=()=>$('editor').close();
$('editorForm').onsubmit=async e=>{e.preventDefault();if(!editing)return;const target=editing,imageData=pendingImage;const submit=e.submitter;submit.disabled=true;try{const data=Object.fromEntries(new FormData(e.target));if(target.type==='password'){if(data.replacement!==data.repeat)throw Error('Mật khẩu nhập lại không khớp');await api('/auth/password','POST',data);token='';sessionStorage.removeItem('foodnow_admin');$('editor').close();showLogin();notice('Đã đổi mật khẩu. Hãy đăng nhập lại.');return;}if(target.type==='driver'){await api('/admin/drivers','POST',data);$('editor').close();await refresh();notice('Đã tạo tài khoản tài xế.');return;}data.active=Number(data.active);if(target.type==='foods'){data.price=Number(data.price);data.restaurant_id=Number(data.restaurant_id);if(imageData){const uploaded=await api('/admin/images','POST',{data:imageData});data.image=uploaded.image;}}await api('/admin/'+target.type+(target.id?'/'+target.id:''),target.id?'PATCH':'POST',data);if(editing===target)$('editor').close();await refresh();notice('Đã lưu vào cơ sở dữ liệu.');}catch(error){$('editorError').textContent=error.message;}finally{submit.disabled=false;}};
setInterval(()=>{if(token&&page==='orders'&&!document.hidden&&!$('editor').open)refresh().catch(e=>notice(e.message));},15000);
if(token)enter().catch(error=>{showLogin();notice(error.message);});

let chatTimer=null,chatGeneration=0;
$('editor').addEventListener('close',()=>{chatGeneration++;clearTimeout(chatTimer);});
function openChat(order){
 editing=null;openEditor('Tin nhắn đơn #'+order.id.slice(0,8));$('editorForm').querySelector('[type=submit]').hidden=true;
 const generation=++chatGeneration,list=el('div',undefined,'chat-list'),label=el('label','Tin nhắn'),input=el('textarea');input.maxLength=2000;input.rows=3;input.required=false;label.append(input);
 let pendingKey=null,pendingText=null,sending=false;
 const send=button('Gửi tin nhắn',async()=>{
  const body=input.value.trim();if(!body||sending)return;
  if(body!==pendingText){pendingText=body;pendingKey=crypto.randomUUID();}
  sending=true;send.disabled=true;input.disabled=true;
  try{await api('/admin/orders/'+order.id+'/messages','POST',{body,clientKey:pendingKey});input.value='';pendingKey=null;pendingText=null;await load();}
  catch(error){notice(error.message);}finally{sending=false;send.disabled=false;input.disabled=false;}
 },'primary');
 $('editorFields').append(list,label,send);
 async function load(){
  clearTimeout(chatTimer);if(generation!==chatGeneration||!$('editor').open)return;
  try{const data=await api('/admin/orders/'+order.id+'/messages');if(generation!==chatGeneration)return;list.replaceChildren();
   if(!data.messages.length)list.append(el('p','Chưa có tin nhắn.'));
   for(const message of data.messages){const row=el('div',undefined,'chat-message '+message.role);row.append(el('strong',message.name+' · '+new Date(message.created_at*1000).toLocaleString('vi-VN')),el('p',message.body));list.append(row);}
  }catch(error){notice(error.message);}
  if(generation===chatGeneration&&$('editor').open)chatTimer=setTimeout(load,4000);
 }
 load();
}
