# Virtual Tour Data Contract v1

A tour is a local graph of panorama scenes. All asset references are relative paths inside the exported tour package.

- Scene: unique ID, title, equirectangular panorama path, zero or more hotspots.
- Hotspot: unique scene-local ID, yaw in [-180, 180], pitch in [-90, 90], accessible label, optional destination scene, optional content.
- Route: ordered scene IDs. Every reference must resolve.
- Manifest: schemaVersion=1, title, scenes, routes.

Example package layout:

```
tour-name/
  tour.json
  panoramas/
    entrance.jpg
    gallery.jpg
  thumbnails/
    entrance.jpg
    gallery.jpg
```

The initial Kotlin model validates references and spherical coordinate bounds. JSON serialization, panorama rendering, editing and export are not yet implemented.
