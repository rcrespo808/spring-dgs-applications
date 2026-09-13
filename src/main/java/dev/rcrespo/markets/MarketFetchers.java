package dev.rcrespo.markets;

import com.netflix.graphql.dgs.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import static dev.rcrespo.markets.Domain.*;

@DgsComponent
public class MarketFetchers {
    private final MarketStore store;
    private final MarketService service;
    private final BinanceClient binance;
    public MarketFetchers(MarketStore store, MarketService service, BinanceClient binance) { this.store = store; this.service = service; this.binance = binance; }

    @DgsQuery public List<BinanceClient.ExchangePrice> binancePrices() { return binance.prices(); }

    @DgsQuery public List<Asset> assets(@InputArgument AssetKind kind, @InputArgument int limit, @InputArgument int offset) {
        MarketService.page(limit, offset); return store.assets(kind, limit, offset);
    }
    @DgsQuery public List<TradingPair> pairs(@InputArgument int limit, @InputArgument int offset) {
        MarketService.page(limit, offset); return store.pairs(limit, offset);
    }
    @DgsQuery public List<MarketIndex> indices() { return store.indices(); }
    @DgsQuery public Conversion convert(@InputArgument String from, @InputArgument String to, @InputArgument String amount) {
        return service.convert(from, to, amount);
    }
    @DgsMutation public TradingPair addPair(@InputArgument String base, @InputArgument String quote) { return service.addPair(base, quote); }
    @DgsMutation public Asset setReferencePrice(@InputArgument String symbol, @InputArgument String usdReference, @InputArgument int expectedVersion, DgsDataFetchingEnvironment env) {
        Asset asset = service.updateReference(symbol, usdReference, expectedVersion);
        // Mutations in the same operation must not reuse a cached pre-update price.
        DataLoader<String, Asset> loader = env.getDataLoader("assets");
        loader.clear(symbol);
        return asset;
    }
    @DgsData(parentType = "Asset", field = "usdReference") public String reference(DgsDataFetchingEnvironment env) {
        Asset asset = env.getSource(); return MarketService.format(asset.usdReference(), 12);
    }
    @DgsData(parentType = "TradingPair", field = "base") public CompletableFuture<Asset> base(DgsDataFetchingEnvironment env) {
        TradingPair pair = env.getSource(); return this.<Asset>loader(env, "assets").load(pair.baseSymbol());
    }
    @DgsData(parentType = "TradingPair", field = "quote") public CompletableFuture<Asset> quote(DgsDataFetchingEnvironment env) {
        TradingPair pair = env.getSource(); return this.<Asset>loader(env, "assets").load(pair.quoteSymbol());
    }
    @DgsData(parentType = "TradingPair", field = "referenceRate") public CompletableFuture<String> rate(DgsDataFetchingEnvironment env) {
        TradingPair pair = env.getSource(); DataLoader<String, Asset> assets = loader(env, "assets");
        return assets.load(pair.baseSymbol()).thenCombine(assets.load(pair.quoteSymbol()), MarketService::rate);
    }
    @DgsData(parentType = "MarketIndex", field = "constituents") public CompletableFuture<List<Constituent>> constituents(DgsDataFetchingEnvironment env) {
        MarketIndex index = env.getSource(); return this.<List<Constituent>>loader(env, "constituents").load(index.id());
    }
    @DgsData(parentType = "Constituent", field = "asset") public CompletableFuture<Asset> constituentAsset(DgsDataFetchingEnvironment env) {
        Constituent constituent = env.getSource(); return this.<Asset>loader(env, "assets").load(constituent.symbol());
    }
    @DgsData(parentType = "Constituent", field = "quantity") public String quantity(DgsDataFetchingEnvironment env) {
        Constituent constituent = env.getSource(); return MarketService.format(constituent.quantity(), 12);
    }
    @DgsData(parentType = "MarketIndex", field = "divisor") public String divisor(DgsDataFetchingEnvironment env) {
        MarketIndex index = env.getSource(); return MarketService.format(index.divisor(), 12);
    }
    @DgsData(parentType = "MarketIndex", field = "valueUsd") public String value(DgsDataFetchingEnvironment env) {
        MarketIndex index = env.getSource(); return MarketService.format(index.valueUsd(), 12);
    }
    @DgsData(parentType = "MarketIndex", field = "level") public String level(DgsDataFetchingEnvironment env) {
        MarketIndex index = env.getSource();
        return index.valueUsd().divide(index.divisor(), 8, RoundingMode.HALF_EVEN).toPlainString();
    }
    private <T> DataLoader<String, T> loader(DgsDataFetchingEnvironment env, String name) { return env.getDataLoader(name); }
}
