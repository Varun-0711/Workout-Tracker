import test from 'node:test';
import assert from 'node:assert/strict';
import { parseWorkbook, demoWorkbook, volume, exerciseProgress, filterWorkouts, dailyWeights, totals } from './data.mjs';

test('parses the Android snapshot and joins sets without changing IDs',()=>{
  const workbook=demoWorkbook(new Date(2026,10,13,12));
  workbook.Workouts.push(['broken','not-a-date','','Running']);
  const data=parseWorkbook(workbook);
  assert.ok(data.workouts.length>20);
  const strength=data.workouts.find(w=>w.activity==='Strength');
  assert.equal(strength.sets.length,9);
  assert.ok(strength.sets.every(s=>s.workoutId===strength.id));
  assert.ok(data.workouts.every(w=>Number.isFinite(+w.date)));
  assert.equal(data.weighIns.length,24);
  assert.throws(()=>parseWorkbook({Workouts:[['Not the right sheet']]}),/unsupported format/);
});
test('volume counts completed weighted strength sets, never jump counts or body weight',()=>{
  const sets=[{completed:true,reps:8,weight:50},{completed:false,reps:8,weight:100},{completed:true,reps:20,weight:null}];
  assert.equal(volume({activity:'Strength',sets}),400);
  assert.equal(volume({activity:'Jump Rope',sets}),0);
  const progress=exerciseProgress([{activity:'Strength',date:new Date(),sets:sets.map(s=>({...s,exercise:'Push-up'}))}],'Push-up');
  assert.equal(progress[0].volume,400);
});
test('records exclude unfinished sets and high-rep 1RM estimates',()=>{
  const points=exerciseProgress([{activity:'Strength',date:new Date(),sets:[
    {exercise:'Squat',completed:true,reps:1,weight:80},
    {exercise:'Squat',completed:true,reps:5,weight:70},
    {exercise:'Squat',completed:true,reps:20,weight:60},
    {exercise:'Squat',completed:false,reps:1,weight:120}
  ]}],'Squat');
  assert.equal(points[0].weight,80);
  assert.equal(points[0].reps,20);
  assert.ok(Math.abs(points[0].e1rm-70*(1+5/30))<.001);
});
test('date range includes local midnight and excludes older and future sessions',()=>{
  const now=new Date(2026,10,13,12), dates=[new Date(2026,9,14,23,59),new Date(2026,9,15,0),new Date(2026,10,13,10),new Date(2026,10,14)];
  const workouts=dates.map(date=>({date,activity:'Running'}));
  assert.equal(filterWorkouts(workouts,'30','all',now).length,2);
  assert.equal(filterWorkouts(workouts,'all','all',now).length,3);
  assert.equal(filterWorkouts(workouts,'30','Cycling',now).length,0);
});
test('body-weight trend averages each day first and does not carry missing days',()=>{
  const entries=[{date:new Date(2026,10,1,8),weight:70},{date:new Date(2026,10,1,18),weight:72},{date:new Date(2026,10,2,8),weight:73},{date:new Date(2026,10,9,8),weight:75}];
  const result=dailyWeights(entries);
  assert.deepEqual(result.map(w=>w.weight),[71,73,75]);
  assert.deepEqual(result.map(w=>w.trend),[71,72,75]);
});
test('missing calorie estimates remain distinct from a real zero estimate',()=>{
  const t=totals([{seconds:0,calories:null,sets:[]},{seconds:600,calories:0,sets:[]}]);
  assert.equal(t.calorieCount,1);assert.equal(t.calories,0);assert.equal(t.seconds,600);
});
