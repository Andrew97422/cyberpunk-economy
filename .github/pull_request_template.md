## What and why

<!-- The change in one or two sentences, and the problem it solves. -->

## How it was verified

<!-- Delete what does not apply. -->

- [ ] `./mvnw -B -DskipTests package` passes for every touched service
- [ ] `npm run typecheck && npm run build` passes in `frontend/`
- [ ] `node e2e/run-scenarios.mjs` passes against a locally running stack
- [ ] Ran manually against `docker compose up -d --build`

## Contract impact

- [ ] Adds or changes a **Kafka command/event** → [`main-server/docs/event-catalog.md`](../main-server/docs/event-catalog.md) updated, payload versioned
- [ ] Adds or changes a **database schema** → new Flyway migration, no edits to an applied one
- [ ] Adds or changes a **gateway REST endpoint** → [`e2e/SCENARIOS.md`](../e2e/SCENARIOS.md) updated
- [ ] Changes **deployment** → all affected compose topologies updated together

## Checks

- [ ] No secret, credential or real participant data added to the repository
- [ ] New configuration is declared in `.env.example` (and `.env.multi.example` if relevant)
