# iLauncher – iOS-style launcher for Android

Jetpack Compose launcher inspired by iOS 16 home screen + iOS 27.3 beta features.

## Build on GitHub (no Android Studio needed)
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab → **Build APK** → wait for the green tick (or click *Run workflow*).
3. Open the finished run → **Artifacts** → download `iLauncher-apk` → unzip → install `app-debug.apk`.

## First run
Open **iLauncher** (setup screen) and follow buttons 1-4.

## Features
- iOS home screen: squircle icons, dock, page dots, Weather + Calendar widgets, App Library (auto categories)
- Spotlight search (swipe down), Control Center (swipe down top-right), Siri-style assistant
  (long-press Search pill; typed or voice commands: "open <app>", "call <number>", "flashlight on", "what time is it", anything else = web search)
- StandBy (landscape + charging, or from Control Center), Health summary (steps)
- iOS-style lock screen shown over the Android keyguard; swipe up -> Android PIN/biometric prompt

## Edit mode (jiggle mode)
- Long-press any home-screen app -> **Edit Home Screen** (icons start wiggling)
- Drag an icon onto another icon to move it; hold it near the left/right edge to move it to another page
- Tap the **–** badge to hide an app from the home screen (it stays in App Library / Spotlight)
- Hidden apps: App Library -> open a folder -> long-press the app -> **Add to Home Screen**
- Tap **Done** (top-right) or press Back/Home to finish. Order is saved automatically.

## Edit weather city
`Config` object in `Util.kt`.
