# User Guide — early build

Grant camera permission. Keep the object fixed and move around it. Follow the yaw/pitch target shown over the preview. Capture after the phone is stable and the object is sharply focused.

The first planner uses 36 angular targets. This is only a starting point; it does not prove all object surfaces are visible.

Images are written to app-private `files/captures`. They are not uploaded.

The Tour tab is a placeholder in this first implementation. Panorama import, 360 navigation, hotspots and tour packaging remain future work.

Known limitations: no blur/exposure/overlap scoring yet; yaw is relative to the device frame and needs object-centered calibration; no session manifest, gallery review, dataset export or reconstruction pipeline yet. No APK has been built or device-tested.
