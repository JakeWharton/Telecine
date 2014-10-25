package com.jakewharton.telecine;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Spinner;
import android.widget.Switch;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import butterknife.OnLongClick;
import com.google.android.gms.analytics.HitBuilders;
import javax.inject.Inject;
import timber.log.Timber;

import static android.view.View.ALPHA;
import static android.view.View.TRANSLATION_Y;

public final class TelecineActivity extends Activity {
  private static final int DURATION_TUTORIAL_TRANSITION = 300;

  @InjectView(R.id.main) View mainView;
  @InjectView(R.id.tutorial) View tutorialView;

  @InjectView(R.id.spinner_video_size_percentage) Spinner videoSizePercentageView;
  @InjectView(R.id.switch_show_countdown) Switch showCountdownView;
  @InjectView(R.id.switch_hide_from_recents) Switch hideFromRecentsView;

  @Inject @VideoSizePercentage IntPreference videoSizePreference;
  @Inject @ShowCountdown BooleanPreference showCountdownPreference;
  @Inject @HideFromRecents BooleanPreference hideFromRecentsPreference;

  @Inject Analytics analytics;

  private VideoSizePercentageAdapter videoSizePercentageAdapter;
  private int longClickCount;
  private boolean showingTutorial;
  private float defaultActionBarElevation;
  private int primaryColor;
  private int primaryColorDark;
  private int mainAnimationTranslationY;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ((TelecineApplication) getApplication()).inject(this);

    Resources res = getResources();
    String taskName = res.getString(R.string.app_name);
    Bitmap taskIcon =
        ((BitmapDrawable) res.getDrawable(R.drawable.ic_videocam_white_48dp)).getBitmap();
    int taskColor = res.getColor(R.color.primary_normal);
    setTaskDescription(new ActivityManager.TaskDescription(taskName, taskIcon, taskColor));

    defaultActionBarElevation = getActionBar().getElevation();

    TypedValue value = new TypedValue();
    Resources.Theme theme = getTheme();
    theme.resolveAttribute(android.R.attr.colorPrimary, value, true);
    primaryColor = value.data;
    theme.resolveAttribute(android.R.attr.colorPrimaryDark, value, true);
    primaryColorDark = value.data;

    mainAnimationTranslationY =
        getResources().getDimensionPixelSize(R.dimen.main_animation_translation_y);

    setContentView(R.layout.activity_main);
    ButterKnife.inject(this);

    tutorialView.getViewTreeObserver()
        .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override public void onGlobalLayout() {
            tutorialView.setTranslationY(tutorialView.getHeight());
            tutorialView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
          }
        });

    videoSizePercentageAdapter = new VideoSizePercentageAdapter(this);

    videoSizePercentageView.setAdapter(videoSizePercentageAdapter);
    videoSizePercentageView.setSelection(
        VideoSizePercentageAdapter.getSelectedPosition(videoSizePreference.get()));

    showCountdownView.setChecked(showCountdownPreference.get());
    hideFromRecentsView.setChecked(hideFromRecentsPreference.get());
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

  @Override public void onBackPressed() {
    if (showingTutorial) {
      hideTutorial();
    } else {
      super.onBackPressed();
    }
  }

  @OnClick(R.id.view_tutorial) void showTutorial() {
    showingTutorial = true;

    // Animate status bar color to primary.
    Animator statusBar = ObjectAnimator.ofArgb(getWindow(), "statusBarColor", primaryColor);

    // Animate tutorial up.
    tutorialView.setVisibility(View.VISIBLE);
    Animator tutorial = ObjectAnimator.ofFloat(tutorialView, TRANSLATION_Y, 0);

    // Animate main down and alpha out.
    PropertyValuesHolder main1 =
        PropertyValuesHolder.ofFloat(TRANSLATION_Y, mainAnimationTranslationY);
    PropertyValuesHolder main2 = PropertyValuesHolder.ofFloat(ALPHA, 0);
    Animator main = ObjectAnimator.ofPropertyValuesHolder(mainView, main1, main2);

    // Animate elevation of action bar to 0.
    Animator actionBar = ObjectAnimator.ofFloat(getActionBar(), "elevation", 0);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(statusBar, tutorial, main, actionBar);
    set.setDuration(DURATION_TUTORIAL_TRANSITION);
    set.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(Animator animation) {
        getActionBar().setTitle(R.string.tutorial_welcome);
      }
    });
    set.start();
  }

  private void hideTutorial() {
    showingTutorial = false;

    // Animate status bar color to primary dark.
    Animator statusBar = ObjectAnimator.ofArgb(getWindow(), "statusBarColor", primaryColorDark);

    // Animate tutorial down.
    int tutorialHeight = tutorialView.getHeight();
    Animator tutorial = ObjectAnimator.ofFloat(tutorialView, TRANSLATION_Y, tutorialHeight);
    tutorial.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(@NonNull Animator animation) {
        tutorialView.setVisibility(View.INVISIBLE);
      }
    });

    // Animate main up and alpha in.
    PropertyValuesHolder main1 = PropertyValuesHolder.ofFloat(TRANSLATION_Y, 0);
    PropertyValuesHolder main2 = PropertyValuesHolder.ofFloat(ALPHA, 1);
    Animator main = ObjectAnimator.ofPropertyValuesHolder(mainView, main1, main2);

    // Animate elevation of action bar back to normal.
    Animator actionBar =
        ObjectAnimator.ofFloat(getActionBar(), "elevation", defaultActionBarElevation);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(statusBar, tutorial, main, actionBar);
    set.setDuration(DURATION_TUTORIAL_TRANSITION);
    set.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationStart(Animator animation) {
        getActionBar().setTitle(R.string.app_name);
      }
    });
    set.start();
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (!CaptureHelper.handleActivityResult(this, requestCode, resultCode, data, analytics)) {
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
