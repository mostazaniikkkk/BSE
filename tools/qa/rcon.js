// Minimal RCON client: rcon(cmd) -> Promise<string>
const net = require('net');
function rcon(cmds, host = '127.0.0.1', port = 25576, pass = 'bse-qa') {
  return new Promise((resolve, reject) => {
    const s = net.connect(port, host);
    let buf = Buffer.alloc(0);
    let id = 1;
    const out = [];
    const send = (type, body) => {
      const b = Buffer.from(body, 'utf8');
      const p = Buffer.alloc(14 + b.length);
      p.writeInt32LE(10 + b.length, 0);
      p.writeInt32LE(id++, 4);
      p.writeInt32LE(type, 8);
      b.copy(p, 12);
      s.write(p);
    };
    let queue = Array.isArray(cmds) ? [...cmds] : [cmds];
    let authed = false;
    s.on('connect', () => send(3, pass));
    s.on('data', d => {
      buf = Buffer.concat([buf, d]);
      while (buf.length >= 4) {
        const len = buf.readInt32LE(0);
        if (buf.length < len + 4) break;
        const body = buf.slice(12, 4 + len - 2).toString('utf8');
        buf = buf.slice(4 + len);
        if (!authed) { authed = true; } else { out.push(body); }
        if (queue.length) send(2, queue.shift()); else { s.end(); resolve(out); }
      }
    });
    s.on('error', reject);
  });
}
module.exports = rcon;
if (require.main === module) rcon(process.argv.slice(2)).then(r => console.log(r.join('\n')));
