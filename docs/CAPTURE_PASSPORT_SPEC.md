# 3DCAP Capture Passport — Product & UX Specification

**Status:** Approved implementation baseline  
**Version:** 1.0.0  
**Scope:** Android-first, offline-first capture evidence and portable dataset identity

## 1. Purpose

A Capture Passport is the human-readable and machine-readable record that travels with one 3DCAP capture session. It explains what was captured, how the capture was organized, what evidence exists, what is missing, what checks were performed, and whether the source dataset is ready for the next processing stage.

It is not a 3D model, camera calibration certificate, or proof that reconstruction will succeed. It is a traceable evidence package for a capture dataset.

## 2. User problem and outcomes

A folder of photographs does not explain whether the object was covered, whether images are duplicated, whether metadata is missing, or what limitations apply to reconstruction. The user needs a portable answer to: "What is in this dataset, how trustworthy is it, and what should I do next?"

The feature must:
1. Give every session a stable Passport identity.
2. Show coverage, warnings, missing planner cells, image integrity and available metadata offline.
3. Include the exact evidence snapshot in dataset export.
4. Allow a receiving workflow to verify image size and SHA-256.
5. Communicate diagnostic limits without calling planner coverage geometric completeness.
6. Remain useful after transfer away from the source phone.

## 3. Personas

- Capture operator: needs next actions and honest readiness.
- 3D technician: needs image inventory, provenance, hashes, dimensions and planner context.
- Archive/commerce operator: needs a self-contained record and repeatable export.
- Reconstruction operator: needs to know which inputs exist and which calibration/pose facts are absent.

## 4. Primary workflow

Create/open session → capture frames → open Passport → inspect status, coverage, warnings and missing cells → resolve actionable issues → generate fresh analysis → export ZIP → inspect and verify on another device → hand off to reconstruction.

## 5. Passport contents

### Identity
- Passport schema version and Capture Manifest schema version
- Session ID and creation time
- Generation timestamp
- Analyzer ID/version
- App version/build identifier when available
- Capture-plan identifier/version

### Capture summary
- Registered frame count
- Unique planner cells covered / total cells
- Coverage percentage
- Duplicate planner-cell count
- Missing planner cells (yaw/pitch)
- Known image dimensions
- EXIF availability count
- Duplicate-content count (SHA-256 equality)

### Quality evidence
Every check includes stable check ID, category, outcome (PASS, WARN, FAIL, NOT_MEASURED), severity, explanation, measured evidence, limitations, and optional remediation.

Initial checks:
- planner_coverage
- planner_cell_duplicates
- image_content_duplicates
- image_dimensions_consistency
- exif_availability
- manifest_integrity
- image_file_integrity

### Readiness
- INSUFFICIENT: empty or below minimum planner coverage, or an integrity blocker.
- REVIEW: enough data to inspect, but warnings or incomplete coverage remain.
- READY: satisfies the explicitly listed planner and metadata gates with no current warnings.

READY means only "ready for the next pipeline stage under the checks listed here". It does not mean visible-surface completeness, calibrated geometry, or successful reconstruction.

### Provenance and limitations
- Planner yaw/pitch values are target coordinates, not calibrated camera extrinsics.
- Camera2 factory intrinsics, when present, remain raw sensor-coordinate metadata, not saved-JPEG/COLMAP calibration.
- EXIF presence is not proof of calibration.
- Blur, feature overlap, exposure suitability, occlusion, parallax and reconstruction success remain NOT_MEASURED until validated analyzers exist.

## 6. UX requirements

- Reachable from session summary and library session detail.
- Readiness and coverage visible without scrolling.
- Every warning explains its trigger and gives a next action or says why it is not actionable.
- Missing cells shown as a simple 12 × 3 grid/list.
- Evidence details expandable with measured values and limitations.
- Export uses Android sharing/storage conventions and reports the platform result.
- Works offline and on narrow screens.
- Status is never communicated by color alone.
- Do not use "reconstructable" as a synonym for READY.

## 7. Non-goals

No photogrammetry/mesh generation, intrinsic/extrinsic solving, feature matching, geometric overlap scoring, blur/exposure/occlusion/parallax analysis, cloud accounts, telemetry, remote AI, panorama stitching, tour editor/viewer, or certification/forensic chain-of-custody claims.

## 8. Privacy and security

- All analysis remains local; no network permission or remote endpoint.
- No GPS/location, account identifiers, phone numbers or personal names in Passport by default.
- Minimize device details; hardware model/API level optional, unique device IDs prohibited.
- Export only files registered to the session; validate safe relative paths.
- Recompute size and SHA-256 while exporting; fail closed on missing/changed images.
- Treat imported JSON as untrusted; validate schema, lengths, numeric ranges and paths.

## 9. Acceptance criteria

- AC-01 Empty session → INSUFFICIENT and 0% coverage.
- AC-02 Coverage uses 36 planner cells (12 azimuth × 3 pitch bands), not raw frame count.
- AC-03 Duplicate planner cells and duplicate image hashes are separate findings.
- AC-04 Mixed dimensions and missing EXIF are explicit warnings.
- AC-05 Passport includes generation time, session identity, analyzer version and manifest schema version.
- AC-06 Measured facts and unmeasured properties are distinct.
- AC-07 READY is impossible when an integrity blocker exists.
- AC-08 ZIP contains Passport JSON and text report with manifest and source images.
- AC-09 Export rejects traversal, unregistered, missing, changed or unsafe files.
- AC-10 Passport validation handles unknown additive fields safely.
- AC-11 Tests cover thresholds, wraparound, duplicates, empty sessions, metadata inconsistency and integrity failures.
- AC-12 CI tests, lint and debug APK build pass before claiming CI verification.
- AC-13 Physical-device verification is recorded separately from CI.

## 10. Release gates

A Documentation → Core contracts → Export integration → UI → CI/device verification. Do not call the feature complete until each gate has evidence.

## 11. Terminology

Capture Manifest = source-of-truth frame inventory. Capture Passport = portable interpretation/report. Quality Evidence = measured checks and limits. Readiness = policy for the next stage, not a reconstruction guarantee. Planner cell = one target yaw/pitch bucket.
