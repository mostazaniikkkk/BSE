// Pillager reload QA: node tools/qa/pillager_setup.js <client log>; client: -Dsoulsmode.qa=idle
// A pillager 7 blocks from a Souls Mode player that cannot die; counts the arrows it has fired every 5 s.
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
    console.log(await rcon(['gamemode souls QaBot', 'difficulty normal', 'forceload add 0 0',
      'fill -10 199 -10 10 199 20 minecraft:stone', 'fill -10 200 -10 10 204 20 minecraft:air',
      'kill @e[type=!player]', 'tp QaBot 0.5 200 0.5 0 0', 'effect give QaBot minecraft:resistance 120 4',
      'effect give QaBot minecraft:regeneration 120 4', 'summon pillager 0.5 200 7.5', 'scoreboard objectives add qa dummy', 'scoreboard objectives add hits minecraft.custom:minecraft.damage_resisted', 'scoreboard players set QaBot hits 0']));
    for (let i = 1; i <= 30; i++) {
      await sleep(1000);
      const r = await rcon(['scoreboard players get QaBot hits',
        'execute as @e[type=pillager,limit=1] run data get entity @s Pos', 'execute as @e[type=pillager,limit=1] run data get entity @s HandItems[0].components']);
      console.log(i + 's ' + r.join(' | ').replace(/has the following entity data: /g, ''));
    }
    process.exit(0);
  }
}, 500);
