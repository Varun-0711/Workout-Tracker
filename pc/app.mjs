import { TABS, parseWorkbook, filterWorkouts, totals, volume, exerciseProgress, dailyWeights, demoWorkbook, dayKey } from './data.mjs';

const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt = (value,digits=0) => new Intl.NumberFormat(undefined,{maximumFractionDigits:digits}).format(value);
const dateText = date => new Date(date).toLocaleDateString(undefined,{day:'numeric',month:'short'});
const minutes = seconds => seconds == null ? '—' : `${fmt(seconds/60)} min`;
const pace = seconds => seconds>0 ? `${Math.floor(seconds/60)}:${String(Math.round(seconds%60)).padStart(2,'0')} /km` : '—';
const empty = (title,body) => `<div class="empty"><strong>${esc(title)}</strong>${esc(body)}</div>`;
let data={workouts:[],weighIns:[],routines:[]}, mode='empty', days='30', activity='all', selectedExercise='', search='';
let token=null, expiresAt=0, sheetId=null, busy=false, googleReady=null;
let calendarMonth=new Date(new Date().getFullYear(),new Date().getMonth(),1), selectedDay=null;
const config=window.WORKOUT_CONFIG||{};

function status(message){$('status').textContent=message;}
function setBusy(value){busy=value; for(const id of ['connect','refresh','change','disconnect','demo']) $(id).disabled=value;}
function current(){return filterWorkouts(data.workouts,days,activity);}
function filteredWeights(){
  const start=new Date();start.setHours(0,0,0,0);start.setDate(start.getDate()-Number(days||0)+1);
  return data.weighIns.filter(w=>days==='all'||w.date>=start);
}
function applyData(next,source){
  $('details').close();$('detail-content').replaceChildren();
  data=next;mode=source;selectedDay=null;search='';
  const types=[...new Set(data.workouts.map(w=>w.activity))].sort();
  $('activity').innerHTML='<option value="all">All activities</option>'+types.map(t=>`<option value="${esc(t)}">${esc(t)}</option>`).join('');
  if(!types.includes(activity)) activity='all'; $('activity').value=activity;
  $('source-label').textContent=source==='demo'?'Demo data · not your workouts':'Google Sheets connected';
  $('source-time').textContent=source==='demo'?'Sample training history for exploring the dashboard.':`Last read ${new Date().toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'})} · snapshot from your phone`;
  $('source-dot').classList.toggle('live',source==='google');
  $('refresh').hidden=source!=='google';$('change').hidden=source!=='google';$('disconnect').hidden=false;
  $('connect').textContent=source==='google'?'Google Sheets connected':'Connect Google Sheets';
  render();
}
function stats(workouts){
  const t=totals(workouts);
  return `<section class="stats" aria-label="Training totals">${[
    ['Workouts',fmt(t.count),'sessions',`${new Set(workouts.map(w=>dayKey(w.date))).size} active days`],
    ['Training time',fmt(t.seconds/3600,1),'hrs','Across completed workouts'],
    ['Estimated calories',t.calorieCount?fmt(t.calories):'—','kcal',`${t.calorieCount} workouts with an estimate`],
    ['Distance',fmt(t.distance/1000,1),'km','Recorded GPS distance']
  ].map(([label,value,unit,note])=>`<div class="stat"><div class="stat-label">${label}</div><div class="stat-value">${value} <small>${unit}</small></div><div class="stat-note">${note}</div></div>`).join('')}</section>`;
}
function chart(points,{unit='',label='Progress chart',second=false}={}){
  const valid=points.filter(p=>Number.isFinite(p.value));
  if(!valid.length)return empty('Nothing to chart yet','Your logged data will appear here.');
  const width=650,height=210,left=47,right=15,top=15,bottom=30;
  const vals=valid.flatMap(p=>second?[p.value,p.trend]:[p.value]).filter(Number.isFinite);
  let low=Math.min(...vals),high=Math.max(...vals);const pad=(high-low)*.16||Math.max(1,high*.05);low=Math.max(0,low-pad);high+=pad;
  const xMin=Math.min(...valid.map(p=>+p.date)),xMax=Math.max(...valid.map(p=>+p.date));
  const x=p=>left+(xMax===xMin?.5:(+p.date-xMin)/(xMax-xMin))*(width-left-right);
  const y=v=>top+(high-v)/(high-low)*(height-top-bottom);
  const line=key=>valid.filter(p=>Number.isFinite(p[key])).map((p,i)=>`${i?'L':'M'}${x(p).toFixed(1)},${y(p[key]).toFixed(1)}`).join(' ');
  const grid=[0,.5,1].map(f=>{const v=low+(high-low)*f;return `<line class="gridline" x1="${left}" x2="${width-right}" y1="${y(v)}" y2="${y(v)}"/><text x="${left-8}" y="${y(v)+3}" text-anchor="end">${fmt(v,v<10?1:0)}</text>`;}).join('');
  return `<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${esc(label)}"><title>${esc(label)}</title>${grid}<path class="line" d="${line('value')}"/>${second?`<path class="trend" d="${line('trend')}"/>`:''}${valid.map(p=>`<circle class="point" cx="${x(p)}" cy="${y(p.value)}" r="3.5"><title>${esc(dateText(p.date))}: ${fmt(p.value,1)} ${esc(unit)}</title></circle>`).join('')}<text x="${left}" y="${height-5}">${esc(dateText(valid[0].date))}</text><text x="${width-right}" y="${height-5}" text-anchor="end">${esc(dateText(valid.at(-1).date))}</text></svg>`;
}
function weeklyChart(workouts){
  const buckets=new Map();
  const now=new Date(), start=days==='all'?(workouts[0]?.date||now):new Date(now.getFullYear(),now.getMonth(),now.getDate()-Number(days)+1);
  const cursor=new Date(start);cursor.setHours(0,0,0,0);cursor.setDate(cursor.getDate()-((cursor.getDay()+6)%7));
  for(let i=0;cursor<=now&&i<520;i++,cursor.setDate(cursor.getDate()+7))buckets.set(dayKey(cursor),{date:new Date(cursor),value:0});
  for(const w of workouts){const d=new Date(w.date);d.setDate(d.getDate()-((d.getDay()+6)%7));const k=dayKey(d);if(!buckets.has(k))buckets.set(k,{date:d,value:0});buckets.get(k).value+=(w.seconds||0)/60;}
  return chart([...buckets.values()].sort((a,b)=>a.date-b.date),{unit:'min',label:'Training minutes per week'});
}
function calendar(workouts){
  const first=new Date(calendarMonth),year=first.getFullYear(),month=first.getMonth();
  const blanks=(first.getDay()+6)%7,count=new Date(year,month+1,0).getDate(),today=dayKey(new Date());
  const logged=new Set(workouts.map(w=>dayKey(w.date)));
  const cells=Array.from({length:blanks},()=>'<span></span>').join('')+Array.from({length:count},(_,i)=>{
    const key=dayKey(new Date(year,month,i+1));return `<button class="day ${logged.has(key)?'logged':''} ${key===today?'today':''} ${key===selectedDay?'selected':''}" data-day="${key}" aria-label="${key}${logged.has(key)?', workouts logged':''}" aria-pressed="${key===selectedDay}">${i+1}</button>`;
  }).join('');
  const selected=workouts.filter(w=>dayKey(w.date)===selectedDay);
  return `<div class="panel-heading"><h2>Training calendar</h2><div class="calendar-head"><button id="prev-month" aria-label="Previous month">‹</button><span class="caption">${esc(first.toLocaleDateString(undefined,{month:'short',year:'numeric'}))}</span><button id="next-month" aria-label="Next month">›</button></div></div><div class="calendar">${['M','T','W','T','F','S','S'].map(d=>`<span class="weekday">${d}</span>`).join('')}${cells}</div><div class="calendar-summary">${workouts.filter(w=>w.date.getMonth()===month&&w.date.getFullYear()===year).length} workouts this month · filtered by selected range</div>${selectedDay?`<div class="day-result"><span class="caption">${esc(selectedDay)}</span>${selected.length?selected.map(w=>`<button data-workout="${esc(w.id)}">${esc(w.name)} · ${minutes(w.seconds)}</button>`).join(''):'<p class="caption">Nothing logged for this day in this range.</p>'}</div>`:''}`;
}
function workoutTable(workouts){
  if(!workouts.length)return empty('No workouts in this view','Try a wider date range or a different activity.');
  return `<div class="table-wrap"><table><thead><tr><th>Workout</th><th>Date</th><th>Duration</th><th>Activity</th><th>Estimated kcal</th><th>Distance</th></tr></thead><tbody>${[...workouts].reverse().map(w=>`<tr><td><button data-workout="${esc(w.id)}">${esc(w.name)}</button></td><td>${esc(dateText(w.date))}</td><td>${minutes(w.seconds)}</td><td><span class="badge">${esc(w.activity)}</span></td><td>${w.calories==null?'—':fmt(w.calories)}</td><td>${w.distance>0?`${fmt(w.distance/1000,2)} km`:'—'}</td></tr>`).join('')}</tbody></table></div>`;
}
function activityMix(workouts){
  const counts=new Map();for(const w of workouts) counts.set(w.activity,(counts.get(w.activity)||0)+1);
  return counts.size?`<div class="activity-bars">${[...counts].sort((a,b)=>b[1]-a[1]).map(([name,count])=>`<div><div class="activity-bar-head"><span>${esc(name)}</span><span>${count} ${count===1?'workout':'workouts'}</span></div><div class="bar-track"><div class="bar-fill" style="width:${100*count/workouts.length}%"></div></div></div>`).join('')}</div>`:empty('A fresh start','Your activity mix will appear after your first sync.');
}
function overview(workouts){
  return `${stats(workouts)}<div class="grid"><section class="panel"><div class="panel-heading"><h2>A little, consistently.</h2><span class="caption">Training minutes / week</span></div>${weeklyChart(workouts)}<div class="legend"><span><i></i>Weekly training time</span></div></section><section class="panel">${calendar(workouts)}</section></div><div class="grid"><section class="panel"><div class="panel-heading"><h2>Recent workouts</h2><a class="caption" href="#history">View all history</a></div>${workoutTable(workouts.slice(-5))}</section><section class="panel"><div class="panel-heading"><h2>Your activity mix</h2><span class="caption">Sessions</span></div>${activityMix(workouts)}</section></div>`;
}
function progress(workouts){
  const exercises=[...new Set(workouts.filter(w=>w.activity==='Strength').flatMap(w=>w.sets.filter(s=>s.completed&&s.reps>0).map(s=>s.exercise)))].sort();
  if(!exercises.includes(selectedExercise))selectedExercise=exercises[0]||'';
  const points=exerciseProgress(workouts,selectedExercise), weighted=points.some(p=>p.weight>0);
  const bestWeight=Math.max(0,...points.map(p=>p.weight)), bestReps=Math.max(0,...points.map(p=>p.reps)), e1rm=Math.max(0,...points.map(p=>p.e1rm));
  const weights=dailyWeights(data.weighIns);const visible=weights.filter(w=>filteredWeights().some(v=>dayKey(v.date)===dayKey(w.date)));
  return `<div class="grid"><section class="panel span-all"><div class="panel-heading"><h2>Exercise progress</h2><span class="caption">Completed strength sets only</span></div><label class="caption" for="exercise-select">Exercise</label><br><select id="exercise-select">${exercises.map(e=>`<option ${e===selectedExercise?'selected':''} value="${esc(e)}">${esc(e)}</option>`).join('')}</select>${points.length?`<div class="records"><div class="record"><span class="stat-label">${weighted?'Heaviest completed set':'Most reps in a set'}</span><strong>${weighted?`${fmt(bestWeight,1)} kg`:`${fmt(bestReps)} reps`}</strong><span class="caption">Within selected range</span></div><div class="record"><span class="stat-label">Estimated one-rep max</span><strong>${e1rm?`${fmt(e1rm,1)} kg`:'—'}</strong><span class="caption">Epley formula · sets of 1–10 reps</span></div><div class="record"><span class="stat-label">Completed training volume</span><strong>${fmt(points.reduce((n,p)=>n+p.volume,0))} kg</strong><span class="caption">Sum of external weight × reps</span></div></div><div class="section-gap">${chart(points.map(p=>({date:p.date,value:weighted?p.weight:p.reps})),{unit:weighted?'kg':'reps',label:`${selectedExercise}: ${weighted?'heaviest weight':'most reps'} per workout`})}</div><p class="caption">${weighted?'Heaviest completed weight':'Most completed reps'} per workout. Estimates describe your logged sets, not a tested maximum.</p>`:empty('Your strength progress starts here','Complete sets on your phone and sync to track records over time.')}</section><section class="panel"><div class="panel-heading"><h2>Body weight</h2><span class="caption">Daily average · kg</span></div>${chart(visible.map(w=>({date:w.date,value:w.weight,trend:w.trend})),{unit:'kg',second:true,label:'Body weight daily average and seven-day trend'})}<div class="legend"><span><i></i>Daily average</span><span>Dashed: 7-day trend</span></div><p class="caption">Weight uses the selected date range across all activities.</p></section><section class="panel"><div class="panel-heading"><h2>Training volume</h2><span class="caption">External weight × reps · kg</span></div>${chart(workouts.filter(w=>w.activity==='Strength').map(w=>({date:w.date,value:volume(w)})),{unit:'kg',label:'Completed strength volume per workout'})}<p class="caption">Only completed weighted strength sets contribute. Jump counts and body weight are not treated as lifting volume.</p></section><section class="panel span-all"><div class="panel-heading"><h2>Distance & pace</h2><span class="caption">${activity==='all'?'Select one activity to compare similar sessions':'Recorded GPS sessions'}</span></div>${workoutTable(workouts.filter(w=>w.distance>0))}<p class="caption">Open a workout for its recorded average pace. Different routes and activities are not directly comparable.</p></section></div>`;
}
function routines(){
  const groups=new Map();for(const r of data.routines){if(activity!=='all'&&r.Activity!==activity)continue;const key=String(r.Routine);if(!groups.has(key))groups.set(key,[]);groups.get(key).push(r);}
  return `<section class="panel"><div class="panel-heading"><h2>Saved on your phone</h2><span class="caption">Routines are independent of the date filter</span></div>${groups.size?[...groups].map(([name,rows])=>`<article class="routine"><h3>${esc(name)}</h3><p class="caption">${esc(rows[0].Activity)}${rows[0].Activity!=='Strength'?` · ${esc(rows[0]['Timer mode'])} · ${esc(rows[0]['Work minutes'])} min work / ${esc(rows[0]['Rest minutes'])} min rest`:''}</p><div class="table-wrap"><table><thead><tr><th>Set</th><th>Exercise</th><th>Reps / jumps</th><th>Weight</th></tr></thead><tbody>${rows.filter(r=>r.Exercise).map(r=>`<tr><td>${esc(r['Set number'])}</td><td>${esc(r.Exercise)}</td><td>${esc(r['Reps or jumps'])}</td><td>${r['Weight (kg)']!==''?`${esc(r['Weight (kg)'])} kg`:'—'}</td></tr>`).join('')}</tbody></table></div></article>`).join(''):empty('No saved routines','Create a routine on your phone, then sync to see it here.')}</section>`;
}
function render(){
  let page=location.hash.slice(1)||'overview';if(!['overview','history','progress','routines'].includes(page))page='overview';
  const titles={overview:'Your progress, at a glance.',history:'Every session has a story.',progress:'See your work add up.',routines:'Your training, laid out.'};
  $('page-title').textContent=titles[page];document.querySelectorAll('nav a').forEach(a=>{a.classList.toggle('active',a.dataset.page===page);if(a.dataset.page===page)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});
  $('range-label').textContent=days==='all'?'All recorded history':`Last ${days} days · through ${dateText(new Date())}`;
  const workouts=current();
  if(mode==='empty'){$('content').innerHTML=`<section class="panel">${empty('Make room for your progress.','Connect the spreadsheet synced from your phone, or explore the demo to take a look around.')}</section>`;return;}
  $('content').innerHTML=page==='overview'?overview(workouts):page==='progress'?progress(workouts):page==='routines'?routines():`${stats(workouts)}<section class="panel"><div class="panel-heading"><h2>Workout history</h2><input id="history-search" type="search" placeholder="Search workouts or exercises" aria-label="Search workouts or exercises" value="${esc(search)}"></div><div id="history-table">${workoutTable(searchWorkouts(workouts))}</div></section>`;
  $('prev-month')?.addEventListener('click',()=>{calendarMonth.setMonth(calendarMonth.getMonth()-1);render();});
  $('next-month')?.addEventListener('click',()=>{calendarMonth.setMonth(calendarMonth.getMonth()+1);render();});
  $('exercise-select')?.addEventListener('change',e=>{selectedExercise=e.target.value;render();});
  $('history-search')?.addEventListener('input',e=>{search=e.target.value;$('history-table').innerHTML=workoutTable(searchWorkouts(current()));});
}
function searchWorkouts(workouts){const q=search.toLocaleLowerCase();return workouts.filter(w=>[w.name,w.activity,...w.sets.map(s=>s.exercise)].some(v=>v.toLocaleLowerCase().includes(q)));}
function details(id){
  const w=data.workouts.find(w=>w.id===id);if(!w)return;
  $('detail-content').innerHTML=`<span class="eyebrow">${esc(w.activity)} · ${esc(w.date.toLocaleString())}</span><h2>${esc(w.name)}</h2><div class="detail-stats"><span>${minutes(w.seconds)}</span><span>${w.calories==null?'No calorie estimate':`${fmt(w.calories)} estimated kcal`}</span>${w.distance>0?`<span>${fmt(w.distance/1000,2)} km</span><span>${pace(w.pace)}</span>`:''}${w.jumps>0?`<span>${fmt(w.jumps)} jumps</span>`:''}</div>${w.sets.length?`<div class="table-wrap"><table><thead><tr><th>Set</th><th>Exercise</th><th>${w.activity==='Jump Rope'?'Jumps':'Reps'}</th>${w.activity==='Strength'?'<th>Weight</th>':'<th>Active time</th>'}<th>Status</th></tr></thead><tbody>${w.sets.map(s=>`<tr><td>${esc(s.index)}</td><td>${esc(s.exercise)}</td><td>${s.reps==null?'—':fmt(s.reps)}</td><td>${w.activity==='Strength'?(s.weight>0?`${fmt(s.weight,1)} kg`:'Body weight'):(s.seconds>0?minutes(s.seconds):'—')}</td><td>${s.completed?'Completed':'Not completed'}</td></tr>`).join('')}</tbody></table></div>`:'<p class="caption">This activity does not have recorded sets.</p>'}<p class="caption section-gap">${w.weight?`Body weight recorded for this session: ${fmt(w.weight,1)} kg. `:''}Changes to workouts are made on your phone.</p>`;
  $('details').showModal();
}
document.addEventListener('click',e=>{const workout=e.target.closest('[data-workout]');if(workout)details(workout.dataset.workout);const day=e.target.closest('[data-day]');if(day){selectedDay=day.dataset.day;render();}});
$('close-details').onclick=()=>$('details').close();
$('details').addEventListener('click',e=>{if(e.target===$('details')){const r=$('details').getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)$('details').close();}});
window.addEventListener('hashchange',render);
document.querySelectorAll('[data-days]').forEach(b=>b.onclick=()=>{days=b.dataset.days;document.querySelectorAll('[data-days]').forEach(x=>x.setAttribute('aria-pressed',String(x===b)));render();});
$('activity').onchange=e=>{activity=e.target.value;render();};
$('demo').onclick=()=>{if(busy)return;sheetId=null;applyData(parseWorkbook(demoWorkbook()),'demo');status('You’re viewing sample data. Connect Google Sheets to see your own workouts.');};

function loadScript(src){return new Promise((resolve,reject)=>{const s=document.createElement('script');s.src=src;s.onload=resolve;s.onerror=()=>{s.remove();reject(new Error('Google could not load. Check your internet connection or browser content blockers.'));};document.head.append(s);});}
async function loadGoogle(){
  if(!config.clientId||!config.apiKey||!config.appId||config.clientId.startsWith('YOUR_')){$('setup').hidden=false;throw new Error('Google connection is not configured yet. See pc/README.md.');}
  if(!googleReady)googleReady=Promise.all([loadScript('https://accounts.google.com/gsi/client'),loadScript('https://apis.google.com/js/api.js')]).then(()=>new Promise((resolve,reject)=>window.gapi.load('picker',{callback:resolve,onerror:()=>reject(new Error('Google Picker could not load.')),timeout:15000,ontimeout:()=>reject(new Error('Google Picker timed out. Try again.'))}))).catch(e=>{googleReady=null;throw e;});
  return googleReady;
}
async function authorize(){
  if(token&&Date.now()<expiresAt)return;
  await new Promise((resolve,reject)=>{
    const client=google.accounts.oauth2.initTokenClient({client_id:config.clientId,scope:'https://www.googleapis.com/auth/drive.file',include_granted_scopes:false,callback:response=>{
      if(response.error){reject(new Error(response.error_description||response.error));return;}
      if(!google.accounts.oauth2.hasGrantedAllScopes(response,'https://www.googleapis.com/auth/drive.file')){reject(new Error('File access was not granted. Try connecting again.'));return;}
      token=response.access_token;expiresAt=Date.now()+Number(response.expires_in||3600)*1000-60000;resolve();
    },error_callback:()=>reject(new Error('Google sign-in was closed or blocked. Allow popups and try again.'))});
    client.requestAccessToken({prompt:token?'':'select_account'});
  });
}
function pickSheet(){
  return new Promise((resolve,reject)=>{
    const picker=new google.picker.PickerBuilder().addView(new google.picker.DocsView(google.picker.ViewId.SPREADSHEETS)).setOAuthToken(token).setDeveloperKey(config.apiKey).setAppId(config.appId).setOrigin(location.origin).setTitle('Choose your Workout Tracker spreadsheet').setCallback(event=>{
      if(event.action===google.picker.Action.PICKED)resolve(event.docs[0].id);
      else if(event.action===google.picker.Action.CANCEL)resolve(null);
      else if(event.action==='error')reject(new Error('Google Picker could not open. Check the developer configuration.'));
    }).build();picker.setVisible(true);
  });
}
async function readSheet(id){
  const query=new URLSearchParams({valueRenderOption:'UNFORMATTED_VALUE'});TABS.forEach(tab=>query.append('ranges',`'${tab}'!A:Z`));
  const response=await fetch(`https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(id)}/values:batchGet?${query}`,{headers:{Authorization:`Bearer ${token}`},cache:'no-store'});
  if(!response.ok){if(response.status===401){token=null;expiresAt=0;}throw new Error(response.status===401?'Google access expired. Click Refresh to sign in again.':response.status===403?'Google cannot read this spreadsheet. Check your account, selected file, and Sheets API setup.':response.status===404?'The spreadsheet could not be found. Choose it again.':`Google Sheets could not be read (HTTP ${response.status}). Your displayed data has not changed.`);}
  const body=await response.json();return parseWorkbook(Object.fromEntries(TABS.map((tab,i)=>[tab,body.valueRanges?.[i]?.values||[]])));
}
async function connect(change=true){
  if(busy)return;setBusy(true);status('Connecting to Google…');
  try{await loadGoogle();await authorize();const id=change||!sheetId?await pickSheet():sheetId;if(!id){status('Spreadsheet selection cancelled.');return;}status('Reading your phone’s latest snapshot…');const next=await readSheet(id);sheetId=id;applyData(next,'google');$('setup').hidden=true;status('Snapshot loaded. To see new workouts, sync on your phone and then click Refresh here.');}
  catch(error){status(error.message);}finally{setBusy(false);}
}
$('connect').onclick=()=>connect(true);$('change').onclick=()=>connect(true);$('refresh').onclick=()=>connect(false);
$('disconnect').onclick=()=>{
  // Forget this browser session without revoking the Android app's shared Cloud-project grant.
  token=null;expiresAt=0;sheetId=null;data={workouts:[],weighIns:[],routines:[]};mode='empty';
  $('details').close();$('detail-content').replaceChildren();
  $('source-label').textContent='No data connected';$('source-time').textContent='Sync on your phone, then connect here.';$('source-dot').classList.remove('live');
  ['refresh','change','disconnect'].forEach(id=>$(id).hidden=true);$('connect').textContent='Connect Google Sheets';status('Disconnected. Workout data and the access token have been cleared from this page.');render();
};
render();
