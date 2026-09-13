import { buildSchema, graphql, GraphQLError } from 'graphql';
import { createIcons, Braces, Github, Play, RotateCcw } from 'lucide';
import schemaText from '../src/main/resources/schema/applications.graphqls';

const schema = buildSchema(schemaText);
const jobs = [
  { id: 'job-1', title: 'Backend Java Engineer', company: 'Example Labs' },
  { id: 'job-2', title: 'Platform Engineer', company: 'Demo Systems' },
];
const candidates = [
  { id: 'candidate-1', name: 'Alex Demo' },
  { id: 'candidate-2', name: 'Sam Example' },
  { id: 'candidate-3', name: 'Taylor Sample' },
];
let applications;
function reset() {
  applications = [
    { id: 'application-1', jobId: 'job-1', candidateId: 'candidate-1', status: 'SUBMITTED' },
    { id: 'application-2', jobId: 'job-1', candidateId: 'candidate-2', status: 'REVIEWING' },
  ];
}
const fail = (code, message) => { throw new GraphQLError(message, { extensions: { code } }); };
function page(items, { limit, offset }) {
  if (limit < 1 || limit > 100 || offset < 0) fail('BAD_INPUT', 'limit must be 1..100 and offset must be non-negative');
  return [...items].sort((a, b) => a.id.localeCompare(b.id)).slice(offset, offset + limit);
}
function expand(application) {
  if (!application) return null;
  return { ...application, job: () => jobs.find(job => job.id === application.jobId), candidate: () => candidates.find(candidate => candidate.id === application.candidateId) };
}
const transitions = { SUBMITTED: ['REVIEWING', 'REJECTED'], REVIEWING: ['INTERVIEW', 'REJECTED'], INTERVIEW: ['OFFERED', 'REJECTED'], OFFERED: [], REJECTED: [] };
const rootValue = {
  jobs: args => page(jobs, args),
  candidates: args => page(candidates, args),
  applications: args => page(applications.filter(item => !args.status || item.status === args.status), args).map(expand),
  application: ({ id }) => expand(applications.find(item => item.id === id)),
  applyToJob: ({ input }) => {
    if (!input.jobId.trim() || !input.candidateId.trim()) fail('BAD_INPUT', 'jobId and candidateId must not be blank');
    if (!jobs.some(job => job.id === input.jobId) || !candidates.some(candidate => candidate.id === input.candidateId)) fail('NOT_FOUND', 'Job or candidate does not exist');
    if (applications.some(item => item.jobId === input.jobId && item.candidateId === input.candidateId)) fail('ALREADY_APPLIED', 'Candidate has already applied to this job');
    const application = { id: crypto.randomUUID(), ...input, status: 'SUBMITTED' };
    applications.push(application);
    return expand(application);
  },
  updateApplicationStatus: ({ id, status }) => {
    const application = applications.find(item => item.id === id);
    if (!application) fail('NOT_FOUND', 'Application does not exist');
    if (!transitions[application.status].includes(status)) fail('INVALID_TRANSITION', `Cannot transition from ${application.status} to ${status}`);
    application.status = status;
    return expand(application);
  },
};
const examples = {
  browse: 'query BrowseApplications {\n  applications(limit: 20) {\n    id\n    status\n    job { title company }\n    candidate { name }\n  }\n}',
  jobs: 'query AvailableJobs {\n  jobs { id title company }\n  candidates { id name }\n}',
  apply: 'mutation Apply {\n  applyToJob(input: {\n    jobId: "job-2"\n    candidateId: "candidate-3"\n  }) {\n    id\n    status\n    job { title }\n  }\n}',
  review: 'mutation Review {\n  updateApplicationStatus(\n    id: "application-1"\n    status: REVIEWING\n  ) { id status }\n}',
  filter: 'query InReview {\n  applications(status: REVIEWING) {\n    id\n    candidate { name }\n    job { title }\n  }\n}',
  invalid: 'mutation SkipSteps {\n  updateApplicationStatus(\n    id: "application-1"\n    status: OFFERED\n  ) { id status }\n}',
};
const query = document.querySelector('#query');
const result = document.querySelector('#result');
const status = document.querySelector('#status');
const runButton = document.querySelector('#run');
async function run() {
  runButton.disabled = true;
  const started = performance.now();
  try {
    const variableValues = JSON.parse(document.querySelector('#variables').value || '{}');
    if (!variableValues || Array.isArray(variableValues) || typeof variableValues !== 'object') throw new Error('Variables must be a JSON object');
    const response = await graphql({ schema, source: query.value, rootValue, variableValues });
    result.textContent = JSON.stringify(response, null, 2);
    status.textContent = `${response.errors ? 'Errors' : 'Complete'} · ${Math.round(performance.now() - started)} ms`;
    status.classList.toggle('error', !!response.errors);
  } catch (error) {
    result.textContent = JSON.stringify({ errors: [{ message: error.message }] }, null, 2);
    status.textContent = 'Invalid input';
    status.classList.add('error');
  } finally {
    runButton.disabled = false;
  }
}
document.querySelector('#example').addEventListener('change', event => {
  query.value = examples[event.target.value];
  document.querySelector('#variables').value = '{}';
});
runButton.addEventListener('click', run);
document.querySelector('#reset').addEventListener('click', () => {
  reset();
  query.value = examples.browse;
  document.querySelector('#example').value = 'browse';
  document.querySelector('#variables').value = '{}';
  run();
});
query.addEventListener('keydown', event => {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') { event.preventDefault(); run(); }
});
createIcons({ icons: { Braces, Github, Play, RotateCcw } });
document.querySelector('#schema').textContent = schemaText;
reset();
query.value = examples.browse;
run();
