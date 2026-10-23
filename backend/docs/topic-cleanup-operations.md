# Topic cleanup: verification and operation

Automatic deletion is **disabled by default**. Keep it disabled until isolated MySQL
verification, dry-run review, and an explicit operator decision have been completed.
No existing database is migrated or cleaned by the development commands below.

## Deployment prerequisites

- Migration V13 adds withdrawal/privacy management; V14 adds the two archives, four
  job tables, UTC deletion metadata, indexes, and an optional audit reason. Both
  scripts are present in the integration worktree. Apply V13 before V14 and verify
  the combined code/schema on isolated MySQL before deployment. Do not edit applied migrations or turn on
  Flyway out-of-order mode to bypass the ordering problem.
- Historical `deleted_at` values are not reinterpreted. A null
  `cleanup_deleted_at_utc` means `HELD_TIME_UNVERIFIED`. The new logical deletion
  path records UTC separately. No historical data is backfilled automatically.
- Confirm MySQL version, InnoDB, isolation, JDBC cancellation/lock waits, backup
  handling, and the common lock order before enabling deletion.
- A 30-second Spring transaction timeout does not guarantee termination of every
  driver/network/COMMIT operation within 30 seconds.

## Local tests

Run the backend test suite with the Maven Wrapper. Ordinary tests use disposable H2.
`TopicCleanupMySqlTest` is skipped unless explicitly enabled; H2 success does not
verify MySQL concurrency or connection-loss behavior.

To opt into MySQL verification, prepare an **empty, disposable local database** whose
name starts with `cleanup_verification_`. Configure these environment variables only
for that test process:

- `CLEANUP_MYSQL_TESTS=true`
- `CLEANUP_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/cleanup_verification_<suffix>`
- `CLEANUP_MYSQL_TEST_USER`
- `CLEANUP_MYSQL_TEST_PASSWORD`

Then run `./mvnw.cmd -Dtest=TopicCleanupMySqlTest test` in `backend`.
The test refuses remote URLs, other database names, and nonempty databases before
Flyway runs. It does not create/drop a database or start Docker. The suite creates
and deletes test rows inside that isolated database. Never supply development or
production credentials. The database must be empty again for a separate rerun.

The prepared suite covers migration application, inherited functional scenarios,
InnoDB, READ COMMITTED, and an actual MySQL lock wait. COMMIT response loss and
process/server termination still require a controlled fault-injection exercise;
do not label those verified merely because the ordinary suite passes.

## Administration

One React page: `/admin/topic-cleanup`.

All API paths start with `/api/admin/topic-cleanup`:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/runs?page=0&size=20` | Job results, newest first |
| GET | `/targets?page=0&size=20&state=STOPPED` | Target state/filter, elapsed hours |
| GET | `/targets/{id}/attempts?page=0&size=20` | Preserved attempt history |
| GET | `/dry-run?size=100` | Read-only candidate/hold inspection |
| POST | `/targets/{id}/resume` | Resume a stopped target; JSON `{"reason":"..."}` |

The authenticated principal contains a User ID, not an email. The current ACTIVE
administrator and role are checked by that ID in the database. Report evidence
still requires the separate privacy-review permission and an audited read; cleanup
administration does not grant that permission. Resume requires CSRF,
a nonblank reason of at most 500 Java characters, and an audit record in the same
transaction. It never starts deletion immediately. Dry-run writes no lease, run,
target, attempt, or archive.

## Scheduling and recovery

- Daily at 04:00 Asia/Tokyo, initially at most 100 distinct Topic slots.
- Holds, retries, and image rechecks consume slots. The same Topic has one slot per
  run. Interrupted reservations are not reused on that day.
- Owner/expiry/generation are stored in the database. A heartbeat uses a separate
  thread and connection. It may wait briefly for a deletion transaction's lease-row
  lock; failed renewal prevents starting further Topics.
- A successful deletion and its attempt marker commit atomically. Lost COMMIT
  responses leave an unfinished marker to reconcile on the next scheduled day.
- Transient failures stop after four failed attempts in the current cycle.
  Integrity failures stop immediately. Resume resets the cycle counter and retains
  all old attempt/audit records.
- X references hold the whole Topic. Reports and evidence are never removed.
- Shared/unverified image originals are retained. Pending image IDs and expected
  owner IDs survive Topic deletion and are rechecked in later runs. Never-used
  uploads and Profile images are outside this cleanup.

Job/audit retention periods are not decided. No automatic expiry is implemented
for these records. The normal logs contain identifiers and exception types, not
exception messages, source content, image bytes, or Throwable objects.

The formal STEP procedure and roadmap remain in the user's external documents;
this file is an operational note, not another authoritative roadmap.
