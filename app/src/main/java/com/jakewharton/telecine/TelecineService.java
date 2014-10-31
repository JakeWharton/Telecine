package com.jakewharton.telecine;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import javax.inject.Inject;
import javax.inject.Provider;
import timber.log.Timber;

public final class TelecineService extends Service {
  private static final String EXTRA_RESULT_CODE = "result-code";
  private static final String EXTRA_DATA = "data";

  public static Intent newIntent(Context context, int resultCode, Intent data) {
    Intent intent = new Intent(context, TelecineService.class);
    intent.putExtra(EXTRA_RESULT_CODE, resultCode);
    intent.putExtra(EXTRA_DATA, data);
    return intent;
  }

  @Inject @ShowCountdown Provider<Boolean> showCountdownProvider;
  @Inject @VideoSizePercentage Provider<Integer> videoSizePercentageProvider;

  private boolean running;
  private RecordingSession recordingSession;

  private final RecordingSession.Listener listener = new RecordingSession.Listener() {
    @Override public void onEnd() {
      Timber.d("Shutting down.");
      stopSelf();
    }
  };

  @Override public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
    if (running) {
      Timber.d("Already running! Ignoring...");
      return START_NOT_STICKY;
    }
    Timber.d("Starting up!");
    running = true;

    int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
    Intent data = intent.getParcelableExtra(EXTRA_DATA);
    if (resultCode == 0 || data == null) {
      throw new IllegalStateException("Result code or data missing.");
    }

    ((TelecineApplication) getApplication()).inject(this);

    recordingSession = new RecordingSession(this, listener, resultCode, data, showCountdownProvider,
        videoSizePercentageProvider);
    recordingSession.showOverlay();

    return START_NOT_STICKY;
  }

  @Override public void onDestroy() {
    recordingSession.destroy();
    super.onDestroy();
  }

  @Override public IBinder onBind(@NonNull Intent intent) {
    throw new AssertionError("Not supported.");
  }
}
