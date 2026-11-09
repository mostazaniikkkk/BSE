// Usage: start the QA server, op QaBot, run "node tools/qa/titanite_setup.js <client stdout log>" and then the dev
// client with JAVA_TOOL_OPTIONS="-Dsoulsmode.qa=titanite" ./gradlew runClient --args="--username QaBot --quickPlayMultiplayer 127.0.0.1:25565"
// QaDriver's "titanite" mode: smithing table reinforcement, anvil rule, infusion attack rating, wandering trader offer.
const fs = require('fs');
const rcon = require('./rcon');
const log = process.argv[2];
const done = new Set();
const base = ['fill -3 99 -2 3 99 5 minecraft:stone', 'fill -3 100 -2 3 103 5 minecraft:air', 'kill @e[type=!player,distance=..20]',
  'tp QaBot 0.5 100 0.5 0 0'];
const steps = {
  smithing: ['gamemode souls QaBot', 'execute as QaBot run souls class explorer', 'clear QaBot', 'time set 6000', ...base,
    'setblock 0 100 2 minecraft:smithing_table', 'experience set QaBot 10 levels', 'give QaBot minecraft:wooden_sword',
    'give QaBot soulsmode:titanite_shard 5'],
  anvil: [...base, 'setblock 0 100 2 minecraft:anvil', 'experience set QaBot 30 levels',
    'give QaBot minecraft:iron_sword[soulsmode:reinforcement=1,damage=100]', 'give QaBot minecraft:iron_sword[soulsmode:reinforcement=3]'],
  infusion: ['clear QaBot', 'item replace entity QaBot weapon.mainhand with minecraft:iron_sword[enchantments={levels:{"soulsmode:fire_infusion":1}}]'],
  trades: [...base, 'summon minecraft:villager 0.5 100 2.5 {NoAI:1b,VillagerData:{profession:"minecraft:weaponsmith",level:4,type:"minecraft:plains"}}'],
};
setInterval(async () => {
  let s = '';
  try { s = fs.readFileSync(log, 'utf8'); } catch (e) { return; }
  for (const [name, cmds] of Object.entries(steps)) {
    if (s.includes('NEED ' + name) && !done.has(name)) { done.add(name); console.log(name, await rcon(cmds)); }
  }
  if (s.includes('NEED trades') && !done.has('loot')) {
    done.add('loot');
    const r = await rcon(['clear QaBot'].concat(Array(20).fill('loot give QaBot loot minecraft:chests/ancient_city'),
      ['clear QaBot soulsmode:titanite_slab 0', 'clear QaBot soulsmode:twinkling_titanite 0']));
    console.log('loot (20 ancient city chests)', r.slice(-2));
    // Mob drops (killed by the player): 50 bats (shard 20 %) and 50 phantoms (large shard 15 %).
    await rcon(['kill @e[type=item]'].concat(Array(50).fill('summon minecraft:bat 0.5 103 6.5 {NoAI:1b}'),
      Array(50).fill('summon minecraft:phantom 0.5 103 6.5 {NoAI:1b}')));
    console.log('drops', await rcon(['execute as @e[type=bat] run damage @s 100 minecraft:player_attack by QaBot',
      'execute as @e[type=phantom] run damage @s 100 minecraft:player_attack by QaBot']));
    await new Promise(res => setTimeout(res, 2500));
    console.log('drops', await rcon(['kill @e[type=item,nbt={Item:{id:"soulsmode:titanite_shard"}}]',
      'kill @e[type=item,nbt={Item:{id:"soulsmode:large_titanite_shard"}}]', 'kill @e[type=item]']));
  }
  if (s.includes('[BSE QA] done')) process.exit(0);
}, 1000);
