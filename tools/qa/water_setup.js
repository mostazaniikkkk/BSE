// Shallow water QA: node tools/qa/water_setup.js <client log>; client: -Dsoulsmode.qa=water
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
let set = false, water = false;
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  if (s.includes('NEED sword') && !set) {
    set = true;
    console.log(await rcon(['gamemode souls QaBot', 'clear QaBot', 'forceload add 0 0',
      'fill -6 199 -6 6 199 20 minecraft:stone', 'fill -6 200 -6 6 203 20 minecraft:air', 'fill 4 200 4 6 200 6 minecraft:water',
      'tp QaBot 0.5 200 0.5 0 0', 'effect give QaBot minecraft:instant_health 1 5',
      'item replace entity QaBot hotbar.0 with minecraft:iron_sword']));
  }
  if (s.includes('WATER please') && !water) {
    water = true;
    console.log(await rcon(['tp QaBot 5.5 200 5.5 0 0']));
  }
  if (s.includes('[BSE QA] done')) {
    process.exit(0);
  }
}, 500);
