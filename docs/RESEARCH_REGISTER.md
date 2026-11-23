# Research Register

No third-party source code was copied into this repository. These projects informed architecture and feature selection.

| Source | Useful contribution | License / note |
|---|---|---|
| https://github.com/nsicart/MosaicEarth-Capture-Android | Live overlap guidance, local photogrammetry, COLMAP export, offline viewers | README states AGPL-3.0-or-later; review before reuse |
| https://github.com/damzenquis/Strata-3D | CameraX smart shutter, coverage dial, on-device MVE/OpenMVS, format support and tests | README states AGPL-3.0-or-later; review before reuse |
| https://github.com/OpenScan-org | Affordable object scanning ecosystem and hardware | Check each sub-repository license |
| https://github.com/colmap/colmap | Structure-from-Motion / Multi-View Stereo and dataset exchange | Verify notices before bundling |
| https://github.com/nerfstudio-project/nerfstudio | NeRF / Gaussian Splat workflows and camera-pose processing | Apache-2.0; high compute demand |
| https://developer.android.com/media/camera/camerax | Android camera lifecycle, preview, capture and vendor extensions | AndroidX; feature support varies |
| https://developers.google.com/ar/develop/java/depth/overview | Optional depth and pose signal | Optional dependency; not baseline |

## Conclusion
Do not block capture on ARCore, LiDAR, cloud processing or remote AI. Start with CameraX + IMU + deterministic coverage; add local image-quality analysis and reconstruction feedback behind capability adapters.
