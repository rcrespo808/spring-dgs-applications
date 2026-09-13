package dev.rcrespo.markets;

import com.netflix.graphql.dgs.DgsDataLoader;
import java.util.*;
import java.util.concurrent.*;
import org.dataloader.MappedBatchLoader;
import static dev.rcrespo.markets.Domain.Constituent;

@DgsDataLoader(name = "constituents")
public class ConstituentLoader implements MappedBatchLoader<String, List<Constituent>> {
    private final MarketStore store;
    public ConstituentLoader(MarketStore store) { this.store = store; }
    public CompletionStage<Map<String, List<Constituent>>> load(Set<String> ids) {
        return CompletableFuture.completedFuture(store.constituentsByIndices(ids));
    }
}
