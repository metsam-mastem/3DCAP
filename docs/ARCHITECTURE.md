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
