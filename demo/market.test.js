import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildSchema, graphql } from 'graphql';
import { createMarket } from './market.js';
import { parseTicker } from './binance.js';
const schema = buildSchema(readFileSync(new URL('../src/main/resources/schema/markets.graphqls', import.meta.url), 'utf8'));
const execute = (source, rootValue = createMarket()) => graphql({schema, source, rootValue});
test('shared schema resolves assets, pairs and normalized basket indices', async () => {
  const r = await execute('{ pairs { base { symbol } quote { symbol } referenceRate } indices { level valueUsd constituents { asset { symbol } quantity } } }');
  assert.equal(r.errors, undefined); assert.equal(r.data.pairs.length, 5);
  assert.equal(r.data.indices[0].level, '100.00000000'); assert.equal(r.data.indices[1].level, '100.00000000');
});
test('decimal conversion rounds once and handles cross rates', async () => {
  const r = await execute('{ a: convert(from:"USD",to:"JPY",amount:"700") { result } b: convert(from:"ETH",to:"BTC",amount:"2") { result } }');
  assert.equal(r.data.a.result, '100000.000000000000'); assert.equal(r.data.b.result, '0.100000000000');
});
test('price change propagates to indices and rejects stale versions', async () => {
  const market = createMarket();
  const change = 'mutation { setReferencePrice(symbol:"BTC",usdReference:"66000",expectedVersion:0) { version } }';
  assert.equal((await execute(change, market)).data.setReferencePrice.version, 1);
  assert.equal((await execute('{ indices { level } }', market)).data.indices[0].level, '105.00000000');
  assert.equal((await execute(change, market)).errors[0].extensions.code, 'CONFLICT');
});
test('duplicate pairs and invalid decimals return business errors', async () => {
  const market = createMarket();
  const query = 'mutation { addPair(base:"SOL",quote:"EUR") { id } }';
  assert.equal((await execute(query, market)).data.addPair.id, 'SOL-EUR');
  assert.equal((await execute(query, market)).errors[0].extensions.code, 'PAIR_EXISTS');
  for (const amount of ['-1','NaN','1e3','0.0000000000001']) assert.equal((await execute(`{ convert(from:"BTC",to:"USD",amount:"${amount}") { result } }`)).errors[0].extensions.code, 'BAD_INPUT');
});
test('Binance stream and REST records retain USDT units and timestamps', () => {
  const stream = parseTicker({s:'BTCUSDT',c:'66000',o:'60000'}, true, 1000);
  assert.equal(stream.price, '66000.00000000'); assert.equal(stream.change24h, '10.000'); assert.equal(stream.quoteAsset, 'USDT');
  assert.equal(stream.observedAt, '1970-01-01T00:00:01.000Z');
  assert.equal(parseTicker({symbol:'ETHUSDT',lastPrice:'3000',priceChangePercent:'-2.5'}, false).source, 'BINANCE_REST');
  assert.throws(() => parseTicker({s:'BTCUSDT',c:'NaN',o:'1'}, true));
});
