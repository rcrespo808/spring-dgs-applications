import Decimal from 'decimal.js';
import { fail } from './market.js';

export const symbols = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT'];
const endpoint = `https://data-api.binance.vision/api/v3/ticker/24hr?symbols=${encodeURIComponent(JSON.stringify(symbols))}`;
const socketUrl = 'wss://data-stream.binance.vision/stream?streams=btcusdt@miniTicker/ethusdt@miniTicker/solusdt@miniTicker';
export function parseTicker(record, streaming, now = Date.now()) {
  const symbol = streaming ? record.s : record.symbol;
  const price = streaming ? record.c : record.lastPrice;
  if (!symbols.includes(symbol) || !new Decimal(price).isFinite() || new Decimal(price).lte(0)) throw new Error('Invalid Binance ticker');
  const change = streaming ? new Decimal(price).minus(record.o).div(record.o).mul(100) : new Decimal(record.priceChangePercent);
  if (!change.isFinite()) throw new Error('Invalid Binance change');
  return {symbol, price: new Decimal(price).toFixed(8), change24h: change.toFixed(3), quoteAsset: 'USDT', observedAt: new Date(now).toISOString(), source: streaming ? 'BINANCE_WEBSOCKET' : 'BINANCE_REST'};
}
export function createFeed(onChange) {
  const quotes = new Map();
  let socket, reconnect, polling, retry = 1000, stopped = false, restRunning = false;
  const fresh = q => q && Date.now() - Date.parse(q.observedAt) < 30000;
  const streaming = () => socket?.readyState === WebSocket.OPEN && symbols.every(s => fresh(quotes.get(s)) && quotes.get(s).source === 'BINANCE_WEBSOCKET');
  const notify = () => onChange(symbols.map(s => quotes.get(s)), streaming() ? 'Live stream' : symbols.every(s => fresh(quotes.get(s))) ? 'REST snapshot' : 'Connecting / stale');
  async function refresh() {
    if (restRunning || streaming() || stopped) return;
    restRunning = true;
    try {
      const response = await fetch(endpoint, {signal: AbortSignal.timeout(8000)});
      if (!response.ok) throw new Error('Market feed unavailable');
      const data = await response.json();
      if (!Array.isArray(data) || data.length !== symbols.length) throw new Error('Incomplete market data');
      const parsed = data.map(r => parseTicker(r, false));
      if (new Set(parsed.map(q => q.symbol)).size !== symbols.length) throw new Error('Incomplete market data');
      for (const q of parsed) if (!fresh(quotes.get(q.symbol)) || quotes.get(q.symbol).source !== 'BINANCE_WEBSOCKET') quotes.set(q.symbol, q);
    } catch { /* Keep the last observation visible with its stale timestamp. */ }
    finally { restRunning = false; notify(); }
  }
  function connect() {
    if (stopped) return;
    socket = new WebSocket(socketUrl);
    socket.onmessage = event => {
      try { const quote = parseTicker(JSON.parse(event.data).data, true); quotes.set(quote.symbol, quote); retry = 1000; notify(); } catch { /* Ignore malformed exchange events. */ }
    };
    socket.onerror = () => socket.close();
    socket.onclose = () => {
      notify();
      if (!stopped) { reconnect = setTimeout(connect, retry); retry = Math.min(retry * 2, 30000); }
    };
  }
  connect(); refresh();
  polling = setInterval(() => { notify(); refresh(); }, 15000);
  return {
    async prices() {
      if (!symbols.every(s => fresh(quotes.get(s)))) await refresh();
      if (!symbols.every(s => fresh(quotes.get(s)))) fail('FEED_UNAVAILABLE', 'Binance data unavailable or stale; retry shortly');
      return symbols.map(s => ({...quotes.get(s)}));
    },
    stop() { stopped = true; clearTimeout(reconnect); clearInterval(polling); socket?.close(); },
  };
}
