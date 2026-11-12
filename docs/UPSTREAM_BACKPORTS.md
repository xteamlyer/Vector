# Upstream backport attribution

Vector-SR is a fork of [JingMatrix/Vector](https://github.com/JingMatrix/Vector). This file records selective upstream backports adapted onto Vector-SR while preserving the legacy View-based Manager and the SR soft-restart/reinjection architecture.

## Attribution policy

For future selective backports:

- Direct cherry-picks should retain Git's `-x` provenance whenever possible.
- Manual or conflict-resolved ports must include the original upstream repository and commit SHA in the commit message.
- When a change is only partially adopted, the commit message and this table must state which semantics were ported and which were intentionally excluded.
- Vector-SR-specific adaptations must not be presented as verbatim upstream changes.
- The Compose/new Manager and the `org.matrix.vector.ipc` Manager/Binder namespace remain outside this backport series unless explicitly adopted later.

Recommended commit trailers for adapted changes:

```text
Upstream-Repository: JingMatrix/Vector
Upstream-Commit: <full SHA>
Upstream-URL: https://github.com/JingMatrix/Vector/commit/<full SHA>
Adaptation: <why a direct cherry-pick was not used>
```

## Current backport map

### 2026-11-06 update

- `569048119f0c1071b3d5069b645cfd4526b7ee8e`: retained the daemon lock and channel through a VM shutdown hook, preserving SR private-file preparation and the separate reinjection lease.
- `420942085be83b7b78034eed48b066bd01eab2a7`: cherry-picked the four legacy resource hook fixes.
- `eaffc816ee78bb4acd45fc42ed1e07a892e8dc46`: adapted late-load service detection, SELinux lookup, daemon bootstrap, and the cold-service handoff to SR's existing AIDL, owner and lease flow. The explicit late-load option and SR restart retries remain supported. Upstream's service unregistration and Binder protocol replacement are outside this backport.
- `93d6647333fb827aa555b5e781e1e626d5320860`: cherry-picked eviction of the parasitic Manager host's pre-cached LoadedApk.
- `7b4688fd5452b4c5389a2895877718689f31f3c0`: ported the two pre-R Manager launch fallbacks. SR already registers the injection proxy on those versions.

### 2026-10-24 update

- `82112f99ae5b9aa66940e35bfc27c813a6673bc7`: adapted resource-cache isolation from tmpfs to a freshly recreated private bind source. Vector-SR additionally stops startup if cleanup, directory creation or mounting fails. The existing private mount namespace and SR recovery remain unchanged.
- `ddeed8ca1ffedfaba7f4341b911089906862d5c4`: completed the LSPlant portion by advancing `49d2e564` to `7a768e31ffb2852797631b5140b54f9702a78743`, including its dex_builder pin. This brings native-hook signature, Android 17 JNI-ID, CloseGuard and Knox compatibility fixes. The trampoline workaround was already present under the old commit hash. libxposed mirror/docs and LSPlt updates were already ported; unrelated dependency and toolchain upgrades remain excluded.

| Upstream commit | Vector-SR status | Notes |
| --- | --- | --- |
| `077131dba53b2be6d4962c3527fcf4f57ebe1241` | Ported | Android 15/16 SystemUI notification-flag workaround. |
| `28daecfa3b30ea21e1a7e33feae6545552acaf04` | Behavior ported | Daemon reads verbose-log preference before dropping the main-thread euid. Upstream log-only cleanup is intentionally not required. |
| `73571c7f8243295ddd2d5de62cffb4f6cbf5c3dc` | Ported | Android 17 `IServiceConnection.connected` four-argument overload and `IBinderSession` stub. |
| `fbcff2f5087209e5667c40f45f2a8ac7a7a6f2b6` | Ported/adapted | Legacy unsafe-hook refusal was blob-compatible; modern refusal was adapted around SR's hook registry. |
| `92f3e60ea9504c9064cc3bca287456d196a5937c` | Behavior ported | JNI pending-exception cleanup, Zygisk Java-entry result handling, and dex2oat `GetStringUTFChars` failure handling are present. |
| `73eba3ac0b6897144be8487e2a86c71449558949` | Runtime + adapted guard ported | `XResources` lambda/compute removal is present. Vector-SR also wires a zero-new-dependency source-level `checkXResourcesIsolationRelease` guard into `preReleaseBuild`; upstream's stronger post-R8 dexlib2 verifier is intentionally not imported. New-Manager logging/archive changes are excluded. |
| `44552398db793a6d02b33acbc66978966950ffef` | Ported | Android 17 static-final reflective write support is integrated into the existing native HookBridge. |
| `94a76d4acafc8f00ebdd4a765336e049c411b1e0` | Ported | Static-final field writes now resolve the real `ArtField` when ART uses index-based JNI IDs in debuggable processes, avoiding small-index dereferences while retaining the pointer-ID fast path. |
| `c4a701aadbf9b4c7a7a65046fe4b9be322a909da` | Ported/adapted | Android 17 canary tagged direct `jfieldID` values are treated as untrusted candidates. Vector-SR validates mapping and access flags before dereference, tries the tag-cleared pointer, and retains the existing index-ID `mirror::Field` fallback. |
| `5ff67a87cb70414d8c52b59f97f56a9de257ee49` | Ported/adapted | `system_server` native-library staging is isolated in `NativeLibraryStager` so it does not own or alter the SR reinjection lease lifecycle. |
| `04093fdbf4cff2426cd1b320d5e568282d0e2a77` | Selectively ported | API101 hook-class-initializer, invoker/chain, exception-mode, preferences, late-system_server, package semantics and scope correctness are selectively adapted without the new Manager IPC model. |
| `abae837a50d3a50577e4a458e2bbf57e64312af3` | API102 behavior ported/adapted | API102 hook/generation and hot-reload behavior is implemented within Vector-SR's existing IPC architecture: atomic callback replacement and invocation snapshots; successor-before-retirement; frozen retired generations; per-entry detach; API102 legacy-API isolation; multi-user ownership; asynchronous request/result delivery; structured outcomes; framework-owned 30-second timeout; `autoHotReload=true`; cached-process thaw/refreeze; `system_server` targets; and mixed Java/native modules using the API-defined process-lifetime native semantics. |
| `97126d865f7ce489006f121e15e51b50c024b4c7` | Ported/adapted | Invoker behavior is aligned with the libxposed interface: reflection-compatible argument validation and widening, virtual dispatch, access-check bypass, `Invoker.Type` handling, exception boundaries and null-vararg entry points. The API102 legacy guard is narrowed to the actual legacy package, resource XML rewriting/layout handling is corrected, and LSPlant is advanced to the upstream conformance baseline. `XposedBridge` is adapted to the retained Vector-SR utility/IPC architecture rather than copied wholesale. |
| `ebb438b948b327b5d0c1bb5495c56c879948990d` | Daemon semantics selectively ported | Empty `staticScope` declarations are ignored as packaging errors; scope requests retain timeout/first-answer semantics; full uninstall resets `Never ask again`; activation clears only the stale `not activated yet` notice. Manager-side Compose scope-editor changes are excluded. |
| `4fcea0e528e737efb5fdadf02ee7fd47d55d527b` | Ported/adapted | Scope requests are batched to one prompt/result with `system` handling, durable approval, first-answer-wins and bounded pending requests while retaining the old receiver/IPC namespace. |
| `f2ef0b20b39ed869fb54be2cb1359a96042dcb40` | Daemon behavior selectively ported | Module-update notifications posted in user 0 prefer a user-0 Manager destination when the module is installed there; unrelated Compose scope UI/navigation changes are excluded. |
| `e8bec6bd714d0f277c8134096501ce36902b32fd` | Ported/adapted | External provider references are released, binder delivery is moved off the UID observer, failures are throttled, binder death is tracked, Android 8.1/9 signatures are handled, and the API27-37 `IUidObserver` method union is declared/overridden. |
| `ac0d4da7ac0dd4fe9ae115832d779b0ca7441860` | Ported/adapted | Module-app binder delivery now carries an attempt token and uses a delivery ownership lock so a late send or queued death from an obsolete process cannot overwrite/remove the replacement process's delivery state. The fix is adapted onto Vector-SR's existing `ModuleService` and API102 hot-reload implementation. |
| `f5655426b28a2c9cf8c6d310643d1f0bdc38bdf4` | Daemon runtime compatibility selectively ported | API27-safe SELinux `FileObserver` construction, explicit `LocalServerSocket` lifetime, and the pre-Q secret-code action literal are applied without importing dependency, AGP/NDK, signing, warning-cleanup or new-Manager changes from the same upstream commit. |
| `3eae64fe77a7c8297659602172c0abcb37c5007b` | Ported/adapted | The R+ `IServiceCallback.Stub` implementation is isolated behind its own holder and pinned against R8 merging so Android 9 does not resolve the missing class during daemon verification. Vector-SR retains its existing proxy registration and SR re-claim state. |
| `5e4dcb92a11e64a74141d94d49f928af0831f803` | Ported/adapted | Package add/remove handling now checks durable scope configuration by package name before falling back to uid-derived scope state, so uninstall/reinstall with a new uid rebuilds injection state without losing the saved scope. Compose scope-editor/documentation changes are excluded. |
| `8239245d83869709d03e3dd869d6fd76287349d4` | Ported/adapted | Daemon startup reasserts `system_file` on `manager.apk` if a system update/restorecon relabels the module tree, preserving Binder FD delivery to the parasitic or installed Manager. Vector-SR's SR metadata and private-file labeling remain unchanged. |

## Vector-SR-specific compatibility work

The integration branch also contains fixes that are not claimed as verbatim upstream code:

- Historical Vector-SR API102 test metadata is accepted when canonical `targetApiVersion` is absent, while standard libxposed metadata remains authoritative. This prevents the daemon and injected process from assigning different API levels to the same APK.
- `BaseInvoker` uses the canonical API102 native callback snapshot as the invocation boundary; a callback already snapshotted cannot disappear because another thread replaced its handle.
- The API102 generation map swap is treated as the irreversible commit point. If later new-generation callback work fails, the retired generation remains frozen rather than being incorrectly re-enabled.
- API102 manual hot reload validates and enqueues promptly, executes module lifecycle code outside the module-app Binder call, and returns the actual result asynchronously. The daemon distinguishes refusal, pre-commit failure, committed-generation callback failure, timeout and process death.
- Frozen cached app targets are thawed only for the addressed process during the reload transaction and restored best-effort afterwards; uid-wide cgroups are never thawed.
- `autoHotReload=true` is driven by the committed `ConfigCache` code-identity swap rather than by a polling window. Same-path APK updates therefore trigger once their new identity is actually visible. The first cache commit also checks already-registered early targets, closing the pre-PackageManager `system_server` update edge; later duplicate broadcasts naturally see no new identity change.
- Hot-reload target eligibility follows the libxposed descriptor rule: a modern module with exactly one Java entry is eligible. `targetApiVersion` still controls generation behavior such as API102 legacy-API isolation, but is not an additional target-discovery gate.
- API102 running-target visibility and manual target selection are constrained to the module app's Android user, while system-process targets retain their device-wide ownership semantics.
- API102 `system_server` targets use the existing Vector-SR reverse target binder. Early registration authenticates against the exact module object previously offered to the process and preserves the actually loaded APK identity until PackageManager metadata is available. A later reload stages the addressed generation directly, so an already-running target remains reloadable even if `system` was subsequently removed from the module's configured scope.
- API102 modules with one Java entry remain reloadable when they also declare `native_init`. In accordance with the libxposed API contract, Vector-SR does not call `UnregisterNatives`, `JNI_OnUnload` or `dlclose`; modules must quiesce old native threads, callbacks, hooks and JNI references in `onHotReloading()` before accepting the swap. Native entrypoint registration is idempotent across generations.
- `system_server` native staging is keyed by module version as well as APK size/mtime and framework version, preventing a same-size/same-mtime package replacement from retaining an older staged `.so` generation.
- Hot-reload extras are passed through after normal Binder unmarshalling without an additional Vector-SR type whitelist. Saved instance state is separately checked, as API102 specifies, for the value itself and direct array/collection/map members defined by the retiring module classloader.
- A system_server restart removes only the dead process's hot-reload targets through its heartbeat death recipient; surviving application targets are not globally discarded by SR recovery and the reinjected system_server registers a new target.
- Device-wide package removal passes a nullable user to `PreferenceStore.deleteModulePrefs`, so preferences from all Android users are actually removed. This intentionally corrects the current upstream call-site typo where `targetUser` is computed but `userId` is still passed.
- `ConfigCache` reads `PackageInfo` version codes through an API27-safe accessor. Current upstream still uses `longVersionCode` directly in this path, so this is recorded as Vector-SR hardening rather than attributed as a verbatim `f5655426` backport.
- The API27 dex2oat compatibility changes are fitted around Vector-SR's existing stale-mount/property-fallback/soft-restart recovery instead of replacing that recovery with upstream's file wholesale.

## Intentionally retained Vector-SR architecture

- Legacy View-based Manager in `app/`.
- Existing `org.lsposed.lspd.*` service/Binder namespace.
- KernelSU soft-restart reinjection owner, lease, PID-reuse and recovery model.
- SR `ModuleCodeIdentity` stale-code diagnostics.

## API102 behavioral compatibility

Vector-SR implements the externally observable libxposed API102 hook/generation and hot-reload behavior while retaining its existing `org.lsposed.lspd.*` transport. The implementation therefore differs internally from upstream Vector's process-wide `IProcessChannel`/`org.matrix.vector.ipc` topology, but preserves the API contract: target discovery, user isolation, asynchronous submission/result delivery, refusal and failure semantics, bounded execution, automatic update reloads, cached targets, `system_server`, one-Java-entry mixed native modules, saved-state handover, and atomic generation/hook replacement behavior.

This is behavioral compatibility, not a claim that Vector-SR and upstream Vector have source-identical daemon or Manager architecture. The acceptance matrix is maintained in `docs/API102_COMPATIBILITY_TESTS.md`.

## 2026-08-08 final PR #22 audit batch

The final audit was assembled on a non-PR staging branch so intermediate fixes did not repeatedly trigger the PR workflow. The batch closes generation-retirement registration races, keeps committed retired contexts frozen, corrects the hot-reload commit boundary, completes uninstall/scope-notification cleanup, restores the release XResources source guard, and removes daemon calls that are unsafe on the declared API 27 minimum. The PR head is updated only after this batch is reviewed as one unit.

This table is intentionally conservative. `Ported` means the relevant behavior exists on this branch; it does not claim source-level identity unless the corresponding commit explicitly records a blob-compatible backport.
