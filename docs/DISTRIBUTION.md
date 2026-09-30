# Distribution — GymGlow-Test

This test repository is public with the owner’s explicit approval. A push to main runs static checks, Kotlin logic tests, Android compilation, lint and Android startup smoke checks. Only after build and smoke succeed does CI publish APK and SHA-256 to a prerelease tagged v1.5 in this repository. No separate distribution repository or RELEASES_TOKEN is required.

The APK installs separately as GymGlow Test (com.aess.gymflow.test) with the standard CI-generated debug signature. It does not replace the existing app or migrate its private data automatically. The supplied signing keystore is excluded from this repository after automatic safety review rejected its publication. It is a debug test build, not a production release.
