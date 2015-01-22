package com.jakewharton.telecine;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.display.VirtualDisplay;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowManager;
import com.google.android.gms.analytics.HitBuilders;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.inject.Provider;
import timber.log.Timber;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
import static android.media.MediaRecorder.OutputFormat.MPEG_4;
import static android.media.MediaRecorder.VideoEncoder.H264;
import static android.media.MediaRecorder.VideoSource.SURFACE;
import static android.os.Environment.DIRECTORY_MOVIES;

final class RecordingSession {
  private static final String DISPLAY_NAME = "telecine";
  private static final int NOTIFICATION_ID = 522592;
  private static final String MIME_TYPE = "video/mp4";

  interface Listener {
    void onEnd();
  }

  private final Handler mainThread = new Handler(Looper.getMainLooper());

  private final Context context;
  private final Listener listener;
  private final int resultCode;
  private final Intent data;

  private final Analytics analytics;
  private final Provider<Boolean> showCountDown;
  private final Provider<Integer> videoSizePercentage;

  private final File outputRoot;
  private final DateFormat fileFormat =
      new SimpleDateFormat("'Telecine_'yyyy-MM-dd-HH-mm-ss'.mp4'", Locale.US);

  private final NotificationManager notificationManager;
  private final WindowManager windowManager;
  private final MediaProjectionManager projectionManager;

  private OverlayView overlayView;
  private MediaRecorder recorder;
  private MediaProjection projection;
  private VirtualDisplay display;
  private String outputFile;
  private boolean running;
  private long recordingStartNanos;

  RecordingSession(Context context, Listener listener, int resultCode, Intent data,
      Analytics analytics, Provider<Boolean> showCountDown, Provider<Integer> videoSizePercentage) {
    this.context = context;
    this.listener = listener;
    this.resultCode = resultCode;
    this.data = data;
    this.analytics = analytics;

    this.showCountDown = showCountDown;
    this.videoSizePercentage = videoSizePercentage;

    File picturesDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES);
    outputRoot = new File(picturesDir, "Telecine");

    notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
    windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
    projectionManager = (MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE);
  }

  public void showOverlay() {
    Timber.d("Adding overlay view to window.");

    OverlayView.Listener overlayListener = new OverlayView.Listener() {
      @Override public void onCancel() {
        cancelOverlay();
      }

      @Override public void onStart() {
        startRecording();
      }

      @Override public void onStop() {
        stopRecording();
      }
    };
    overlayView = OverlayView.create(context, overlayListener, showCountDown.get());
    windowManager.addView(overlayView, OverlayView.createLayoutParams(context));

    analytics.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_RECORDING).setAction(Analytics.ACTION_OVERLAY_SHOW).build());
  }

  private void hideOverlay() {
    if (overlayView != null) {
      Timber.d("Removing overlay view from window.");
      windowManager.removeView(overlayView);
      overlayView = null;

      analytics.send(new HitBuilders.EventBuilder() //
          .setCategory(Analytics.CATEGORY_RECORDING)
          .setAction(Analytics.ACTION_OVERLAY_HIDE)
          .build());
    }
  }

  private void cancelOverlay() {
    hideOverlay();
    listener.onEnd();

    analytics.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_RECORDING)
        .setAction(Analytics.ACTION_OVERLAY_CANCEL)
        .build());
  }

  private void startRecording() {
    Timber.d("Starting screen recording...");

    if (outputRoot.mkdirs()) {
      Timber.e("Unable to create output directory '%s'.", outputRoot.getAbsolutePath());
      // We're probably about to crash, but at least the log will indicate as to why.
    }

    int sizePercentage = videoSizePercentage.get();
    Timber.d("Video size: %s%%", sizePercentage);

    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    int displayHeight = displayMetrics.heightPixels * sizePercentage / 100;
    int displayWidth = displayMetrics.widthPixels * sizePercentage / 100;
    int displayDpi = displayMetrics.densityDpi;

    recorder = new MediaRecorder();
    recorder.setVideoSource(SURFACE);
    recorder.setOutputFormat(MPEG_4);
    recorder.setVideoFrameRate(30);
    recorder.setVideoEncoder(H264);
    recorder.setVideoSize(displayWidth, displayHeight);
    recorder.setVideoEncodingBitRate(8 * 1000 * 1000);

    String outputName = fileFormat.format(new Date());
    outputFile = new File(outputRoot, outputName).getAbsolutePath();
    Timber.i("Output file '%s'.", outputFile);
    recorder.setOutputFile(outputFile);

    try {
      recorder.prepare();
    } catch (IOException e) {
      throw new RuntimeException("Unable to prepare MediaRecorder.", e);
    }

    projection = projectionManager.getMediaProjection(resultCode, data);

    Surface surface = recorder.getSurface();
    display = projection.createVirtualDisplay(DISPLAY_NAME, displayWidth, displayHeight, displayDpi,
        VIRTUAL_DISPLAY_FLAG_PRESENTATION, surface, null, null);

    recorder.start();
    running = true;
    recordingStartNanos = System.nanoTime();

    Timber.d("Screen recording started.");

    analytics.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_RECORDING)
        .setAction(Analytics.ACTION_RECORDING_START)
        .build());
  }

  private void stopRecording() {
    Timber.d("Stopping screen recording...");

    if (!running) {
      throw new IllegalStateException("Not running.");
    }
    running = false;

    hideOverlay();

    // Stop the projection in order to flush everything to the recorder.
    projection.stop();

    // Stop the recorder which writes the contents to the file.
    recorder.stop();

    long recordingStopNanos = System.nanoTime();

    recorder.release();
    display.release();

    analytics.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_RECORDING)
        .setAction(Analytics.ACTION_RECORDING_STOP)
        .build());
    analytics.send(new HitBuilders.TimingBuilder() //
        .setCategory(Analytics.CATEGORY_RECORDING)
        .setValue(TimeUnit.NANOSECONDS.toMillis(recordingStopNanos - recordingStartNanos))
        .setVariable(Analytics.VARIABLE_RECORDING_LENGTH)
        .build());

    Timber.d("Screen recording stopped. Notifying media scanner of new video.");

    MediaScannerConnection.scanFile(context, new String[] { outputFile }, null,
        new MediaScannerConnection.OnScanCompletedListener() {
          @Override public void onScanCompleted(String path, final Uri uri) {
            Timber.d("Media scanner completed.");
            mainThread.post(new Runnable() {
              @Override public void run() {
                showNotification(uri, null);
              }
            });
          }
        });
  }

  private void showNotification(final Uri uri, Bitmap bitmap) {
    Intent viewIntent = new Intent(ACTION_VIEW, uri);
    PendingIntent pendingViewIntent = PendingIntent.getActivity(context, 0, viewIntent, 0);

    Intent shareIntent = new Intent(ACTION_SEND);
    shareIntent.setType(MIME_TYPE);
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
    PendingIntent pendingShareIntent = PendingIntent.getActivity(context, 0, shareIntent, 0);

    String title = context.getString(R.string.notification_title);
    String subtitle = context.getString(R.string.notification_subtitle);
    Notification.Builder builder = new Notification.Builder(context) //
        .setContentTitle(title)
        .setContentText(subtitle)
        .setWhen(System.currentTimeMillis())
        .setShowWhen(true)
        .setSmallIcon(R.drawable.ic_videocam_white_24dp)
        .setColor(context.getResources().getColor(R.color.primary_normal))
        .setContentIntent(pendingViewIntent)
        .setAutoCancel(true)
        .addAction(R.drawable.ic_share_white_24dp, "Share", pendingShareIntent);

    if (bitmap != null) {
      builder.setLargeIcon(createSquareBitmap(bitmap)) //
          .setStyle(new Notification.BigPictureStyle() //
              .setBigContentTitle(title)
              .setSummaryText(subtitle)
              .bigPicture(bitmap));
    }

    notificationManager.notify(NOTIFICATION_ID, builder.build());

    if (bitmap != null) {
      listener.onEnd();
      return;
    }

    new AsyncTask<Void, Void, Bitmap>() {
      @Override protected Bitmap doInBackground(@NonNull Void... none) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, uri);
        return retriever.getFrameAtTime();
      }

      @Override protected void onPostExecute(@Nullable Bitmap bitmap) {
        if (bitmap != null) {
          showNotification(uri, bitmap);
        } else {
          listener.onEnd();
        }
      }
    }.execute();
  }

  private static Bitmap createSquareBitmap(Bitmap bitmap) {
    int x = 0;
    int y = 0;
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    if (width > height) {
      x = (width - height) / 2;
      //noinspection SuspiciousNameCombination
      width = height;
    } else {
      y = (height - width) / 2;
      //noinspection SuspiciousNameCombination
      height = width;
    }
    return Bitmap.createBitmap(bitmap, x, y, width, height, null, true);
  }

  public void destroy() {
    if (running) {
      Timber.w("Destroyed while running!");
      stopRecording();
    }
  }
}
