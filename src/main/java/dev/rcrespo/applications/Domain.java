package dev.rcrespo.applications;

public final class Domain {
    private Domain() {}

    public record Job(String id, String title, String company) {}
    public record Candidate(String id, String name) {}
    public record Application(String id, String jobId, String candidateId, Status status) {}
    public record ApplyInput(String jobId, String candidateId) {}
    public enum Status { SUBMITTED, REVIEWING, INTERVIEW, OFFERED, REJECTED }

    public static final class BusinessException extends RuntimeException {
        private final String code;

        public BusinessException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() { return code; }
    }
}
