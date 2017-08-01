package com.jakewharton.telecine;

import android.app.Notification;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import com.nightlynexus.demomode.BarsBuilder;
import com.nightlynexus.demomode.BatteryBuilder;
import com.nightlynexus.demomode.ClockBuilder;
import com.nightlynexus.demomode.DemoMode;
import com.nightlynexus.demomode.NetworkBuilder;
import com.nightlynexus.demomode.NotificationsBuilder;
import com.nightlynexus.demomode.SystemIconsBuilder;
import com.nightlynexus.demomode.WifiBuilder;
import dagger.android.AndroidInjection;
import javax.inject.Inject;
import javax.inject.Provider;
import timber.log.Timber;

import static android.app.Notification.PRIORITY_MIN;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

public final class TelecineService extends Service {
  private static final String EXTRA_RESULT_CODE = "result-code";
  private static final String EXTRA_DATA = "data";
  private static final int NOTIFICATION_ID = 99118822;
  private static final String SHOW_TOUCHES = "show_touches";

  static Intent newIntent(Context context, int resultCode, Intent data) {
    Intent intent = new Intent(context, TelecineService.class);
    intent.putExtra(EXTRA_RESULT_CODE, resultCode);
    intent.putExtra(EXTRA_DATA, data);
    return intent;
  }

  @Inject @ShowCountdown Provider<Boolean> showCountdownProvider;
  @Inject @VideoSizePercentage Provider<Integer> videoSizePercentageProvider;
  @Inject @RecordingNotification Provider<Boolean> recordingNotificationProvider;
  @Inject @ShowTouches Provider<Boolean> showTouchesProvider;
  @Inject @UseDemoMode Provider<Boolean> useDemoModeProvider;

  @Inject Analytics analytics;
  @Inject ContentResolver contentResolver;

  private boolean running;
  private RecordingSession recordingSession;

  private final RecordingSession.Listener listener = new RecordingSession.Listener() {
    private boolean showTouches;
    private boolean useDemoMode;

    @Override public void onPrepare() {
      showTouches = showTouchesProvider.get();
      useDemoMode = useDemoModeProvider.get();
      if (useDemoMode) {
        sendBroadcast(new BarsBuilder().mode(BarsBuilder.BarsMode.TRANSPARENT).build());
        sendBroadcast(new BatteryBuilder().level(100).plugged(FALSE).build());
        sendBroadcast(new ClockBuilder().setTimeInHoursAndMinutes("1200").build());
        sendBroadcast(new NetworkBuilder().airplane(FALSE)
            .carrierNetworkChange(FALSE)
            .mobile(TRUE, NetworkBuilder.Datatype.LTE, 0, 4)
            .nosim(FALSE)
            .build());
        sendBroadcast(new NotificationsBuilder().visible(FALSE).build());
        sendBroadcast(new SystemIconsBuilder().alarm(FALSE)
            .bluetooth(SystemIconsBuilder.BluetoothMode.HIDE)
            .cast(FALSE)
            .hotspot(FALSE)
            .location(FALSE)
            .mute(FALSE)
            .speakerphone(FALSE)
            .tty(FALSE)
            .vibrate(FALSE)
            .zen(SystemIconsBuilder.ZenMode.HIDE)
            .build());
        sendBroadcast(new WifiBuilder().fully(TRUE).wifi(TRUE, 4).build());
      }
    }

    @Override public void onStart() {
      if (showTouches) {
        Settings.System.putInt(contentResolver, SHOW_TOUCHES, 1);
      }

      if (!recordingNotificationProvider.get()) {
        return; // No running notification was requested.
      }

      Context context = getApplicationContext();
      String title = context.getString(R.string.notification_recording_title);
      String subtitle = context.getString(R.string.notification_recording_subtitle);
      Notification notification = new Notification.Builder(context) //
          .setContentTitle(title)
          .setContentText(subtitle)
          .setSmallIcon(R.drawable.ic_videocam_white_24dp)
          .setColor(ContextCompat.getColor(context, R.color.primary_normal))
          .setAutoCancel(true)
          .setPriority(PRIORITY_MIN)
          .build();

      Timber.d("Moving service into the foreground with recording notification.");
      startForeground(NOTIFICATION_ID, notification);
    }

    @Override public void onStop() {
      if (showTouches) {
        Settings.System.putInt(contentResolver, SHOW_TOUCHES, 0);
      }
      if (useDemoMode) {
        sendBroadcast(DemoMode.buildExit());
      }
    }

    @Override public void onEnd() {
      Timber.d("Shutting down.");
      stopSelf();
    }
  };

  @Override public void onCreate() {
    AndroidInjection.inject(this);
    super.onCreate();
  }

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

    recordingSession =
        new RecordingSession(this, listener, resultCode, data, analytics, showCountdownProvider,
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
