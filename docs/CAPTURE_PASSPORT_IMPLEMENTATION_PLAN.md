# Capture Passport — Implementation & Verification Plan

## 1. Scope lock

This feature adds a portable evidence/report layer around the existing capture manifest and dataset-quality analyzer. It does not replace the manifest, rewrite the capture planner, or implement photogrammetry.

Existing foundations confirmed in this branch:
- CaptureManifest schema v1 with frame sequence, planner yaw/pitch, byte size, SHA-256, timestamp and optional camera metadata.
- DatasetQualityAnalyzer with 36 planner cells, duplicate-cell count, duplicate SHA-256 warning, missing EXIF warning and mixed-dimensions warning.
- CaptureSessionStore ZIP export with manifest, COLMAP notes and streaming image integrity checks.
- Existing tests for coverage, repeated cells, duplicate content, EXIF/dimension warnings and empty sessions.

## 2. Module boundaries

### Core, pure Kotlin
- CapturePassport.kt: typed Passport, session summary, analyzer metadata and findings.
- CapturePassportAnalyzer.kt: maps manifest + existing quality report + optional integrity evidence into typed findings.
- CapturePassportContract.kt: validation and cross-document invariants.
- CapturePassportJson.kt: deterministic JSON encoding/decoding without Android UI dependencies.
- PassportTextReport.kt: deterministic human-readable report rendering.

### Data/storage
- Extend CaptureSessionStore export to create Passport snapshot and write it into the same ZIP.
- Keep existing manifest schema unchanged.
- Integrity result must be produced by the same streaming pass that writes image entries; do not load all large images into memory.
- Publish no completed destination if export fails.

### UI
- Add Passport summary/detail reachable from session/library flow.
- Show readiness, coverage, missing cells, warnings and unmeasured limitations.
- Provide check details and next action.
- Reuse existing navigation; no new navigation framework.

## 3. Data flow

    CaptureManifest
          |
          v
    DatasetQualityAnalyzer (existing)
          |
          v
    CapturePassportAnalyzer <--- export-time integrity evidence
          |
          +----> passport.json
          +----> passport-report.txt
          |
          v
    Dataset ZIP + manifest + source images + import notes

## 4. TDD sequence

1. Add tests for typed check outcomes and exact evidence (RED).
2. Implement Passport models and analyzer (GREEN).
3. Add contract tests for schema, enums, timestamps, counts, session mismatch and missing-cell uniqueness.
4. Add deterministic JSON round-trip tests.
5. Add report text tests from the same Passport model.
6. Refactor only while tests remain green.
7. Add export integration tests for ZIP contents, session agreement and image integrity.
8. Add UI state tests where feasible; perform manual UI verification only when an Android environment is available.

## 5. Verification commands

Current CI runs:
- gradle testDebugUnitTest
- gradle lintDebug
- gradle assembleDebug

No Gradle wrapper is checked in. Hosted CI uses JDK 17 and Gradle 8.11.1. Record exact environment and command for local verification.

## 6. Evidence matrix

| ID | Requirement | Evidence |
|---|---|---|
| V-01 | Empty session is INSUFFICIENT | Core unit test |
| V-02 | Exact 36-cell coverage and missing targets | Core unit test |
| V-03 | Duplicate cells differ from duplicate content | Core unit test |
| V-04 | EXIF/dimension signals typed and explicit | Core unit test |
| V-05 | Unmeasured checks are never PASS | Core unit test |
| V-06 | Passport JSON deterministic round-trip | Serialization test |
| V-07 | Passport and manifest session/count agree | Contract test |
| V-08 | Integrity PASS follows streaming verification only | Export integration test |
| V-09 | ZIP includes manifest, Passport JSON/report and registered images | ZIP test |
| V-10 | Unsafe/unregistered/missing/changed image fails export | Export security tests |
| V-11 | UI renders readiness and warnings from Passport model | UI test/manual evidence |
| V-12 | CI tests, lint and APK build pass | GitHub Actions run/artifact |

## 7. Risks and controls

- False confidence: show planner limits beside readiness.
- Memory pressure: stream image bytes and hashes.
- Schema drift: version Passport independently from Manifest and analyzer.
- Contradictory reports: JSON and text generated from one immutable model.
- Partial ZIP: use temporary destination and publish only after successful close/validation.
- Old sessions: optional metadata remains null and becomes explicit warning/NOT_MEASURED.
- Privacy leakage: no GPS or unique device IDs by default.
- Over-scope: defer blur, overlap, occlusion, parallax, calibration and reconstruction.

## 8. Implementation order and current state

- P0 Documentation: DONE — product specification, evidence model, data contract, architecture, requirements and user guide are committed.
- P1 Core contracts: IMPLEMENTED IN BRANCH — typed model, analyzer, validator, JSON serializer and text renderer; test execution is pending.
- P2 ZIP export integration: IMPLEMENTED IN BRANCH — Passport entries are added after streamed image size/hash verification; integration tests are committed.
- P3 UI: IMPLEMENTED IN BRANCH — Library summary, details dialog and shareable JSON preview are committed.
- P4 CI/device verification: NOT RUN / PENDING — no successful hosted CI result or physical-device test is claimed.

Do not call the feature complete until P1–P4 evidence is recorded.
