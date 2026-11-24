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

Requires JDK 17, Android SDK 36, Android Gradle Plugin 8.9.1 and Gradle 8.11.1. The repository currently uses the installed Gradle command in CI (no Gradle wrapper is checked in).

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

## Windows COLMAP runner

The exported dataset can be reconstructed on a Windows PC with a compatible COLMAP installation:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\windows\Invoke-3DCapColmap.ps1 `
  -ZipPath "D:\capture\3dcap-dataset.zip" `
  -WorkspacePath "D:\3dcap-work\session-001" `
  -ColmapPath "C:\COLMAP\COLMAP.bat" `
  -Profile DRAFT
```

The runner validates each source image against the manifest's size and SHA-256 before invoking COLMAP. It produces a sparse model and dense `fused.ply` when the dataset and hardware permit. Use a fresh, empty workspace. This script is not yet Windows-tested.

## Camera metadata boundary

The app records EXIF facts and, when exposed by the selected camera, optional Camera2 factory sensor calibration. Camera2 factory intrinsics remain in the Android sensor pre-correction active-array coordinate system; they are not transformed into saved-JPEG or COLMAP calibration parameters. The Camera2 bridge currently uses CameraX 1.6.0; its Android build requirements are SDK 36, AGP 8.9.1 and Gradle 8.11.1.


## Capture Passport
The next feature slice adds a portable, versioned quality-evidence report to each capture dataset. Read the [product specification](docs/CAPTURE_PASSPORT_SPEC.md), [quality evidence model](docs/QUALITY_EVIDENCE_MODEL.md), [JSON/ZIP data contract](docs/CAPTURE_PASSPORT_DATA_CONTRACT.md), and [implementation/verification plan](docs/CAPTURE_PASSPORT_IMPLEMENTATION_PLAN.md). Documentation is committed; implementation and verification remain separate gates.
