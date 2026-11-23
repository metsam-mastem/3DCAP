# Research Register

No third-party source code was copied into this repository. These projects informed architecture and feature selection.

| Source | Useful contribution | License / note |
|---|---|---|
| https://github.com/nsicart/MosaicEarth-Capture-Android | Live overlap guidance, local photogrammetry, COLMAP export, offline viewers | README states AGPL-3.0-or-later; review before reuse |
| https://github.com/damzenquis/Strata-3D | CameraX smart shutter, coverage dial, on-device MVE/OpenMVS, format support and tests | README states AGPL-3.0-or-later; review before reuse |
| https://github.com/OpenScan-org | Affordable object scanning ecosystem and hardware | Check each sub-repository license |
| https://github.com/colmap/colmap | Structure-from-Motion / Multi-View Stereo and dataset exchange | Verify notices before bundling |
| https://github.com/nerfstudio-project/nerfstudio | NeRF / Gaussian Splat workflows and camera-pose processing | Apache-2.0; high compute demand |
| https://github.com/DavidMajzik/statictour | Mobile-first 360 tour editor, room links, offline static export | MIT; strong candidate for a separately vendored web tour module |
| https://github.com/illerin/PanoPath | Panorama editor, scene graph, floor-plan pins and self-hosted offline export | License must be confirmed before reuse |
| https://github.com/RohaCode/Orbix | 26-position guided 360 capture, local stitch and sensor viewer concept | Public repository is a landing page; README says app source is private |
| https://github.com/freeKC/Immuch360 | Cross-platform 360 / VR180 / panorama / Gaussian Splat viewer | AGPL-3.0; evaluate as a separate viewer dependency |
| https://www.hackster.io/Nazrim/open3dscanner-e4321a | Smartphone + ESP32 + stepper turntable + controlled lighting; automated multi-view acquisition | CC BY-NC-SA 4.0; design inspiration only, not suitable for unrestricted commercial reuse |
| https://www.hackster.io/news/a-diy-3d-scanner-for-your-android-phone-27f8c9d8b05c | Low-cost Android + Arduino rotating turntable concept | Article references a DIY build; verify original CAD/firmware license before reuse |
| https://developer.android.com/media/camera/camerax | Android camera lifecycle, preview, capture and vendor extensions | AndroidX; feature support varies |
| https://developers.google.com/ar/develop/java/depth/overview | Optional depth and pose signal | Optional dependency; not baseline |

## Conclusion
Do not block capture on ARCore, LiDAR, cloud processing or remote AI. Start with CameraX + IMU + deterministic coverage; add local image-quality analysis and reconstruction feedback behind capability adapters.
