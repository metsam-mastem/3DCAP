# User Guide — early build

Grant camera permission. Keep the object fixed and move around it. Follow the yaw/pitch target shown over the preview. Capture after the phone is stable and the object is sharply focused.

The initial planner uses 36 angular targets (12 azimuth sectors × 3 pitch bands). This is a coverage scaffold, not proof that every surface is visible or reconstructable.

Images and the session manifest are written to app-private `files/captures`; they are not uploaded. In Library, review sampled thumbnails and frame metadata. Delete removes a selected image after confirmation. **Retake same view** asks for confirmation, removes the previous image from the session, then prioritizes its original planner yaw/pitch target in Capture.

JSON and CSV exports contain metadata only. Dataset ZIP export contains `manifest.json`, `images/`, and `COLMAP_IMPORT.txt`. The guide includes example COLMAP CLI steps. The yaw/pitch values are capture-planner targets, not calibrated camera poses; the archive is not a precomputed COLMAP sparse model.

The Tour tab remains a placeholder. Panorama import/stitching, 360 navigation, hotspot editing and tour packaging are not implemented. No physical-device verification is claimed until tested on hardware.


## Calibration and dataset diagnostics — development status
EXIF values shown in the library are camera metadata only. They are not calibrated intrinsics or per-image camera poses. A separate calibration-profile contract is being added for measured fx/fy/cx/cy and lens distortion; the checkerboard/ChArUco calibration capture and solve workflow is not available yet.

The dataset-quality engine currently evaluates planner-angle cell coverage, duplicate cells, missing cells, EXIF availability and inconsistent image dimensions. Its readiness label is a capture-planning diagnostic, not proof that the object is fully covered or that COLMAP will reconstruct it. Blur, feature overlap, occlusion, parallax and reconstruction success are not yet measured by this report.
