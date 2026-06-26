# Multi-machine deployment (Variant A)

Split the stack across **3 machines on the same LAN**, all sharing the single
Kafka broker on machine 1. No code changes — the services already talk only via
Kafka, and each keeps its own Postgres locally.

```
Machine 1 (CORE)     kafka + core (gateway) + frontend + core-postgres   → compose.core.yaml
Machine 2 (FINANCE)  banking + account + access (+ their postgres)        → compose.finance.yaml
Machine 3 (REST)     card + terminal + marketplace + news + audit (+ pg)  → compose.rest.yaml
```

Players/staff open the app at **http://<machine1-ip>:8088**.

---

## Prerequisites (all machines)
- Docker + Docker Compose.
- The repo present at the same path (build runs locally on each machine).
- Same LAN; machine 1 reachable from 2 & 3.
- On machine 1, allow inbound **9092** (Kafka), **8088** (frontend), **8080** (gateway).
  Postgres ports are only used locally and don't need to be opened across machines.

## 1. Configure (every machine)
```bash
cp .env.multi.example .env
# edit .env → set CORE_HOST to MACHINE 1's LAN IP (same value on all 3 machines)
```
Find machine 1's IP: `ipconfig` (Windows) / `ip a` (Linux) — e.g. `192.168.1.10`.

## 2. Start — machine 1 FIRST (Kafka must be up before the others connect)
```bash
# Machine 1:
docker compose -f compose.core.yaml up -d --build

# Machine 2:
docker compose -f compose.finance.yaml up -d --build

# Machine 3:
docker compose -f compose.rest.yaml up -d --build
```
Services on 2 & 3 retry until Kafka is reachable, so exact ordering isn't critical,
but starting machine 1 first avoids noisy startup logs.

## 3. Verify
```bash
# From machine 1 (or any LAN host):
curl -s -X POST http://<machine1-ip>:8080/api/auth/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"publicName":"admin","password":"admin12345"}'
# → 200 with a token means gateway ↔ account/access (on machine 2) work over Kafka.

# Full integration suite (run from anywhere on the LAN):
BASE=http://<machine1-ip>:8080/api node e2e/run-scenarios.mjs
```

## How it works / notes
- **Kafka advertised address:** machine 1's Kafka advertises `EXTERNAL://${CORE_HOST}:9092`
  to remote machines, and `INTERNAL://kafka:9094` to the co-located `core`. This is the
  one thing that makes cross-host work — if `CORE_HOST` is wrong, machines 2 & 3 connect
  but then get redirected to an unreachable address and time out.
- **DB-per-service:** each service reaches its Postgres over its own machine's docker
  network (`DB_HOST=<svc>-postgres`). No cross-host SQL.
- **Memory:** with ~3 JVMs per machine, the default heaps are fine; the `-Xmx` caps are
  kept (harmless) so even a modest laptop copes.
- **Single points:** one Kafka broker + one gateway. Fine for an event. For HA you'd run
  a 3-broker Kafka cluster with RF≥3 (revert the RF=1 settings) — out of scope here.
- **Rebalancing:** if machine 1 (Kafka) restarts, services on 2 & 3 reconnect automatically
  (`restart: unless-stopped` + Spring Kafka retry). Avoid `docker compose restart` of a
  single service — use `down && up -d` of that machine's compose to avoid DNS races.

## Single-machine (unchanged)
The original all-in-one `docker-compose.yaml` still works for one machine:
```bash
docker compose up -d --build      # full stack, 138/138 e2e green
```
