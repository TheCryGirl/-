// 补截 #49~#52 五个页面：店铺资料、管理员店铺管理、收藏夹、浏览足迹、个人中心头像
import fs from 'node:fs';
import path from 'node:path';

const OUT_DIR = process.argv[2] || 'shots';
const CDP = 'http://127.0.0.1:9222';
const APP = 'http://127.0.0.1:5173';

const ACCOUNTS = {
  buyer: { account: 'dmx787', password: 'demopass' },
  seller: { account: 'sel2693', password: 'Demo12345' },
  admin: { account: 'adm2693', password: 'Demo12345' },
};

const SHOTS = [
  { key: 'fig3-10', file: 'fig3-10_seller_shop.png',  url: '/seller/shop', role: 'seller', wait: 3500 },
  { key: 'fig3-14', file: 'fig3-14_profile.png',      url: '/profile',     role: 'buyer',  wait: 3500 },
];

let msgId = 0;
const pending = new Map();
let ws;

function send(method, params = {}) {
  const id = ++msgId;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
    setTimeout(() => {
      if (pending.has(id)) { pending.delete(id); reject(new Error(`timeout: ${method}`)); }
    }, 30000);
  });
}

async function connect() {
  const targets = await fetch(`${CDP}/json/list`).then(r => r.json());
  const page = targets.find(t => t.type === 'page');
  if (!page) throw new Error('no page target');
  ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => {
    ws.addEventListener('open', res, { once: true });
    ws.addEventListener('error', rej, { once: true });
  });
  ws.addEventListener('message', ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
    }
  });
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1600, height: 950, deviceScaleFactor: 1, mobile: false,
  });
}

async function evaluate(expression) {
  const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  return r.result?.value;
}

async function goto(url, wait = 2500) {
  await send('Page.navigate', { url });
  await new Promise(r => setTimeout(r, wait));
}

async function login(role) {
  const acc = ACCOUNTS[role];
  const ok = await evaluate(`(async () => {
    const r = await fetch('/api/users/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(${JSON.stringify(acc)})
    });
    const j = await r.json();
    if (j.code !== 0) return 'login failed: ' + j.message;
    localStorage.setItem('token', j.data.token);
    localStorage.setItem('user', JSON.stringify(j.data.user));
    return 'ok';
  })()`);
  console.log(`  login(${role}): ${ok}`);
}

async function capture(file) {
  const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const out = path.join(OUT_DIR, file);
  fs.writeFileSync(out, Buffer.from(data, 'base64'));
  console.log(`  saved ${file}  ${Math.round(fs.statSync(out).size / 1024)} KB`);
}

const run = async () => {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  await connect();
  await goto(`${APP}/products`, 2000);

  let lastRole = null;
  for (const s of SHOTS) {
    if (s.role !== lastRole) {
      await evaluate('localStorage.clear(); sessionStorage.clear();');
      await goto(`${APP}/login`, 1500);
      await login(s.role);
      lastRole = s.role;
    }
    await goto(`${APP}${s.url}`, s.wait);
    const title = await evaluate('document.title');
    console.log(`  nav ${s.url}  title="${title}"`);
    await capture(s.file);
  }

  ws.close();
  console.log('DONE');
};

run().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
