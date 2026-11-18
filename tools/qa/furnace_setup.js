// Incandescent minerals QA: node tools/qa/furnace_setup.js <client log>; client: -Dsoulsmode.qa=furnace
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
let done = false;
let finished = false;
let crafted = false;
const sleep = ms => new Promise(r => setTimeout(r, ms));
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  if (s.includes('NEED furnace') && !done) {
    done = true;
    console.log(await rcon(['gamemode souls QaBot', 'execute as QaBot run souls class explorer', 'clear QaBot',
      'gamerule soulsIncandescentMinerals true', 'forceload add 0 0', 'fill -4 199 -4 4 199 8 minecraft:stone',
      'fill -4 200 -4 4 203 8 minecraft:air', 'setblock 0 200 2 minecraft:furnace[facing=north]',
      'item replace block 0 200 2 container.0 with minecraft:raw_iron 2', 'item replace block 0 200 2 container.1 with minecraft:coal 1', 'xp set QaBot 0 points', 'xp set QaBot 0 levels',
      'setblock 3 200 5 minecraft:water_cauldron[level=3]', 'kill @e[type=item]', 'kill @e[type=experience_orb]',
      'tp QaBot 0.5 200 0.5 0 30', 'item replace entity QaBot hotbar.0 with minecraft:dirt', 'item replace entity QaBot hotbar.1 with minecraft:stick', 'effect give QaBot minecraft:instant_health 1 5', 'effect give QaBot minecraft:hunger 30 255']));
  }
  if (s.includes('CRAFT souls') && !crafted) {
    crafted = true;
    console.log(await rcon(['list', 'tag QaBot list', 'execute as QaBot if predicate bseqa:souls run say predicate souls: yes',
      'gamemode survival QaBot', 'tag QaBot list',
      'execute as QaBot unless predicate bseqa:souls run say predicate outside: no']));
  }
  if (s.includes('[BSE QA] done') && !finished) {
    finished = true;
    // Server-side parts: quench in the cauldron, cool on the ground.
    console.log(await rcon(['kill @e[type=item]', 'kill @e[type=experience_orb]',
      'summon item 3.5 200.4 5.5 {Item:{id:"soulsmode:incandescent_iron_ingot",count:5},PickupDelay:32767}',
      'summon item -2.5 200.1 5.5 {Item:{id:"soulsmode:incandescent_gold_ingot",count:2},PickupDelay:32767}']));
    await sleep(1500);
    console.log(await rcon(['data get entity @e[type=item,x=3.5,y=200,z=5.5,distance=..1,limit=1] Item',
      'execute if entity @e[type=experience_orb] run say orbs present', 'data get block 3 200 5',
      'execute as @e[type=experience_orb] run data get entity @s Value',
      'data get entity @e[type=item,x=-2.5,y=200,z=5.5,distance=..1,limit=1] Item']));
    await sleep(5500);
    console.log(await rcon(['data get entity @e[type=item,x=-2.5,y=200,z=5.5,distance=..1,limit=1] Item',
      'execute as @e[type=experience_orb] run data get entity @s Value']));
    process.exit(0);
  }
}, 1000);
