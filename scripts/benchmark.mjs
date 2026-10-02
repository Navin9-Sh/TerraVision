import { readFileSync } from 'node:fs';
import { cpus, totalmem } from 'node:os';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { randomBytes } from 'node:crypto';

const BASE = process.env.BASE || 'http://localhost:8080';
const EMAIL = process.env.EMAIL;
const PASSWORD = process.env.PASSWORD;
const REQUESTS = Number(process.env.REQUESTS || 200);
const WARMUP = Number(process.env.WARMUP || 10);
const CONCURRENCY = (process.env.CONCURRENCY || '1,4,8').split(',').map(Number);

if (!EMAIL || !PASSWORD) {
    console.error('Set EMAIL and PASSWORD (a verified account) in the environment.');
    process.exit(1);
}

const here = dirname(fileURLToPath(import.meta.url));
const sample = readFileSync(join(here, '..', 'backend', 'src', 'test', 'resources', 'sample-tile.jpg'));

async function login() {
    const res = await fetch(`${BASE}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
    });
    if (!res.ok) throw new Error(`login failed: ${res.status} ${await res.text()}`);
    return (await res.json()).token;
}

async function predictOnce(token) {
    const form = new FormData();
    form.append('image', new Blob([sample, randomBytes(16)], { type: 'image/jpeg' }), 'tile.jpg');
    const start = performance.now();
    const res = await fetch(`${BASE}/api/v1/predict`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form });
    const ms = performance.now() - start;
    if (!res.ok) throw new Error(`predict failed: ${res.status} ${await res.text()}`);
    const body = await res.json();
    return { ms, inferenceMs: body.inferenceTimeMs };
}

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];

async function run(token, concurrency) {
    let issued = 0;
    const latencies = [];
    const inference = [];
    const started = performance.now();

    async function worker() {
        while (issued < REQUESTS) {
            issued++;
            const r = await predictOnce(token);
            latencies.push(r.ms);
            inference.push(r.inferenceMs);
        }
    }
    await Promise.all(Array.from({ length: concurrency }, worker));

    const seconds = (performance.now() - started) / 1000;
    latencies.sort((a, b) => a - b);
    inference.sort((a, b) => a - b);
    return {
        concurrency,
        requests: latencies.length,
        throughput: +(latencies.length / seconds).toFixed(1),
        p50: Math.round(pct(latencies, 50)),
        p95: Math.round(pct(latencies, 95)),
        p99: Math.round(pct(latencies, 99)),
        inferenceP50: pct(inference, 50),
    };
}

const token = await login();
console.log(`Warming up (${WARMUP} requests)...`);
for (let i = 0; i < WARMUP; i++) await predictOnce(token);

const rows = [];
for (const c of CONCURRENCY) {
    console.log(`Running ${REQUESTS} requests at concurrency ${c}...`);
    rows.push(await run(token, c));
}

const cpu = cpus();
console.log(`\nMachine: ${cpu[0].model.trim()} (${cpu.length} logical cores), ${(totalmem() / 2 ** 30).toFixed(1)} GB RAM`);
console.log(`Target:  ${BASE}\n`);
console.log('| Concurrency | Requests | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Model time p50 (ms) |');
console.log('|---|---|---|---|---|---|---|');
for (const r of rows) {
    console.log(`| ${r.concurrency} | ${r.requests} | ${r.throughput} | ${r.p50} | ${r.p95} | ${r.p99} | ${r.inferenceP50} |`);
}
