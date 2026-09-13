package dev.rcrespo.applications;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static dev.rcrespo.applications.Domain.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationApiTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean ApplicationStore store;

    @BeforeEach
    void resetData() {
        jdbc.update("delete from applications");
        jdbc.update("insert into applications(id, job_id, candidate_id, status) values ('application-1', 'job-1', 'candidate-1', 'SUBMITTED')");
        jdbc.update("insert into applications(id, job_id, candidate_id, status) values ('application-2', 'job-1', 'candidate-2', 'REVIEWING')");
        clearInvocations(store);
    }

    private JsonNode execute(String query) {
        var response = http.postForEntity("/graphql", Map.of("query", query), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    private void error(JsonNode result, String code) {
        assertThat(result.path("errors").get(0).path("extensions").path("code").asText()).isEqualTo(code);
    }

    @Test
    void batchesNestedRelationsAndDeduplicatesSharedJob() {
        var result = execute("{ applications { id status job { title } candidate { name } } }");
        assertThat(result.has("errors")).isFalse();
        assertThat(result.at("/data/applications").size()).isEqualTo(2);
        assertThat(result.at("/data/applications/0/job/title").asText()).isEqualTo("Backend Java Engineer");
        verify(store, times(1)).jobsByIds(Set.of("job-1"));
        verify(store, times(1)).candidatesByIds(Set.of("candidate-1", "candidate-2"));
    }

    @Test
    void selectingOnlyIdsDoesNotLoadRelationships() {
        execute("{ applications { id } }");
        verify(store, never()).jobsByIds(anySet());
        verify(store, never()).candidatesByIds(anySet());
    }

    @Test
    void appliesAndReadsPersistedApplication() {
        var result = execute("mutation { applyToJob(input: { jobId: \"job-2\", candidateId: \"candidate-3\" }) { id status job { id } } }");
        assertThat(result.has("errors")).isFalse();
        assertThat(result.at("/data/applyToJob/status").asText()).isEqualTo("SUBMITTED");
        String id = result.at("/data/applyToJob/id").asText();
        var read = execute("{ application(id: \"" + id + "\") { id candidate { name } } }");
        assertThat(read.at("/data/application/candidate/name").asText()).isEqualTo("Taylor Sample");
        assertThat(jdbc.queryForObject("select count(*) from applications where id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateApplications() {
        error(execute("mutation { applyToJob(input: {jobId: \"job-1\", candidateId: \"candidate-1\"}) { id } }"), "ALREADY_APPLIED");
    }

    @Test
    void duplicateConstraintHoldsUnderConcurrentRequests() {
        String mutation = "mutation { applyToJob(input: {jobId: \"job-2\", candidateId: \"candidate-3\"}) { id } }";
        var first = CompletableFuture.supplyAsync(() -> execute(mutation));
        var second = CompletableFuture.supplyAsync(() -> execute(mutation));
        var results = java.util.List.of(first.join(), second.join());
        assertThat(results.stream().filter(result -> !result.has("errors")).count()).isEqualTo(1);
        error(results.stream().filter(result -> result.has("errors")).findFirst().orElseThrow(), "ALREADY_APPLIED");
    }

    @Test
    void rejectsUnknownAndBlankReferences() {
        error(execute("mutation { applyToJob(input: {jobId: \"missing\", candidateId: \"candidate-1\"}) { id } }"), "NOT_FOUND");
        error(execute("mutation { applyToJob(input: {jobId: \" \" , candidateId: \"candidate-1\"}) { id } }"), "BAD_INPUT");
    }

    @Test
    void progressesThroughWorkflowAndRejectsChangesToTerminalStatus() {
        for (String status : new String[]{"REVIEWING", "INTERVIEW", "OFFERED"}) {
            var result = execute("mutation { updateApplicationStatus(id: \"application-1\", status: " + status + ") { status } }");
            assertThat(result.at("/data/updateApplicationStatus/status").asText()).isEqualTo(status);
        }
        error(execute("mutation { updateApplicationStatus(id: \"application-1\", status: REJECTED) { id } }"), "INVALID_TRANSITION");
    }

    @Test
    void rejectsSkippedStepsAndUnknownApplications() {
        error(execute("mutation { updateApplicationStatus(id: \"application-1\", status: OFFERED) { id } }"), "INVALID_TRANSITION");
        error(execute("mutation { updateApplicationStatus(id: \"missing\", status: REVIEWING) { id } }"), "NOT_FOUND");
    }

    @Test
    void staleStatusCannotOverwriteNewerStatus() {
        assertThat(store.transition("application-1", Status.SUBMITTED, Status.REVIEWING)).isTrue();
        assertThat(store.transition("application-1", Status.SUBMITTED, Status.REJECTED)).isFalse();
        assertThat(store.application("application-1").status()).isEqualTo(Status.REVIEWING);
    }

    @Test
    void filtersAndBoundsPages() {
        var result = execute("{ applications(status: REVIEWING, limit: 1) { id } jobs(limit: 1, offset: 1) { id } candidates(limit: 1) { id } }");
        assertThat(result.has("errors")).isFalse();
        assertThat(result.at("/data/applications/0/id").asText()).isEqualTo("application-2");
        assertThat(result.at("/data/jobs/0/id").asText()).isEqualTo("job-2");
        error(execute("{ applications(limit: 101) { id } }"), "BAD_INPUT");
        error(execute("{ jobs(offset: -1) { id } }"), "BAD_INPUT");
    }

    @Test
    void unknownLookupIsNullAndInvalidEnumIsRejectedBySchema() {
        assertThat(execute("{ application(id: \"missing\") { id } }").at("/data/application").isNull()).isTrue();
        assertThat(execute("{ applications(status: UNKNOWN) { id } }").path("errors").isArray()).isTrue();
    }
}
