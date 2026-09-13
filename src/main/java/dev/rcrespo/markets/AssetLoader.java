package dev.rcrespo.markets;

import com.netflix.graphql.dgs.DgsDataLoader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.dataloader.MappedBatchLoader;
import static dev.rcrespo.markets.Domain.Asset;

@DgsDataLoader(name = "assets")
public class AssetLoader implements MappedBatchLoader<String, Asset> {
    private final MarketStore store;
    public AssetLoader(MarketStore store) { this.store = store; }
    public CompletionStage<Map<String, Asset>> load(Set<String> symbols) {
        return CompletableFuture.completedFuture(store.assetsBySymbols(symbols));
    }
}
