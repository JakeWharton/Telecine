package com.jakewharton.telecine;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

final class VideoSizePercentageAdapter extends BaseAdapter {
  static int getSelectedPosition(int value) {
    switch (value) {
      case 100: return 0;
      case 75: return 1;
      case 50: return 2;
      default: return 0;
    }
  }

  private final LayoutInflater inflater;

  VideoSizePercentageAdapter(Context context) {
    inflater = LayoutInflater.from(context);
  }

  @Override public int getCount() {
    return 3;
  }

  @Override public Integer getItem(int position) {
    switch (position) {
      case 0: return 100;
      case 1: return 75;
      case 2: return 50;
      default: throw new IllegalArgumentException("Unknown position: " + position);
    }
  }

  @Override public long getItemId(int position) {
    return position;
  }

  @Override public View getView(int position, View convertView, @NonNull ViewGroup parent) {
    TextView tv = (TextView) convertView;
    if (tv == null) {
      tv =
          (TextView) inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
    }

    tv.setText(String.valueOf(getItem(position)) + "%");

    return tv;
  }
}
