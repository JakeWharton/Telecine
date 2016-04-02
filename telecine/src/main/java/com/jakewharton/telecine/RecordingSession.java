package com.jakewharton.telecine;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.display.VirtualDisplay;
import android.media.CamcorderProfile;
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

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
import static android.media.MediaRecorder.OutputFormat.MPEG_4;
import static android.media.MediaRecorder.VideoEncoder.H264;
import static android.media.MediaRecorder.VideoSource.SURFACE;
import static android.os.Environment.DIRECTORY_MOVIES;

final class RecordingSession {
  static final int NOTIFICATION_ID = 522592;

  private static final String DISPLAY_NAME = "telecine";
  private static final String MIME_TYPE = "video/mp4";

  interface Listener {
    /** Invoked immediately prior to the start of recording. */
    void onStart();

    /** Invoked immediately after the end of recording. */
    void onStop();

    /** Invoked after all work for this session has completed. */
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
        .setCategory(Analytics.CATEGORY_RECORDING)
        .setAction(Analytics.ACTION_OVERLAY_SHOW)
        .build());
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

  private RecordingInfo getRecordingInfo() {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
    wm.getDefaultDisplay().getRealMetrics(displayMetrics);
    int displayWidth = displayMetrics.widthPixels;
    int displayHeight = displayMetrics.heightPixels;
    int displayDensity = displayMetrics.densityDpi;
    Timber.d("Display size: %s x %s @ %s", displayWidth, displayHeight, displayDensity);

    Configuration configuration = context.getResources().getConfiguration();
    boolean isLandscape = configuration.orientation == ORIENTATION_LANDSCAPE;
    Timber.d("Display landscape: %s", isLandscape);

    // Get the best camera profile available. We assume MediaRecorder supports the highest.
    CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
    int cameraWidth = camcorderProfile != null ? camcorderProfile.videoFrameWidth : -1;
    int cameraHeight = camcorderProfile != null ? camcorderProfile.videoFrameHeight : -1;
    int cameraFrameRate = camcorderProfile != null ? camcorderProfile.videoFrameRate : 30;
    Timber.d("Camera size: %s x %s framerate: %s", cameraWidth, cameraHeight, cameraFrameRate);

    int sizePercentage = videoSizePercentage.get();
    Timber.d("Size percentage: %s", sizePercentage);

    return calculateRecordingInfo(displayWidth, displayHeight, displayDensity, isLandscape,
        cameraWidth, cameraHeight, cameraFrameRate, sizePercentage);
  }

  private void startRecording() {
    Timber.d("Starting screen recording...");

    if (!outputRoot.mkdirs()) {
      Timber.e("Unable to create output directory '%s'.", outputRoot.getAbsolutePath());
      // We're probably about to crash, but at least the log will indicate as to why.
    }

    RecordingInfo recordingInfo = getRecordingInfo();
    Timber.d("Recording: %s x %s @ %s", recordingInfo.width, recordingInfo.height,
        recordingInfo.density);

    recorder = new MediaRecorder();
    recorder.setVideoSource(SURFACE);
    recorder.setOutputFormat(MPEG_4);
    recorder.setVideoFrameRate(recordingInfo.frameRate);
    recorder.setVideoEncoder(H264);
    recorder.setVideoSize(recordingInfo.width, recordingInfo.height);
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
    display =
        projection.createVirtualDisplay(DISPLAY_NAME, recordingInfo.width, recordingInfo.height,
            recordingInfo.density, VIRTUAL_DISPLAY_FLAG_PRESENTATION, surface, null, null);

    recorder.start();
    running = true;
    recordingStartNanos = System.nanoTime();
    listener.onStart();

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

    boolean propagate = false;
    try {
      // Stop the projection in order to flush everything to the recorder.
      projection.stop();
      // Stop the recorder which writes the contents to the file.
      recorder.stop();

      propagate = true;
    } finally {
      try {
        // Ensure the listener can tear down its resources regardless if stopping crashes.
        listener.onStop();
      } catch (RuntimeException e) {
        if (propagate) {
          throw e; // Only allow listener exceptions to propagate if stopped successfully.
        }
      }
    }

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
    PendingIntent pendingViewIntent =
        PendingIntent.getActivity(context, 0, viewIntent, FLAG_CANCEL_CURRENT);

    Intent shareIntent = new Intent(ACTION_SEND);
    shareIntent.setType(MIME_TYPE);
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
    shareIntent = Intent.createChooser(shareIntent, null);
    PendingIntent pendingShareIntent =
        PendingIntent.getActivity(context, 0, shareIntent, FLAG_CANCEL_CURRENT);

    Intent deleteIntent = new Intent(context, DeleteRecordingBroadcastReceiver.class);
    deleteIntent.setData(uri);
    PendingIntent pendingDeleteIntent =
        PendingIntent.getBroadcast(context, 0, deleteIntent, FLAG_CANCEL_CURRENT);

    CharSequence title = context.getText(R.string.notification_captured_title);
    CharSequence subtitle = context.getText(R.string.notification_captured_subtitle);
    CharSequence share = context.getText(R.string.notification_captured_share);
    CharSequence delete = context.getText(R.string.notification_captured_delete);
    Notification.Builder builder = new Notification.Builder(context) //
        .setContentTitle(title)
        .setContentText(subtitle)
        .setWhen(System.currentTimeMillis())
        .setShowWhen(true)
        .setSmallIcon(R.drawable.ic_videocam_white_24dp)
        .setColor(context.getResources().getColor(R.color.primary_normal))
        .setContentIntent(pendingViewIntent)
        .setAutoCancel(true)
        .addAction(R.drawable.ic_share_white_24dp, share, pendingShareIntent)
        .addAction(R.drawable.ic_delete_white_24dp, delete, pendingDeleteIntent);

    if (bitmap != null) {
      builder.setLargeIcon(createSquareBitmap(bitmap))
          .setStyle(new Notification.BigPictureStyle() //
              .setBigContentTitle(title) //
              .setSummaryText(subtitle) //
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

  static RecordingInfo calculateRecordingInfo(int displayWidth, int displayHeight,
      int displayDensity, boolean isLandscapeDevice, int cameraWidth, int cameraHeight,
      int cameraFrameRate, int sizePercentage) {
    // Scale the display size before any maximum size calculations.
    displayWidth = displayWidth * sizePercentage / 100;
    displayHeight = displayHeight * sizePercentage / 100;

    if (cameraWidth == -1 && cameraHeight == -1) {
      // No cameras. Fall back to the display size.
      return new RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity);
    }

    int frameWidth = isLandscapeDevice ? cameraWidth : cameraHeight;
    int frameHeight = isLandscapeDevice ? cameraHeight : cameraWidth;
    if (frameWidth >= displayWidth && frameHeight >= displayHeight) {
      // Frame can hold the entire display. Use exact values.
      return new RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity);
    }

    // Calculate new width or height to preserve aspect ratio.
    if (isLandscapeDevice) {
      frameWidth = displayWidth * frameHeight / displayHeight;
    } else {
      frameHeight = displayHeight * frameWidth / displayWidth;
    }
    return new RecordingInfo(frameWidth, frameHeight, cameraFrameRate, displayDensity);
  }

  static final class RecordingInfo {
    final int width;
    final int height;
    final int frameRate;
    final int density;

    RecordingInfo(int width, int height, int frameRate, int density) {
      this.width = width;
      this.height = height;
      this.frameRate = frameRate;
      this.density = density;
    }
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

  public static final class DeleteRecordingBroadcastReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
      NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
      notificationManager.cancel(NOTIFICATION_ID);
      final Uri uri = intent.getData();
      final ContentResolver contentResolver = context.getContentResolver();
      new AsyncTask<Void, Void, Void>() {
        @Override protected Void doInBackground(@NonNull Void... none) {
          int rowsDeleted = contentResolver.delete(uri, null, null);
          if (rowsDeleted == 1) {
            Timber.i("Deleted recording.");
          } else {
            Timber.e("Error deleting recording.");
          }
          return null;
        }
      }.execute();
    }
  }
}
