# Union Manager

The Android management application for JLUCraft identity, membership, organization workflows, activities, and host administration.

## Setup and structure

`app/` contains Kotlin and Jetpack Compose code; `native/` contains the Rust JNI layer. Keep sibling `union-core` and `minecraftd` checkouts available. Use the existing Android SDK and repository build directories. Device execution requires a selected physical device.

## Validation

Select one validation channel for a batch and reuse successful results for unchanged inputs. Standard commands:

```sh
./scripts/build-native.sh
./scripts/test-android.sh build
```

Read [AGENTS.md](AGENTS.md) before making changes.
