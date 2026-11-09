// Usage: start the QA server with -Dsoulsmode.qa.log=true, op QaBot, run
// "node tools/qa/hits_setup.js <client stdout log> <item>" and then the dev client with
// JAVA_TOOL_OPTIONS="-Dsoulsmode.qa=hits" ./gradlew runClient --args="--username QaBot --quickPlayMultiplayer 127.0.0.1:25565"
// <item> is a give argument, e.g. 'minecraft:iron_sword[enchantments={levels:{"soulsmode:bleed_infusion":1}}]'.
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
const item = process.argv[3] || 'minecraft:stick';
let done = false;
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  if (s.includes('NEED zombie') && !done) {
    done = true;
    console.log(await rcon(['gamemode souls QaBot', 'execute as QaBot run souls class explorer', 'time set 18000', 'difficulty peaceful', 'difficulty easy',
      'fill -3 99 -2 3 99 5 minecraft:stone', 'fill -3 100 -2 3 103 5 minecraft:air', 'kill @e[type=!player,distance=..20]',
      'tp QaBot 0.5 100 0.5 0 0', 'clear QaBot', 'item replace entity QaBot weapon.mainhand with ' + item,
      'effect give QaBot minecraft:saturation 600 1 true',
      `summon minecraft:zombie 0.5 100 2.2 {CustomName:'"QaTarget"',NoAI:1b,PersistenceRequired:1b,Health:1000f,attributes:[{id:"minecraft:generic.max_health",base:1000},{id:"minecraft:generic.knockback_resistance",base:1}]}`]));
  }
  // The DS2 hit reaction pushes the zombie back: keep it in reach.
  if (done) await rcon(['tp @e[type=zombie,name=QaTarget] 0.5 100 2.2 180 0']);
  if (s.includes('[BSE QA] done')) process.exit(0);
}, 1000);
