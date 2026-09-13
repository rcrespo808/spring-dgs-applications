package dev.rcrespo.applications;

import com.netflix.graphql.dgs.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import static dev.rcrespo.applications.Domain.*;

@DgsComponent
public class ApplicationFetchers {
    private final ApplicationStore store;
    private final ApplicationService service;

    public ApplicationFetchers(ApplicationStore store, ApplicationService service) {
        this.store = store;
        this.service = service;
    }

    @DgsQuery
    public List<Job> jobs(@InputArgument int limit, @InputArgument int offset) {
        ApplicationService.page(limit, offset);
        return store.jobs(limit, offset);
    }

    @DgsQuery
    public List<Candidate> candidates(@InputArgument int limit, @InputArgument int offset) {
        ApplicationService.page(limit, offset);
        return store.candidates(limit, offset);
    }

    @DgsQuery
    public List<Application> applications(@InputArgument Status status, @InputArgument int limit, @InputArgument int offset) {
        ApplicationService.page(limit, offset);
        return store.applications(status, limit, offset);
    }

    @DgsQuery
    public Application application(@InputArgument String id) { return store.application(id); }

    @DgsMutation
    public Application applyToJob(@InputArgument ApplyInput input) { return service.apply(input); }

    @DgsMutation
    public Application updateApplicationStatus(@InputArgument String id, @InputArgument Status status) {
        return service.changeStatus(id, status);
    }

    @DgsData(parentType = "Application", field = "job")
    public CompletableFuture<Job> job(DgsDataFetchingEnvironment environment) {
        Application application = environment.getSource();
        DataLoader<String, Job> loader = environment.getDataLoader("jobs");
        return loader.load(application.jobId());
    }

    @DgsData(parentType = "Application", field = "candidate")
    public CompletableFuture<Candidate> candidate(DgsDataFetchingEnvironment environment) {
        Application application = environment.getSource();
        DataLoader<String, Candidate> loader = environment.getDataLoader("candidates");
        return loader.load(application.candidateId());
    }
}
