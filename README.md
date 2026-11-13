# Personal Dialler v0.3

Floating dots + private phone app. No internet permission, nothing leaves the phone.

## Build
Push to GitHub (branch main). Actions > "Build APK" > download the artifact, install app-debug.apk.
The first build step checks that no Kotlin file was cut off during upload.

## Panel
- Drag a contact, recent or favourite onto an icon: call, SMS, WhatsApp, Telegram, email, note, block
- Double-tap a contact or recent to repeat the last action used with them (call if none)
- Search covers contacts and recents together
- Keypad button (next to search) opens a keypad on the lower half; typed digits filter the list
- Sort button flips the list so the newest or A entries sit at the bottom
- Toggle icons: flashlight, Bluetooth, Wi-Fi, sound/vibrate

## Settings
Dark or light, accent colour, square or rounded buttons, keypad opacity, dot size and opacity, panel width, side.
