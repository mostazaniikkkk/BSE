// Usage: start the QA server, op QaBot, run "node tools/qa/tools_setup.js <client stdout log>" and then the dev client
// with JAVA_TOOL_OPTIONS="-Dsoulsmode.qa=tools" ./gradlew runClient --args="--username QaBot --quickPlayMultiplayer 127.0.0.1:25565"
// QaDriver's "tools" mode checks that the axe strips, the hoe tills and harvests, and a sword still attacks.
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
const done = new Set();
const base = ['fill -3 99 -2 3 99 5 minecraft:stone', 'fill -3 100 -2 3 103 5 minecraft:air', 'tp QaBot 0.5 100 0.5 0 0'];
const steps = {
  axe: ['gamemode souls QaBot', 'execute as QaBot run souls class explorer', 'clear QaBot', 'time set 6000', ...base,
    'setblock 0 100 2 minecraft:oak_log', 'item replace entity QaBot weapon.mainhand with minecraft:iron_axe'],
  hoe: [...base, 'setblock 0 99 2 minecraft:grass_block', 'item replace entity QaBot weapon.mainhand with minecraft:iron_hoe'],
  wheat: [...base, 'setblock 0 99 2 minecraft:farmland', 'setblock 0 100 2 minecraft:wheat[age=7]', 'clear QaBot minecraft:wheat',
    'item replace entity QaBot weapon.mainhand with minecraft:iron_hoe'],
  sword: [...base, 'setblock 0 100 2 minecraft:oak_log', 'item replace entity QaBot weapon.mainhand with minecraft:iron_sword'],
};
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  for (const [name, cmds] of Object.entries(steps)) {
    if (s.includes('NEED ' + name) && !done.has(name)) {
      done.add(name);
      if (name === 'sword') console.log('wheat picked up:', await rcon(['clear QaBot minecraft:wheat 0']));
      console.log(name, await rcon(cmds));
    }
  }
  if (s.includes('[BSE QA] done')) process.exit(0);
}, 1000);
