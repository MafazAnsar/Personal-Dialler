# Personal Dialler v0.7

Floating dots + private phone app. No internet permission, nothing leaves the phone.

## Build
Push to GitHub (branch main). Actions > "Build APK" > download the artifact, install app-debug.apk.
The first build step checks that no Kotlin file was cut off during upload.
The app is signed with the key in app/debug.keystore, so later updates install over the old version.
(The first install of v0.5 needs the old version uninstalled once, because the signing key changed.)

## Panel
- Opens on Frequent (top 4 of the last 250 calls, merged per contact), then Recent and Contacts. Swipe left/right anywhere to switch.
- Drag a contact onto an icon. Icons and their order are chosen in the app: call, SMS, WhatsApp, Telegram, Signal, Viber, email, call history, remind me, note, block.
- Tap a contact to select it, then tap an icon to use it. With nothing selected, tapping an icon opens that app. Dragging still works.
- Double-tap repeats the last action used with that person.
- Search covers contacts and recents. Keypad button opens a keypad on the lower half. Sort button flips the list.
- Toggles: flashlight, Bluetooth, Wi-Fi, mobile data panel, Do Not Disturb, sound/vibrate.

## Calls
- Dual SIM: ask every time, SIM 1, SIM 2 or phone default.
- Incoming calls: swipe the handle right to answer or left to decline (taps do nothing). The End button is pinned at the bottom.
- Call screen: audio output picker, hold, add call, swap and merge for call waiting, notes, block.
- Decline with a quick reply SMS (edit the replies in the app). Volume key silences the ringer.
- Block list, plus optional rules for numbers not in contacts and hidden numbers.
- Popup after each call: call back, message, note, block, remind me later.

## Support
Back up and restore notes, blocks and settings from the app. "Share last error report" sends the last crash details.
