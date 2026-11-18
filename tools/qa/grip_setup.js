// Power stance QA: node tools/qa/grip_setup.js <client log>; client: -Dsoulsmode.qa=grip
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
let set = false;
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  if (s.includes('NEED swords') && !set) {
    set = true;
    console.log(await rcon(['gamemode souls QaBot', 'execute as QaBot run souls status', 'clear QaBot',
      'forceload add 0 0', 'fill -6 199 -6 6 199 20 minecraft:stone', 'fill -6 200 -6 6 203 20 minecraft:air',
      'tp QaBot 0.5 200 0.5 0 0', 'item replace entity QaBot weapon.mainhand with minecraft:stone_sword',
      'item replace entity QaBot weapon.offhand with minecraft:stone_sword']));
  }
  if (s.includes('[BSE QA] done')) {
    process.exit(0);
  }
}, 500);
