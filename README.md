# Personal Dialler v0.10

Floating dots + private phone app. No internet permission, nothing leaves the phone.

## Build
Push to GitHub (branch main). Actions > "Build APK" > download the artifact, install app-debug.apk.
The build first checks that no Kotlin file was cut off during upload, then runs the unit tests.

## Signing key (one-time setup)
The signing key is no longer stored in the repository. The build reads it from four repository secrets
(GitHub > Settings > Secrets and variables > Actions > New repository secret):

| Secret | Value |
|---|---|
| RELEASE_KEYSTORE | the keystore file as one line of base64 |
| KEYSTORE_PASSWORD | the keystore password |
| KEY_ALIAS | the key alias |
| KEY_PASSWORD | the key password |

The build stops with a clear message if RELEASE_KEYSTORE is missing.
Also delete app/debug.keystore from your repository (it is now ignored by git).

Pick one of two routes:
- **Keep updating without uninstalling (v0.8 and v0.9 keys).** Base64-encode the keystore you already use
  (alias `androiddebugkey`, both passwords `android`) and store it with those values. The APK is signed
  with the same key as before, so it installs over your current version. Note that this key and its
  password are still in the repository history, so this route does not make signing safer.
- **Rotate to a private key (recommended).** Create a new keystore, for example
  `keytool -genkeypair -keystore dialler.keystore -alias dialler -keyalg RSA -keysize 2048 -validity 36500`,
  then base64 it (`base64 -w0 dialler.keystore`, or in PowerShell
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("dialler.keystore"))`) and store it with your passwords.
  Android treats this as a different app signer, so **v0.10 needs one uninstall of the old version first**
  (the same one-time step as the earlier key change). Back up notes and blocks from the app before uninstalling.
  Keep a safe copy of the new keystore: without it you cannot publish updates that install over this one.

## Panel
- Opens on Frequent (top 4 of the last 250 calls, merged per contact), then Recent and Contacts. Swipe left/right anywhere to switch.
- Drag a contact onto an icon. Icons and their order are chosen in the app: call, SMS, WhatsApp, Telegram, Signal, Viber, email, call history, remind me, note, block.
- Tap a contact to select it, then tap an icon to use it. With nothing selected, tapping an icon opens that app. Dragging still works.
- Long-press a contact or number and let go without dragging for a compact themed menu next to it: save to contacts, add/remove favourite, call history, note, remind, block, copy. The Save to contacts and Favourite icons do the same by drag or tap.
- Double-tap repeats the last action used with that person.
- Search covers contacts and recents. Keypad button opens a keypad on the lower half. Sort button flips the list.
- Toggles: flashlight, Bluetooth, Wi-Fi, mobile data panel, Do Not Disturb, sound/vibrate.

- Pressing Home or Recents closes the panel, like Back.

## Calls
- Dual SIM: ask every time, SIM 1, SIM 2 or phone default.
- Incoming calls: swipe the handle right to answer or left to decline (taps do nothing). The End button is pinned at the bottom.
- Call screen: audio output picker, hold, add call, swap and merge for call waiting, notes, block.
- Decline with a quick reply SMS (edit the replies in the app). Volume key silences the ringer.
- Block list, plus optional rules for numbers not in contacts and hidden numbers.
- Popup after each call: call back, message, note, block, remind me later.

## Matching numbers
Notes, blocks, favourites and last actions identify a person by the last N digits of the number (N is 9 by default and can be set from 7 to 12 in the app, under the country code). Numbers are normalised with Android's PhoneNumberUtils first, so `+94 77 555 1234`, `077-5551234` and `(077) 555 1234` are the same person.

## Support
Back up and restore notes, blocks and settings from the app. "Share last error report" sends the last crash details.
