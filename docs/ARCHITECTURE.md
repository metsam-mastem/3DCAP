# 3DCAP Architecture — 0.1.0

## Product boundary
Android-first, offline-first guided multi-view capture, reconstruction dataset preparation and interactive virtual tours.

## Runtime layers
1. Device adapter: CameraX, rotation-vector sensor, optional Camera2 interop.
2. Capture intelligence: deterministic yaw/pitch coverage; next-view target; future blur, exposure, overlap and parallax scoring.
3. Session store: app-private images, versioned JSON manifest, frame orientation and sequence metadata, streaming SHA-256, atomic manifest replacement and backup recovery.
4. Reconstruction adapters: COLMAP export first; optional MVE/OpenMVS and Gaussian Splatting subject to ABI, RAM, GPU and thermal capability.
5. Tour runtime: equirectangular panoramas, scene graph, hotspots, offline navigation and export.

## Capture planner
Default plan: 12 azimuth sectors at 30° and 3 pitch bands (-25°, +15°, +55°) = 36 targets. This is a coverage scaffold, not a guarantee that every surface is visible or reconstructable. Geometry-aware planning must later account for occlusion, texture, calibration, focal length, baseline, blur and reconstruction feedback.

## Offline and privacy
No INTERNET permission in the initial manifest. Images stay in app-private storage. No account, telemetry, remote inference or cloud processing.

## Device capability strategy
Baseline CameraX + rotation vector; optional Camera2 manual controls and vendor extensions only when supported. Optional ARCore depth/pose behind capability detection; never a hard dependency. iPhone computational photography features cannot be assumed available on Android.

## Tour data model
Scene = panorama + title + metadata. Hotspot = normalized spherical coordinate + target scene/content card. Route = ordered scene IDs. Tour manifest = versioned JSON and local asset references.

## Implemented in the current development branch
The local library lists sampled thumbnails and frame metadata, supports confirmed deletion and confirmed same-view retake, shares JSON/CSV metadata, and exports a ZIP containing `manifest.json`, `images/`, and `COLMAP_IMPORT.txt`. Each image is checked against its recorded size and SHA-256 before packaging. The retake action removes the old frame and prioritizes its original planner yaw/pitch target; these are planner coordinates, not camera extrinsics.

## Deferred / not claimed
COLMAP reconstruction, calibrated camera intrinsics/extrinsics, panorama stitching/viewer, hotspot editing, and physical-device verification remain incomplete. The ZIP is a source-image input package with usage guidance, not a COLMAP sparse model. No `cameras.txt`, `images.txt`, or `points3D.txt` is fabricated. CI and physical-device verification are separate gates.


## Calibration profile contract (implemented; calibration solver deferred)
Per-camera calibration is stored as a separate typed profile, not inferred from EXIF. The profile records image dimensions, camera model, fx/fy/cx/cy, distortion coefficients, calibration pattern, sample-view count, reprojection error and timestamp. The contract currently accepts at least 8 calibration views and reprojection error <= 2.0 px. These are initial acceptance limits, not a claim that any profile has been measured or validated on a device. Checkerboard/ChArUco detection, intrinsic solving, profile persistence and UI are not implemented yet.

## Dataset quality engine (implemented as a pure analysis contract)
The first deterministic report evaluates the existing 36 planner cells, repeated cells, missing cells, EXIF availability and mixed image dimensions. Readiness is INSUFFICIENT below 35% coverage, REVIEW below 80% or when warnings exist, and READY only at >=80% with no warnings. This report does not measure feature overlap, surface visibility, blur, exposure, parallax or reconstructability; those require separate image/geometry signals and reconstruction feedback.


## Calibration acquisition quality gate
A pure Kotlin sample-set analyzer now rejects sets with fewer than 8 sharp samples, fewer than 8 distinct 45-degree yaw bins, fewer than 3 distinct tilt bands, invalid orientation/dimensions, or mixed image dimensions. This is a capture-set diversity gate only; it does not detect a checkerboard/ChArUco pattern or solve camera intrinsics.

## Desktop reconstruction runner
`tools/windows/Invoke-3DCapColmap.ps1` validates ZIP paths, manifest schema, every image's byte size and SHA-256, then runs COLMAP feature extraction, exhaustive matching, sparse mapping, undistortion, PatchMatch stereo and fusion. It writes per-stage logs and a JSON summary. It requires a fresh empty workspace and a compatible COLMAP Windows executable. No Windows execution has been performed in this environment.


## Optional Camera2 factory sensor calibration
The Android capture adapter now reads Camera2 factory intrinsics when the selected CameraX camera exposes them: fx/fy/cx/cy/skew, camera ID, pre-correction and active sensor-array rectangles, sensor orientation, and the optional five-coefficient Brown-Conrady distortion model (API 28+). These values are preserved as raw sensor-coordinate metadata in the versioned session manifest and CSV. Absence is normal and does not block capture.

**Coordinate boundary:** Android defines factory intrinsics in the sensor pre-correction active-array coordinate system. The adapter does not transform them for CameraX JPEG crop, scale, rotation, or distortion correction. They are not calibration values for the saved JPEG and must not be passed to COLMAP as image intrinsics until that mapping is measured and validated. The calibration profile contract remains separate from factory metadata.

CameraX dependencies are aligned at 1.6.2 to expose the supported Camera2 interop API. This is a library upgrade, not a claim of device-level validation.
