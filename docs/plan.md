# MicroG-RE Fork Analysis & Rebase Plan

## Overview

**Fork**: `MorpheApp/MicroG-RE` (origin/main)
**Upstream**: `microg/GmsCore` (upstream/master)
**Merge base**: `891503f9` (tag: `v0.3.0.233515`, "Build: fix linter")
**Primary author**: WSTxda (330 of 482 commits)

### Divergence Summary
| Metric | Count |
|--------|-------|
| Fork-only commits | 482 |
| Upstream-only commits | 1,055 |
| Cherry-picked from upstream (drop) | 65 |
| Duplicate commits from dev→main merges (drop) | 74 |
| Merge commits (drop in rebase) | 13 |
| Revert pairs — net zero effect (drop) | 16 (8 pairs) |
| **Total to DROP** | **165** |
| **Remaining to KEEP/SQUASH** | **317** |

### Diff Stats (fork vs upstream at merge base)
- 3,541 files changed
- 27,103 insertions, 196,168 deletions
- 461 files added, 198 modified, 2,882 deleted

The large deletion count is because upstream added many modules (maps, location, drive, wearable, fido, nearby, firebase-auth, droidguard, games, ads-lite, etc.) that the fork diverged from before they were added, so they appear as "deleted" in the fork's tree.

---

## Commit Categorization

### Category Counts (after removing 165 droppable commits, 317 remaining)
| Category | Count | Squash Target |
|----------|-------|---------------|
| **Other/Misc** | 88 | 8-12 commits |
| **UI/Theme Redesign** | 58 | 5-8 commits |
| **Account Manager** | 58 | 5-8 commits |
| **GMS Version Bumps** | 22 | 1 commit |
| **Gradle/Dependencies** | 16 | 1-2 commits |
| **Push Notification/GCM** | 12 | 2-3 commits |
| **Patcher Detection** | 10 | 1-2 commits |
| **Update Checker** | 10 | 1-2 commits |
| **Translations/Crowdin** | 9 | 2-3 commits |
| **Bug Fixes** | 8 | Keep individual |
| **CI/CD** | 7 | 1-2 commits |
| **Self-Check** | 7 | 1-2 commits |
| **Huawei Support** | 5 | 1-2 commits |
| **Features** | 3 | Keep individual |
| **README** | 2 | 1 commit |
| **Screenshots** | 2 | 1 commit |
| **TOTAL** | **317** | **~40-50 commits** |

---

## Cherry-Picked Commits (65)

These commits have identical patch-ids to upstream commits and should be **dropped** during rebase (upstream already has them). Grouped by area:

### Licensing Service (15 commits)
Cherry-picked the entire licensing service feature from upstream:
- `1b0cf32c` Licensing service
- `784c5cb5` Don't provide most negative results in V2 licensing
- `7dfb4456` Improve license notification icon & sound
- `f1a653d5` Query licenses from multiple Google accounts in a row
- `b53c16fe` Dismiss all notifications if user wants to sign in
- `b097f1f5` Always request `android.permission.GET_ACCOUNTS`
- `10b92a41` Target JVM 1.8 instead of 17 in fakestore
- `3dc59f86` Require permission to check license
- `621afa19` / `ddf5a4bf` Apply review (×2)
- `9eb4cfcb` / `3ecebbf7` Check preference from within Licensing service (×2)
- `8477cb2e` Load device profile data for licensing user agent
- `a8329b01` Use Google androidId for Licensing
- `877846b2` License Check: Correctly check callingUid when having multiple accounts

### Map/Location Fixes (10 commits)
- `72970a73` Location: add SNCF/Ouigo location lookup
- `618733b5` Location: Catch exceptions from callback
- `96b9e55a` Location provider: Add support for more moving wifis
- `11287d22` Location: Set low-power for network location requests
- `f84ce7dc` Location: handle invalid or dead old binder on location request update
- `18ab6846` HMS Maps: Have MapContext report correct package name except to HMS
- Various map-related bug fixes (HKTaxi, KMB, Claro Flex, Skype, weather apps, mapbox)

### Auth/Push (8 commits)
- `582c03a3` Auth: update credentials feature version
- `aafa4391` Auth: Remove unnecessary prompts
- `3ae2f6aa` Auth: Make account visible after creation when configured to do so
- `d7307ec8` Improve the signOut method of AuthSignInService
- `f2340c7f` Add google.sent_time extra to C2DM receive intent
- `a0a73aec` Only include sent time when valid, also add TTL
- `a2abc91e` Push: Do not allow app data to override service fields
- `ece09d1e` Account settings: Allow profile picture page

### CI/Build (8 commits)
- `57bfe21d` Optimize Gradle build on GitHub
- `c8b62dbe` GitHub Gradle build improvements
- `381b397e` Separate assemble and check on GitHub workflow
- `2aa144f8` Correctly handle optional spaces in GitHub workflow
- `88adf9e8` Update Gradle Wrapper validation action
- `bd3ae7ca` Add update check for Github actions
- `780346e3` Bump gradle/actions from 3 to 4
- `f4dd994b` / `ea4e37f6` Bump actions/checkout from 4 to 5 (×2)

### Other Cherry-picks
- `cfb3a843` fixes exception from Repeated Thread Creation
- `a8e9f38c` fixes unhandled NullPointerException for styleOperations
- `08fc0c14` Fido: Allow background activity launch
- `870102d6` UI: Remove permission request dialogs from recent apps
- `4c71aeb3` Fake signature: Add additional packages
- `42755ddc` Self-Check: Adjust to better cover most signature spoofing scenarios
- Various others (settings permissions, POST_NOTIFICATIONS, etc.)

---

## Squashable Groups (317 remaining → ~40-50 target)

After dropping 165 commits (cherry-picks, duplicates, merges, revert pairs), the 317 remaining commits group as follows:

### Group 1: UI/Theme Redesign (58 commits → 5-8 squashed)
The largest group. The Material3 redesign was done iteratively over many months.

**Subgroup A — Initial theme & branding** (~12 commits → 1):
`b814cafa` Spoof package name + Apply theme → `c9fc33ef` Update app theme → `51d490a3` Preference redesign → `5f50a64e` Layout redesign → `325c718d` / `44e37bbc` Update app icon → `b31354ce` Update icons → `0d07f258` Fix incorrect theme → `dd9a1a3f` Show app in launcher → `41271ce0` Update monochrome icon

**Subgroup B — Material3 migration** (~15 commits → 1):
`64ce1a1b` Redesign app to Material3 → `0bc53edb` Adapt theme for older Android → `a9f17dbe` Update Material Colors → `27138a79` / `7aaca458` Update colors for YouTube → `f10fe166` New MicroG RE icon → `9cde6af1` Clean vector drawables → `7a5f907a` Update UI icons → `9193495b` Update settings link icons → `d0e4e3b9` Menu icon padding → `954080361` Color error in dialog → `76a4f774` Collapsed toolbar

**Subgroup C — Hide icon in launcher** (~8 commits → 1):
`18ffcd0a` Add hide icon → `836e3590` Improvement → `4cf691f6` Switch state → `d450aeb3` Fix option → `c1352a20` Battery optimization fix → `24c148f1` HyperOS fix → `f61f5886` First install fix

**Subgroup D — Switchbar & component styling** (~6 commits → 1):
`ae3ba59d` Switchbar disabled color → `6bf6cd2f` Color error → `84b0aa19` Color state → `55904d96` Remove from confirm → `1f786eaa` Text color state → `3d09af88` Thumb icon

**Subgroup E — Material3Expressive upgrade** (~5 commits → 1):
`9dfd269e` Switch to M3Expressive → `b9281002` Theme colors → `9f19e7d9` Redesign UI → `b0025226` Material library version → `79299cdf` Icon resources

**Subgroup F — Misc layout & UI fixes** (~12 commits → 1):
Various layout improvements, paddings, transitions, RTL support, edge-to-edge

### Group 2: Account Manager (58 commits → 5-8 squashed)
Complete account management overhaul.

**Subgroup A — Initial account screen** (~10 commits → 1):
`a05883f0` Remove authenticator prefs → `4e5eb162` Add account manager → `9dc8c91c` Tap to remove → `b8798ca4` Dynamic list → `8bab38f2` Toast on remove → `149f9690` Drop updateAccountList → `eaeac30d` Update list after remove → `2deff9bf` Default account tag → `de16de6a` Login theme

**Subgroup B — Login UI redesign** (~12 commits → 1):
`f088aff1` Login assistant layout → `a99703ec` Login assistant design → `76d08690` Dark theme WebView → `fb67ccf8` M3 progress indicator → `2b857162` Refactor Login Assistant UI → `35f36a32` Login text style → `d0aa7ac8` Login screen design → `c1e514ec` Wave animation → `8b22ff4a` Login layouts → `24c2e086` Screen rotation fix → `2db055b5` Text frame fix

**Subgroup C — Account settings/privacy** (~10 commits → 1):
`034a8da2` Legacy account option → `8990922d` Change to privacy → `73f42c7f` Update preferences → `c39213044` Privacy in settings → `cfc7b6b5` Manage Google Account → `04811fd2` Refactor accounts screen → `b7204d96` YouTube Music link → `f0111cd6` Update fragments → Various auth integration commits

**Subgroup D — Account lifecycle & bug fixes** (~8 commits → 1):
`09b4b19a` IllegalStateException fix → `67b7e6e6` ActivityNotFoundException → `7e7302ed` NullPointerException snackbar → `62d28f2a` Refactor lifecycle → `14e7c6c9` Undo remove → `c17c7321` Privacy crash fix → `627f4921` Login errors → `96d3a64a` Registration flow fix

**Subgroup E — External auth contributions** (~6 commits → keep 3-4 individual):
`275f3603` [Fynn Godau] Replace hardcoded auth token
`dc5df50e` [Fynn Godau] Notify if not signed in
`024e92c6` [Marvin W] Auth: Allow stripping device name
`f56dc668` [DaVinci9196] Auth: Return result from login
`402e03e7` [DaVinci9196] Auth: Correctly handle permission
`e1f28d3b` [DaVinci9196] Make login activity single task

### Group 3: GMS Version Bumps (22 commits → 1)
All `Bump GMS version to X.XX.XX` from v23.43.55 to v25.50.34. Squash to single: "Bump GMS version to 25.50.34"

### Group 4: Gradle/Dependencies (16 commits → 1-2)
`b11f9a50` Set min SDK 23 → Various gradle/dependency updates → `0d2aaa99` Final Gradle version.
Keep min SDK change separate; squash rest.

### Group 5: Push Notification/GCM (12 commits → 2-3)
`2fd0a156` Redesign ask gcm dialog → `76322d1f` M3 dialog → `a78e271d` Foreground service actions → `4e39ab0f` Refactor permission dialog → `96f65ba1` No history flag → `66e1d854` Display over others apps permission → misc fixes

### Group 6: Patcher Detection (10 commits → 1-2)
`12e731cf` Add patchers → `a6c26ea1` Self-check integration → `5ae7c20a` Improve detection → `38ddd1e6` Anddea → `4d5fc3a9` rufusin → `735630c5` / `35c8ddbf` ReVanced ID → `bcf83376` Blacklist

### Group 7: Update Checker (10 commits → 1-2)
`65628833` Add button → `a2eb62de` Improve → `6a48f6f7` Replace AsyncTask → `437c2706` Error handling → `5ef1545e` Snackbar → `f244c3e2` Fix ANR → `573ed244` Modularize

### Group 8: Self-Check (7 commits → 1-2)
Remove → Re-add → Fix layout → Fix toolbar → Update icon → Neutral result

### Group 9: Translations/Crowdin (9 remaining + manual translations → 2-3)
- Crowdin infrastructure: actions, config, file paths (→ 1)
- Manual translations: PT-BR, Arabic, Polish, Indonesian, Turkish, Vietnamese, Ukrainian, Chinese, Russian, Italian (→ 1)
- Crowdin bot updates (→ 1)

### Group 10: CI/CD (7 commits → 1-2)
GitHub Actions, release workflow, Java version, build fixes

### Group 11: Huawei Support (5 commits → 1-2)
`f8118db1` Add support → `2bd4dcfc` Huawei button → `cb7fe427` -lh flavor → `2f18930e` Sign-in summary → `d3e97521` Untranslatable

### Group 12: Other/Misc (88 commits → 8-12)
This is the catch-all group. Suggested sub-squashes:
- **App heading** (7 commits → 1): package name, version, patcher name, buttons, design
- **Settings organization** (6 commits → 1): move preferences, fragment labels, menu options
- **String/resource management** (8 commits → 1): strings cleanup, organize, mark non-translatable
- **Morphe branding** (4 commits → 1): README links, badges, about layout
- **Edge-to-edge** (3 commits → 1): enable, disable, re-enable with window insets
- **Individual features to keep** (~15 commits): redfin profile, x86 support, enableOnBackInvokedCallback, battery optimization card, fragment transitions, YouTube data options, etc.
- **Misc cleanup** (~10 commits → 1-2): various cleanings, typo fixes, small updates

### Group 13: Revert Pairs (8 pairs, 16 commits → 0)
All already removed. These pairs cancel out:
1. `ef84e45b` Add uninstall button ↔ `e63c2ab3` Revert
2. `9ddf3c24` Make icon show in launcher ↔ `e75e340b` Revert  
3. `3efd0518` Add QUERY_ALL_PACKAGES ↔ `add75006` Revert
4. `bed78d3c` Replace alarm permission ↔ `56c4190a` Revert
5. `8c70ba84` Add identity sign-in ↔ `21afca05` Revert
6. `174a7ba5` Add AUDIT service ↔ `5c7929b4` Revert
7. `e3cdbfab` Disable YT Advanced detection ↔ `c53bbdd3` Revert
8. `2c8c977d` Disable hide icon on Huawei ↔ `0e4c0cfe` Revert

---

## Summary of Fork Changes

### 1. Complete UI Redesign
The fork completely redesigned the microG settings UI:
- Migrated from original microG theme → Material3 → Material3Expressive
- New app icon (matching YouTube/ReVanced branding)
- Monochrome icon support
- Collapsed toolbar with settings fragments
- Fragment transitions (MaterialSharedAxis)
- Edge-to-edge display support
- RTL support
- Hide icon in launcher option
- Custom switchbar styling

### 2. Account Manager Overhaul
Replaced the original account/authenticator preferences with a full account manager:
- Dedicated AccountsFragment with dynamic account list
- FAB to add accounts (with app shortcut)
- Tap-to-remove with undo via Snackbar
- Redesigned login assistant UI with Material3 progress indicators
- Dark theme support in WebView login
- Privacy settings integration
- Default account tagging
- YouTube/YouTube Music account manager link handling
- Legacy account manager option preserved

### 3. Huawei/HMS Support
- Added Huawei device support with `-lh` build flavor
- Huawei button in settings
- HMS Maps package name spoofing
- Additional apps for Huawei device compatibility

### 4. ReVanced/Patcher Integration
- Package spoofing for Google News, Google Photos
- Patcher detection (ReVanced, Anddea, rufusin packages)
- Changed default group ID to "ReVanced"
- Redfin device profile
- YouTube data/history management options
- App heading shows patcher name and version

### 5. Update Checker
- In-app update checking via GitHub releases
- Check updates button in settings
- Modular UpdateChecker with error handling
- ANR-free async implementation

### 6. Self-Check Enhancements
- Re-added self-check option in settings
- Neutral result for non-required permissions
- Patcher package installation check
- Battery optimization request card

### 7. Push Notification Improvements
- Ask permission for display over other apps
- Foreground service notification actions
- No history flag for push register requests
- Advanced fragment lifecycle fixes

### 8. Crowdin Translation Infrastructure
- Crowdin integration via GitHub Actions
- Translations for: Portuguese-BR, Arabic, Polish, Indonesian, Turkish, Vietnamese, Ukrainian, Chinese, Russian, Italian
- Automated push/pull from Crowdin

### 9. CI/CD Pipeline
- GitHub Actions release workflow
- Build, release, and Crowdin sync automation
- Issue templates (bug report, discussion)

### 10. Build System Changes
- Min SDK raised to 23 (from upstream's lower value)
- Custom build output naming
- Changed version name format
- Gradle build cache enabled
- x86/x86_64 ABI support
- Various Gradle and dependency updates

### 11. Misc Changes
- Foreground service OEM utils (Xiaomi battery optimization)
- Package spoofing utilities
- GMS version bumps (currently at 25.50.34)
- Edge-to-edge display
- enableOnBackInvokedCallback support

---

## Recommended Rebase Strategy

### Phase 1: Preparation (commits to DROP — 165 total)
- 65 cherry-picked commits (identical patches already in upstream)
- 74 duplicate commits (dev→main merge workflow artifacts)
- 13 merge commits (eliminated by rebase)
- 16 revert-pair commits (8 pairs that cancel out — net zero)
- Note: Some overlap between these categories; net drop is 165.

### Phase 2: Squash Groups (317 → ~40-50 logical commits)
Apply the detailed squash plan from above:
1. **UI/Theme**: 58 → 5-8 squashed commits (by sub-feature)
2. **Account Manager**: 58 → 5-8 squashed commits (by sub-feature)
3. **GMS Version Bumps**: 22 → 1 commit
4. **Gradle/Dependencies**: 16 → 1-2 commits
5. **Push Notification**: 12 → 2-3 commits
6. **Patcher Detection**: 10 → 1-2 commits
7. **Update Checker**: 10 → 1-2 commits
8. **Translations/Crowdin**: 9 → 2-3 commits
9. **Bug Fixes**: 8 → keep individual (~8)
10. **CI/CD**: 7 → 1-2 commits
11. **Self-Check**: 7 → 1-2 commits
12. **Huawei**: 5 → 1-2 commits
13. **Other/Misc**: 88 → 8-12 commits (by sub-topic)
14. **Features**: 3 → keep individual
15. **README + Screenshots**: 4 → 1-2 commits

### Phase 3: Interactive Rebase Execution
- Create a new branch from `upstream/master`
- Cherry-pick the ~40-50 squashed/selected commits in logical order
- Resolve conflicts as they arise (expect significant conflicts in `play-services-core/`)
- The fork deleted many modules that upstream added since the merge base — decide whether to keep those modules or continue excluding them

### Phase 4: Validation
- Build the project with `./gradlew assembleRelease`
- Verify all fork features still work
- Run any existing tests

---

## Todos

The following tasks track the work needed:

1. **catalog-cherry-picks**: Document all 65 cherry-picked commits with their upstream equivalents
2. **catalog-duplicates**: Document all 74 duplicate commits from dev→main workflow
3. **catalog-reverts**: Document all revert pairs and determine net effect
4. **prepare-squash-plan**: Create detailed interactive rebase script with squash/drop instructions
5. **execute-rebase**: Perform the actual rebase onto upstream/master
6. **resolve-conflicts**: Handle merge conflicts during rebase
7. **validate-build**: Verify the rebased branch builds successfully
8. **validate-features**: Test that fork-specific features still work
