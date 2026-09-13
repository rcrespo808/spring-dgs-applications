package dev.rcrespo.applications;

import com.netflix.graphql.dgs.DgsDataLoader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.dataloader.MappedBatchLoader;
import static dev.rcrespo.applications.Domain.Job;

@DgsDataLoader(name = "jobs")
public class JobLoader implements MappedBatchLoader<String, Job> {
    private final ApplicationStore store;
    public JobLoader(ApplicationStore store) { this.store = store; }

    @Override
    public CompletionStage<Map<String, Job>> load(Set<String> ids) {
        return CompletableFuture.completedFuture(store.jobsByIds(ids));
    }
}
