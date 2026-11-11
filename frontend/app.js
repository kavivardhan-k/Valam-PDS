const loginView=document.querySelector('#loginView');
const appView=document.querySelector('#appView');
const loginError=document.querySelector('#loginError');
const message=document.querySelector('#message');
let shopCache=[];
let warehouseCache=[];
let dispatchCache=[];
let receiptCache=[];
let graphCache={nodes:[],edges:[]};
let activeRoute=null;
let refreshInProgress=false;
let signedInUser="Staff";
let currentRole="";
let loginRole="";
function showLogin(){loginView.classList.remove('hidden');appView.classList.add('hidden');currentRole='';loginRole='';document.querySelector('#loginPanel').classList.add('hidden');document.querySelector('#roleChoice').classList.remove('hidden');document.querySelector('#loginShop').value='';document.querySelector('#username').value='';document.querySelector('#password').value='';}
function showApp(){loginView.classList.add('hidden');appView.classList.remove('hidden')}
function applyRoleVisibility(){document.body.classList.toggle('fps-mode',currentRole==='FPS');document.querySelectorAll('[data-visible-role]').forEach(el=>{const role=el.dataset.visibleRole;el.classList.toggle('role-hidden',!(role==='BOTH'||role===currentRole))});document.querySelector('.sidebar-caption').textContent=currentRole==='FPS'?'FAIR PRICE SHOP PORTAL':'VALAM DISTRICT OFFICE';document.querySelector('#fpsQuotaPanel').classList.toggle('hidden',currentRole!=='FPS');document.querySelector('#requestShopLabel').classList.toggle('hidden',currentRole==='FPS');}
function setProfileName(username){
  signedInUser=username||'Admin';
  const clean=signedInUser.trim()||'Admin';
  const words=clean.split(/\s+/);
  const initials=words.length>1?(words[0][0]+words[words.length-1][0]):clean.slice(0,1);
  document.querySelector('#profileName').textContent=clean;
  document.querySelector('#profilePopoverName').textContent=clean;
  document.querySelector('#profileAvatar').textContent=initials.toUpperCase();
  document.querySelector('#profilePopoverAvatar').textContent=initials.toUpperCase();
}
async function api(path,options={}){
  const response=await fetch(path,{...options,cache:'no-store',credentials:'same-origin',headers:{'Content-Type':'application/json',...(options.headers||{})}});
  const data=await response.json().catch(()=>({error:'Unexpected server response'}));
  if(response.status===401){if(path!=='/api/login')showLogin();throw new Error(data.error||'Please sign in again.')}
  if(!response.ok)throw new Error(data.error||'Request failed');
  return data;
}
function cell(tr,value){const td=document.createElement('td');td.textContent=value==null?'':String(value);tr.append(td);return td}
function emptyRow(target,columns,text){const tr=document.createElement('tr');const td=cell(tr,text);td.colSpan=columns;td.className='empty';target.replaceChildren(tr)}
function addRows(target,rows,keys,columns,emptyText){
  target.replaceChildren();if(!rows.length){emptyRow(target,columns,emptyText);return}
  rows.forEach(row=>{const tr=document.createElement('tr');keys.forEach(k=>cell(tr,row[k]));target.append(tr)});
}
function renderLocationCards(target,rows,type){
  target.replaceChildren();
  if(!rows.length){const empty=document.createElement('p');empty.className='empty-card';empty.textContent='No saved locations yet.';target.append(empty);return;}
  rows.forEach(row=>{
    const card=document.createElement('article');card.className='location-card';
    const header=document.createElement('div');header.className='location-card-head';
    const titleBox=document.createElement('div');
    const eyebrow=document.createElement('span');eyebrow.className='location-kind';eyebrow.textContent=type==='warehouse'?'WAREHOUSE':'FAIR PRICE SHOP';
    const title=document.createElement('h3');title.textContent=row.name;
    titleBox.append(eyebrow,title);
    const actions=document.createElement('div');actions.className='location-actions';
    const edit=document.createElement('button');edit.type='button';edit.className='edit-location';edit.textContent='Edit details';
    edit.addEventListener('click',()=>openLocationEditor(type,row));
    const connect=document.createElement('button');connect.type='button';connect.className='edit-location';connect.textContent='Add road';
    connect.addEventListener('click',()=>startRoadForLocation(row));
    const remove=document.createElement('button');remove.type='button';remove.className='remove-location';remove.textContent='Remove';
    remove.addEventListener('click',()=>removeLocation(type,row));
    actions.append(edit,connect,remove);header.append(titleBox,actions);card.append(header);
    const id=document.createElement('p');id.className='location-id';id.textContent='Location ID · '+row.id+(type==='shop'?' · '+Number(row.population||0).toLocaleString()+' people registered':'');card.append(id);
    const roads=graphCache.edges.filter(edge=>edge.from===row.id||edge.to===row.id);
    const connection=document.createElement('div');connection.className='location-road-list';
    if(!roads.length){const none=document.createElement('p');none.className='location-road';none.textContent='No direct road connections';connection.append(none);}
    roads.forEach(road=>{const line=document.createElement('p');line.className='location-road';line.textContent='↔ '+(road.from===row.id?road.to:road.from)+' · '+road.minutes+' min';connection.append(line)});
    card.append(connection);
    const stocks=document.createElement('div');stocks.className='stock-grid';
    [['Rice',row.rice],['Wheat',row.wheat],['Sugar',row.sugar]].forEach(([name,quantity])=>{
      const item=document.createElement('div');item.className='stock-item';
      const label=document.createElement('span');label.textContent=name;
      const amount=document.createElement('strong');amount.textContent=Number(quantity||0).toLocaleString(undefined,{maximumFractionDigits:1})+' kg';
      item.append(label,amount);stocks.append(item);
    });
    card.append(stocks);
    if(type==='shop'){const quota=document.createElement('p');quota.className='monthly-shop-quota';const amount=(total,used)=>Math.max(0,Number(total||0)-Number(used||0)).toLocaleString();quota.textContent='Shop allocation this month · Rice '+Number(row.riceQuota||0).toLocaleString()+' kg ('+amount(row.riceQuota,row.riceUsed)+' available) · Wheat '+Number(row.wheatQuota||0).toLocaleString()+' kg ('+amount(row.wheatQuota,row.wheatUsed)+' available) · Sugar '+Number(row.sugarQuota||0).toLocaleString()+' kg ('+amount(row.sugarQuota,row.sugarUsed)+' available)';card.append(quota);}
    target.append(card);
  });
}
function startRoadForLocation(row){
  openPage('routes');
  document.querySelector('#roadFrom').value=row.id;
  document.querySelector('#roadTo').value='';
  document.querySelector('#roadTo').focus();
  message.textContent='Choose another location and enter the road travel time.';
  document.querySelector('#roadForm').scrollIntoView({behavior:'smooth',block:'center'});
}
async function removeLocation(type,row){
  const kind=type==='warehouse'?'warehouse':'Fair Price Shop';
  if(!window.confirm('Remove '+kind+' '+row.id+' ('+row.name+')? Saved requests or dispatch history may prevent removal.'))return;
  const endpoint=type==='warehouse'?'/api/warehouses':'/api/shops';
  try{await api(endpoint,{method:'DELETE',body:JSON.stringify({id:row.id})});if(await refresh())message.textContent=kind+' removed. Its road links and saved stock rows were removed too.';}
  catch(error){message.textContent=error.message;}
}
function renderReceiptHistory(receipts){
  const target=document.querySelector('#receiptRows');target.replaceChildren();
  if(!receipts.length){emptyRow(target,4,'No receipts confirmed yet.');return;}
  const groups=new Map();
  receipts.forEach(receipt=>{
    const match=/^(\d{4})-(\d{2})/.exec(receipt.confirmedAt||'');
    const key=match?`${match[1]}-${match[2]}`:'unknown';
    if(!groups.has(key))groups.set(key,[]);groups.get(key).push(receipt);
  });
  [...groups.keys()].sort((a,b)=>b.localeCompare(a)).forEach(key=>{
    const heading=document.createElement('tr');heading.className='month-heading';
    const label=document.createElement('th');label.colSpan=4;
    if(key==='unknown')label.textContent='Date unavailable';
    else {const [year,month]=key.split('-').map(Number);label.textContent=new Date(year,month-1,1).toLocaleDateString(undefined,{month:'long',year:'numeric'});}
    heading.append(label);target.append(heading);
    groups.get(key).forEach(receipt=>{const tr=document.createElement('tr');['dispatchId','shop','items','confirmedAt'].forEach(k=>cell(tr,receipt[k]));target.append(tr)});
  });
}
function renderFleetCards(vehicles){
  const target=document.querySelector('#fleetCards');target.replaceChildren();
  const available=vehicles.filter(vehicle=>vehicle.available).length;
  const onDelivery=vehicles.length-available;
  document.querySelector('#fleetSummary').textContent=available+' free · '+onDelivery+' on delivery';
  const transportCount=document.querySelector('#transportStatCount');
  const transportDetail=document.querySelector('#transportStatDetail');
  if(transportCount)transportCount.textContent=available+' free';
  if(transportDetail)transportDetail.textContent=onDelivery+' on delivery · '+vehicles.length+' total';
  if(!vehicles.length){const empty=document.createElement('div');empty.className='empty-receipts';empty.textContent='No vehicles are configured.';target.append(empty);return;}
  vehicles.forEach(vehicle=>{
    const card=document.createElement('article');card.className='fleet-card '+(vehicle.available?'fleet-free':'fleet-busy');
    const top=document.createElement('div');top.className='fleet-card-top';
    const icon=document.createElement('span');icon.className='fleet-icon';icon.textContent=vehicle.capacity>1200?'🚚':'🚐';
    const status=document.createElement('span');status.className='fleet-status';status.textContent=vehicle.available?'Available':'On delivery';top.append(icon,status);
    const name=document.createElement('h3');name.textContent=vehicle.name;
    const capacity=document.createElement('p');capacity.className='fleet-capacity';capacity.textContent='Capacity · '+Number(vehicle.capacity).toLocaleString()+' kg';
    const note=document.createElement('p');note.className='fleet-note';note.textContent=vehicle.available?'Ready for a suitable request':(vehicle.dispatch?'Assigned to '+vehicle.dispatch+' · confirm every shop receipt to release':'Assigned to a delivery');
    card.append(top,name,capacity,note);
    if(!vehicle.available){
      const trip=document.createElement('div');trip.className='fleet-trip-details';
      const loadTitle=document.createElement('strong');loadTitle.textContent='Currently carrying';
      const load=document.createElement('div');load.className='fleet-load';load.textContent=Number(vehicle.loadKg||0).toLocaleString()+' kg of '+Number(vehicle.capacity).toLocaleString()+' kg';
      const cargo=document.createElement('p');cargo.className='fleet-cargo';
      cargo.textContent=Object.entries(vehicle.cargo||{}).map(([item,amount])=>item+' '+Number(amount).toLocaleString()+' kg').join(' · ')||'No remaining goods';
      trip.append(loadTitle,load,cargo);
      if(vehicle.stops&&vehicle.stops.length){
        const stopTitle=document.createElement('strong');stopTitle.className='fleet-stop-heading';stopTitle.textContent='Deliveries on this trip';trip.append(stopTitle);
        const list=document.createElement('ol');list.className='fleet-stop-list';
        vehicle.stops.forEach(stop=>{
          const row=document.createElement('li');
          const shop=document.createElement('strong');shop.textContent=stop.shop+' ('+stop.shopId+')';
          const items=document.createElement('span');items.textContent=Object.entries(stop.items||{}).map(([item,amount])=>item+' '+Number(amount).toLocaleString()+' kg').join(', ');
          row.append(shop,items);list.append(row);
        });
        trip.append(list);
      }
      if(vehicle.route&&vehicle.route.length){
        const routeButton=document.createElement('button');routeButton.type='button';routeButton.className='secondary fleet-route-button';routeButton.textContent='View trip route →';
        routeButton.addEventListener('click',()=>showVehicleRoute(vehicle));trip.append(routeButton);
      }
      card.append(trip);
    }
    target.append(card);
  });
}
function renderPendingReceipts(dispatches){
  const target=document.querySelector('#pendingReceiptCards');target.replaceChildren();
  const pending=dispatches.filter(dispatch=>dispatch.receipt==='Waiting');
  if(!pending.length){const empty=document.createElement('div');empty.className='empty-receipts';empty.textContent='There are no deliveries waiting for shop confirmation.';target.append(empty);return;}
  pending.forEach(dispatch=>{
    const card=document.createElement('article');card.className='pending-receipt-card';
    const heading=document.createElement('div');heading.className='pending-receipt-head';
    const shop=document.createElement('div');const overline=document.createElement('span');overline.className='location-kind';overline.textContent='FAIR PRICE SHOP';
    const name=document.createElement('h3');name.textContent=dispatch.shop;shop.append(overline,name);
    const badge=document.createElement('span');badge.className='receipt-pending-badge';badge.textContent='Awaiting confirmation';heading.append(shop,badge);card.append(heading);
    const details=document.createElement('div');details.className='receipt-details-grid';
    [['Dispatch',dispatch.id],['Request',dispatch.request],['Vehicle',dispatch.vehicle],['Route',dispatch.route]].forEach(([label,value])=>{const item=document.createElement('div');const key=document.createElement('span');key.textContent=label;const val=document.createElement('strong');val.textContent=value||'—';item.append(key,val);details.append(item)});
    card.append(details);
    const confirm=document.createElement('button');confirm.type='button';confirm.className='primary confirm-delivery';confirm.textContent='Confirm shop has received goods';
    confirm.addEventListener('click',()=>confirmShopReceipt(dispatch,confirm));card.append(confirm);target.append(card);
  });
}
async function confirmShopReceipt(dispatch,button){
  button.disabled=true;button.textContent='Saving confirmation…';message.textContent='';
  try{
    await api('/api/receipts',{method:'POST',body:JSON.stringify({dispatchId:dispatch.id})});
    if(await refresh())message.textContent='Receipt confirmed for '+dispatch.shop+'. Shop stock has been updated.';
  }catch(error){message.textContent=error.message;}
  finally{if(button.isConnected){button.disabled=false;button.textContent='Confirm shop has received goods';}}
}
function openLocationEditor(type,row){
  document.querySelector('#editLocationId').value=row.id;
  document.querySelector('#editLocationKind').value=type;
  const popWrap=document.querySelector('#editPopulationWrap');popWrap.classList.toggle('hidden',type!=='shop');document.querySelector('#editPopulation').value=row.population||0;
  document.querySelector('#editLocationName').value=row.name;
  document.querySelector('#editLocationType').textContent=type==='warehouse'?'WAREHOUSE DETAILS':'FAIR PRICE SHOP DETAILS';
  document.querySelector('#editLocationTitle').textContent='Edit '+(type==='warehouse'?'warehouse location':'shop location');
  const connector=document.querySelector('#editLocationConnect');connector.replaceChildren();
  const noRoad=document.createElement('option');noRoad.value='';noRoad.textContent='No direct road connection';connector.append(noRoad);
  graphCache.nodes.filter(node=>node.id!==row.id).forEach(node=>{
    const option=document.createElement('option');option.value=node.id;option.textContent=node.id+' · '+node.name;connector.append(option);
  });
  const currentRoad=graphCache.edges.find(edge=>edge.from===row.id||edge.to===row.id);
  connector.value=currentRoad?(currentRoad.from===row.id?currentRoad.to:currentRoad.from):'';
  const travelTime=document.querySelector('#editLocationTime');travelTime.value=currentRoad?currentRoad.minutes:'5';travelTime.disabled=!currentRoad;
  document.querySelector('#editLocationDialog').showModal();
}
async function refresh(announce=false){
  if(refreshInProgress)return false;
  refreshInProgress=true;
  const refreshButton=document.querySelector('#refreshButton');
  if(refreshButton){refreshButton.disabled=true;if(announce)refreshButton.textContent='Refreshing…';}
  if(announce)message.textContent='Refreshing saved records…';
  try{
    const fps=currentRole==='FPS';
    const [stats,dispatchData,requestData,warehouseData,shopData,receiptData,locationData,graphData,vehicleData,optionsData]=await Promise.all([
      api('/api/stats'),api('/api/dispatches'),api('/api/requests'),fps?Promise.resolve({rows:[]}):api('/api/warehouses'),api('/api/shops'),fps?api('/api/receipts'):Promise.resolve({receipts:[]}),api('/api/locations'),api('/api/graph'),fps?Promise.resolve({vehicles:[]}):api('/api/vehicles')
    ]);
    showApp();warehouseCache=warehouseData.rows;shopCache=shopData.rows;dispatchCache=dispatchData.dispatches;receiptCache=receiptData.receipts;graphCache=graphData;applyRoleVisibility();activeRoute=null;document.querySelector('#routeResult').classList.remove('route-unreachable');document.querySelector('#routeResult').textContent='Choose a start and destination to explore the road network.';
    document.querySelector('#requestCount').textContent=stats.requests;
    document.querySelector('#dispatchCount').textContent=stats.dispatches;
    document.querySelector('#successfulDeliveryCount').textContent=stats.delivered;
    document.querySelector('#unableCount').textContent=stats.unable;
    if(!fps)renderFleetCards(vehicleData.vehicles);
    const waiting=dispatchData.dispatches.filter(d=>d.receipt==='Waiting').length;
    document.querySelector('#receiptCount').textContent=waiting+' waiting';document.querySelector('#receiptNavCount').textContent=waiting;document.querySelector('#receiptNavCount').classList.toggle('is-zero',waiting===0);document.querySelector('#receiptHeroCount').textContent=waiting;
    addRows(document.querySelector('#requestRows'),requestData.requests,['id','shop','urgency','items','totalKg','status','reason'],7,'No requests.');
    renderLocationCards(document.querySelector('#warehouseCards'),warehouseData.rows,'warehouse');
    renderLocationCards(document.querySelector('#shopCards'),shopData.rows,'shop');
    fillRouteSelects(graphData.nodes);renderRoadNetwork(graphData);document.querySelector('#networkSummary').textContent=graphData.nodes.length+' places · '+graphData.edges.length+' roads';
    fillSelect('#roadFrom',graphData.nodes.map(n=>({id:n.id,label:n.id+' · '+n.name})));fillSelect('#roadTo',graphData.nodes.map(n=>({id:n.id,label:n.id+' · '+n.name})));
    fillSelect('#stockWarehouse',warehouseData.rows.map(w=>({id:w.id,label:w.id+' – '+w.name})));
    fillSelect('#warehouseConnect',locationData.locations.map(id=>({id,label:id})));
    fillSelect('#shopConnect',locationData.locations.map(id=>({id,label:id})));
    const dispatchBody=document.querySelector('#dispatchRows');dispatchBody.replaceChildren();
    if(!dispatchData.dispatches.length)emptyRow(dispatchBody,7,'No dispatches yet. Process pending requests to create records.');
    else dispatchData.dispatches.forEach(d=>{const tr=document.createElement('tr');['id','request','warehouse','shop','vehicle','route'].forEach(k=>cell(tr,d[k]));const td=cell(tr,d.receipt);td.className='receipt-'+(d.receipt==='Confirmed'?'done':'waiting');dispatchBody.append(tr)});
    renderReceiptHistory(receiptData.receipts);
    fillSelect('#requestShop',shopCache.map(s=>({id:s.id,label:s.id+' – '+s.name})));
    const current=shopCache[0];if(fps&&current){
      document.querySelector('#requestQuotaHint').classList.remove('hidden');
      document.querySelector('#fpsPeopleCount').textContent=Number(current.population).toLocaleString();
      document.querySelector('#fpsPopulationInput').value=current.population;
      const quota=document.querySelector('#quotaCards');quota.replaceChildren();
      [['Rice','riceQuota','riceUsed','riceCommitted','rice'],['Wheat','wheatQuota','wheatUsed','wheatCommitted','wheat'],['Sugar','sugarQuota','sugarUsed','sugarCommitted','sugar']].forEach(([name,totalKey,receivedKey,committedKey,field])=>{
        const total=Number(current[totalKey]||0),received=Number(current[receivedKey]||0),committed=Number(current[committedKey]||0);
        const inTransit=Math.max(0,committed-received),remaining=Math.max(0,total-committed);
        document.querySelector('#'+field).max=remaining;
        const card=document.createElement('article');card.className='quota-card';
        const label=document.createElement('span');label.textContent=name;
        const amount=document.createElement('strong');amount.textContent=received.toLocaleString()+' / '+total.toLocaleString()+' kg';
        const detail=document.createElement('small');detail.textContent=received.toLocaleString()+' kg received this month';
        const reserved=document.createElement('small');reserved.textContent=inTransit.toLocaleString()+' kg on the road · '+remaining.toLocaleString()+' kg available to dispatch';
        card.append(label,amount,detail,reserved);quota.append(card);
      });
      document.querySelector('#pageTitle').textContent=current.name+' · Shop overview';
      document.querySelector('#pageSubtitle').textContent='';
    }
    if(!fps){document.querySelector('#requestQuotaHint').classList.add('hidden');['rice','wheat','sugar'].forEach(field=>document.querySelector('#'+field).removeAttribute('max'));document.querySelector('#page-shops .panel-head h2').textContent='Fair Price Shop stock';document.querySelector('#page-shops .panel-head .muted').textContent="Add shops here. A shop's stock increases only after you confirm a delivery receipt.";}else{document.querySelector('#page-shops .panel-head h2').textContent='My shop stock';document.querySelector('#page-shops .panel-head .muted').textContent='Current balances for this shop. Stock increases only after you confirm a delivery.';}
    renderPendingReceipts(dispatchData.dispatches);
    document.querySelector('#connection').textContent='Records up to date';
    if(announce)message.textContent='Records refreshed.';
    return true;
  }catch(error){if(loginView.classList.contains('hidden')){message.textContent='Refresh failed: '+error.message;document.querySelector('#connection').textContent='Refresh failed';}return false;}
  finally{refreshInProgress=false;if(refreshButton){refreshButton.disabled=false;refreshButton.textContent='Refresh';}}
}
function openPage(name){
  if(currentRole==='FPS'&&name==='warehouses'||currentRole==='WAREHOUSE'&&(name==='confirmations'||name==='receipts'))return;
  document.querySelectorAll('.page-view').forEach(page=>page.classList.add('hidden'));
  document.querySelector('#page-'+name).classList.remove('hidden');
  document.querySelectorAll('#menu button[data-page]').forEach(button=>button.classList.toggle('active',button.dataset.page===name));
  const pages={dashboard:['Operations overview','A clear view of district supply activity.'],requests:['Delivery requests','Triage shortages and send complete requests into dispatch planning.'],warehouses:['Warehouse inventory','Keep source stock balances accurate before allocating a delivery.'],shops:['Fair Price Shop inventory','Review shop balances; receipt confirmation adds received goods.'],routes:['Road map','Explore saved road links and find the quickest reachable route.'],dispatches:['Dispatch records','Track the delivery from warehouse departure to shop arrival.'],confirmations:['Shop receiving desk','Record the handover only after the Fair Price Shop confirms receipt.'],receipts:['Receipt history','Look back at shop-confirmed deliveries, organized by month.']};
  document.querySelector('#pageTitle').textContent=(name==='dashboard'&&currentRole==='FPS'&&shopCache[0])?shopCache[0].name+' · Shop overview':pages[name][0];
  document.querySelector('#pageSubtitle').textContent=(name==='dashboard'&&currentRole==='FPS')?'':pages[name][1];
  document.querySelector('#pageKicker').textContent=name==='confirmations'?'ACTION NEEDED':'VALAM DISTRICT · OPERATIONS';
}
function fillSelect(selector,items){const select=document.querySelector(selector),old=select.value;select.replaceChildren();if(!items.length){const o=document.createElement('option');o.value='';o.textContent='No saved options found';o.disabled=true;o.selected=true;select.append(o);return;}items.forEach(item=>{const o=document.createElement('option');o.value=item.id;o.textContent=item.label;select.append(o)});if(items.some(i=>i.id===old))select.value=old;}
function fillRouteSelects(nodes){
  const sorted=[...nodes].sort((a,b)=>a.id.localeCompare(b.id));
  ['#routeStart','#routeEnd'].forEach(selector=>{
    const select=document.querySelector(selector),previous=select.value;
    select.replaceChildren();
    const placeholder=document.createElement('option');placeholder.value='';placeholder.textContent='Choose a location';select.append(placeholder);
    sorted.forEach(node=>{const option=document.createElement('option');option.value=node.id;option.textContent=node.id+' · '+node.name;select.append(option)});
    if(sorted.some(node=>node.id===previous))select.value=previous;
  });
}
function routeEdgeKey(first,second){return [first,second].sort().join('|')}
function findFastestRoute(start,destination){
  if(!start||!destination||start===destination)return null;
  const distance=new Map(),previous=new Map(),visited=new Set(),neighbors=new Map();
  graphCache.nodes.forEach(node=>{distance.set(node.id,Infinity);neighbors.set(node.id,[])});
  graphCache.edges.forEach(edge=>{neighbors.get(edge.from)?.push({id:edge.to,time:Number(edge.minutes)});neighbors.get(edge.to)?.push({id:edge.from,time:Number(edge.minutes)})});
  if(!distance.has(start)||!distance.has(destination))return null;
  distance.set(start,0);
  while(visited.size<distance.size){
    let current=null,best=Infinity;
    for(const id of [...distance.keys()].sort())if(!visited.has(id)&&distance.get(id)<best){current=id;best=distance.get(id)}
    if(current===null)break;
    if(current===destination)break;
    visited.add(current);
    for(const road of (neighbors.get(current)||[]).sort((a,b)=>a.id.localeCompare(b.id))){
      const next=best+road.time,old=distance.get(road.id);
      if(next<old||(next===old&&current<(previous.get(road.id)||'~'))){distance.set(road.id,next);previous.set(road.id,current)}
    }
  }
  if(!Number.isFinite(distance.get(destination)))return null;
  const ids=[destination];let cursor=destination;
  while(cursor!==start){cursor=previous.get(cursor);if(!cursor)return null;ids.unshift(cursor)}
  return {ids,minutes:distance.get(destination)};
}
function paintRouteResult(route,trip=null){
  const result=document.querySelector('#routeResult');result.replaceChildren();
  if(!route){result.textContent='No connected road route was found between these locations.';result.classList.add('route-unreachable');return}
  result.classList.remove('route-unreachable');
  const label=document.createElement('strong');label.textContent=trip?'Planned vehicle trip':'Fastest available route';result.append(label);
  const path=document.createElement('div');path.className='route-path';
  route.ids.forEach((id,index)=>{const chip=document.createElement('span');chip.className='route-chip';chip.textContent=id;path.append(chip);if(index<route.ids.length-1){const arrow=document.createElement('span');arrow.className='route-path-arrow';arrow.textContent='→';path.append(arrow)}});
  const time=document.createElement('span');time.className='route-time';time.textContent=route.minutes.toFixed(1)+' min';result.append(path,time);
  const legs=document.createElement('div');legs.className='route-leg-breakdown';
  const legsHeading=document.createElement('strong');legsHeading.textContent='Road legs';legs.append(legsHeading);
  const edgeTimes=new Map((graphCache?.edges||[]).map(edge=>[[edge.from,edge.to].sort().join('|'),Number(edge.minutes)]));
  for(let index=0;index<route.ids.length-1;index++){
    const from=route.ids[index],to=route.ids[index+1],leg=document.createElement('div');leg.className='route-leg-item';
    const places=document.createElement('span');places.textContent=from+' → '+to;
    const minutes=document.createElement('strong');const legTime=edgeTimes.get([from,to].sort().join('|'));
    minutes.textContent=Number.isFinite(legTime)?legTime.toFixed(1)+' min':'time unavailable';
    leg.append(places,minutes);legs.append(leg);
  }
  if(route.ids.length>1)result.append(legs);
  if(trip){
    const summary=document.createElement('p');summary.className='trip-route-summary';
    summary.textContent=trip.name+' leaves '+(trip.warehouseName||trip.warehouseId)+' carrying '+Number(trip.loadKg||0).toLocaleString()+' kg.';result.append(summary);
    const heading=document.createElement('strong');heading.className='trip-stops-heading';heading.textContent='Shop stops and goods';result.append(heading);
    const stops=document.createElement('ol');stops.className='trip-route-stops';
    const ordered=[...(trip.stops||[])].sort((a,b)=>route.ids.indexOf(a.shopId)-route.ids.indexOf(b.shopId));
    ordered.forEach((stop,index)=>{const item=document.createElement('li');const name=document.createElement('strong');name.textContent='Stop '+(index+1)+': '+stop.shop+' ('+stop.shopId+')';const goods=document.createElement('span');goods.textContent=stop.request+' · '+Number(stop.weight).toLocaleString()+' kg · '+Object.entries(stop.items||{}).map(([commodity,amount])=>commodity+' '+Number(amount).toLocaleString()+' kg').join(', ');item.append(name,goods);stops.append(item)});
    result.append(stops);
  }
}
function showVehicleRoute(vehicle){
  const route={ids:vehicle.route||[],minutes:Number(vehicle.routeMinutes||0)};
  if(route.ids.length<2){message.textContent='No saved route is available for this vehicle.';return}
  openPage('routes');
  const from=document.querySelector('#routeStart'),to=document.querySelector('#routeEnd');
  from.value=vehicle.warehouseId||route.ids[0];
  const lastStop=(vehicle.stops||[]).length?vehicle.stops[vehicle.stops.length-1].shopId:route.ids[route.ids.length-1];
  to.value=lastStop;
  activeRoute={...route,trip:vehicle};
  paintRouteResult(route,vehicle);renderRoadNetwork(graphCache);
  document.querySelector('#page-routes').scrollIntoView({behavior:'smooth',block:'start'});
}
function showFastestRoute(){
  const start=document.querySelector('#routeStart').value,destination=document.querySelector('#routeEnd').value;
  if(!start||!destination){const result=document.querySelector('#routeResult');result.classList.remove('route-unreachable');result.textContent='Choose both a starting point and a destination.';return}
  if(start===destination){const result=document.querySelector('#routeResult');result.classList.remove('route-unreachable');result.textContent='Choose two different locations to compare a route.';return}
  activeRoute=findFastestRoute(start,destination);paintRouteResult(activeRoute);renderRoadNetwork(graphCache);
}
function clearFastestRoute(){
  activeRoute=null;document.querySelector('#routeStart').value='';document.querySelector('#routeEnd').value='';
  const result=document.querySelector('#routeResult');result.classList.remove('route-unreachable');result.textContent='Choose a start and destination to explore the road network.';renderRoadNetwork(graphCache);
}
function chooseMapLocation(id){
  const start=document.querySelector('#routeStart'),end=document.querySelector('#routeEnd');
  if(!start.value||end.value){start.value=id;end.value='';const result=document.querySelector('#routeResult');result.classList.remove('route-unreachable');result.textContent='Starting point selected: '+id+'. Now choose a destination.';activeRoute=null;renderRoadNetwork(graphCache);return}
  if(start.value===id){start.value=id;end.value='';return}
  end.value=id;showFastestRoute();
}
async function removeRoad(edge,button){
  if(!window.confirm('Remove road '+edge.from+' ↔ '+edge.to+'? This may make some destinations unreachable.'))return;
  button.disabled=true;
  try{
    await api('/api/roads',{method:'DELETE',body:JSON.stringify({from:edge.from,to:edge.to})});
    if(await refresh())message.textContent='Road connection removed from both directions.';
    openPage('routes');
  }catch(error){message.textContent=error.message;button.disabled=false;}
}
function renderRoadNetwork(graph){
  const svg=document.querySelector('#networkGraph'),ns='http://www.w3.org/2000/svg';
  const byId=new Map(graph.nodes.map(node=>[node.id,node]));
  const columns={Warehouse:[],'Road junction':[],'Fair Price Shop':[]};
  graph.nodes.forEach(node=>(columns[node.type]||columns['Road junction']).push(node));
  Object.values(columns).forEach(nodes=>nodes.sort((a,b)=>a.id.localeCompare(b.id)));
  const maxRows=Math.max(1,...Object.values(columns).map(nodes=>nodes.length));
  const height=Math.max(560,100+maxRows*96),centers={Warehouse:160,'Road junction':550,'Fair Price Shop':940},positions=new Map();
  svg.replaceChildren();svg.setAttribute('viewBox','0 0 1100 '+height);svg.setAttribute('height',height);
  const add=(tag,attrs,parent=svg)=>{const element=document.createElementNS(ns,tag);Object.entries(attrs).forEach(([key,value])=>element.setAttribute(key,value));parent.append(element);return element};
  Object.entries(columns).forEach(([type,nodes])=>nodes.forEach((node,index)=>{const y=55+(index+1)*(height-110)/(nodes.length+1);positions.set(node.id,{x:centers[type]||550,y})}));
  const routeEdges=new Set();
  if(activeRoute)for(let i=0;i<activeRoute.ids.length-1;i++)routeEdges.add(routeEdgeKey(activeRoute.ids[i],activeRoute.ids[i+1]));
  graph.edges.forEach(edge=>{
    const a=positions.get(edge.from),b=positions.get(edge.to);if(!a||!b)return;
    const onRoute=routeEdges.has(routeEdgeKey(edge.from,edge.to));
    const line=add('line',{x1:a.x,y1:a.y,x2:b.x,y2:b.y,class:onRoute?'network-edge route-highlight':'network-edge'});
    const roadTip=add('title',{},line);roadTip.textContent=edge.from+' to '+edge.to+': '+Number(edge.minutes).toFixed(1)+' minutes';
    if(onRoute){const label=add('text',{x:(a.x+b.x)/2,y:(a.y+b.y)/2-8,class:'edge-label edge-label-active'});label.textContent=Number(edge.minutes).toFixed(1)+' min';}
  });
  const rows=document.querySelector('#routeRows');rows.replaceChildren();
  if(!graph.edges.length)emptyRow(rows,4,'No road links have been recorded.');
  else graph.edges.forEach(edge=>{
    const tr=document.createElement('tr'),from=byId.get(edge.from),to=byId.get(edge.to);
    cell(tr,edge.from+' · '+(from?from.name:edge.from));cell(tr,edge.to+' · '+(to?to.name:edge.to));cell(tr,Number(edge.minutes).toFixed(1)+' minutes');
    const action=cell(tr,'');const remove=document.createElement('button');remove.type='button';remove.className='remove-road';remove.innerHTML='🗑 Remove';remove.setAttribute('aria-label','Remove road '+edge.from+' to '+edge.to);remove.addEventListener('click',()=>removeRoad(edge,remove));action.append(remove);rows.append(tr);
  });
  graph.nodes.forEach(node=>{
    const position=positions.get(node.id),group=add('g',{class:'network-node','data-location':node.id,tabindex:'0',role:'button','aria-label':'Select '+node.id+' '+node.name});
    const title=add('title',{},group);title.textContent=node.type+': '+node.id+' — '+node.name;
    const category=node.type==='Warehouse'?'warehouse':node.type==='Fair Price Shop'?'shop':'junction';
    add('rect',{x:position.x-118,y:position.y-29,width:236,height:58,rx:15,class:'node-card node-'+category},group);
    add('circle',{cx:position.x-93,cy:position.y,r:7,class:'node-dot '+category+'-dot-fill'},group);
    const idText=add('text',{x:position.x-76,y:position.y-3,class:'node-id'},group);idText.textContent=node.id;
    const nameText=add('text',{x:position.x-76,y:position.y+15,class:'node-name'},group);nameText.textContent=node.name.length>26?node.name.slice(0,24)+'…':node.name;
    if(activeRoute&&activeRoute.ids.includes(node.id))group.classList.add('node-on-route');if(document.querySelector('#routeStart').value===node.id||document.querySelector('#routeEnd').value===node.id)group.classList.add('node-selected');
    group.addEventListener('click',()=>chooseMapLocation(node.id));
    group.addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();chooseMapLocation(node.id)}});
  });
}
document.querySelectorAll('#menu button[data-page]').forEach(b=>b.addEventListener('click',()=>openPage(b.dataset.page)));
document.querySelectorAll('[data-go]').forEach(b=>b.addEventListener('click',()=>openPage(b.dataset.go)));
let loginCaptchaId='';
async function loadCaptcha(){
  try{const challenge=await api('/api/captcha');loginCaptchaId=challenge.id;document.querySelector('#loginCaptchaQuestion').textContent=challenge.question;document.querySelector('#loginCaptchaAnswer').value='';}
  catch(error){document.querySelector('#loginCaptchaQuestion').textContent='Verification unavailable';}
}
function updateDemoCredentials(){const shopId=document.querySelector('#loginShop').value;const username=shopId?(shopId.toUpperCase()==='FPS01'?'fps.demo':'fps.'+shopId.toLowerCase()):'Select your FPS to view demo user ID';document.querySelector('#demoCredentials').textContent=loginRole==='WAREHOUSE'?'warehouse.demo / ValamWH#2026':username+' / ValamFPS#2026';if(loginRole==='FPS'&&shopId)document.querySelector('#username').placeholder='Demo: '+username;}
async function loadLoginShops(){const select=document.querySelector('#loginShop');select.replaceChildren();const placeholder=document.createElement('option');placeholder.value='';placeholder.textContent='Loading available shops…';select.append(placeholder);try{const data=await api('/api/shop-options');select.replaceChildren();const first=document.createElement('option');first.value='';first.textContent='Choose your assigned shop';select.append(first);data.shops.forEach(shop=>{const option=document.createElement('option');option.value=shop.id;option.textContent=shop.id+' · '+shop.name;select.append(option)});updateDemoCredentials();}catch(error){select.replaceChildren();const option=document.createElement('option');option.value='';option.textContent='Could not load shops';select.append(option);document.querySelector('#loginError').textContent=error.message;}}
function chooseLoginRole(role){loginRole=role;document.querySelector('#roleChoice').classList.add('hidden');document.querySelector('#loginPanel').classList.remove('hidden');document.querySelector('#selectedRoleLabel').textContent=role==='WAREHOUSE'?'Warehouse staff sign-in':'Fair Price Shop sign-in';document.querySelector('#loginShopWrap').classList.toggle('hidden',role!=='FPS');document.querySelector('#loginShop').required=role==='FPS';document.querySelector('#demoCredentials').textContent=role==='WAREHOUSE'?'warehouse.demo / ValamWH#2026':'Choose your assigned shop to see its demo user ID';document.querySelector('#loginError').textContent='';if(role==='FPS')loadLoginShops();loadCaptcha();}
document.querySelectorAll('.role-card').forEach(button=>button.addEventListener('click',()=>chooseLoginRole(button.dataset.role)));
document.querySelector('#changeRole').addEventListener('click',()=>{loginRole='';document.querySelector('#loginPanel').classList.add('hidden');document.querySelector('#roleChoice').classList.remove('hidden')});
async function finishAuthentication(signedIn){currentRole=signedIn.role;setProfileName(signedIn.username);document.querySelector('#profileRole').textContent=currentRole==='FPS'?'Fair Price Shop staff':'Warehouse staff';document.querySelector('#profileRolePopover').textContent=currentRole==='FPS'?'Fair Price Shop access':'Warehouse operations access';document.body.classList.toggle('fps-mode',currentRole==='FPS');showApp();await refresh();}
document.querySelector('#loginForm').addEventListener('submit',async event=>{
  event.preventDefault();loginError.textContent='';
  try{const signedIn=await api('/api/login',{method:'POST',body:JSON.stringify({role:loginRole,shopId:document.querySelector('#loginShop').value,username:document.querySelector('#username').value,password:document.querySelector('#password').value,captchaId:loginCaptchaId,captchaAnswer:document.querySelector('#loginCaptchaAnswer').value})});loginCaptchaId='';document.querySelector('#password').value='';await finishAuthentication(signedIn);}
  catch(error){loginError.textContent=error.message;loginCaptchaId='';await loadCaptcha();}
});
document.querySelector('#refreshLoginCaptcha').addEventListener('click',loadCaptcha);
document.querySelector('#loginShop').addEventListener('change',updateDemoCredentials);
document.querySelector('#logoutButton').addEventListener('click',async()=>{try{await api('/api/logout',{method:'POST',body:'{}'});}catch(_){}document.querySelector('#profileMenu').open=false;showLogin();});
document.addEventListener('click',event=>{const menu=document.querySelector('#profileMenu');if(menu.open&&!menu.contains(event.target))menu.open=false;});
document.querySelector('#refreshButton').addEventListener('click',()=>refresh(true));
document.querySelector('#fpsPopulationForm').addEventListener('submit',async event=>{event.preventDefault();message.textContent='';try{await api('/api/shops/population',{method:'POST',body:JSON.stringify({population:document.querySelector('#fpsPopulationInput').value})});if(await refresh())message.textContent='People count updated. Monthly limits have been recalculated for this shop.';}catch(error){message.textContent=error.message;}});
document.querySelector('#processButton').addEventListener('click',async()=>{
  const b=document.querySelector('#processButton');b.disabled=true;b.textContent='Processing…';message.textContent='';
  try{await api('/api/process',{method:'POST',body:'{}'});if(await refresh())message.textContent='Pending requests processed. Review results in the request list or Dispatches tab.';}
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
document.querySelector('#shopForm').addEventListener('submit',async event=>{event.preventDefault();message.textContent='';try{await api('/api/shops',{method:'POST',body:JSON.stringify({id:document.querySelector('#newShopId').value.trim(),name:document.querySelector('#newShopName').value.trim(),rice:document.querySelector('#shopRice').value,wheat:document.querySelector('#shopWheat').value,sugar:document.querySelector('#shopSugar').value,population:document.querySelector('#shopPopulation').value,connectTo:document.querySelector('#shopConnect').value,travelTime:document.querySelector('#shopTime').value})});message.textContent='Fair Price Shop added and saved to records.';event.target.reset();await refresh();openPage('shops');}catch(error){message.textContent=error.message;}});
document.querySelector('#warehouseForm').addEventListener('submit',async event=>{event.preventDefault();message.textContent='';try{await api('/api/warehouses',{method:'POST',body:JSON.stringify(newLocationBody('warehouse'))});message.textContent='Warehouse added and saved to records.';event.target.reset();await refresh();openPage('warehouses');}catch(error){message.textContent=error.message;}});
document.querySelector('#warehouseStockForm').addEventListener('submit',async event=>{event.preventDefault();message.textContent='';try{await api('/api/warehouse-stock',{method:'POST',body:JSON.stringify({warehouseId:document.querySelector('#stockWarehouse').value,commodity:document.querySelector('#stockCommodity').value,quantity:document.querySelector('#stockQuantity').value})});message.textContent='Warehouse stock balance saved.';document.querySelector('#stockQuantity').value='';await refresh();openPage('warehouses');}catch(error){message.textContent=error.message;}});
const roadForm=document.querySelector('#roadForm');
const removeRoadFormBtn=document.querySelector('#removeRoadFormBtn');
if(removeRoadFormBtn)removeRoadFormBtn.addEventListener('click',async()=>{
  const from=document.querySelector('#roadFrom').value,to=document.querySelector('#roadTo').value;
  if(!from||!to){message.textContent='Please select both "From location" and "To location" to remove a road connection.';return;}
  if(from===to){message.textContent='Please select two different locations.';return;}
  await removeRoad({from,to},removeRoadFormBtn);
});
if(roadForm)roadForm.addEventListener('submit',async event=>{
  event.preventDefault();message.textContent='';
  try{
    await api('/api/roads',{method:'POST',body:JSON.stringify({from:document.querySelector('#roadFrom').value,to:document.querySelector('#roadTo').value,minutes:document.querySelector('#roadMinutes').value})});
    document.querySelector('#roadMinutes').value='';if(await refresh())message.textContent='Road connection saved. The route map now includes this path.';
    openPage('routes');
  }catch(error){message.textContent=error.message;}
});
document.querySelector('#editLocationForm').addEventListener('submit',async event=>{
  event.preventDefault();message.textContent='';
  const type=document.querySelector('#editLocationKind').value;
  const endpoint=type==='warehouse'?'/api/warehouses':'/api/shops';
  try{
    await api(endpoint,{method:'PUT',body:JSON.stringify({id:document.querySelector('#editLocationId').value,name:document.querySelector('#editLocationName').value.trim(),connectTo:document.querySelector('#editLocationConnect').value,travelTime:document.querySelector('#editLocationTime').value,population:document.querySelector('#editPopulation').value||'0'})});
    document.querySelector('#editLocationDialog').close();if(await refresh())message.textContent='Location details saved. Other road connections were kept.';
  }catch(error){message.textContent=error.message;}
});
document.querySelector('#editLocationConnect').addEventListener('change',event=>{document.querySelector('#editLocationTime').disabled=!event.target.value;});
function closeLocationDialog(){
  const dialog=document.querySelector('#editLocationDialog');
  if(dialog.open)dialog.close();
}
document.querySelector('#cancelLocationEdit').addEventListener('click',closeLocationDialog);
document.querySelector('.dialog-close').addEventListener('click',event=>{event.preventDefault();closeLocationDialog();});
document.querySelector('#editLocationDialog').addEventListener('click',event=>{
  if(event.target===event.currentTarget)closeLocationDialog();
});
function csvCell(value){
  const text=value==null?'':String(value);
  const safe=typeof value==='string'&&/^[=+@-]/.test(text)?"'"+text:text;
  return '"'+safe.replace(/"/g,'""')+'"';
}
function downloadCsvReport(fileName,headers,rows){
  const data='\uFEFF'+[headers,...rows].map(row=>row.map(csvCell).join(',')).join('\r\n');
  const blob=new Blob([data],{type:'text/csv;charset=utf-8'});
  const url=URL.createObjectURL(blob),link=document.createElement('a');
  link.href=url;link.download='ValamPDS_'+fileName+'_'+new Date().toISOString().slice(0,10)+'.csv';
  document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
  message.textContent='Report downloaded. The CSV file can be opened in Excel.';
}
function downloadDeliveryReport(){
  const rows=dispatchCache.map(d=>[d.id,d.request,d.warehouse,d.shop,d.vehicle,d.route,d.receipt,d.updated]);
  downloadCsvReport('delivery_details',['Dispatch ID','Request ID','Warehouse','Fair Price Shop','Vehicle','Route','Receipt status','Last updated'],rows);
}
function downloadWarehouseReport(){
  const rows=warehouseCache.map(w=>[w.id,w.name,w.rice,w.wheat,w.sugar,Number(w.rice||0)+Number(w.wheat||0)+Number(w.sugar||0)]);
  downloadCsvReport('warehouse_stocks',['Warehouse ID','Warehouse name','Rice (kg)','Wheat (kg)','Sugar (kg)','Total stock (kg)'],rows);
}
function downloadShopReport(){
  const rows=shopCache.map(s=>[s.id,s.name,s.population,s.rice,s.wheat,s.sugar,s.riceQuota,s.wheatQuota,s.sugarQuota,s.riceUsed,s.wheatUsed,s.sugarUsed,s.riceCommitted,s.wheatCommitted,s.sugarCommitted,Number(s.rice||0)+Number(s.wheat||0)+Number(s.sugar||0)]);
  downloadCsvReport('fair_price_shop_stocks',['Shop ID','Fair Price Shop','Registered people','Rice stock (kg)','Wheat stock (kg)','Sugar stock (kg)','Rice monthly limit (kg)','Wheat monthly limit (kg)','Sugar monthly limit (kg)','Rice received this month (kg)','Wheat received this month (kg)','Sugar received this month (kg)','Rice allocation reserved this month (kg)','Wheat allocation reserved this month (kg)','Sugar allocation reserved this month (kg)','Total stock (kg)'],rows);
}
function downloadReceiptReport(){
  const rows=receiptCache.map(receipt=>[receipt.dispatchId,receipt.shop,receipt.items,receipt.confirmedAt]);
  downloadCsvReport('receipt_history',['Dispatch ID','Fair Price Shop','Items','Confirmed at'],rows);
}
document.querySelector('#findRouteButton').addEventListener('click',showFastestRoute);
document.querySelector('#clearRouteButton').addEventListener('click',clearFastestRoute);
document.querySelector('#routeStart').addEventListener('change',()=>{activeRoute=null;const result=document.querySelector('#routeResult');result.classList.remove('route-unreachable');result.textContent='Choose a start and destination to explore the road network.';renderRoadNetwork(graphCache)});
document.querySelector('#routeEnd').addEventListener('change',()=>{activeRoute=null;const result=document.querySelector('#routeResult');result.classList.remove('route-unreachable');result.textContent='Choose a start and destination to explore the road network.';renderRoadNetwork(graphCache)});
async function downloadFreshReport(action){if(await refresh())action();}
[['downloadDispatchReport',downloadDeliveryReport],['downloadDispatchReportSmall',downloadDeliveryReport],['downloadWarehouseReport',downloadWarehouseReport],['downloadWarehouseReportSmall',downloadWarehouseReport],['downloadShopReport',downloadShopReport],['downloadShopReportSmall',downloadShopReport],['downloadReceiptReport',downloadReceiptReport]].forEach(([id,action])=>{const button=document.querySelector('#'+id);if(button)button.addEventListener('click',()=>downloadFreshReport(action));});
document.querySelector('#todayLabel').textContent=new Date().toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric'});
loadCaptcha('login');
refresh();


// ===== VIBE DASHBOARD — appended enhancement =====
(function(){
  // Patch: wrap refresh() to also sync vibe dashboard and restore icon button
  const _origRefresh = (typeof refresh === 'function') ? refresh : window.refresh;
  if(typeof _origRefresh === 'function'){
    window.refresh = refresh = async function(announce){
      const result = await _origRefresh.call(this, announce);
      // Restore the icon button text (original refresh() sets it to "Refresh")
      const rb = document.getElementById('refreshButton');
      if(rb) rb.textContent = '↻';
      syncVibeDashboard();
      return result;
    };
  }

  function syncVibeDashboard(){
    syncKanbanFromDOM();
    syncProgressFromDOM();
    syncUpcomingDeadlines();
  }

  function syncUpcomingDeadlines(){
    const list = document.getElementById('upcomingList');
    if(!list) return;
    const now = new Date();
    const months = ['JAN','FEB','MAR','APR','MAY','JUN','JUL','AUG','SEP','OCT','NOV','DEC'];
    const curMonth = months[now.getMonth()];
    const d1 = now.getDate();
    const d2 = new Date(now.getTime() + 2*86400000).getDate();
    const d3 = new Date(now.getTime() + 6*86400000).getDate();

    list.innerHTML = `
      <div class="upcoming-item">
        <div class="upcoming-date-badge" style="background:#eef2ff;color:#4f6ef7;">
          <span class="upcoming-day">${d1}</span>
          <span class="upcoming-month">${curMonth}</span>
        </div>
        <div class="upcoming-detail">
          <strong>Daily Dispatch Batch</strong>
          <small>Review prioritized shops</small>
        </div>
      </div>
      <div class="upcoming-item">
        <div class="upcoming-date-badge" style="background:#f0fdfa;color:#0d9488;">
          <span class="upcoming-day">${d2}</span>
          <span class="upcoming-month">${curMonth}</span>
        </div>
        <div class="upcoming-detail">
          <strong>Shop Stock Reconciliation</strong>
          <small>FPS monthly allocations</small>
        </div>
      </div>
      <div class="upcoming-item">
        <div class="upcoming-date-badge" style="background:#fffbeb;color:#d97706;">
          <span class="upcoming-day">${d3}</span>
          <span class="upcoming-month">${curMonth}</span>
        </div>
        <div class="upcoming-detail">
          <strong>District Buffer Audit</strong>
          <small>Warehouse grain reserves</small>
        </div>
      </div>
    `;
  }

  function syncKanbanFromDOM(){
    const rows = Array.from(document.querySelectorAll('#requestRows tr:not(.month-heading)'));
    const pending = rows.filter(tr => {
      const cells = tr.querySelectorAll('td');
      return cells[5] && cells[5].textContent.trim() === 'PENDING';
    }).map(tr => {
      const c = tr.querySelectorAll('td');
      return {id: c[0]?.textContent, shop: c[1]?.textContent, urgency: c[2]?.textContent};
    });

    const dispatchRows = Array.from(document.querySelectorAll('#dispatchRows tr'));
    const dispatched = dispatchRows.filter(tr => {
      const cells = tr.querySelectorAll('td');
      return cells[6] && cells[6].textContent.trim() === 'Waiting';
    }).map(tr => {
      const c = tr.querySelectorAll('td');
      return {id: c[0]?.textContent, shop: c[3]?.textContent, warehouse: c[2]?.textContent, receipt: 'Waiting'};
    });
    const delivered = dispatchRows.filter(tr => {
      const cells = tr.querySelectorAll('td');
      return cells[6] && cells[6].textContent.trim() === 'Confirmed';
    }).map(tr => {
      const c = tr.querySelectorAll('td');
      return {id: c[0]?.textContent, shop: c[3]?.textContent, warehouse: c[2]?.textContent, receipt: 'Confirmed'};
    });

    renderKanbanCol('kanban-pending-cards', 'kanban-pending-count', pending);
    renderKanbanCol('kanban-dispatched-cards', 'kanban-dispatched-count', dispatched);
    renderKanbanCol('kanban-delivered-cards', 'kanban-delivered-count', delivered);
  }

  function renderKanbanCol(cardsId, countId, items){
    const container = document.getElementById(cardsId);
    const countEl = document.getElementById(countId);
    if(!container) return;
    if(countEl) countEl.textContent = items.length;
    container.replaceChildren();
    if(!items.length){
      const e = document.createElement('div');
      e.className = 'kanban-empty';
      e.textContent = 'No items here';
      container.append(e);
      return;
    }
    items.slice(0,5).forEach(item => {
      const card = document.createElement('div');
      card.className = 'kanban-card';
      const urgency = item.urgency || (item.receipt === 'Confirmed' ? 'DONE' : 'IN TRANSIT');
      const bc = urgency==='HIGH'?'badge-high':urgency==='DONE'?'badge-done':urgency==='IN TRANSIT'?'badge-transit':'badge-med';
      const shopCode = (item.shop||'?').replace(/[^A-Z0-9]/gi,'').slice(0,2).toUpperCase();
      const statusText=item.receipt==='Confirmed'?'Receipt confirmed':item.receipt==='Waiting'?'Awaiting receipt':'Pending request';
      card.innerHTML = `
        <div class="kanban-card-top">
          <span class="kanban-badge ${bc}">${urgency}</span>
        </div>
        <div class="kanban-card-title">${item.id||'—'}</div>
        <div class="kanban-card-sub">${item.shop||item.warehouse||'—'}</div>
        <div class="kanban-card-foot">
          <span class="kanban-card-date">${statusText}</span>
          <span class="kanban-avatar">${shopCode}</span>
        </div>`;
      container.append(card);
    });
  }

  function syncProgressFromDOM(){
    const total = parseInt(document.getElementById('requestCount')?.textContent)||0;
    const delivered = parseInt(document.getElementById('successfulDeliveryCount')?.textContent)||0;
    const pct = total>0?Math.round((delivered/total)*100):0;
    const fill = document.getElementById('priorityProgressFill');
    const label = document.getElementById('priorityProgressLabel');
    if(fill) fill.style.width = pct+'%';
    if(label) label.textContent = pct+'% of recorded requests have confirmed receipts';
  }

  // Hook quick tool buttons in right panel
  document.querySelectorAll('.quick-tool-btn[data-go]').forEach(b => {
    b.addEventListener('click', () => openPage(b.dataset.go));
  });
  const qrReports = document.getElementById('qr-reports');
  if(qrReports){
    qrReports.addEventListener('click', () => {
      openPage('dashboard');
      const rc = document.querySelector('.report-center');
      if(rc) rc.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  }
  document.querySelectorAll('.allocation-head button[data-go]').forEach(b => {
    b.addEventListener('click', () => openPage(b.dataset.go));
  });
  document.querySelectorAll('.upcoming-head button[data-go]').forEach(b => {
    b.addEventListener('click', () => openPage(b.dataset.go));
  });

  // Right panel search navigates to relevant sections
  const rsInput = document.querySelector('.right-search-input');
  if(rsInput){
    rsInput.addEventListener('keydown', e => {
      if(e.key === 'Enter') {
        const q = rsInput.value.toLowerCase();
        if(q.includes('request')) openPage('requests');
        else if(q.includes('dispatch')) openPage('dispatches');
        else if(q.includes('shop')) openPage('shops');
        else if(q.includes('warehouse')) openPage('warehouses');
        else if(q.includes('route')||q.includes('road')) openPage('routes');
        else message.textContent=q.trim()?'No matching section found. Try requests, dispatches, shops, warehouses, or roads.':'Enter a section name to navigate.';
        rsInput.value = '';
      }
    });
  }

  // Override fleet render to also update allocation panel
  const _origRenderFleet = (typeof renderFleetCards === 'function') ? renderFleetCards : window.renderFleetCards;
  if(typeof _origRenderFleet === 'function'){
    window.renderFleetCards = renderFleetCards = function(vehicles){
      _origRenderFleet.call(this, vehicles);
      updateAllocationPanel(vehicles);
    };
  }

  function updateAllocationPanel(vehicles){
    const list = document.getElementById('allocationList');
    if(!list) return;
    list.replaceChildren();
    if(!vehicles||!vehicles.length){
      list.innerHTML = '<div class="allocation-item"><div class="allocation-name">No vehicles configured</div></div>';
      return;
    }
    vehicles.forEach(v => {
      const item = document.createElement('div');
      item.className = 'allocation-item';
      const busy = !v.available;
      item.innerHTML = `
        <div class="allocation-name">${v.name}</div>
        <div class="allocation-bar-wrap"><div class="allocation-bar ${busy?'allocation-bar-busy':'allocation-bar-free'}" style="width:${busy?'100':'15'}%"></div></div>
        <div class="allocation-tasks">${v.available?'Free':'On delivery'}</div>
      `;
      list.append(item);
    });
  }

  // Sync on first load (after initial refresh() completes)
  setTimeout(() => syncVibeDashboard(), 1200);
})();
