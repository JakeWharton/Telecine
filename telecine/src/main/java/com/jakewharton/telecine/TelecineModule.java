package com.jakewharton.telecine;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import dagger.Module;
import dagger.Provides;
import timber.log.Timber;

import javax.inject.Singleton;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

@Module
final class TelecineModule {
  private static final String PREFERENCES_NAME = "telecine";
  private static final boolean DEFAULT_SHOW_COUNTDOWN = true;
  private static final boolean DEFAULT_HIDE_FROM_RECENTS = false;
  private static final boolean DEFAULT_SHOW_TOUCHES = false;
  private static final boolean DEFAULT_USE_DEMO_MODE = false;
  private static final boolean DEFAULT_RECORDING_NOTIFICATION = false;
  private static final int DEFAULT_VIDEO_SIZE_PERCENTAGE = 100;

  private final TelecineApplication app;

  TelecineModule(TelecineApplication app) {
    this.app = app;
  }

  @Provides @Singleton Analytics provideAnalytics() {
    if (BuildConfig.DEBUG) {
      return new Analytics() {
        @Override public void send(Map<String, String> params) {
          Timber.tag("Analytics").d(String.valueOf(params));
        }
      };
    }

    GoogleAnalytics googleAnalytics = GoogleAnalytics.getInstance(app);
    Tracker tracker = googleAnalytics.newTracker(BuildConfig.ANALYTICS_KEY);
    tracker.setSessionTimeout(300); // ms? s? better be s.
    return new Analytics.GoogleAnalytics(tracker);
  }

  @Provides @Singleton ContentResolver provideContentResolver() {
    return app.getContentResolver();
  }

  @Provides @Singleton SharedPreferences provideSharedPreferences() {
    return app.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
  }

  @Provides @Singleton @ShowCountdown static BooleanPreference provideShowCountdownPreference(
      SharedPreferences prefs) {
    return new BooleanPreference(prefs, "show-countdown", DEFAULT_SHOW_COUNTDOWN);
  }

  @Provides @ShowCountdown static Boolean provideShowCountdown(@ShowCountdown BooleanPreference pref) {
    return pref.get();
  }

  @Provides @Singleton @RecordingNotification
  static BooleanPreference provideRecordingNotificationPreference(SharedPreferences prefs) {
    return new BooleanPreference(prefs, "recording-notification", DEFAULT_RECORDING_NOTIFICATION);
  }

  @Provides @RecordingNotification static Boolean provideRecordingNotification(
      @RecordingNotification BooleanPreference pref) {
    return pref.get();
  }

  @Provides @Singleton @HideFromRecents static BooleanPreference provideHideFromRecentsPreference(
      SharedPreferences prefs) {
    return new BooleanPreference(prefs, "hide-from-recents", DEFAULT_HIDE_FROM_RECENTS);
  }

  @Provides @Singleton @ShowTouches static BooleanPreference provideShowTouchesPreference(
      SharedPreferences prefs) {
    return new BooleanPreference(prefs, "show-touches", DEFAULT_SHOW_TOUCHES);
  }

  @Provides @ShowTouches static Boolean provideShowTouches(@ShowTouches BooleanPreference pref) {
    return pref.get();
  }

  @Provides @Singleton @UseDemoMode static BooleanPreference provideUseDemoModePreference(
      SharedPreferences prefs) {
    return new BooleanPreference(prefs, "use-demo-mode", DEFAULT_USE_DEMO_MODE);
  }

  @Provides @UseDemoMode static Boolean provideUseDemoMode(@UseDemoMode BooleanPreference pref) {
    return pref.get();
  }

  @Provides @Singleton @VideoSizePercentage static IntPreference provideVideoSizePercentagePreference(
      SharedPreferences prefs) {
    return new IntPreference(prefs, "video-size", DEFAULT_VIDEO_SIZE_PERCENTAGE);
  }

  @Provides @VideoSizePercentage static Integer provideVideoSizePercentage(
      @VideoSizePercentage IntPreference pref) {
    return pref.get();
  }
}
