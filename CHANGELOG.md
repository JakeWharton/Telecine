Change Log
==========

1.3.0 *(2015-07-14)*
--------------------

Public:

 * Option for enabling "Show Touches" feature which places white dots on the recording for screen
   interactions.
 * Translations for FR, PT, ZH.


Internal:

 * Alter height on M preview to be 24dp to match new status bar height.
 * Correct log when unable to create output dir to actually log when unable.


1.2.0 *(2015-02-24)*
--------------------

Public:

 * Option for a running notification while recording. This prevents the recording process from
   being killed by Android.
 * Translations for IT, JA, TR.
 * Fix: MediaRecorder prepare crash on most devices.


1.1.0 *(2015-01-18)*
--------------------

Public:

 * Replaced the 1x1 widget with a shortcut.
 * Support RTL.
 * Translations for DE, PL.
 * Fix: MediaRecorder prepare crash on some devices, not all.


1.0.0 *(2015-01-08)*
--------------------

ZOMG 1.0!

Internal:

 * Housekeeping for open sourcing.
   * Move Bugsnag key to `BuildConfig`.
   * Regenerated Bugsnag key!


0.4.1 *(2014-11-11)*
--------------------

Public:

 * New icon!

Internal:

 * Fix widget launching in the same task as the activity.
 * Automatically finish widget activity if home is pressed.
 * Update Bugsnag to 2.2.3 to fix packaging problem with 2.2.2.


0.4.0 *(2014-11-06)*
--------------------

Public:

 * Add 1x1 widget to auto-launch recording overlay.


0.3.2 *(2014-11-04)*
--------------------

Public:

 * Fix: Crash when 50% video size resolution was selected.


0.3.1 *(2014-10-31)*
--------------------

Internal:

 * Add Google Analytics events.


0.3.0 *(2014-10-31)*
--------------------

Public:

 * Add preference for setting video size as a percentage (100%, 75%, 50%).

Internal:

 * Switch from Crashlytics to Bugsnag.


0.2.0 *(2014-10-25)*
--------------------

Public:

 * Add preference toggle for disabling the three second countdown.
 * Add preference toggle for removing the activity from the recents list.

Internal:

 * Remove separate processes for the activity and service.
 * Introduce Dagger for dependency management and injection.


0.1.0 *(2014-10-25)*
--------------------

First beta!
