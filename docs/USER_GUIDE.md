# User Guide — early build

Grant camera permission. Keep the object fixed and move around it. Follow the yaw/pitch target shown over the preview. Capture after the phone is stable and the object is sharply focused.

The initial planner uses 36 angular targets (12 azimuth sectors × 3 pitch bands). This is a coverage scaffold, not proof that every surface is visible or reconstructable.

Images and the session manifest are written to app-private `files/captures`; they are not uploaded. In Library, review sampled thumbnails and frame metadata. Delete removes a selected image after confirmation. **Retake same view** asks for confirmation, removes the previous image from the session, then prioritizes its original planner yaw/pitch target in Capture.

JSON and CSV exports contain metadata only. Dataset ZIP export contains `manifest.json`, `images/`, and `COLMAP_IMPORT.txt`. The guide includes example COLMAP CLI steps. The yaw/pitch values are capture-planner targets, not calibrated camera poses; the archive is not a precomputed COLMAP sparse model.

The Tour tab remains a placeholder. Panorama import/stitching, 360 navigation, hotspot editing and tour packaging are not implemented. No physical-device verification is claimed until tested on hardware.


## Calibration and dataset diagnostics — development status
EXIF values shown in the library are camera metadata only. They are not calibrated intrinsics or per-image camera poses. A separate calibration-profile contract is being added for measured fx/fy/cx/cy and lens distortion; the checkerboard/ChArUco calibration capture and solve workflow is not available yet.

The dataset-quality engine currently evaluates planner-angle cell coverage, duplicate cells, missing cells, duplicate image content by SHA-256, EXIF availability and inconsistent image dimensions. It flags identical image bytes even when they were recorded against different planner angles. Its readiness label is a capture-planning diagnostic, not proof that the object is fully covered or that COLMAP will reconstruct it. Blur, feature overlap, occlusion, parallax and reconstruction success are not yet measured by this report.


## Windows reconstruction workflow
After exporting the Dataset ZIP, install a compatible Windows COLMAP build. Open PowerShell and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\windows\Invoke-3DCapColmap.ps1 `
  -ZipPath "D:\capture\3dcap-dataset.zip" `
  -WorkspacePath "D:\3dcap-work\session-001" `
  -ColmapPath "C:\COLMAP\COLMAP.bat" `
  -Profile DRAFT
```

Use a new empty workspace folder for every run. The script validates the archive and frame hashes before starting reconstruction. It stops on a failed stage, records logs under `logs/`, and writes `run-summary.json`. DRAFT uses a 1200 px maximum undistorted image size; BALANCED uses 2000 px; HIGH uses 3200 px. Dense reconstruction can require substantial RAM/GPU resources. The script is committed but has not been executed on Windows yet.


## Camera2 factory calibration metadata
When the selected camera provides factory calibration, 3DCAP saves it with each captured frame: sensor intrinsics, lens distortion coefficients (where supported), sensor active-array rectangles, camera ID and sensor orientation. Some camera devices do not provide all of these fields; the absence of the data does not block capture.

These values refer to the Android sensor's pre-correction coordinate system. They are shown as raw factory metadata only. 3DCAP does not yet map them to the saved JPEG's crop, scale, rotation and distortion pipeline. Do not treat them as the saved image's calibrated intrinsics or COLMAP camera parameters.


## Capture Passport (implemented in the development branch; verification pending)
The Library shows a Passport summary and an expandable human-readable report. The report summarizes planner coverage, missing targets, repeated planner cells, duplicate image content, EXIF availability and known dimension consistency. A JSON preview can be shared from the Passport detail. Dataset ZIP export includes passport.json and passport-report.txt after image byte-size and SHA-256 verification. Before export, image-byte integrity is explicitly NOT_MEASURED. The Passport does not claim that a dataset is reconstructable: feature overlap, blur, occlusion, parallax, calibration accuracy and reconstruction success are not measured by the MVP. CI and physical-device verification remain pending. See the Capture Passport documents in docs/.
