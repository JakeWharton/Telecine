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
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
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
import static android.os.Environment.DIRECTORY_PICTURES;

final class RecordingSession {
  private static final String DISPLAY_NAME = "telecine";
  private static final int NOTIFICATION_ID = 522592;
  private static final String MIME_TYPE = "video/mp4";

  public static RecordingSession create(Context context, Listener listener, int resultCode,
      Intent data, boolean showCountdown) {
    return new RecordingSession(context, listener, resultCode, data, showCountdown);
  }

  interface Listener {
    void onEnd();
  }

  private final Handler mainThread = new Handler(Looper.getMainLooper());

  private final Context context;
  private final Listener listener;
  private final int resultCode;
  private final Intent data;

  private final File outputRoot;
  private final DateFormat fileFormat =
      new SimpleDateFormat("'Telecine_'yyyy-MM-dd-HH-mm-ss'.mp4'");

  private final int displayHeight;
  private final int displayWidth;
  private final int displayDpi;

  private final NotificationManager notificationManager;
  private final WindowManager windowManager;
  private final MediaProjectionManager projectionManager;

  private final OverlayView overlayView;
  private final OverlayView.Listener overlayListener = new OverlayView.Listener() {
    @Override public void onCancel() {
      cancelRecording();
    }

    @Override public void onStart() {
      startRecording();
    }

    @Override public void onStop() {
      stopRecording();
    }
  };

  private MediaRecorder recorder;
  private MediaProjection projection;
  private VirtualDisplay display;
  private String outputPath;
  private File gifFile;
  private boolean running;

  RecordingSession(Context context, Listener listener, int resultCode, Intent data,
      boolean showCountDown) {
    this.context = context;
    this.listener = listener;
    this.resultCode = resultCode;
    this.data = data;

    File picturesDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES);
    outputRoot = new File(picturesDir, "Telecine");

    gifFile = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES), "temp.gif");
    Timber.d("GIF GIF GIF: %s", gifFile.getAbsoluteFile());

    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    displayHeight = displayMetrics.heightPixels;
    displayWidth = displayMetrics.widthPixels;
    displayDpi = displayMetrics.densityDpi;

    notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
    windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
    projectionManager = (MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE);

    overlayView = OverlayView.create(context, overlayListener, showCountDown);
  }

  public void showOverlay() {
    Timber.d("Adding overlay view to window.");
    windowManager.addView(overlayView, OverlayView.createLayoutParams(context));
  }

  private void hideOverlay() {
    Timber.d("Removing overlay view from window.");
    windowManager.removeView(overlayView);
  }

  private void cancelRecording() {
    hideOverlay();
    listener.onEnd();
  }

  private void startRecording() {
    Timber.d("Starting screen recording...");

    if (!outputRoot.mkdirs()) {
      Timber.e("Unable to create output directory '%s'.", outputRoot.getAbsolutePath());
      // We're probably about to crash, but at least the log will indicate as to why.
    }

    recorder = new MediaRecorder();
    recorder.setVideoSource(SURFACE);
    recorder.setOutputFormat(MPEG_4);
    recorder.setVideoEncoder(H264);
    recorder.setVideoSize(displayWidth, displayHeight);
    recorder.setVideoEncodingBitRate(8 * 1000 * 1000);

    String outputName = fileFormat.format(new Date());
    outputPath = new File(outputRoot, outputName).getAbsolutePath();
    Timber.i("Output file '%s'.", outputPath);
    recorder.setOutputFile(outputPath);

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

    Timber.d("Screen recording started.");
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

    recorder.release();
    display.release();

    Timber.d("Screen recording stopped.");

    final File cacheDir = new File(context.getCacheDir(), UUID.randomUUID().toString());
    cacheDir.mkdirs();
    new Thread(new Runnable() {
      @Override public void run() {
        Mp4ToGifConverter.convert(new File(outputPath), gifFile, displayWidth, displayHeight);
        Timber.d("GIF DONE");
      }
    }).start();

    Timber.d("Notifying media scanner of new video.");

    MediaScannerConnection.scanFile(context, new String[] { outputPath }, null,
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

    String title = "Screen recording captured.";
    String subtitle = "Touch to view your screen recording.";
    Notification.Builder builder = new Notification.Builder(context) //
        .setContentTitle(title)
        .setContentText(subtitle)
        .setWhen(System.currentTimeMillis())
        .setShowWhen(true)
        .setSmallIcon(R.drawable.ic_launcher)
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
      width = height;
    } else {
      y = (height - width) / 2;
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
