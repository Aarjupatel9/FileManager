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

### Project Configuration
- ✅ Updated comprehensive `.gitignore`

---

## Notes

- **Bluetooth Controls**: Fully working via `MediaSessionCompat`
- **Background Playback**: Music continues when app is minimized
- **Notification Listener**: Service declared but `startApplicationServices()` is commented out
