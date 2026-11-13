export const TABS = ['Workouts', 'Sets', 'Weigh-ins', 'Routines'];
export const number = value => value === '' || value == null || !Number.isFinite(Number(value)) ? null : Number(value);
export const dayKey = value => { const d = new Date(value); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; };
export function objects(rows = []) {
  const [headers = [], ...values] = rows;
  return values.filter(row => row.some(v => v !== '' && v != null)).map(row => Object.fromEntries(headers.map((h,i) => [h, row[i] ?? ''])));
}
export function parseWorkbook(tabs) {
  const header = tabs.Workouts?.[0] || [];
  if (!['Workout ID', 'Started (UTC)', 'Activity'].every(h => header.includes(h))) throw new Error('Select the Workout Tracker spreadsheet created by your phone. The Workouts tab is missing or has an unsupported format.');
  const sets = objects(tabs.Sets).map(r => ({ workoutId:String(r['Workout ID']), index:number(r['Set number']), exercise:String(r.Exercise), reps:number(r['Reps or jumps']), weight:number(r['Weight (kg)']), completed:String(r.Completed).toLowerCase() === 'true', seconds:number(r['Active time (seconds)']) }));
  const workouts = objects(tabs.Workouts).map(r => ({ id:String(r['Workout ID']), date:new Date(r['Started (UTC)']), ended:new Date(r['Ended (UTC)']), activity:String(r.Activity), name:String(r.Name || r.Activity), seconds:number(r['Duration (seconds)']), calories:number(r['Estimated calories']), weight:number(r['Body weight (kg)']), distance:number(r['Distance (m)']), pace:number(r['Average pace (seconds/km)']), jumps:number(r['Jump count']), sets:sets.filter(s=>s.workoutId===String(r['Workout ID'])) })).filter(w=>Number.isFinite(+w.date)).sort((a,b)=>a.date-b.date);
  const weighIns = objects(tabs['Weigh-ins']).map(r=>({date:new Date(r['Timestamp (UTC)']), weight:number(r['Weight (kg)'])})).filter(w=>Number.isFinite(+w.date)&&w.weight>0).sort((a,b)=>a.date-b.date);
  return {workouts, weighIns, routines:objects(tabs.Routines)};
}
export function filterWorkouts(workouts, days, activity='all', now=new Date()) {
  const start = new Date(now); start.setHours(0,0,0,0);
  if (days !== 'all') start.setDate(start.getDate()-Number(days)+1);
  return workouts.filter(w=>(days==='all'||w.date>=start)&&w.date<=now&&(activity==='all'||w.activity===activity));
}
export function volume(workout) { return workout.activity==='Strength' ? workout.sets.filter(s=>s.completed&&s.reps>0&&s.weight>0).reduce((n,s)=>n+s.reps*s.weight,0) : 0; }
export function totals(workouts) {
  return { count:workouts.length, seconds:workouts.reduce((n,w)=>n+(w.seconds||0),0), calories:workouts.reduce((n,w)=>n+(w.calories||0),0), calorieCount:workouts.filter(w=>w.calories!=null).length, volume:workouts.reduce((n,w)=>n+volume(w),0), distance:workouts.reduce((n,w)=>n+(w.distance||0),0) };
}
export function exerciseProgress(workouts, exercise) {
  return workouts.filter(w=>w.activity==='Strength').map(w=>{
    const sets=w.sets.filter(s=>s.exercise===exercise&&s.completed&&s.reps>0);
    return {date:w.date, sets, weight:Math.max(0,...sets.map(s=>s.weight||0)), reps:Math.max(0,...sets.map(s=>s.reps)), volume:sets.reduce((n,s)=>n+(s.weight||0)*s.reps,0), e1rm:Math.max(0,...sets.filter(s=>s.weight>0&&s.reps<=10).map(s=>s.reps===1?s.weight:s.weight*(1+s.reps/30)))};
  }).filter(p=>p.sets.length);
}
export function dailyWeights(weighIns) {
  const groups=new Map();
  for(const w of weighIns){const k=dayKey(w.date); const list=groups.get(k)||[];list.push(w);groups.set(k,list);}
  const days=[...groups.values()].map(list=>({date:list[0].date,weight:list.reduce((n,w)=>n+w.weight,0)/list.length})).sort((a,b)=>a.date-b.date);
  return days.map(d=>{const start=new Date(d.date);start.setDate(start.getDate()-6);start.setHours(0,0,0,0);const window=days.filter(w=>w.date>=start&&w.date<=d.date);return {...d,trend:window.reduce((n,w)=>n+w.weight,0)/window.length};});
}
export function demoWorkbook(now=new Date()) {
  const rows=[['Workout ID','Started (UTC)','Ended (UTC)','Activity','Name','Duration (seconds)','Estimated calories','Body weight (kg)','Height (cm)','Distance (m)','Average pace (seconds/km)','Jump count']];
  const sets=[['Workout ID','Set number','Exercise','Reps or jumps','Weight (kg)','Completed','Active time (seconds)']];
  const weights=[['Timestamp (UTC)','Weight (kg)']];
  for(let i=70;i>=0;i--){
    const date=new Date(now);date.setDate(date.getDate()-i);date.setHours(7,30,0,0);
    if(i%3===0) weights.push([date.toISOString(), Math.round((72+i*.025+Math.sin(i)*.2)*10)/10]);
    if(i%7===1||i%7===4) continue;
    const strength=i%3!==0, activity=strength?'Strength':i%2===0?'Running':'Jump Rope', seconds=strength?2400:activity==='Running'?1800:720;
    const id=String(+date), jumps=activity==='Jump Rope'?720:0;
    rows.push([id,date.toISOString(),new Date(+date+seconds*1000).toISOString(),activity,strength?'Full body':activity,seconds,strength?180:activity==='Running'?310:120,72,185,activity==='Running'?4800:'',activity==='Running'?375:'',jumps||'']);
    if(strength) ['Bench Press','Squat','Push-up'].forEach((exercise,j)=>{for(let s=1;s<=3;s++)sets.push([id,s,exercise,8+(j===2?4:0),j===2?'':40+j*20+Math.floor((70-i)/14)*2.5,true,0]);});
    else if(jumps) for(let s=1;s<=3;s++)sets.push([id,s,'Jump Rope',240,'',true,120]);
  }
  return {Workouts:rows,Sets:sets,'Weigh-ins':weights,Routines:[['Routine','Activity','Timer mode','Work minutes','Rest minutes','Setup seconds','Strength rest seconds','Set number','Exercise','Reps or jumps','Weight (kg)'],['Full body','Strength','intervals','30','5','10',90,1,'Bench Press',8,50],['Full body','Strength','intervals','30','5','10',90,2,'Squat',8,70],['Jump rope intervals','Jump Rope','intervals','2','1','10',90,1,'Jump Rope',240,'']]};
}
