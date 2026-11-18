// Petrification QA: node tools/qa/petrify_setup.js <client log>; client: -Dsoulsmode.qa=petrify
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
const done = new Set();
async function step(name, cmds) {
  if (done.has(name)) return;
  done.add(name);
  console.log(name, await rcon(cmds));
}
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  if (s.includes('STEP setup')) await step('setup', ['data get entity QaBot "fabric:attachments"', 'gamemode souls QaBot', 'clear QaBot',
    'forceload add -16 -16 16 16', 'fill -8 199 -8 8 199 20 minecraft:stone', 'fill -8 200 -8 8 204 20 minecraft:air',
    'kill @e[type=!player,distance=..40,x=0,y=200,z=0]', 'tp QaBot 0.5 200 0.5 0 0', 'time set noon',
    'effect give QaBot minecraft:instant_health 1 5', 'effect give QaBot minecraft:resistance 1000 4 true',
    'place feature soulsmode:petrified_statue 0 200 3', 'place feature soulsmode:petrified_statue -3 200 6',
    'place feature soulsmode:petrified_statue 3 200 6', 'place feature soulsmode:petrified_statue -1 200 8',
    'place feature soulsmode:petrified_statue 2 200 9']);
  if (s.includes('STEP gauges')) await step('gauges', ['effect clear QaBot minecraft:resistance',
    'souls status QaBot petrify 60', 'souls status QaBot curse 50']);
  if (s.includes('STEP curse')) await step('curse', ['souls status QaBot curse 100', 'data get entity QaBot "fabric:attachments"']);
  if (s.includes('STEP branch')) await step('branch', ['give QaBot soulsmode:fragrant_branch 2']);
  if (s.includes('STEP petrify') && !done.has('petrify')) {
    await step('petrify', ['clear QaBot', 'souls status QaBot petrify 500']);
    for (const ms of [500, 1000, 1500, 2000, 3000]) {
      await new Promise(r => setTimeout(r, 500));
      console.log('body', ms, await rcon(['data get entity QaBot DeathTime', 'data get entity QaBot Health']));
    }
  }
  if (s.includes('STEP hollow')) await step('hollow', ['data get entity QaBot "fabric:attachments"']);
  if (s.includes('[BSE QA] done')) process.exit(0);
}, 400);
