# Recording backup - how it works and how to set it up

There are two ways to back recordings up. Both are optional and off by default
(Recordings > gear > **Back up recordings**).

## 1. "Folder / File manager" (no setup at all)
Pick a folder in Android's own file picker.
- **A folder inside Google Drive**: in the picker open the menu (three lines) > **Drive**, make or open a folder, tap
  **Use this folder**. Ashu Phone copies each recording there and the Drive app uploads it.
- **A folder on the phone** (Internal storage): recordings are copied there and show up in any file manager.
  They do **not** go to Google Drive - the card shows a warning when you pick such a folder.

Tap **Test backup**: it writes a small test file and tells you where it went.

## 2. "Google account" (direct to Google Drive)
Tap **Sign in with Google** once. Recordings are uploaded straight into a folder named **Ashu Phone Recordings** in
your Drive, named with the contact and date/time. Ashu Phone only gets the narrow `drive.file` permission: it can see
and touch only the files it created, never the rest of your Drive.

### One-time developer setup (only needed once, by the person who builds the app)
The sign-in uses the Google Cloud project behind this app's Firebase project.
1. Open https://console.cloud.google.com and pick the SAME project as your Firebase project.
2. **APIs & Services > Library** > search **Google Drive API** > **Enable**.
3. **APIs & Services > OAuth consent screen**: add the scope `.../auth/drive.file` (it is a non-sensitive scope).
   While the consent screen is in "Testing", add your Google accounts as test users; to let everyone sign in, publish it.
4. Make sure your release signing key's **SHA-1** is registered for the Android app in Firebase (Project settings > Your apps).
If step 2 is missing, Test backup says: "The Google Drive API is not enabled for this app yet".

## Good to know
- With **Cloud only** on, a new recording waits in the app's PRIVATE storage (never in the Music folder) until the upload is
  complete, then it is removed from the phone. If the upload cannot finish, the recording stays.
- Uploads wait for Wi-Fi if "Wi-Fi only" is on (phone folders never need a network) and for charging if "Only while charging" is on.
  The card says exactly what it is waiting for.
- A background check runs every few hours and queues anything that was missed.
