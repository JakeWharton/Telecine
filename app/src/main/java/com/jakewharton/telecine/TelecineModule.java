package com.jakewharton.telecine;

import android.content.SharedPreferences;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

import static android.content.Context.MODE_PRIVATE;

@Module(injects = {
    TelecineActivity.class, TelecineService.class
})
final class TelecineModule {
  private static final String PREFERENCES_NAME = "telecine";
  private static final boolean DEFAULT_SHOW_COUNTDOWN = true;
  private static final boolean DEFAULT_HIDE_FROM_RECENTS = false;
  private static final int DEFAULT_VIDEO_SIZE_PERCENTAGE = 100;

  private final TelecineApplication app;

  TelecineModule(TelecineApplication app) {
    this.app = app;
  }

  @Provides @Singleton SharedPreferences provideSharedPreferences() {
    return app.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
  }

  @Provides @Singleton @ShowCountdown BooleanPreference provideShowCountdownPreference(
      SharedPreferences prefs) {
    return new BooleanPreference(prefs, "show-countdown", DEFAULT_SHOW_COUNTDOWN);
  }

  @Provides @ShowCountdown Boolean provideShowCountdown(@ShowCountdown BooleanPreference pref) {
    return pref.get();
  }

  @Provides @Singleton @HideFromRecents BooleanPreference provideHideFromRecentsPreference(
      SharedPreferences prefs) {
    return new BooleanPreference(prefs, "hide-from-recents", DEFAULT_HIDE_FROM_RECENTS);
  }

  @Provides @Singleton @VideoSizePercentage IntPreference provideVideoSizePercentagePreference(
      SharedPreferences prefs) {
    return new IntPreference(prefs, "video-size", DEFAULT_VIDEO_SIZE_PERCENTAGE);
  }

  @Provides @VideoSizePercentage Integer provideVideoSizePercentage(
      @VideoSizePercentage IntPreference pref) {
    return pref.get();
  }
}
