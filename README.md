# 3DCAP — Offline Intelligent 3D Capture Studio

3DCAP is an Android-first, offline-first capture workspace for guided multi-view object capture, quality-aware shot planning, portable reconstruction datasets, and interactive virtual tours.

## Project status

This repository was empty at initialization. The first implementation milestone is a native Android capture MVP with:
- CameraX preview and still capture
- IMU-assisted orientation / stability feedback
- deterministic spherical coverage planner and next-shot guidance
- local session storage and exportable capture manifest
- offline 360° panorama / tour viewer foundation

3D reconstruction (COLMAP / MVE / OpenMVS / Gaussian Splatting) is an adapter-driven pipeline and is not claimed as implemented until its native toolchain is integrated and verified.

## Architecture

- `app/`: Android application (Kotlin, Jetpack Compose, CameraX)
- `core/capture/`: pure Kotlin capture planning and quality logic
- `docs/`: requirements, architecture, user guide, research and verification
- `tools/`: reproducible local build and validation helpers

## Build

Requires JDK 17, Android SDK 35, and Gradle 8.9. The repository currently uses the installed Gradle command in CI (no Gradle wrapper is checked in).

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

## Privacy

Capture and project data are stored locally. Network access is not required by the capture workflow. Any future cloud connector must be opt-in and isolated behind an adapter.

## Research references

- [CameraX](https://developer.android.com/media/camera/camerax)
- [ARCore Depth](https://developers.google.com/ar/develop/java/depth/overview)
- [COLMAP](https://github.com/colmap/colmap)
- [OpenScan](https://github.com/OpenScan-org)
- [MosaicEarth Capture for Android](https://github.com/nsicart/MosaicEarth-Capture-Android)
- [Strata:3D](https://github.com/damzenquis/Strata-3D)
- [Nerfstudio](https://github.com/nerfstudio-project/nerfstudio)

## License

Project licensing and third-party notices are being established before integrating external source code. Research references are not copied code.
