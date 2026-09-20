#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
OUT="${TMPDIR:-/tmp}/gymflow-pure-tests.jar"
kotlinc \
  "$ROOT/app/src/main/java/com/aess/gymflow/Models.kt" \
  "$ROOT/app/src/main/java/com/aess/gymflow/BirthDate.kt" \
  "$ROOT/app/src/main/java/com/aess/gymflow/WorkoutData.kt" \
  "$ROOT/app/src/main/java/com/aess/gymflow/WorkoutProgression.kt" \
  "$ROOT/app/src/main/java/com/aess/gymflow/WorkoutHistoryLogic.kt" \
  "$ROOT/app/src/main/java/com/aess/gymflow/WorkoutRecordUpdater.kt" \
  "$ROOT/app/src/main/java/com/aess/gymflow/PersonalizedWorkouts.kt" \
  "$ROOT/tools/pure-tests/Stubs.kt" \
  "$ROOT/tools/pure-tests/Main.kt" \
  -include-runtime -d "$OUT"
java -jar "$OUT"
