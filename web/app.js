const loginView=document.querySelector('#loginView');
const appView=document.querySelector('#appView');
const loginError=document.querySelector('#loginError');
const message=document.querySelector('#message');
let shopCache=[];
function showLogin(){loginView.classList.remove('hidden');appView.classList.add('hidden')}
function showApp(){loginView.classList.add('hidden');appView.classList.remove('hidden')}
async function api(path,options={}){
  const response=await fetch(path,{...options,credentials:'same-origin',headers:{'Content-Type':'application/json',...(options.headers||{})}});
  const data=await response.json().catch(()=>({error:'Unexpected server response'}));
  if(response.status===401){showLogin();throw new Error(data.error||'Please sign in again.')}
  if(!response.ok)throw new Error(data.error||'Request failed');
  return data;
}
function cell(tr,value){const td=document.createElement('td');td.textContent=value==null?'':String(value);tr.append(td);return td}
function emptyRow(target,columns,text){const tr=document.createElement('tr');const td=cell(tr,text);td.colSpan=columns;td.className='empty';target.replaceChildren(tr)}
function addRows(target,rows,keys,columns,emptyText){
  target.replaceChildren();if(!rows.length){emptyRow(target,columns,emptyText);return}
  rows.forEach(row=>{const tr=document.createElement('tr');keys.forEach(k=>cell(tr,row[k]));target.append(tr)});
}
async function refresh(){
  try{
    const [stats,dispatchData,requestData,warehouseData,shopData,receiptData,locationData]=await Promise.all([
      api('/api/stats'),api('/api/dispatches'),api('/api/requests'),api('/api/warehouses'),api('/api/shops'),api('/api/receipts'),api('/api/locations')
    ]);
    showApp();shopCache=shopData.rows;
    document.querySelector('#requestCount').textContent=stats.requests;
    document.querySelector('#dispatchCount').textContent=stats.dispatches;
    const waiting=dispatchData.dispatches.filter(d=>d.receipt==='Waiting').length;
    document.querySelector('#receiptCount').textContent=waiting+' waiting';
    addRows(document.querySelector('#requestRows'),requestData.requests,['id','shop','urgency','items','totalKg','status','reason'],7,'No requests.');
    addRows(document.querySelector('#warehouseRows'),warehouseData.rows,['id','name','rice','wheat','sugar'],5,'No warehouse records.');
    addRows(document.querySelector('#shopRows'),shopData.rows,['id','name','rice','wheat','sugar'],5,'No shop records.');
    fillSelect('#stockWarehouse',warehouseData.rows.map(w=>({id:w.id,label:w.id+' – '+w.name})));
    fillSelect('#warehouseConnect',locationData.locations.map(id=>({id,label:id})));
    fillSelect('#shopConnect',locationData.locations.map(id=>({id,label:id})));
    const dispatchBody=document.querySelector('#dispatchRows');dispatchBody.replaceChildren();
    if(!dispatchData.dispatches.length)emptyRow(dispatchBody,7,'No dispatches yet. Process pending requests to create records.');
    else dispatchData.dispatches.forEach(d=>{const tr=document.createElement('tr');['id','request','warehouse','shop','vehicle','route'].forEach(k=>cell(tr,d[k]));const td=cell(tr,d.receipt);td.className='receipt-'+(d.receipt==='Confirmed'?'done':'waiting');dispatchBody.append(tr)});
    addRows(document.querySelector('#receiptRows'),receiptData.receipts,['dispatchId','shop','items','confirmedAt'],4,'No receipts confirmed yet.');
    const shopSelect=document.querySelector('#requestShop');
    if(shopSelect.options.length!==shopCache.length){shopSelect.replaceChildren();shopCache.forEach(s=>{const o=document.createElement('option');o.value=s.id;o.textContent=s.id+' – '+s.name;shopSelect.append(o)})}
    const receiptSelect=document.querySelector('#receiptDispatch');receiptSelect.replaceChildren();
    const waitingRows=dispatchData.dispatches.filter(d=>d.receipt==='Waiting');
    if(!waitingRows.length){const o=document.createElement('option');o.value='';o.textContent='No unconfirmed dispatch';receiptSelect.append(o)}
    else waitingRows.forEach(d=>{const o=document.createElement('option');o.value=d.id;o.textContent=d.id+' – '+d.shop;receiptSelect.append(o)});
    document.querySelector('#connection').textContent='SQLite connected';
  }catch(error){if(loginView.classList.contains('hidden'))message.textContent=error.message;}
}
function openPage(name){
  document.querySelectorAll('.page-view').forEach(p=>p.classList.add('hidden'));
  document.querySelector('#page-'+name).classList.remove('hidden');
  document.querySelectorAll('#menu button[data-page]').forEach(b=>b.classList.toggle('active',b.dataset.page===name));
  document.querySelector('#pageTitle').textContent=({dashboard:'Dashboard',requests:'Delivery requests',warehouses:'Warehouse stock',shops:'Fair Price Shop stock',dispatches:'Dispatch records',receipts:'Receipt history'})[name];
}
function fillSelect(selector,items){const select=document.querySelector(selector),old=select.value;select.replaceChildren();items.forEach(item=>{const o=document.createElement('option');o.value=item.id;o.textContent=item.label;select.append(o)});if(items.some(i=>i.id===old))select.value=old;}
document.querySelectorAll('#menu button[data-page]').forEach(b=>b.addEventListener('click',()=>openPage(b.dataset.page)));
document.querySelector('[data-go="requests"]').addEventListener('click',()=>openPage('requests'));
document.querySelector('#loginForm').addEventListener('submit',async event=>{
  event.preventDefault();loginError.textContent='';
  try{await api('/api/login',{method:'POST',body:JSON.stringify({username:document.querySelector('#username').value,password:document.querySelector('#password').value})});document.querySelector('#password').value='';showApp();await refresh();}
  catch(error){loginError.textContent=error.message;}
});
document.querySelector('#logoutButton').addEventListener('click',async()=>{try{await api('/api/logout',{method:'POST',body:'{}'});}catch(_){}showLogin();});
document.querySelector('#refreshButton').addEventListener('click',refresh);
document.querySelector('#processButton').addEventListener('click',async()=>{
  const b=document.querySelector('#processButton');b.disabled=true;b.textContent='Processing…';message.textContent='';
  try{await api('/api/process',{method:'POST',body:'{}'});message.textContent='Pending requests processed and database records updated.';await refresh();openPage('dispatches');}
  catch(error){message.textContent=error.message;}
  finally{b.disabled=false;b.textContent='Process pending requests';}
});
document.querySelector('#requestForm').addEventListener('submit',async event=>{
  event.preventDefault();message.textContent='';
  try{
    const body={shopId:document.querySelector('#requestShop').value,urgency:document.querySelector('#requestUrgency').value,rice:document.querySelector('#rice').value,wheat:document.querySelector('#wheat').value,sugar:document.querySelector('#sugar').value};
    const result=await api('/api/requests',{method:'POST',body:JSON.stringify(body)});message.textContent='Created and saved request '+result.requestId+'.';
    document.querySelector('#rice').value='0';document.querySelector('#wheat').value='0';document.querySelector('#sugar').value='0';await refresh();openPage('requests');
  }catch(error){message.textContent=error.message;}
});
function newLocationBody(prefix){return {id:document.querySelector('#'+prefix+'Id').value.trim(),name:document.querySelector('#'+prefix+'Name').value.trim(),rice:document.querySelector('#'+prefix+'Rice').value,wheat:document.querySelector('#'+prefix+'Wheat').value,sugar:document.querySelector('#'+prefix+'Sugar').value,connectTo:document.querySelector('#'+prefix+'Connect').value,travelTime:document.querySelector('#'+prefix+'Time').value};}
document.querySelector('#warehouseForm').addEventListener('submit',async event=>{event.preventDefault();message.textContent='';try{await api('/api/warehouses',{method:'POST',body:JSON.stringify(newLocationBody('warehouse'))});message.textContent='Warehouse added and saved in the separate database.';event.target.reset();await refresh();openPage('warehouses');}catch(error){message.textContent=error.message;}});
document.querySelector('#shopForm').addEventListener('submit',async event=>{event.preventDefault();message.textContent='';try{await api('/api/shops',{method:'POST',body:JSON.stringify({id:document.querySelector('#newShopId').value.trim(),name:document.querySelector('#newShopName').value.trim(),rice:document.querySelector('#shopRice').value,wheat:document.querySelector('#shopWheat').value,sugar:document.querySelector('#shopSugar').value,connectTo:document.querySelector('#shopConnect').value,travelTime:document.querySelector('#shopTime').value})});message.textContent='Fair Price Shop added and saved in the separate database.';event.target.reset();await refresh();openPage('shops');}catch(error){message.textContent=error.message;}});
document.querySelector('#warehouseStockForm').addEventListener('submit',async event=>{event.preventDefault();message.textContent='';try{await api('/api/warehouse-stock',{method:'POST',body:JSON.stringify({warehouseId:document.querySelector('#stockWarehouse').value,commodity:document.querySelector('#stockCommodity').value,quantity:document.querySelector('#stockQuantity').value})});message.textContent='Warehouse stock balance saved.';document.querySelector('#stockQuantity').value='';await refresh();openPage('warehouses');}catch(error){message.textContent=error.message;}});
document.querySelector('#confirmReceipt').addEventListener('click',async()=>{
  const id=document.querySelector('#receiptDispatch').value;if(!id){message.textContent='There is no unconfirmed dispatch to receive.';return;}
  try{await api('/api/receipts',{method:'POST',body:JSON.stringify({dispatchId:id})});message.textContent='Receipt confirmed. Shop stock and receipt history were updated in the database.';await refresh();openPage('receipts');}
  catch(error){message.textContent=error.message;}
});
refresh();
