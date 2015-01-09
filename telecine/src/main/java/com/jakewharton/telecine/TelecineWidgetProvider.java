package com.jakewharton.telecine;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import javax.inject.Inject;

public final class TelecineWidgetProvider extends AppWidgetProvider {
  @Inject Tracker tracker;

  @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
    for (int id : ids) {
      RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget);

      Intent intent = new Intent(context, TelecineWidgetLaunchActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      rv.setOnClickPendingIntent(R.id.widget, pendingIntent);

      manager.updateAppWidget(id, rv);
    }

    super.onUpdate(context, manager, ids);
  }

  @Override public void onDeleted(Context context, int[] ids) {
    super.onDeleted(context, ids);

    ((TelecineApplication) context.getApplicationContext()).inject(this);
    tracker.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_WIDGET) //
        .setAction(Analytics.ACTION_WIDGET_REMOVED) //
        .build());
  }
}
