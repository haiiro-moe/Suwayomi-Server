# Suwairo manual verification

## Why this is needed

The automated server test suite requires TCP port `4567`. On the development machine, that port is currently occupied by an unrelated `Tachimanga.app` JVM (PID `46836`). It must not be terminated automatically because it is outside this checkout.

## Before running the full test suite

1. Check the process currently listening on port `4567`:

   ```bash
   lsof -nP -iTCP:4567 -sTCP:LISTEN
   ps -o pid,ppid,user,lstart,command -p <PID>
   ```

2. If the process is the unrelated Tachimanga process and it is safe to stop, terminate it using its reported PID:

   ```bash
   kill <PID>
   ```

   If it does not exit after a few seconds:

   ```bash
   kill -9 <PID>
   ```

3. Confirm that port `4567` is free:

   ```bash
   lsof -nP -iTCP:4567 -sTCP:LISTEN
   ```

   The command should produce no listener row.

4. Run the server tests from the repository root:

   ```bash
   GRADLE_USER_HOME=/tmp/suwairo-gradle ./gradlew \
     -Dorg.gradle.daemon=false \
     -Dorg.gradle.jvmargs=-Xmx5g \
     -Dkotlin.compiler.execution.strategy=in-process \
     :server:test
   ```

## Docker verification

To ensure no Compose container is holding the port before testing:

```bash
docker compose down --remove-orphans
docker compose config --quiet
docker compose build --progress=plain
docker compose up -d
curl --fail http://127.0.0.1:4567/
docker compose ps
docker compose down --remove-orphans
```

The service should report `healthy`, and `curl` should return HTTP 200.
