# GymFlow pure logic checks

These checks compile only platform-independent workout logic, so they can run even when the Android SDK and Gradle Wrapper are unavailable.

Run from the project root:

```sh
./tools/pure-tests/run.sh
```

Expected result:

```text
PURE_TEST_OK
```

Coverage includes workout progression, duplicate-completion protection, rest restoration by absolute end time, state normalization, idempotent workout-history merge, personal-record upgrades, full completion of representative generated plans, and birth-date/calendar rules including leap years, future-date clamping, ISO serialization, and migration from the legacy millisecond format.
