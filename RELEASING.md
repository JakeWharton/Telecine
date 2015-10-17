Release Process
===============

Setup
-----

Place the following in `~/.gradle/gradle.properties`:
```
TELECINE_BUGSNAG_KEY=<key>
TELECINE_ANALYTICS_KEY=<key>
```


Release
-------

 1. Update the `telecine/build.gradle` versions to whatever you feel conveys the sheer awesomeness
    of the update to which you are about to bestow on the masses.

 2. Update the `CHANGELOG.md` with the public and internal changes.

 3. Commit with `git commit -am "Prepare version X.Y.Z."` replacing 'X.Y.Z' with the version number.

 4. Run `./release.sh /path/to/your.keystore`. Enter your password when prompted.

 5. Create an annotated tag with `git tag -a X.Y.X -m "Version X.Y.Z"` replacing 'X.Y.Z' with the
    version number.

 6. Push. Push tags (`git push --tags`).

 7. Install the release APK.

 8. Launch the app. Long press and release the "Launch" button 5 times. Ensure that the app crashed
    and that the exception was reported to Bugsnag.

 9. :shipit:
