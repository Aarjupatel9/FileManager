# FileManager - TODO

## Remaining Tasks

### Code Quality Improvements

- [ ] Address suppressed lint warnings
  - Review `@SuppressLint("NotifyDataSetChanged")` in FileAdapter
  - Review `@SuppressLint("QueryPermissionsNeeded")` in openSelectedFile

- [ ] Extract magic numbers to constants
  - Job scheduler period (20 * 60 * 1000)
  - Max file name length (30)

- [ ] Add comprehensive error handling
  - File operations edge cases
  - MediaPlayer error handling

- [ ] Remove commented code
  - Line 140 in MainActivity: `// startApplicationServices()`

### Security Review

- [ ] Review exported components
  - Audit `MyBroadcastReceiver` export necessity
  - Audit `MusicPlayerActivity` export necessity

### Testing

- [ ] Write unit tests for FileManagerViewModel
- [ ] Write unit tests for SettingsManager
- [ ] Write unit tests for file sorting logic
- [ ] Test on Android 13+ device
- [ ] Test file operations
- [ ] Test app rotation during playback

---

## Completed ✅

### Critical Bugs Fixed
- ✅ Bug #1: Renamed `FIlesEntry.kt` to `FileEntry.kt`
- ✅ Bug #2: Removed duplicate `RECEIVE_BOOT_COMPLETED` permission
- ✅ Bug #3: Fixed TextToSpeechManager Activity Declaration
- ✅ Bug #4: Fixed BroadcastReceiver Memory Leak
- ✅ Bug #6: Converted var to const val in Constants
- ✅ Bug #7: Updated deprecated stopForeground API
- ✅ Bug #8: Added READ_MEDIA_AUDIO Permission
- ✅ Bug #9: Fixed NullPointerException (.get(0) → [0])
- ✅ Bug #10: Handler cleanup in MusicPlayerActivity
- ✅ Bug #11: API 34 Foreground Service Crash
- ✅ Bug #12: Missing Attribution and Audio Permission

### Build Warnings Fixed
- ✅ Fixed all 7 compiler warnings in MyNotificationListener and FileAdapter

### Music Player Enhancements
- ✅ Service refactoring (MediaPlayer state fixes, seekbar sync)
- ✅ UI refinements (layout, spacing, buttons)
- ✅ Back navigation to parent folder
- ✅ Bluetooth media controls (play/pause/next/previous)
- ✅ Stop/Close functionality with notification cleanup

### Universal Counter Features
- ✅ Universal multi-category target-tracking counter
- ✅ Daily and lifetime target limits per category
- ✅ Smart auto-sorting by usage
- ✅ Haptic feedback toggle
- ✅ Configurable background reminders (Alarms)
- ✅ Historical daily counters list view
- ✅ Cloud sync support (CodeShare integration)
- ✅ Secure login modal for cloud sync

### Music Player Enhancements (v2)
- ✅ App registered as system audio handler (ACTION_VIEW for audio/*)
- ✅ Expanded audio format support (.flac, .ogg, .aac, .m4a, .opus, .wma, .aiff)
- ✅ Mini music card tappable anywhere to open full player
- ✅ Folder-as-Playlist: tap playlist folder → Open or Play dialog
- ✅ Play All FAB inside playlist sub-folders
- ✅ Add song to playlist (Move to Playlist)
- ✅ Move song between playlists (Move to Another Playlist)
- ✅ Remove song from playlist (with confirmation)
- ✅ Delete entire playlist folder (with confirmation)
- ✅ Rename playlist folder
- ✅ One-time default music player prompt in MusicPlayerActivity
- ✅ Path-specific sort order preferences (independent sort per folder)
- ✅ Custom Playlist Order (4th option) in playlists with manual song reordering via drag-and-drop (ItemTouchHelper)
- ✅ Automatically play playlists in their customized/path-specific sort order

### Permission Flow & UX Fixes
- ✅ Coordinated storage permission flow to avoid duplicate dialogs
- ✅ Added `onResume()` checking to automatically reload/load file list when returning from system permission Settings
- ✅ Bypassed redundant media permissions (`READ_MEDIA_*`) on Android 11+ when broad All Files Access (`MANAGE_EXTERNAL_STORAGE`) is granted
- ✅ Added `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` checks for JapCounter reminders

### Modern UI Overhaul & Splash Screen
- ✅ Added custom Splash Screen (1.5-second delay, Material 3 styles, centered app logo card, custom tagline, and progress indicator)
- ✅ Registered `SplashActivity` as launcher activity to handle initialization cleanly
- ✅ Modernized File Explorer screen list items using beautiful MaterialCardViews with dynamic file size/date details (`item_file.xml`, `FileAdapter.kt`)
- ✅ Overhauled Main Activity header layout: custom bottom-rounded card header with bold title and modern spacing (`activity_main.xml`)
- ✅ Refined dynamic chip-pill breadcrumbs to highlight the active directory with color primary container tinting (`MainActivity.kt`)
- ✅ Redesigned Music Player screen with a premium, immersive dark-themed gradient background (`activity_music_player.xml`, `music_player_background.xml`)
- ✅ Implemented dynamic rotating vinyl disc animations during active playback and modern solid media playback control states (`MusicPlayerActivity.kt`, `ic_play_arrow_24.xml`, `ic_pause_24.xml`)
- ✅ Relocated folder creation action from the top navbar to a bottom-right FloatingActionButton (`addFab`) showing an option menu popup
- ✅ Introduced spacing gaps between the top appbar and file content for a cleaner layout
- ✅ Upgraded all dialog popups to modern Material 3 `MaterialAlertDialogBuilder` popups (including file management actions, folder creation, renaming, permission prompts, cloud sync, and settings)
- ✅ Modernized input fields inside creation and rename dialogs with start icons and clear text actions (`create_folder_dialog.xml`, `rename_dialog.xml`)
- ✅ Redesigned the File Sort Options Dialog layout using a nested Material 3 CardView containing separated rows and bold typography (`sort_dialog.xml`)
- ✅ Modernized the bottom mini player layout (`music_card_bottom.xml`) with card outline borders, standard primary player buttons, and custom vector media controls
- ✅ Added continuous package-resolved startup prompting on launch to set the app as the default handler for audio files (`MainActivity.kt`, `AndroidManifest.xml`)
- ✅ Added high-contrast color highlights to active text labels and a prominent primary container pill background to active sorting buttons in the File Sort Options Dialog (`MainActivity.kt`)
- ✅ Fixed a horizontal layout push-out bug in the File Sort Options Dialog that hid the "Playlist Order" row by wrapping the divider and items in a vertical container layout (`sort_dialog.xml`)
- ✅ Bypassed the redundant playlist open/play choice dialog on folder clicks to directly open playlist folders instantly (`FileAdapter.kt`)

### Project Configuration
- ✅ Updated comprehensive `.gitignore`

---

## Notes

- **Bluetooth Controls**: Fully working via `MediaSessionCompat`
- **Background Playback**: Music continues when app is minimized
- **Notification Listener**: Service declared but `startApplicationServices()` is commented out
