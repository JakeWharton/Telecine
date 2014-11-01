package com.jakewharton.telecine;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Spinner;
import android.widget.Switch;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import javax.inject.Inject;
import timber.log.Timber;

public final class TelecineActivity extends Activity {
  private static final int CREATE_SCREEN_CAPTURE = 4242;

  @InjectView(R.id.spinner_video_size_percentage) Spinner videoSizePercentageView;
  @InjectView(R.id.switch_show_countdown) Switch showCountdownView;
  @InjectView(R.id.switch_hide_from_recents) Switch hideFromRecentsView;

  @Inject @VideoSizePercentage IntPreference videoSizePreference;
  @Inject @ShowCountdown BooleanPreference showCountdownPreference;
  @Inject @HideFromRecents BooleanPreference hideFromRecentsPreference;

  @Inject Tracker tracker;

  private VideoSizePercentageAdapter videoSizePercentageAdapter;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ((TelecineApplication) getApplication()).inject(this);

    setContentView(R.layout.activity_main);
    ButterKnife.inject(this);

    videoSizePercentageAdapter = new VideoSizePercentageAdapter(this);

    videoSizePercentageView.setAdapter(videoSizePercentageAdapter);
    videoSizePercentageView.setSelection(
        VideoSizePercentageAdapter.getSelectedPosition(videoSizePreference.get()));

    showCountdownView.setChecked(showCountdownPreference.get());
    hideFromRecentsView.setChecked(hideFromRecentsPreference.get());
  }

  @OnClick(R.id.launch) void onLaunchClicked() {
    Timber.d("Attempting to acquire permission to screen capture.");

    MediaProjectionManager manager =
        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    Intent intent = manager.createScreenCaptureIntent();
    startActivityForResult(intent, CREATE_SCREEN_CAPTURE);

    tracker.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_SETTINGS)
        .setAction(Analytics.ACTION_CAPTURE_INTENT_LAUNCH)
        .build());
  }

  @OnItemSelected(R.id.spinner_video_size_percentage) void onVideoSizePercentageSelected(
      int position) {
    int newValue = videoSizePercentageAdapter.getItem(position);
    int oldValue = videoSizePreference.get();
    if (newValue != oldValue) {
      Timber.d("Video size percentage changing to %s%%", newValue);
      videoSizePreference.set(newValue);

      tracker.send(new HitBuilders.EventBuilder() //
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

      tracker.send(new HitBuilders.EventBuilder() //
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

      tracker.send(new HitBuilders.EventBuilder() //
          .setCategory(Analytics.CATEGORY_SETTINGS)
          .setAction(Analytics.ACTION_CHANGE_HIDE_RECENTS)
          .setValue(newValue ? 1 : 0)
          .build());
    }
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case CREATE_SCREEN_CAPTURE:
        if (resultCode == 0) {
          Timber.d("Failed to acquire permission to screen capture.");
        } else {
          Timber.d("Acquired permission to screen capture. Starting service.");
          startService(TelecineService.newIntent(this, resultCode, data));
        }

        tracker.send(new HitBuilders.EventBuilder() //
            .setCategory(Analytics.CATEGORY_SETTINGS)
            .setAction(Analytics.ACTION_CAPTURE_INTENT_RESULT)
            .setValue(resultCode)
            .build());

        break;

      default:
        super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override protected void onStop() {
    super.onStop();

    if (hideFromRecentsPreference.get()) {
      Timber.d("Removing task because hide from recents preference was enabled.");
      finishAndRemoveTask();
    }
  }
}
