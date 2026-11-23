# Product Requirements

## Primary flow
Create session → live preview → follow next angle → stabilize → capture → review → export dataset → reconstruct/import 3D scene → compose offline tour.

## Functional requirements
FR-01 Camera preview and full-resolution still capture.
FR-02 Offline operation after installation.
FR-03 Sensor yaw/pitch when rotation-vector sensor exists.
FR-04 Deterministic coverage plan with duplicate suppression.
FR-05 Next-shot target and progress.
FR-06 Session-safe storage and recovery manifest. (Implemented in the current development branch; CI verification pending.)
FR-07 Review, retake and delete frames.
FR-08 JPEG + metadata + COLMAP-compatible export.
FR-09 360 panorama import and interactive viewer.
FR-10 Hotspots, scene links and tour export.

## Quality requirements
No network permission; lifecycle-safe camera; responsive UI; pure Kotlin planner tests; graceful sensor fallback; cancellable thermal-aware reconstruction; versioned provenance manifest.

## 0.1 acceptance criteria
App launches and requests camera permission; preview binds to lifecycle; still capture saves privately; orientation is displayed if sensor exists; planner has 36 unique targets; tests cover wraparound, snapping, duplicate suppression and completion; no internet permission.
