# Illager Armada 1.0.1.1 — Stable Release Checklist

## Source and build
- [x] No changes to 1.0.1 Java code, Mixins, cargo manifests or default settings
- [x] 1.0.1.1a passed Java 17 / Gradle 8.9 CI and source verification
- [x] Promote Gradle/Fabric metadata to version 1.0.1.1
- [x] Prepare final CurseForge description and changelog
- [x] Remove the obsolete 1.0.1 auto-publication workflow
- [ ] Final stable JAR CI build and metadata verification
- [ ] Compare final classes and resources with 1.0.1 release
- [ ] Verify public source snapshot and independent CI build

## Publication
- [ ] Confirm source is public at https://github.com/10bibbys-cmyk/Illager-Armada-Source
- [ ] Update CurseForge description and Source Code field
- [ ] Upload release JAR for Fabric 1.20.1 as Release
- [ ] Verify approved CurseForge file is live

The previous 1.0.1 gunnery fix was tested in-game. The 1.0.1.1 maintenance alpha was CI-tested; there is no additional assertion here that the alpha received a separate in-game regression test.
