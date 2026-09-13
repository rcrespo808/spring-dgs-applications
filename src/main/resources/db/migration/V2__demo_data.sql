insert into jobs(id, title, company) values
    ('job-1', 'Backend Java Engineer', 'Example Labs'),
    ('job-2', 'Platform Engineer', 'Demo Systems');
insert into candidates(id, name) values
    ('candidate-1', 'Alex Demo'),
    ('candidate-2', 'Sam Example'),
    ('candidate-3', 'Taylor Sample');
insert into applications(id, job_id, candidate_id, status) values
    ('application-1', 'job-1', 'candidate-1', 'SUBMITTED'),
    ('application-2', 'job-1', 'candidate-2', 'REVIEWING');
