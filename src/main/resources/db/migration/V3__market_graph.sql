create table market_assets (
    symbol varchar(12) primary key,
    name varchar(120) not null,
    kind varchar(10) not null check (kind in ('CRYPTO', 'FIAT')),
    usd_reference numeric(30,12) not null check (usd_reference > 0),
    version integer not null default 0 check (version >= 0)
);
create table trading_pairs (
    id varchar(25) primary key,
    base_symbol varchar(12) not null references market_assets(symbol),
    quote_symbol varchar(12) not null references market_assets(symbol),
    constraint unique_trading_pair unique(base_symbol, quote_symbol),
    check (base_symbol <> quote_symbol)
);
create table market_indices (id varchar(20) primary key, name varchar(120) not null, divisor numeric(30,12) not null check (divisor > 0));
create table index_constituents (
    index_id varchar(20) not null references market_indices(id),
    symbol varchar(12) not null references market_assets(symbol),
    quantity numeric(30,12) not null check (quantity > 0),
    primary key(index_id, symbol)
);
insert into market_assets(symbol, name, kind, usd_reference) values
    ('BTC', 'Bitcoin', 'CRYPTO', 60000), ('ETH', 'Ether', 'CRYPTO', 3000),
    ('SOL', 'Solana', 'CRYPTO', 150), ('USD', 'US Dollar', 'FIAT', 1),
    ('EUR', 'Euro', 'FIAT', 1.1), ('JPY', 'Japanese Yen', 'FIAT', 0.007);
insert into trading_pairs(id, base_symbol, quote_symbol) values
    ('BTC-USD', 'BTC', 'USD'), ('ETH-USD', 'ETH', 'USD'),
    ('ETH-BTC', 'ETH', 'BTC'), ('EUR-USD', 'EUR', 'USD'), ('USD-JPY', 'USD', 'JPY');
insert into market_indices(id, name, divisor) values
    ('CRYPTO-2', 'Crypto Duo', 12), ('FX-2', 'Currency Basket', 0.9);
insert into index_constituents(index_id, symbol, quantity) values
    ('CRYPTO-2', 'BTC', 0.01), ('CRYPTO-2', 'ETH', 0.2),
    ('FX-2', 'EUR', 50), ('FX-2', 'JPY', 5000);
