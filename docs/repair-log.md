# Repair Log

## 2026-07-20 - Decoder safety and frame ownership

Status: Completed

Scope: `AnimatedImageLimits`, `AnimatedFrame`, `GifDecoder`, `ApngDecoder`, decoder tests.

Symptom: A resource pack could trigger large allocations before encoded-size validation; malformed PNG chunks could be indexed or allocated unsafely; transparent GIF delta frames erased previously composited pixels.

Root cause: Decoder limits only covered compressed input incompletely, parser envelopes were not strictly validated, and GIF transparency was treated as transparent-black output rather than a no-op on the composited canvas.

Decision: Use fixed non-configurable limits for encoded input, dimensions, frame count, and retained decoded pixels. Reject malformed data with controlled decode errors.

Repair: Added bounded input reads, checked allocation geometry, cumulative frame accounting, strict APNG chunk/CRC/sequence/frame validation, immutable frame ownership, GIF logical-screen background handling, and correct transparent-pixel composition.

Migration: Animated assets over 16 MiB, over 2,048 pixels on either side, over 256 frames, or over the decoded-pixel budget are now rejected and leave the static PNG fallback active.

Verification: `./gradlew.bat test` passed on 2026-07-20, covering bounded inputs, invalid dimensions, GIF transparent-frame composition, APNG CRC/geometry validation, frame ownership, and animation revision behavior.

Follow-up: Exercise an external corpus of GIF/APNG edge cases in the development client.

## 2026-07-20 - Filter-aware resource selection

Status: Completed

Scope: `AnimatedResourceResolver`, `AnimatedTextureReloadListener`, `AnimatedTextureRegistry`.

Symptom: Raw `ResourcePack.open` fallback could bypass resource filters and format precedence depended on registry insertion order.

Root cause: The reload listener reopened packs outside the effective `ResourceManager` view and registered GIF/APNG resources independently.

Decision: `ResourceManager` is the sole source of animation resources. The highest-priority visible source pack wins; APNG wins only when both formats are from that same priority layer.

Repair: Removed raw-pack access and standalone unpaired animation loading. Resolve one selected animation for each visible PNG fallback before decoding; retain one canonical registry entry per target and emit structured selection/fallback logs.

Migration: Unpaired animations are ignored. A corrupt winning asset no longer falls through to a lower-priority animation.

Verification: `./gradlew.bat test` passed on 2026-07-20 for registry-adjacent animation state and configuration parsing; resource-filter and layered-pack cases require `runClient`.

Follow-up: Verify resource filter, high-priority GIF, lower-priority APNG, and same-pack dual-format behavior in a live client.

## 2026-07-20 - Atlas-aware animated uploads

Status: Completed

Scope: `SpriteAtlasTextureMixin`, `AnimatedTexture`, `AnimatedTextureTickManager`, mixin configuration.

Symptom: Non-block/mob-effect/GUI atlas sprites were discarded, GUI animation stopped without a world, every tracked sprite reallocated/uploaded on every tick, and mip levels above zero retained static pixels.

Root cause: Tracking retained only `SpriteContents`, then searched a fixed atlas list. The Mixin and tick paths competed, and both updated only mip level zero.

Decision: Track each exact stitched sprite with its atlas and stitch mip level. Use direct revision-driven uploads as the sole animation path.

Repair: Capture `StitchResult.regions()` and `mipLevel()`, bind entries by atlas/sprite pair, support atlas-scoped GUI and mob-effect aliases, upload only on frame revision changes, refresh all mip levels without changing atlas sampler filtering, and remove the competing `SpriteContentsAnimationMixin` and `TextureManagerMixin` paths.

Migration: Atlas size override support was removed with its unused placeholder Mixin. Custom atlas prefix transformations beyond standard paths remain unsupported without parsing atlas JSON.

Verification: `./gradlew.bat test verifyExampleResourcePack` and `./gradlew.bat build` passed on 2026-07-20. GUI, non-block atlas, distance/mipmap, reload, and Sodium cases require `runClient`.

Follow-up: Run the client regression matrix listed in the README with generated and layered resource packs.

## 2026-07-20 - Configuration and example resource pack

Status: Completed

Scope: `AnimatedTexturesConfig`, `AnimatedTexturesModMenu`, `build.gradle`, `src/exampleResourcePack/pack.mcmeta`.

Symptom: `null` JSON configuration could produce later null dereferences; ModMenu exposed atlas-size/log/Mipmap controls with no implementation; the example output was not a complete resource pack and its APNG sequence numbers were invalid.

Root cause: Config deserialization was published without normalization, inactive settings remained public, and Gradle wrote mutable artifacts into the repository root.

Decision: Keep only scaling mode as a supported setting. Always refresh Mipmaps as a rendering invariant. Generate the example pack under `build/` and package a root-valid ZIP.

Repair: Normalize null/malformed configuration to defaults, use a draft-only ModMenu editor, remove inactive settings and source files, add `pack.mcmeta`, correct APNG sequence numbering, add ZIP packaging, and add example-pack verification to `check`.

Migration: Legacy `enableMipmaps`, `atlasSize`, and `logLevel` JSON keys are ignored and disappear on the next save. The example pack moved from `example_pack/` to `build/generated/example-pack/` and `build/libs/`.

Verification: `./gradlew.bat test verifyExampleResourcePack` and `./gradlew.bat build` passed on 2026-07-20.

Follow-up: Confirm the generated ZIP installs as a Minecraft 1.21 resource pack.

## 2026-07-21 - Playback, reload transactions, and release validation

Status: Completed

Scope: finite GIF/APNG playback metadata, reload-wide budgets, immutable registry snapshots, reload-attempt ownership, atlas generation publication, upload retry backoff, timing overflow handling, scaling correctness, and regression tests.

Symptom: Finite animations looped forever; lag and clock changes could drift playback; failed or overlapping resource reloads could mix stale atlas data with a newer registry; repeated upload failures could retry and warn every tick; per-file limits did not cap retained data across the whole reload.

Root cause: Decoder APIs discarded playback metadata, animation timing used a lossy wall-clock anchor, registry and atlas bindings were published through separate mutable authorities, atlas capture was not owned by the overall `ResourceReload`, and retry scheduling was revision-scoped rather than binding-scoped.

Repair: Added metadata-preserving decoded animations, bounded finite playback, alpha-correct pixel-center scaling, strict palette/transparency checks, staging builders and reload-wide budgets, executor-scoped reload attempts committed only after overall reload success, one immutable active generation for registry and bindings, and binding-level exponential upload retry with bounded diagnostics.

Verification: `gradlew.bat test`, `verifyExampleResourcePack`, `check`, and `build` passed on 2026-07-21. The unit suite covers decoder reuse and limits, finite/infinite playback, elapsed overflow, registry snapshots, budget accounting, reload ownership, and upload retry deadlines. Sodium compatibility on Minecraft 1.21 was confirmed by the project owner. Iris validation remains pending.

Migration: The runtime registry is now read-only; direct mutation/publication helpers were removed. GIFs without a recognized loop extension play once, while NETSCAPE/ANIMEXTS and APNG play counts are honored. Generated examples and release artifacts use version 1.2.0.
