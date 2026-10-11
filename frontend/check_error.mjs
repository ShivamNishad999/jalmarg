import { spawn } from 'child_process';

const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const port = 9334;

const browser = spawn(edgePath, [
  '--headless',
  `--remote-debugging-port=${port}`,
  '--disable-gpu',
  '--no-first-run',
  '--no-default-browser-check',
  'http://localhost:5173/'
]);

setTimeout(async () => {
  try {
    const res = await fetch(`http://127.0.0.1:${port}/json`);
    const pages = await res.json();
    const target = pages.find(p => p.url.includes('5173'));
    if (target && target.webSocketDebuggerUrl) {
      console.log('Connected to target WS...');
      const ws = new globalThis.WebSocket(target.webSocketDebuggerUrl);
      ws.onopen = () => {
        ws.send(JSON.stringify({ id: 1, method: 'Runtime.enable' }));
        ws.send(JSON.stringify({ id: 2, method: 'Log.enable' }));
        ws.send(JSON.stringify({ id: 3, method: 'Page.enable' }));
        ws.send(JSON.stringify({ id: 4, method: 'Page.reload' }));
      };
      ws.onmessage = (event) => {
        const msg = JSON.parse(event.data);
        if (msg.method === 'Runtime.exceptionThrown') {
          console.error('EXCEPTION THROWN:', JSON.stringify(msg.params.exceptionDetails, null, 2));
        }
        if (msg.method === 'Runtime.consoleAPICalled') {
          console.log('CONSOLE:', msg.params.type, JSON.stringify(msg.params.args));
        }
      };
      setTimeout(() => {
        console.log('Done checking.');
        browser.kill();
        process.exit(0);
      }, 4000);
    }
  } catch (err) {
    console.error('Fetch error:', err);
    browser.kill();
    process.exit(1);
  }
}, 2000);
