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
  }

  @OnItemSelected(R.id.spinner_video_size_percentage) void onVideoSizePercentageSelected(
      int position) {
    int newValue = videoSizePercentageAdapter.getItem(position);
    Timber.d("Video size percentage changing to %s%%", newValue);
    videoSizePreference.set(newValue);
  }

  @OnCheckedChanged(R.id.switch_show_countdown) void onShowCountdownChanged() {
    boolean newValue = showCountdownView.isChecked();
    Timber.d("Hide show countdown changing to %s", newValue);
    showCountdownPreference.set(newValue);
  }

  @OnCheckedChanged(R.id.switch_hide_from_recents) void onHideFromRecentsChanged() {
    boolean newValue = hideFromRecentsView.isChecked();
    Timber.d("Hide from recents preference changing to %s", newValue);
    hideFromRecentsPreference.set(newValue);
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
