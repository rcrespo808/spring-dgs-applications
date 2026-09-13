import { buildSchema, graphql } from 'graphql';
import { createIcons, Activity, Github, Play, RotateCcw, BookOpen } from 'lucide';
import schemaText from '../src/main/resources/schema/markets.graphqls';
import { createMarket } from './market.js';
import { createFeed } from './binance.js';

const schema = buildSchema(schemaText);
let market = createMarket();
const examples = {
  live: '{\n  binancePrices {\n    symbol\n    price\n    quoteAsset\n    change24h\n    observedAt\n    source\n  }\n}',
  pairs: '{\n  pairs {\n    id\n    base { symbol kind }\n    quote { symbol kind }\n    referenceRate\n  }\n}',
  assets: '{\n  assets {\n    symbol name kind\n    usdReference version\n  }\n}',
  convert: '{\n  convert(from: "ETH", to: "BTC", amount: "2") {\n    from to\n    amount\n    rate\n    result\n  }\n}',
  indices: '{\n  indices {\n    id name\n    level valueUsd divisor\n    constituents {\n      asset { symbol usdReference }\n      quantity\n    }\n  }\n}',
  price: 'mutation {\n  setReferencePrice(\n    symbol: "BTC"\n    usdReference: "66000"\n    expectedVersion: 0\n  ) { symbol usdReference version }\n}',
  add: 'mutation {\n  addPair(base: "SOL", quote: "EUR") {\n    id referenceRate\n    base { symbol }\n    quote { symbol }\n  }\n}',
};
const query = document.querySelector('#query');
const result = document.querySelector('#result');
const status = document.querySelector('#status');
const runButton = document.querySelector('#run');
const histories = new Map();
function drawHistory(symbol, price) {
  const values = histories.get(symbol) || [];
  values.push(Number(price)); if (values.length > 90) values.shift(); histories.set(symbol, values);
  const canvas = document.querySelector(`[data-chart="${symbol}"]`);
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const low = Math.min(...values), span = Math.max(...values) - low || 1;
  ctx.strokeStyle = '#18836c'; ctx.lineWidth = 2; ctx.beginPath();
  values.forEach((p,i) => { const x = 2 + i * (canvas.width - 4) / Math.max(1, values.length - 1); const y = canvas.height - 5 - (p - low) / span * (canvas.height - 10); if (i) ctx.lineTo(x,y); else ctx.moveTo(x,y); });
  ctx.stroke();
}
const feed = createFeed((quotes, state) => {
  document.querySelector('#feed-status').textContent = state;
  for (const q of quotes.filter(Boolean)) {
    const row = document.querySelector(`[data-symbol="${q.symbol}"]`);
    row.querySelector('.price').textContent = new Intl.NumberFormat('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(q.price);
    const change = row.querySelector('.change');
    change.textContent = `${Number(q.change24h) >= 0 ? '+' : ''}${q.change24h}%`;
    change.classList.toggle('negative', Number(q.change24h) < 0);
    row.querySelector('time').textContent = new Date(q.observedAt).toLocaleTimeString();
    row.querySelector('time').dateTime = q.observedAt;
    row.classList.toggle('stale', Date.now() - Date.parse(q.observedAt) >= 30000);
    drawHistory(q.symbol, q.price);
  }
});
window.addEventListener('pagehide', () => feed.stop());
async function run() {
  runButton.disabled = true;
  try {
    const variableValues = JSON.parse(document.querySelector('#variables').value || '{}');
    if (!variableValues || Array.isArray(variableValues) || typeof variableValues !== 'object') throw new Error('Variables must be a JSON object');
    const response = await graphql({schema, source: query.value, rootValue: {...market, binancePrices: () => feed.prices()}, variableValues});
    result.textContent = JSON.stringify(response, null, 2);
    status.textContent = response.errors ? 'Errors' : 'Complete';
    status.classList.toggle('error', !!response.errors);
  } catch(error) { result.textContent = JSON.stringify({errors: [{message: error.message}]}, null, 2); status.textContent = 'Invalid input'; status.classList.add('error'); }
  finally { runButton.disabled = false; }
}
document.querySelector('#example').addEventListener('change', event => { query.value = examples[event.target.value]; document.querySelector('#variables').value = '{}'; });
runButton.addEventListener('click', run);
document.querySelector('#reset').addEventListener('click', () => { market = createMarket(); query.value = examples.pairs; document.querySelector('#example').value = 'pairs'; document.querySelector('#variables').value = '{}'; run(); });
query.addEventListener('keydown', event => { if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') { event.preventDefault(); run(); } });
createIcons({icons: {Activity, Github, Play, RotateCcw, BookOpen}});
document.querySelector('#schema').textContent = schemaText;
query.value = examples.pairs;
run();
