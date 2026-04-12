# Remaining TODO Items
Generated: 2026-04-12

## No TODOs or FIXMEs in codebase
The source code contains no TODO or FIXME comments.

## Known Limitations / Recommended Follow-ups

### Release build minification (low urgency)
- `app/build.gradle.kts` has `isMinifyEnabled = false` for the release build type.
- Before publishing to the Play Store, consider enabling R8 minification:
  ```
  isMinifyEnabled = true
  isShrinkResources = true
  ```
  and validating that Hilt, Room, and Retrofit survive ProGuard rules.

### Export/Import schema versioning (low urgency)
- The JSON export format has no version field.
- If fields are added or renamed in the future, old export files may fail silently.
- Consider adding a `schemaVersion: Int` field to `ExportData`.
