package com.jakewharton.telecine;

import com.google.android.gms.analytics.Tracker;
import java.util.Map;

interface Analytics {
  String CATEGORY_SETTINGS = "Settings";
  String CATEGORY_RECORDING = "Recording";
  String CATEGORY_SHORTCUT = "Shortcut";

  String ACTION_CAPTURE_INTENT_LAUNCH = "Launch Overlay Launch";
  String ACTION_CAPTURE_INTENT_RESULT = "Launch Overlay Result";
  String ACTION_CHANGE_VIDEO_SIZE = "Change Video Size";
  String ACTION_CHANGE_SHOW_COUNTDOWN = "Show Countdown";
  String ACTION_CHANGE_HIDE_RECENTS = "Hide In Recents";
  String ACTION_CHANGE_RECORDING_NOTIFICATION = "Recording Notification";
  String ACTION_CHANGE_SHOW_TOUCHES = "Show Touches";
  String ACTION_OVERLAY_SHOW = "Overlay Show";
  String ACTION_OVERLAY_HIDE = "Overlay Hide";
  String ACTION_OVERLAY_CANCEL = "Overlay Cancel";
  String ACTION_RECORDING_START = "Recording Start";
  String ACTION_RECORDING_STOP = "Recording Stop";
  String ACTION_SHORTCUT_ADDED = "Shortcut Added";
  String ACTION_SHORTCUT_LAUNCHED = "Shortcut Launched";

  String VARIABLE_RECORDING_LENGTH = "Recording Length";

  /** @see {@link Tracker#send(Map)} for usage. */
  void send(Map<String, String> params);

  class GoogleAnalytics implements Analytics {
    private final Tracker tracker;

    public GoogleAnalytics(Tracker tracker) {
      this.tracker = tracker;
    }

    @Override public void send(Map<String, String> params) {
      tracker.send(params);
    }
  }
}
