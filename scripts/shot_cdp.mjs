// 通过 CDP 截图：浏览器在后台渲染，不经过系统屏幕
// 用法: node shot_cdp.js <outDir> <shotKey> [--login buyer|seller|admin]
import fs from 'node:fs';
import path from 'node:path';

const OUT_DIR = process.argv[2] || 'shots';
const SHOT_KEY = process.argv[3] || 'all';
const LOGIN = process.argv[4] || '';

const CDP = 'http://127.0.0.1:9222';
const APP = 'http://127.0.0.1:5173';

const ACCOUNTS = {
  buyer: { account: 'dmx787', password: 'demopass' },
  seller: { account: 'sel2693', password: 'Demo12345' },
  admin: { account: 'adm2693', password: 'Demo12345' },
};

const SHOTS = [
  { key: 'fig3-1', file: 'fig3-1_product_list.png',  url: '/products',                          role: 'buyer' },
  { key: 'fig3-2', file: 'fig3-2_product_detail.png', url: '/products/P179447567001',            role: 'buyer' },
  { key: 'fig3-3', file: 'fig3-3_cart.png',           url: '/cart',                              role: 'buyer' },
  { key: 'fig3-4', file: 'fig3-4_order_confirm.png',  url: '/order/confirm',                     role: 'buyer', viaCart: true },
  { key: 'fig3-5', file: 'fig3-5_order_pay.png',      url: null,                                 role: 'buyer', viaOrder: true },
  { key: 'fig3-6', file: 'fig3-6_order_detail.png',   url: null,                                 role: 'buyer', viaOrder: true },
  { key: 'fig3-7', file: 'fig3-7_seller_products.png',url: '/seller/products',                   role: 'seller' },
  { key: 'fig3-8', file: 'fig3-8_admin_finance.png',  url: '/admin/finance',                     role: 'admin' },
  { key: 'fig3-9', file: 'fig3-9_admin_risk.png',     url: '/admin/risk',                        role: 'admin' },
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
  let page = targets.find(t => t.type === 'page');
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
  if (!acc) return;
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
  return ok;
}

async function clearAuth() {
  await evaluate(`localStorage.clear(); sessionStorage.clear();`);
}

async function capture(file) {
  const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const out = path.join(OUT_DIR, file);
  fs.writeFileSync(out, Buffer.from(data, 'base64'));
  const kb = Math.round(fs.statSync(out).size / 1024);
  console.log(`  saved ${file}  ${kb} KB`);
}

const run = async () => {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  await connect();
  await goto(`${APP}/products`, 2000);

  const targets = SHOT_KEY === 'all' ? SHOTS : SHOTS.filter(s => s.key === SHOT_KEY);
  let lastRole = null;

  for (const s of targets) {
    if (s.role !== lastRole) {
      await clearAuth();
      await goto(`${APP}/login`, 1500);
      await login(s.role);
      lastRole = s.role;
    }

    await goto(`${APP}${s.url || '/products'}`, 2500);

    if (s.viaCart) {
      await goto(`${APP}/cart`, 2000);
      await evaluate(`(() => {
        const b = [...document.querySelectorAll('button')].find(x => x.textContent.includes('结算'));
        if (b) b.click();
      })()`);
      await new Promise(r => setTimeout(r, 2500));
    }
    if (s.viaOrder) {
      // 先走完整下单流程，拿到支付页与详情页
      await goto(`${APP}/cart`, 2000);
      await evaluate(`(() => {
        const b = [...document.querySelectorAll('button')].find(x => x.textContent.includes('结算'));
        if (b) b.click();
      })()`);
      await new Promise(r => setTimeout(r, 2500));
      // 在确认订单页提交
      await evaluate(`(() => {
        const b = [...document.querySelectorAll('button')].find(x => x.textContent.includes('提交订单'));
        if (b) b.click();
      })()`);
      await new Promise(r => setTimeout(r, 4000));
      const payUrl = await evaluate(`location.href`);
      console.log(`  order submitted, url=${payUrl}`);
      if (s.key === 'fig3-5') {
        // 支付页：先创建支付单
        await evaluate(`(() => {
          const b = [...document.querySelectorAll('button')].find(x => x.textContent.includes('创建支付单'));
          if (b) b.click();
        })()`);
        await new Promise(r => setTimeout(r, 2500));
      }
      if (s.key === 'fig3-6') {
        // 详情页：先付费再进详情
        await evaluate(`(() => {
          const b = [...document.querySelectorAll('button')].find(x => x.textContent.includes('创建支付单'));
          if (b) b.click();
        })()`);
        await new Promise(r => setTimeout(r, 2000));
        await evaluate(`(() => {
          const b = [...document.querySelectorAll('button')].find(x => x.textContent.includes('模拟支付成功'));
          if (b) b.click();
        })()`);
        await new Promise(r => setTimeout(r, 2500));
        await evaluate(`(() => {
          const a = [...document.querySelectorAll('a')].find(x => x.textContent.includes('查看订单详情'));
          if (a) a.click();
        })()`);
        await new Promise(r => setTimeout(r, 2500));
      }
    }
    await capture(s.file);
  }

  ws.close();
  console.log('DONE');
};

run().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
