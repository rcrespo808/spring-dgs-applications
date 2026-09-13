package dev.rcrespo.markets;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import static dev.rcrespo.markets.Domain.*;

@Repository
public class MarketStore {
    private static final RowMapper<Asset> ASSET = (r, n) -> new Asset(r.getString("symbol"), r.getString("name"), AssetKind.valueOf(r.getString("kind")), r.getBigDecimal("usd_reference"), r.getInt("version"));
    private static final RowMapper<TradingPair> PAIR = (r, n) -> new TradingPair(r.getString("id"), r.getString("base_symbol"), r.getString("quote_symbol"));
    private static final RowMapper<MarketIndex> INDEX = (r, n) -> new MarketIndex(r.getString("id"), r.getString("name"), r.getBigDecimal("divisor"), r.getBigDecimal("value_usd"));
    private final NamedParameterJdbcTemplate jdbc;
    public MarketStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Asset> assets(AssetKind kind, int limit, int offset) {
        return jdbc.query("select * from market_assets" + (kind == null ? "" : " where kind = :kind") + " order by symbol limit :limit offset :offset",
                Map.of("kind", kind == null ? "" : kind.name(), "limit", limit, "offset", offset), ASSET);
    }
    public Map<String, Asset> assetsBySymbols(Set<String> symbols) {
        if (symbols.isEmpty()) return Map.of();
        return jdbc.query("select * from market_assets where symbol in (:symbols)", Map.of("symbols", symbols), ASSET)
                .stream().collect(Collectors.toMap(Asset::symbol, Function.identity()));
    }
    public List<TradingPair> pairs(int limit, int offset) {
        return jdbc.query("select * from trading_pairs order by id limit :limit offset :offset", Map.of("limit", limit, "offset", offset), PAIR);
    }
    public void insertPair(TradingPair pair) {
        jdbc.update("insert into trading_pairs(id, base_symbol, quote_symbol) values (:id, :base, :quote)", Map.of("id", pair.id(), "base", pair.baseSymbol(), "quote", pair.quoteSymbol()));
    }
    public List<MarketIndex> indices() {
        return jdbc.query("select i.id, i.name, i.divisor, sum(c.quantity * a.usd_reference) as value_usd " +
                "from market_indices i join index_constituents c on c.index_id = i.id " +
                "join market_assets a on a.symbol = c.symbol group by i.id, i.name, i.divisor order by i.id", Map.of(), INDEX);
    }
    public Map<String, List<Constituent>> constituentsByIndices(Set<String> ids) {
        Map<String, List<Constituent>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new ArrayList<>()));
        if (!ids.isEmpty()) jdbc.query("select * from index_constituents where index_id in (:ids) order by index_id, symbol", Map.of("ids", ids), r -> {
            result.get(r.getString("index_id")).add(new Constituent(r.getString("symbol"), r.getBigDecimal("quantity")));
        });
        return result;
    }
    public boolean updateReference(String symbol, java.math.BigDecimal price, int expectedVersion) {
        return jdbc.update("update market_assets set usd_reference = :price, version = version + 1 where symbol = :symbol and version = :version",
                Map.of("symbol", symbol, "price", price, "version", expectedVersion)) == 1;
    }
}
