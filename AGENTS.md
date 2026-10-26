# AGENTS.md — union-manager

Follow the workspace rules in `../AGENTS.md`. Read [README.md](README.md) for project scope, setup, and standard validation commands.

## Documentation ownership

Keep project documentation in the root `AGENTS.md` and `README.md` only. Product content and required license files retain their existing locations. Workspace progress and evidence belong in `../TODO.md`; unresolved issues belong in `../PROBLEMS.md`. These workspace records are machine-local and are not published.

## Development boundaries

`app/` contains Kotlin and Jetpack Compose code; `native/` contains the Rust JNI layer. Keep sibling `union-core` and `minecraftd` checkouts available. Use the existing Android SDK and repository build directories. Device execution requires an explicitly selected physical device.

The founder roster (`创世名单`) is server-source data: the pinned genesis is defined outside the app and is immutable once published. Do not add UI that authors or rewrites it — the old genesis builder and the `replace_chairs` action were removed for this reason. Founder changes go through multi-signature succession (`propose/approve/execute/cancel_succession`) or device transfer (`bind_device`/`revoke_device`), which are offered by the identity action form.

Change only the affected subsystem. Use existing dependencies and build directories. Finish each batch before validation; select one validation channel and reuse successful evidence for unchanged inputs. Report skipped checks and device execution limits explicitly.
