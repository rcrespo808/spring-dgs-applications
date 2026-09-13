import Decimal from 'decimal.js';
import { GraphQLError } from 'graphql';

Decimal.set({ precision: 60, rounding: Decimal.ROUND_HALF_EVEN });
export const fail = (code, message) => { throw new GraphQLError(message, { extensions: { code } }); };
export function decimal(value, positive = false) {
  if (typeof value !== 'string' || !/^[0-9]{1,15}(\.[0-9]{1,12})?$/.test(value)) fail('BAD_INPUT', 'Use a decimal string with at most 15 integer and 12 fractional digits');
  const result = new Decimal(value);
  if (positive && result.isZero()) fail('BAD_INPUT', 'Reference price must be positive');
  return result;
}
export function createMarket() {
  const assets = [
    ['BTC', 'Bitcoin', 'CRYPTO', '60000'], ['ETH', 'Ether', 'CRYPTO', '3000'], ['SOL', 'Solana', 'CRYPTO', '150'],
    ['USD', 'US Dollar', 'FIAT', '1'], ['EUR', 'Euro', 'FIAT', '1.1'], ['JPY', 'Japanese Yen', 'FIAT', '0.007'],
  ].map(([symbol, name, kind, price]) => ({ symbol, name, kind, usdReference: new Decimal(price).toFixed(12), version: 0 }));
  const get = symbol => assets.find(a => a.symbol === symbol) || fail('NOT_FOUND', 'Unknown currency symbol');
  const rate = (base, quote) => new Decimal(get(base).usdReference).div(get(quote).usdReference).toFixed(12);
  const pair = (base, quote) => ({ id: `${base}-${quote}`, base: () => get(base), quote: () => get(quote), referenceRate: () => rate(base, quote) });
  const pairs = [['BTC','USD'],['ETH','USD'],['ETH','BTC'],['EUR','USD'],['USD','JPY']].map(([b,q]) => pair(b,q));
  const indices = [
    { id: 'CRYPTO-2', name: 'Crypto Duo', divisor: '12', members: [['BTC','0.01'],['ETH','0.2']] },
    { id: 'FX-2', name: 'Currency Basket', divisor: '0.9', members: [['EUR','50'],['JPY','5000']] },
  ].map(i => {
    const value = () => i.members.reduce((sum, [symbol, quantity]) => sum.plus(new Decimal(get(symbol).usdReference).mul(quantity)), new Decimal(0));
    return { id: i.id, name: i.name, divisor: new Decimal(i.divisor).toFixed(12), constituents: () => i.members.map(([symbol, quantity]) => ({ asset: get(symbol), quantity: new Decimal(quantity).toFixed(12) })), valueUsd: () => value().toFixed(12), level: () => value().div(i.divisor).toFixed(8) };
  });
  function page(items, {limit, offset}, key) {
    if (limit < 1 || limit > 100 || offset < 0) fail('BAD_INPUT', 'limit must be 1..100 and offset must be non-negative');
    return [...items].sort((a,b) => a[key].localeCompare(b[key])).slice(offset, offset + limit);
  }
  return {
    assets: args => page(assets.filter(a => !args.kind || a.kind === args.kind), args, 'symbol'),
    pairs: args => page(pairs, args, 'id'),
    indices: () => indices,
    convert: ({from, to, amount}) => {
      const value = decimal(amount);
      return {from, to, amount: value.toFixed(12), rate: rate(from, to), result: value.mul(get(from).usdReference).div(get(to).usdReference).toFixed(12)};
    },
    addPair: ({base, quote}) => {
      if (base === quote) fail('BAD_INPUT', 'A trading pair needs two different assets');
      get(base); get(quote);
      if (pairs.some(p => p.id === `${base}-${quote}`)) fail('PAIR_EXISTS', 'Trading pair already exists');
      const created = pair(base, quote); pairs.push(created); return created;
    },
    setReferencePrice: ({symbol, usdReference, expectedVersion}) => {
      const price = decimal(usdReference, true);
      if (expectedVersion < 0) fail('BAD_INPUT', 'Version must be non-negative');
      const asset = get(symbol);
      if (symbol === 'USD') fail('BAD_INPUT', 'USD is the fixed reference unit');
      if (expectedVersion !== asset.version) fail('CONFLICT', 'Reference changed; reload the version and retry');
      asset.usdReference = price.toFixed(12); asset.version++;
      return {...asset};
    },
  };
}
