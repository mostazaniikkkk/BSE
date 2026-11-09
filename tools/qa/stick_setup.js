// Usage: start the QA server, op QaBot, run "node tools/qa/stick_setup.js <client stdout log>" and then the dev client
// with JAVA_TOOL_OPTIONS="-Dsoulsmode.qa=stick" ./gradlew runClient --args="--username QaBot --quickPlayMultiplayer 127.0.0.1:25565"
// QaDriver's "stick" mode: Unbreaking III from a book on a stick at the anvil, then 24 hits on a zombie.
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
const done = new Set();
const steps = {
  anvil: ['gamemode souls QaBot', 'execute as QaBot run souls class explorer', 'clear QaBot', 'time set 6000',
    'fill -3 99 -2 3 99 5 minecraft:stone', 'fill -3 100 -2 3 103 5 minecraft:air', 'kill @e[type=!player,distance=..20]',
    'tp QaBot 0.5 100 0.5 0 0', 'experience set QaBot 30 levels', 'give QaBot minecraft:stick'].concat(process.argv[3] === 'table'
      ? ['setblock 0 100 2 minecraft:enchanting_table', 'give QaBot minecraft:lapis_lazuli 3']
      : ['setblock 0 100 2 minecraft:anvil', 'give QaBot minecraft:enchanted_book[stored_enchantments={levels:{"minecraft:unbreaking":3}}]']),
  zombie: ['gamemode souls QaBot', 'tp QaBot 0.5 100 0.5 0 0', 'fill -3 99 -2 3 99 5 minecraft:stone', 'fill -3 100 -2 3 103 5 minecraft:air',
    'kill @e[type=!player,distance=..20]', ...(process.argv[3] === 'plain' ? ['clear QaBot', 'item replace entity QaBot weapon.mainhand with minecraft:stick'] : []), 'effect give QaBot minecraft:saturation 120 1 true',
    'summon minecraft:zombie 0.5 100 2.5 {NoAI:1b,Health:1000f,attributes:[{id:"minecraft:generic.max_health",base:1000},{id:"minecraft:generic.knockback_resistance",base:1}]}'],
};
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  for (const [name, cmds] of Object.entries(steps)) {
    if (s.includes('NEED ' + name) && !done.has(name)) { done.add(name); console.log(name, await rcon(cmds)); }
  }
  if (s.includes('[BSE QA] done')) process.exit(0);
}, 1000);
