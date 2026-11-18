# Lino Life native push notifications

All three apps use Capacitor 8.5.3, Push Notifications 8.1.3 and Firebase project `linolife-8901b`. Each app has separate Android and iOS registrations.

| App | Android application ID and iOS bundle ID | WebView entry |
| --- | --- | --- |
| Customer | `co.linolife.app` | https://linolife.co/capp |
| Partner | `co.linolife.papp` | https://linolife.co/papp |
| Admin | `co.linolife.aapp` | https://linolife.co/aapp |

## Firebase and Apple settings

- Keep the app-specific `google-services.json` in `android/app` and `GoogleService-Info.plist` in `ios/App/App`. Download replacements from the matching Firebase registration when needed. Service-account JSON and APNs private keys belong in server or provider configuration.
- Android uses the `linolife_updates` notification channel and requests notification permission on Android 13 and later when the signed-in user opts in. Use a device or emulator with Google Play services for FCM delivery tests.
- Enable **Push Notifications** for each of the three Apple Developer App IDs. In Xcode, open `ios/App/App.xcodeproj`, select the App target and choose the corresponding development team and provisioning profile under **Signing & Capabilities**. The project already includes Push Notifications, with development APNs entitlement for Debug and production entitlement for Release. Regenerate profiles after enabling the capability if needed.
- In Firebase **Project settings → Cloud Messaging**, configure APNs credentials for each iOS app. Upload the APNs authentication key with its Key ID and Apple Team ID, using a key authorized for the relevant bundle IDs and environments. Test a development build and a TestFlight or release build on real iPhones.
- The sender uses visible FCM notifications with routing data. Android background and terminated-state display is handled by the OS. A force-stopped Android app must be opened again before delivery can resume. iOS background and terminated-state alerts are handled by APNs. Test visible alerts and taps independently from background JavaScript execution.

## Backend deployment

Deploy the Laravel notification code together with its existing notification routes, shared Blade client, JavaScript and the push-device surface migration `2026_11_16_000001_add_push_device_surface.php`. The native apps can open the production URLs and use the older bridge contract while the site is on its previous version. The authenticated notification tap route and the updated role-aware browser client require this deployment.

Configure Laravel FCM for `linolife-8901b` with the server-only credential and the Firebase Web app and public VAPID settings for the same project. Run the following from the deployed Laravel root.

```sh
php artisan migrate --force
php artisan config:cache
php artisan notifications:check
```

With `FCM_DELIVERY_MODE=deferred`, the sender runs after the PHP response. If the deployment uses database delivery, restart the existing worker after the code and config update. Provider acceptance in the log confirms that Firebase accepted the request.

## Build and test

For each Android repo on Windows, run these commands from the app root.

```powershell
npm ci
npx cap sync android
Set-Location android
.\gradlew.bat :app:assembleDebug
```

For each iOS repo on a Mac with a compatible Xcode installation, run `npm ci` and `npx cap sync ios`, then open `ios/App/App.xcodeproj` and build to the corresponding signed device.

1. In each app, sign in as that app’s role. Open the notification inbox and choose **Enable notifications**. Verify the OS permission prompt and the enabled status on a clean installation.
2. Send a test update from the signed-in account’s inbox. Test with the app visible, backgrounded and terminated. Confirm one visible alert per installation. Tap an alert and check that the authenticated destination stays in the correct portal. Check the cold-start tap on both platforms and sign-in continuation after a session expires.
3. Confirm that the update appears in the same website and app inbox. Mark it read in one and verify the other shows the updated unread count.
4. Disable push or sign out. Confirm the installation no longer receives that account’s new updates. Sign in as a different account for the same role, enable push and verify that only the new account’s updates arrive. Reinstall a test build to exercise replacement of its device registration.
5. Open each WebView URL in a normal browser, sign in and enable browser notifications. Verify Firebase Web Push and the shared inbox there separately from the native app.

[Capacitor Push Notifications](https://capacitorjs.com/docs/apis/push-notifications) · [Firebase APNs setup](https://firebase.google.com/docs/cloud-messaging/ios/get-started)
