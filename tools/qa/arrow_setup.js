// Arrow QA: node tools/qa/arrow_setup.js <client log>; client: -Dsoulsmode.qa=arrow
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
let done = false;
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  if (s.includes('NEED bow') && !done) {
    done = true;
    console.log(await rcon(['gamemode souls QaBot', 'execute as QaBot run souls class explorer', 'clear QaBot',
      'forceload add 0 0', 'fill -6 199 -2 6 199 24 minecraft:stone', 'fill -6 200 20 6 208 20 minecraft:oak_planks',
      'kill @e[type=arrow]', 'tp QaBot 0.5 200 0.5 0 0',
      'item replace entity QaBot hotbar.0 with minecraft:bow', 'give QaBot minecraft:arrow 8']));
  }
  if (s.includes('[BSE QA] done')) {
    console.log(await rcon(['execute as @e[type=arrow] run data get entity @s inGround', 'execute as @e[type=arrow] run data get entity @s pickup', 'execute as @e[type=arrow] run data get entity @s Pos']));
    process.exit(0);
  }
}, 1000);
