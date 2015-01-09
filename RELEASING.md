Release Process
===============

 1. Update the `app/build.gradle` versions to whatever you feel conveys the sheer awesomeness of
    the update to which you are about to bestow on the masses.

 2. Update the `CHANGELOG.md` with the public and internal changes.

 3. Commit. Push.

 4. Run `./release.sh /path/to/your.keystore`. Enter your password when prompted.

 5. Install the release APK.

 6. Launch the app. Long press and release the "Launch" button 5 times. Ensure that the app crashed
    and that the exception was reported to Bugsnag.

 7. :shipit:
