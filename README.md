# Lino Life customer app

Capacitor loads `https://linolife.co/capp` inside the native app. The remote page
uses `navigator.geolocation` and HTML file inputs. Installing Capacitor plugins
alone does not grant platform permissions or replace these browser APIs.
Android 8.0/API 26 or newer is required by the installed barcode scanner.

## App icon and splash screen

The original artwork is stored in `assets/branding/linolifeicon.png` (app icon)
and `assets/branding/linolife.png` (wide splash logo). Run `npm run assets:generate`
after replacing either source. This creates all Android density assets, the
512px Play Store icon, the opaque 1024px iOS icon, launch images and a visual
preview in `assets/branding/preview.png`.

Android adaptive icons keep the complete logo inside the circular safe area.
The launch theme shows the wide logo centered on white; Android 12+ uses a
separate image sized for the system splash icon's safe area. iOS uses aspect-fit
launch images with white padding so portrait and landscape do not crop the logo.
On Android 12+, `MainActivity` installs the system splash before `super.onCreate`
to apply its post-splash AppCompat theme before Activity initialization. The
exit listener removes the splash as the first app frame becomes ready, without
holding the Activity behind a timed animation.
Android's `includePlugins` list also omits native Google Maps: the hosted capp
uses browser maps, while the unused native SDK initializes its renderer on the
UI thread during startup. Location permission/geolocation, uploads and Firebase
notifications remain included. The Maps npm package is retained for future use;
add it to `android.includePlugins` and sync if native map views are introduced.
See [Android splash screen sizing](https://developer.android.com/develop/ui/views/launch/splash-screen).

Rebuild and reinstall the native app to see updated branding. iOS launch-screen
snapshots and Android launcher icons may remain cached until the app is
reinstalled or the launcher refreshes.

## Build

```sh
npm ci
npx cap sync android
cd android
./gradlew assembleDebug
```

On Windows, use `gradlew.bat`. Install the rebuilt APK from
`android/app/build/outputs/apk/debug/app-debug.apk` to test the native changes.

The iOS Xcode project is in `ios/App/App.xcodeproj`, with Swift Package Manager
dependencies. On a Mac with Xcode, run `npm ci` and `npx cap sync ios`, open the
project, select your signing team and build for a device.

`capacitor.config.ts` is the active configuration. Keep the legacy
`capacitor.config.json` consistent if changing the server URL or app identity.

## Location and uploads

- Android declares foreground coarse/fine location and camera permissions.
  Capacitor requests them when the page asks for location or camera access.
  Approximate location is sufficient for the nearby partners feature.
- Android uses `UploadWebChromeClient` to keep all accepted file types visible
  in the system picker, including PNG/WebP alongside JPEG and PDFs in mixed
  document fields. Capacitor continues to handle results, cancellation and
  permission prompts. The picker does not require broad storage permission.
- iOS declares location, camera and photo library usage descriptions in
  `ios/App/App/Info.plist`. No background location capability is enabled.

Permission references: [Capacitor Geolocation](https://capacitorjs.com/docs/apis/geolocation),
[Camera](https://capacitorjs.com/docs/apis/camera), and
[iOS configuration](https://capacitorjs.com/docs/ios/configuration).

## Device verification

1. Install the rebuilt app. Tap the nearby location control and allow location.
   Confirm nearby results load. Also check approximate location on Android.
2. Deny location and confirm the city selector remains usable. If permission was
   previously denied, enable it in the phone's app settings and retry.
3. On registration/profile, select and upload a JPEG, PNG and WebP separately.
   Check the preview or saved profile image after submission. For NID/document
   fields, also select a PDF. Keep files within the web form's size limits.
4. Cancel a picker, then reopen it. Check capture-enabled inputs on a real camera
   device and multiple selection on any form that supports it.
5. Repeat on iOS, allowing access when prompted.

Android picker regression tests require a connected device or emulator:

```sh
cd android
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=co.linolife.app.MainActivityStartupTest,co.linolife.app.UploadWebChromeClientTest
```

An existing installed app needs a new native build for permission changes to take
effect. Syncing web assets alone does not update an APK or an iOS installation.

## Firebase push notifications

Both native apps use Firebase project `linolife` and app ID `co.linolife.app`:

- Android config: `android/app/google-services.json`. The Google Services Gradle
  plugin is required, so a missing or mismatched config fails the build.
- iOS config: `ios/App/App/GoogleService-Info.plist`, included in the app resources.
  FirebaseCore and FirebaseMessaging 12.19.3 are linked directly to the App target
  through Swift Package Manager. `npx cap sync ios` preserves these dependencies.
- Android channel: `linolife_updates`, matching the server payload; the native
  plugin creates it before the first message, with a separate notification icon.
- iOS AppDelegate initializes Firebase, maps the APNs device token to Firebase,
  forwards FCM registration/refresh events to Capacitor and forwards APNs errors.
  Capacitor retains control of foreground notifications and notification taps.
- Debug uses the development APNs entitlement; Release uses production. No silent
  background notification handler is implemented.

The hosted website currently expects `median.firebaseMessaging`. The bundled
`www/native-push.js` adapts that API to Capacitor and is injected at document start
on both platforms. It runs only in the main frame at `https://linolife.co`. The
existing website continues to request permission after the user taps Enable,
save the native token to `/push/devices` with session/CSRF protection, handle
notification taps, and unlink the account before token deletion on logout.
The adapter does not request permission or register a device on its own.
Token refreshes are fetched again when the website registers on load/resume.

Before verifying iPhone delivery on a Mac:

1. Select the Apple signing team for `co.linolife.app` and use a provisioning
   profile with Push Notifications enabled. Build using Xcode 26 or newer as
   required by Capacitor 8.
2. In Firebase console → Project settings → Cloud Messaging → Apple app,
   upload an APNs authentication key with its Key ID and Apple Team ID if one is
   not already configured. The supplied plist does not include this key. Keep the
   APNs private key in Firebase; it does not belong in the app bundle.
3. Ensure the hosted backend has FCM enabled and valid server credentials for
   project `linolife`. Client JSON/plist files do not configure server sending.
4. Install the new app, sign in, tap Enable notifications, allow permission and
   send a test from the notification inbox. Verify foreground, background and
   closed-app delivery and tap navigation. Repeat on Android 13+ and iOS.
5. Disable notifications or sign out and verify the account/device link is
   removed before trying another account on the same phone.

Run `npm test` for the native notification adapter tests. These verify the bridge
contract with mocked native plugins, not FCM/APNs delivery on a phone.

References: [Capacitor Push Notifications](https://capacitorjs.com/docs/apis/push-notifications),
[Firebase Apple setup and APNs key](https://firebase.google.com/docs/cloud-messaging/ios/client).
