# Quality Evidence Model

**Version:** 1.0.0  
**Analyzer:** 3dcap-capture-quality  
**Planner:** 12 azimuth sectors × 3 pitch bands = 36 cells

## 1. Principles

1. Every status is backed by a reproducible measurement.
2. Missing measurements are NOT_MEASURED, never PASS.
3. Warnings explain trigger and remediation.
4. Readiness is a policy over explicit checks, not an opaque score.
5. Do not combine unrelated signals into a misleading quality percentage.
6. Keep planner coverage, integrity, metadata and geometric reconstructability separate.

## 2. Check contract

| Field | Meaning |
|---|---|
| check_id | Stable machine identifier |
| category | coverage, integrity, metadata, image_quality, geometry, reconstruction |
| outcome | PASS, WARN, FAIL, NOT_MEASURED |
| severity | INFO, WARNING, BLOCKER |
| summary | User-facing explanation |
| evidence | Typed measured values |
| limitations | What the check cannot establish |
| remediation | Optional next action |
| analyzer_version | Logic version |

## 3. MVP checks

### Q-001 planner coverage
Normalize yaw to [0,360), snap to 30-degree sectors, assign nearest pitch band (-25, +15, +55). Report covered/total cells, percentage and missing cells. PASS at >=80%; WARN at 35–79%; FAIL below 35% or no frames. This does not prove physical surface visibility.

### Q-002 duplicate planner cells
Report repeated cells and repeated frame count. PASS at zero; WARN otherwise. Two frames in one cell may still show different useful views; this is only planner repetition.

### Q-003 duplicate image content
Compare recorded SHA-256 values and verify bytes during export. PASS when no duplicate digest; WARN otherwise. Different encodings of the same visual content are not detected.

### Q-004 image dimensions consistency
Use known EXIF width/height pairs. PASS when known dimensions agree; WARN when multiple pairs exist; NOT_MEASURED when no usable dimensions exist. EXIF dimensions may not equal post-rotation/crop pixel dimensions.

### Q-005 EXIF availability
Report frames with and without readable camera metadata. PASS when all have it; WARN when some are missing; NOT_MEASURED for empty sessions. Metadata presence is not calibration validation.

### Q-006 manifest integrity
Validate schema, unique sequences/names, timestamps, paths, dimensions and hash syntax. PASS on successful contract validation; FAIL on contract violation. Syntax validity does not prove image bytes match.

### Q-007 image file integrity
During export, check registered file existence, safe path, byte count and SHA-256. PASS only if every image matches; FAIL on missing, unsafe, changed or unreadable file. Hash equality proves byte identity relative to manifest, not authenticity of capture event.

## 4. Explicitly deferred checks

These MUST be NOT_MEASURED (or shown in a clear unmeasured section), never PASS:
- motion blur / focus quality
- exposure clipping / dynamic range
- feature overlap
- texture richness / low-texture risk
- occlusion / unseen surface detection
- baseline / parallax sufficiency
- intrinsic calibration accuracy
- per-image pose accuracy
- COLMAP sparse/dense reconstruction success
- mesh completeness, scale accuracy and texture quality

## 5. Readiness policy

This is not a weighted average.

- INSUFFICIENT: no frames, coverage <35%, or any integrity blocker.
- REVIEW: coverage >=35% and <80%, any warning, or required measurement unavailable.
- READY: coverage >=80%, no warnings, manifest integrity PASS, image file integrity PASS, and all MVP-required metadata consistency checks PASS.

Future profiles may include QUICK_PREVIEW, OBJECT_PHOTOGRAMMETRY and ARCHIVE_GRADE. MVP uses one transparent policy. Do not market this as reconstruction probability.

## 6. Existing implementation mapping

The current DatasetQualityAnalyzer computes planner coverage, repeated cells, repeated SHA-256 values, missing EXIF and mixed known dimensions. It currently returns aggregate warnings and readiness. Passport work preserves these measured semantics while upgrading output to typed checks and explicit evidence.

File existence and hash verification currently occur during ZIP export. Passport must surface integrity only after bytes have actually been checked; before that, report NOT_MEASURED.

## 7. Determinism

Core analysis is pure and deterministic for identical inputs. Inject generation time through a clock/caller. Sort findings by stable check ID and frames by sequence. Machine fields use stable enums. Percentages retain existing integer rounding unless a schema change explicitly revises it.
