# Suwairo manual verification

## Port and test-suite prerequisite

The server tests require TCP port `4567`. Check ownership before stopping anything:

```bash
lsof -nP -iTCP:4567 -sTCP:LISTEN
ps -o pid,ppid,user,lstart,command -p <PID>
```

If the listener is this repository's Compose service, stop it with:

```bash
docker compose down --remove-orphans
```

If it is an unrelated process, stop it only after confirming that it is safe to do so. Do not terminate unrelated applications automatically.

Run the full suite:

```bash
GRADLE_USER_HOME=/tmp/suwairo-gradle ./gradlew \
  -Dorg.gradle.daemon=false \
  -Dorg.gradle.jvmargs=-Xmx5g \
  -Dkotlin.compiler.execution.strategy=in-process \
  :server:test
```

## Docker and OrbStack verification

```bash
docker compose down --remove-orphans
docker compose config --quiet
docker compose build --progress=plain
docker compose up -d

curl --fail -i http://127.0.0.1:4567/
curl --fail -i http://server.suwairo.orb.local/
docker compose ps
docker compose logs --tail=200 server
```

Expected results:

- both curl requests return HTTP `200`;
- `docker compose ps` reports the server as `healthy`;
- logs show Javalin listening on port `4567`;
- the logs do not contain a GraphQL schema initialization failure.

Stop the local stack afterward if it is not needed:

```bash
docker compose down --remove-orphans
```

## Multi-user manual verification

Use the owner account to create a non-owner role and user through GraphQL:

1. Query `permissionNodes` and choose only the permissions required for the test role.
2. Call `createRole` with those nodes.
3. Call `createUser` with the role ID.
4. Log in as the new user and verify that unauthorized fields return GraphQL authorization errors.
5. Grant that user read access to one category with `setCategoryAccess`.
6. Verify that manga, chapters, favorites, and chapter mutations outside that category are hidden or rejected.
7. Verify that `createBackup`, `restoreBackup`, `validateBackup`, and `restoreStatus` are rejected for the non-owner.
8. Verify that the owner can still perform all backup operations.

## Remaining manual/architectural boundary

Tracker credentials are still held by the existing process-global tracker singleton preferences. Do not use multiple users with the same tracker account until tracker clients are refactored to be user-scoped. The safe implementation requires passing a user-scoped tracker session/client through login, token refresh, tracking mutations, and all tracker implementations; merely adding a user ID to the existing shared preference key would not provide isolation.

For that slice, manual verification must use two tracker accounts:

1. Log in as user A and authenticate tracker account A.
2. Log in as user B and authenticate tracker account B.
3. Confirm each user sees and updates only their own tracker session.
4. Restart the container and confirm encrypted credentials remain usable.
5. Confirm backup exports do not expose plaintext tracker credentials.
