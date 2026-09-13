package dev.rcrespo.applications;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import static dev.rcrespo.applications.Domain.*;

@Repository
public class ApplicationStore {
    private static final RowMapper<Job> JOB = (rs, row) -> new Job(rs.getString("id"), rs.getString("title"), rs.getString("company"));
    private static final RowMapper<Candidate> CANDIDATE = (rs, row) -> new Candidate(rs.getString("id"), rs.getString("name"));
    private static final RowMapper<Application> APPLICATION = (rs, row) -> new Application(rs.getString("id"), rs.getString("job_id"), rs.getString("candidate_id"), Status.valueOf(rs.getString("status")));
    private final NamedParameterJdbcTemplate jdbc;

    public ApplicationStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Job> jobs(int limit, int offset) {
        return jdbc.query("select * from jobs order by id limit :limit offset :offset", Map.of("limit", limit, "offset", offset), JOB);
    }

    public List<Candidate> candidates(int limit, int offset) {
        return jdbc.query("select * from candidates order by id limit :limit offset :offset", Map.of("limit", limit, "offset", offset), CANDIDATE);
    }

    public Map<String, Job> jobsByIds(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return jdbc.query("select * from jobs where id in (:ids)", Map.of("ids", ids), JOB)
                .stream().collect(Collectors.toMap(Job::id, Function.identity()));
    }

    public Map<String, Candidate> candidatesByIds(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return jdbc.query("select * from candidates where id in (:ids)", Map.of("ids", ids), CANDIDATE)
                .stream().collect(Collectors.toMap(Candidate::id, Function.identity()));
    }

    public List<Application> applications(Status status, int limit, int offset) {
        String filter = status == null ? "" : " where status = :status";
        return jdbc.query("select * from applications" + filter + " order by id limit :limit offset :offset",
                Map.of("status", status == null ? "" : status.name(), "limit", limit, "offset", offset), APPLICATION);
    }

    public Application application(String id) {
        return jdbc.query("select * from applications where id = :id", Map.of("id", id), APPLICATION)
                .stream().findFirst().orElse(null);
    }

    public void insert(Application application) {
        jdbc.update("insert into applications(id, job_id, candidate_id, status) values (:id, :job, :candidate, :status)",
                Map.of("id", application.id(), "job", application.jobId(), "candidate", application.candidateId(), "status", application.status().name()));
    }

    public boolean transition(String id, Status previous, Status next) {
        return jdbc.update("update applications set status = :next where id = :id and status = :previous",
                Map.of("id", id, "previous", previous.name(), "next", next.name())) == 1;
    }
}
