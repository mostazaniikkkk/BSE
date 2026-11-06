// Usage: start the QA server, op QaBot, run "node tools/qa/bow_setup.js <client stdout log>" and then the dev client with
// JAVA_TOOL_OPTIONS="-Dsoulsmode.qa=bow [-Dsoulsmode.qa.camera=first]" ./gradlew runClient --args="--username QaBot --quickPlayMultiplayer 127.0.0.1:25565"
// (QaDriver drives the keys and logs "[BSE QA]" lines).
// Watches the dev client's log for the QA driver's NEED lines and answers them through RCON.
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
const done = new Set();
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  if (s.includes('NEED bow') && !done.has('bow')) {
    done.add('bow');
    console.log(await rcon(['gamemode souls QaBot', 'execute as QaBot run souls class explorer', 'clear QaBot', 'tp QaBot -9.5 84 6.5 0 0',
      'item replace entity QaBot weapon.offhand with minecraft:shield', 'item replace entity QaBot hotbar.0 with minecraft:bow', 'give QaBot minecraft:arrow 16']));
  }
  if (s.includes('NEED crossbow') && !done.has('crossbow')) {
    done.add('crossbow');
    console.log(await rcon(['item replace entity QaBot weapon.mainhand with minecraft:crossbow']));
  }
  for (const mark of ['after A', 'after B', 'after F']) {
    if (s.includes(mark) && !done.has(mark)) { done.add(mark); console.log(mark, await rcon(['clear QaBot minecraft:arrow 0', 'data get entity QaBot SelectedItem', 'data get entity QaBot OnGround', 'execute as QaBot run gamemode query'])); }
  }
  if (s.includes('[BSE QA] done')) process.exit(0);
}, 1000);
