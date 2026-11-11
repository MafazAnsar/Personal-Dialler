# Personal Dialler v0.2

Floating dots + private phone app. No internet permission, nothing leaves the phone.

## Build
Push to GitHub (branch main). Actions > "Build APK" > download the artifact, install app-debug.apk.
The first step of the build checks that no Kotlin file was cut off during upload.

## First run
Open the app and go through steps 1-5. Step 4 makes it your Phone app, step 5 turns on caller ID and blocking.
Then tap "Start floating dots".

## Features
- Dots: swipe in, drag a contact or favourite onto Call / SMS / WhatsApp / Telegram / Email / Note / Block
- Panel tabs: Recent (type, time, duration, missed in red), Contacts, Keypad; favourites strip on top
- Phone app: incoming and in-call screens, mute, speaker, hold, keypad tones, notes, block
- Notes and a block list stored only on the phone; popup after each call
- Settings: dark or light, accent colour, dot size and opacity, panel width, side
