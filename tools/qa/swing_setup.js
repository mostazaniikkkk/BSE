// Usage: start the QA server, op QaBot, run "node tools/qa/swing_setup.js <client stdout log>" and then the dev client
// with JAVA_TOOL_OPTIONS="-Dsoulsmode.qa=swing" ./gradlew runClient --args="--username QaBot --quickPlayMultiplayer 127.0.0.1:25565"
// QaDriver's "swing" mode swings a weapon whose requirements are met and one whose are not (DS2 swing speed).
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
const done = new Set();
const steps = {
  sword: ['gamemode souls QaBot', 'execute as QaBot run souls class explorer', 'clear QaBot', 'time set 6000',
    'fill -3 99 -2 3 99 5 minecraft:stone', 'fill -3 100 -2 3 103 5 minecraft:air', 'tp QaBot 0.5 100 0.5 0 0',
    'effect give QaBot minecraft:saturation 60 1 true', 'item replace entity QaBot weapon.mainhand with minecraft:wooden_sword'],
  axe: ['item replace entity QaBot weapon.mainhand with minecraft:netherite_axe'],
};
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  for (const [name, cmds] of Object.entries(steps)) {
    if (s.includes('NEED ' + name) && !done.has(name)) { done.add(name); console.log(name, await rcon(cmds)); }
  }
  if (s.includes('[BSE QA] done')) process.exit(0);
}, 1000);
