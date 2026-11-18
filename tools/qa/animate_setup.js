// /souls animate QA: node tools/qa/animate_setup.js <client log>; client: -Dsoulsmode.qa=animate
// t=60.. the client holds forward. 1) a built-in TAE without seconds (its duration, 2.0 s); 2) a pack-style id without
// seconds (must fail); 3) a victim clip for 6 s, killed after 2 s.
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
const sleep = ms => new Promise(r => setTimeout(r, ms));
let started = false;
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  if (s.includes('[BSE QA] READY') && !started) {
    started = true;
    console.log(await rcon(['list', 'gamemode souls QaBot', 'forceload add 0 0', 'fill -6 199 -6 6 199 20 minecraft:stone',
      'fill -6 200 -6 6 203 20 minecraft:air', 'tp QaBot 0.5 200 0.5 0 0', 'effect give QaBot minecraft:instant_health 1 5',
      'scoreboard objectives add qa dummy']));
    await sleep(2500);
    console.log(await rcon(['execute store result score QaBot qa run souls animate QaBot 29010012',
      'scoreboard players get QaBot qa']));
    await sleep(3000);
    console.log(await rcon(['souls animate QaBot darktwo:no_such_clip']));
    console.log(await rcon(['tp QaBot 0.5 200 0.5 0 0', 'souls animate QaBot soulsmode:ds2_victim_79020010 6']));
    await sleep(2000);
    console.log(await rcon(['kill QaBot']));
    await sleep(3000);
    process.exit(0);
  }
}, 500);
