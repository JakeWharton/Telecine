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

  @InjectView(R.id.preference_show_countdown) Switch showCountdownView;
  @InjectView(R.id.preference_hide_from_recents) Switch hideFromRecentsView;
  @InjectView(R.id.preference_video_quality) Spinner videoQualityView;

  @Inject @ShowCountdown BooleanPreference showCountdownPreference;
  @Inject @HideFromRecents BooleanPreference hideFromRecentsPreference;
  @Inject @VideoQuality EnumPreference<EncodingQuality> videoQualityPreference;

  private boolean callbacksEnabled;
  private EnumAdapter<EncodingQuality> encodingQualityAdapter;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ((TelecineApplication) getApplication()).inject(this);

    setContentView(R.layout.activity_main);
    ButterKnife.inject(this);

    showCountdownView.setChecked(showCountdownPreference.get());

    hideFromRecentsView.setChecked(hideFromRecentsPreference.get());

    encodingQualityAdapter = new EnumAdapter<>(this, EncodingQuality.class);
    videoQualityView.setAdapter(encodingQualityAdapter);
    videoQualityView.setSelection(videoQualityPreference.get().ordinal());

    callbacksEnabled = true;
  }

  @OnClick(R.id.launch) void onLaunchClicked() {
    Timber.d("Attempting to acquire permission to screen capture.");

    MediaProjectionManager manager =
        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    Intent intent = manager.createScreenCaptureIntent();
    startActivityForResult(intent, CREATE_SCREEN_CAPTURE);
  }

  @OnCheckedChanged(R.id.preference_show_countdown) void onShowCountdownChanged() {
    if (!callbacksEnabled) return;
    boolean newValue = showCountdownView.isChecked();
    Timber.d("Hide show countdown changing to %s", newValue);
    showCountdownPreference.set(newValue);
  }

  @OnCheckedChanged(R.id.preference_hide_from_recents) void onHideFromRecentsChanged() {
    if (!callbacksEnabled) return;
    boolean newValue = hideFromRecentsView.isChecked();
    Timber.d("Hide from recents preference changing to %s", newValue);
    hideFromRecentsPreference.set(newValue);
  }

  @OnItemSelected(R.id.preference_video_quality) void onVideoQualitySelected(int position) {
    if (!callbacksEnabled) return;
    EncodingQuality newValue = encodingQualityAdapter.getItem(position);
    Timber.d("Video quality changing to %s", newValue);
    videoQualityPreference.set(newValue);
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
