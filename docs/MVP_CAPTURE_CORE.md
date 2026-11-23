# 3DCAP Capture Core MVP 0.1

This branch adds a platform-neutral Kotlin capture core that can be called by the planned Android/CameraX application. It does not add camera UI, sensor integration, reconstruction, or panorama rendering.

## Implemented in this slice

- Deterministic Fibonacci-sphere candidate generation.
- Maximin next-shot selection: recommend the candidate with the greatest minimum angular distance from already captured orientations.
- Coverage estimate over the fixed candidate set. This is a planning heuristic, not a reconstruction-quality guarantee.
- Strict capture-session and shot contract.
- JSON manifest encode/decode with schema version 1.0.
- Safe relative image paths; absolute paths and parent-directory traversal are rejected.
- Streaming SHA-256 image intake for JPEG, PNG, and WebP.
- File-size limit, symbolic-link rejection, file-change check, and extension/signature agreement.
- Unit tests for deterministic planning, coverage growth, manifest round-trip, traversal rejection, image hashing, and signature mismatch.

## VINT relationship

This is an independent Kotlin implementation of the media-intake and provenance boundaries needed by 3DCAP. It does not copy VINT source files. VINT remains a separate Python media-analysis project; the future integration boundary should be a versioned JSON manifest or an explicit export/import adapter, not a Python runtime embedded in Android.

## Deliberate non-goals

- No claim of 3D reconstruction.
- No COLMAP, OpenMVS, Nerfstudio, or Gaussian Splatting integration.
- No cloud upload or network requirement.
- No direct CameraX/IMU dependency in this pure core.
- No destructive media operations.

## Verification

Requires JDK 17 and Gradle. Run `gradle test` from the repository root. Source and tests are added in this branch; execution remains pending CI. Do not mark tests PASS until a GitHub Actions run reports success.
