# Personal Dialler (v0.1) - floating dots

Drupe-style floating dots. Swipe inward, hold a contact, drag it onto Call / SMS / WhatsApp / Telegram / Email.
No internet permission, no accounts, nothing leaves the phone.

## Get the APK (no Android Studio needed)
1. Create a new private repo on GitHub.
2. Push this folder:
   git init && git add . && git commit -m "first" && git branch -M main
   git remote add origin <your-repo-url> && git push -u origin main
3. On GitHub: Actions > "Build APK" > latest run > Artifacts > download "PersonalDialer-debug-apk".
4. Unzip, copy app-debug.apk to the phone, install (allow "install unknown apps").
5. Open the app, tick steps 1-3, tap "Start floating dots".

If the build fails, open the failed run, copy the red error lines and paste them back to Claude.

## Not in v0.1 yet
Default Phone role, keypad, in-call screen, caller identity from notes, block list, post-call popup.
