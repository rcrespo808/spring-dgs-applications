package dev.rcrespo.applications;

import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.rcrespo.applications.Domain.*;

@Service
public class ApplicationService {
    private final ApplicationStore store;

    public ApplicationService(ApplicationStore store) { this.store = store; }

    public static void page(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new BusinessException("BAD_INPUT", "limit must be 1..100 and offset must be non-negative");
        }
    }

    @Transactional
    public Application apply(ApplyInput input) {
        if (input.jobId().isBlank() || input.candidateId().isBlank()) {
            throw new BusinessException("BAD_INPUT", "jobId and candidateId must not be blank");
        }
        if (!store.jobsByIds(Set.of(input.jobId())).containsKey(input.jobId()) ||
                !store.candidatesByIds(Set.of(input.candidateId())).containsKey(input.candidateId())) {
            throw new BusinessException("NOT_FOUND", "Job or candidate does not exist");
        }
        var application = new Application(UUID.randomUUID().toString(), input.jobId(), input.candidateId(), Status.SUBMITTED);
        try {
            store.insert(application);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("ALREADY_APPLIED", "Candidate has already applied to this job");
        }
        return application;
    }

    @Transactional
    public Application changeStatus(String id, Status next) {
        Application current = store.application(id);
        if (current == null) throw new BusinessException("NOT_FOUND", "Application does not exist");
        boolean allowed = switch (current.status()) {
            case SUBMITTED -> next == Status.REVIEWING || next == Status.REJECTED;
            case REVIEWING -> next == Status.INTERVIEW || next == Status.REJECTED;
            case INTERVIEW -> next == Status.OFFERED || next == Status.REJECTED;
            case OFFERED, REJECTED -> false;
        };
        if (!allowed) throw new BusinessException("INVALID_TRANSITION", "Cannot transition from " + current.status() + " to " + next);
        if (!store.transition(id, current.status(), next)) {
            throw new BusinessException("CONFLICT", "Application changed concurrently; reload and retry");
        }
        return new Application(id, current.jobId(), current.candidateId(), next);
    }
}
