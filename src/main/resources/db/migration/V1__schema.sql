create table jobs (
    id varchar(36) primary key,
    title varchar(160) not null,
    company varchar(160) not null
);
create table candidates (
    id varchar(36) primary key,
    name varchar(160) not null
);
create table applications (
    id varchar(36) primary key,
    job_id varchar(36) not null references jobs(id),
    candidate_id varchar(36) not null references candidates(id),
    status varchar(20) not null check (status in ('SUBMITTED', 'REVIEWING', 'INTERVIEW', 'OFFERED', 'REJECTED')),
    constraint one_application_per_job unique (job_id, candidate_id)
);
create index applications_status_id on applications(status, id);
