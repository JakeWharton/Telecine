package com.jakewharton.telecine;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Spinner;
import android.widget.Switch;
import butterknife.Bind;
import butterknife.BindColor;
import butterknife.BindString;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import butterknife.OnLongClick;
import com.google.android.gms.analytics.HitBuilders;
import javax.inject.Inject;
import timber.log.Timber;

public final class TelecineActivity extends Activity {
  @Bind(R.id.spinner_video_size_percentage) Spinner videoSizePercentageView;
  @Bind(R.id.switch_show_countdown) Switch showCountdownView;
  @Bind(R.id.switch_hide_from_recents) Switch hideFromRecentsView;
  @Bind(R.id.switch_recording_notification) Switch recordingNotificationView;
  @Bind(R.id.switch_show_touches) Switch showTouchesView;

  @BindString(R.string.app_name) String appName;
  @BindColor(R.color.primary_normal) int primaryNormal;

  @Inject @VideoSizePercentage IntPreference videoSizePreference;
  @Inject @ShowCountdown BooleanPreference showCountdownPreference;
  @Inject @HideFromRecents BooleanPreference hideFromRecentsPreference;
  @Inject @RecordingNotification BooleanPreference recordingNotificationPreference;
  @Inject @ShowTouches BooleanPreference showTouchesPreference;

  @Inject Analytics analytics;

  private VideoSizePercentageAdapter videoSizePercentageAdapter;
  private int longClickCount;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ((TelecineApplication) getApplication()).inject(this);

    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);

    Resources res = getResources();
    Bitmap taskIcon = BitmapFactory.decodeResource(res, R.drawable.ic_videocam_white_48dp);
    setTaskDescription(new ActivityManager.TaskDescription(appName, taskIcon, primaryNormal));

    videoSizePercentageAdapter = new VideoSizePercentageAdapter(this);

    videoSizePercentageView.setAdapter(videoSizePercentageAdapter);
    videoSizePercentageView.setSelection(
        VideoSizePercentageAdapter.getSelectedPosition(videoSizePreference.get()));

    showCountdownView.setChecked(showCountdownPreference.get());
    hideFromRecentsView.setChecked(hideFromRecentsPreference.get());
    recordingNotificationView.setChecked(recordingNotificationPreference.get());
    showTouchesView.setChecked(showTouchesPreference.get());
  }

  @OnClick(R.id.launch) void onLaunchClicked() {
    if (longClickCount > 0) {
      longClickCount = 0;
      Timber.d("Long click count reset.");
    }

    Timber.d("Attempting to acquire permission to screen capture.");
    CaptureHelper.fireScreenCaptureIntent(this, analytics);
  }

  @OnLongClick(R.id.launch) boolean onLongClick() {
    if (++longClickCount == 5) {
      throw new RuntimeException("Crash! Bang! Pow! This is only a test...");
    }
    Timber.d("Long click count updated to %s", longClickCount);
    return true;
  }

  @OnItemSelected(R.id.spinner_video_size_percentage) void onVideoSizePercentageSelected(
      int position) {
    int newValue = videoSizePercentageAdapter.getItem(position);
    int oldValue = videoSizePreference.get();
    if (newValue != oldValue) {
      Timber.d("Video size percentage changing to %s%%", newValue);
      videoSizePreference.set(newValue);

      analytics.send(new HitBuilders.EventBuilder() //
          .setCategory(Analytics.CATEGORY_SETTINGS)
          .setAction(Analytics.ACTION_CHANGE_VIDEO_SIZE)
          .setValue(newValue)
          .build());
    }
  }

  @OnCheckedChanged(R.id.switch_show_countdown) void onShowCountdownChanged() {
    boolean newValue = showCountdownView.isChecked();
    boolean oldValue = showCountdownPreference.get();
    if (newValue != oldValue) {
      Timber.d("Hide show countdown changing to %s", newValue);
      showCountdownPreference.set(newValue);

      analytics.send(new HitBuilders.EventBuilder() //
          .setCategory(Analytics.CATEGORY_SETTINGS)
          .setAction(Analytics.ACTION_CHANGE_SHOW_COUNTDOWN)
          .setValue(newValue ? 1 : 0)
          .build());
    }
  }

  @OnCheckedChanged(R.id.switch_hide_from_recents) void onHideFromRecentsChanged() {
    boolean newValue = hideFromRecentsView.isChecked();
    boolean oldValue = hideFromRecentsPreference.get();
    if (newValue != oldValue) {
      Timber.d("Hide from recents preference changing to %s", newValue);
      hideFromRecentsPreference.set(newValue);

      analytics.send(new HitBuilders.EventBuilder() //
          .setCategory(Analytics.CATEGORY_SETTINGS)
          .setAction(Analytics.ACTION_CHANGE_HIDE_RECENTS)
          .setValue(newValue ? 1 : 0)
          .build());
    }
  }

  @OnCheckedChanged(R.id.switch_recording_notification) void onRecordingNotificationChanged() {
    boolean newValue = recordingNotificationView.isChecked();
    boolean oldValue = recordingNotificationPreference.get();
    if (newValue != oldValue) {
      Timber.d("Recording notification preference changing to %s", newValue);
      recordingNotificationPreference.set(newValue);

      analytics.send(new HitBuilders.EventBuilder() //
          .setCategory(Analytics.CATEGORY_SETTINGS)
          .setAction(Analytics.ACTION_CHANGE_RECORDING_NOTIFICATION)
          .setValue(newValue ? 1 : 0)
          .build());
    }
  }

  @OnCheckedChanged(R.id.switch_show_touches) void onShowTouchesChanged() {
    boolean newValue = showTouchesView.isChecked();
    boolean oldValue = showTouchesPreference.get();
    if (newValue != oldValue) {
      Timber.d("Show touches preference changing to %s", newValue);
      showTouchesPreference.set(newValue);

      analytics.send(new HitBuilders.EventBuilder() //
          .setCategory(Analytics.CATEGORY_SETTINGS)
          .setAction(Analytics.ACTION_CHANGE_SHOW_TOUCHES)
          .setValue(newValue ? 1 : 0)
          .build());
    }
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (!CaptureHelper.handleActivityResult(this, requestCode, resultCode, data, analytics)) {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override protected void onStop() {
    super.onStop();
    if (hideFromRecentsPreference.get() && !isChangingConfigurations()) {
      Timber.d("Removing task because hide from recents preference was enabled.");
      finishAndRemoveTask();
    }
  }
}
