# Product Requirements

## Primary flow
Create session → live preview → follow next angle → stabilize → capture → review → Passport diagnostics → export dataset with evidence → reconstruct/import 3D scene → compose offline tour.

## Functional requirements
FR-01 Camera preview and full-resolution still capture.
FR-02 Offline operation after installation.
FR-03 Sensor yaw/pitch when rotation-vector sensor exists.
FR-04 Deterministic coverage plan with duplicate suppression.
FR-05 Next-shot target and progress.
FR-06 Session-safe storage and recovery manifest. (Implemented in current development branch; CI verification is separate.)
FR-07 Review, retake same planner view, and delete frames. (Implemented in current development branch; CI verification is separate.)
FR-08 JPEG + metadata + COLMAP source-image input package with explicit pose/calibration limitations. (Implemented; this is not a COLMAP sparse model.)
FR-09 360 panorama import and interactive viewer.
FR-10 Hotspots, scene links and tour export.
FR-11 Generate a versioned Capture Passport for each session.
FR-12 Report planner coverage, duplicate planner cells, duplicate image hashes, EXIF availability and known image-dimension consistency as typed evidence checks.
FR-13 Distinguish measured, warning, failed and not-measured properties.
FR-14 Include Passport JSON and a human-readable report in the dataset ZIP.
FR-15 Verify every registered image's path, byte size and SHA-256 during export; fail closed on mismatch.
FR-16 Validate Passport schema and consistency with the Capture Manifest.

## Quality requirements
No network permission; lifecycle-safe camera; responsive UI; pure Kotlin planner/quality tests; graceful sensor fallback; cancellable thermal-aware reconstruction; versioned provenance manifest and Passport; deterministic analysis; bounded-memory streaming export; safe archive paths; no personally identifying metadata by default.

## Capture Passport acceptance criteria
- Empty session is INSUFFICIENT and 0% coverage.
- Coverage uses 36 planner cells (12 azimuth × 3 pitch bands), not frame count.
- Duplicate planner cells and duplicate image content are separate checks.
- Missing EXIF and mixed known dimensions are explicit warnings.
- READY is blocked by any manifest or image-integrity failure.
- Image integrity is PASS only after export-time byte-size and SHA-256 verification.
- Blur, feature overlap, occlusion, parallax, camera calibration accuracy and reconstruction success remain NOT_MEASURED until validated analyzers are implemented.
- Passport JSON and text report are generated from one immutable report model.
- Dataset ZIP includes passport.json, passport-report.txt, manifest.json, source images and COLMAP import notes.
- CI tests, lint and APK build must pass; physical-device verification is a separate gate.

## 0.1 capture acceptance criteria
App launches and requests camera permission; preview binds to lifecycle; still capture saves privately; orientation is displayed if sensor exists; planner has 36 unique targets; tests cover wraparound, snapping, duplicate suppression and completion; no internet permission.
