const {chromium}=require('playwright');
const path=require('node:path');const {pathToFileURL}=require('node:url');const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage({viewport:{width:390,height:650},userAgent:'FoodNow/1.0 (Android student project map smoke test)'});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  page.on('requestfailed',request=>{if(request.url().includes('tile.openstreetmap.org'))console.log('Tile network:',request.failure()?.errorText);});
  page.on('response',response=>{if(response.url().includes('tile.openstreetmap.org')&&response.status()>=400)console.log('Tile HTTP:',response.status());});
  const url=pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/map/index.html')).href;
  await page.goto(url);await page.waitForFunction(()=>typeof loaded!=='undefined'&&loaded,{timeout:30000});
  assert.equal(await page.locator('.pin').count(),0);
  await page.locator('#map').click({position:{x:195,y:325}});
  await page.locator('.pin').waitFor();
  assert.match(await page.locator('#status').textContent(),/10\.77/);
  const before=await page.locator('#status').textContent();
  await page.mouse.move(195,325);await page.mouse.down();await page.mouse.move(220,360,{steps:8});await page.mouse.up();
  assert.notEqual(await page.locator('#status').textContent(),before);
  await page.screenshot({path:path.join(__dirname,'data','delivery-map.png')});
  await page.goto(url+'#10.75,106.7,read');await page.reload();await page.locator('.pin').waitFor();
  await page.locator('#map').click({position:{x:120,y:350}});
  assert.equal(await page.evaluate(()=>marker.getLatLng().lat),10.75);
  assert.deepEqual(errors,[]);console.log('PASS: real OSM tiles, tap to select, drag pin, read-only stored location, no JS errors.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
