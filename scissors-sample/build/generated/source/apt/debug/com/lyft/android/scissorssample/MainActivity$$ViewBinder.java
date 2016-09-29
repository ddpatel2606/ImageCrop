// Generated code from Butter Knife. Do not modify!
package com.lyft.android.scissorssample;

import android.view.View;
import butterknife.ButterKnife.Finder;
import butterknife.ButterKnife.ViewBinder;

public class MainActivity$$ViewBinder<T extends com.lyft.android.scissorssample.MainActivity> implements ViewBinder<T> {
  @Override public void bind(final Finder finder, final T target, Object source) {
    View view;
    view = finder.findRequiredView(source, 2131492970, "field 'cropView' and method 'onTouchCropView'");
    target.cropView = finder.castView(view, 2131492970, "field 'cropView'");
    view.setOnTouchListener(
      new android.view.View.OnTouchListener() {
        @Override public boolean onTouch(
          android.view.View p0,
          android.view.MotionEvent p1
        ) {
          return target.onTouchCropView(p1);
        }
      });
    view = finder.findRequiredView(source, 2131492973, "field 'pickButton' and method 'onPickClicked'");
    target.pickButton = view;
    view.setOnClickListener(
      new butterknife.internal.DebouncingOnClickListener() {
        @Override public void doClick(
          android.view.View p0
        ) {
          target.onPickClicked();
        }
      });
    view = finder.findRequiredView(source, 2131492971, "method 'onCropClicked'");
    view.setOnClickListener(
      new butterknife.internal.DebouncingOnClickListener() {
        @Override public void doClick(
          android.view.View p0
        ) {
          target.onCropClicked();
        }
      });
    view = finder.findRequiredView(source, 2131492972, "method 'onPickClicked'");
    view.setOnClickListener(
      new butterknife.internal.DebouncingOnClickListener() {
        @Override public void doClick(
          android.view.View p0
        ) {
          target.onPickClicked();
        }
      });
    view = finder.findRequiredView(source, 2131492974, "method 'onRatioClicked'");
    view.setOnClickListener(
      new butterknife.internal.DebouncingOnClickListener() {
        @Override public void doClick(
          android.view.View p0
        ) {
          target.onRatioClicked();
        }
      });
    target.buttons = Finder.listOf(
        finder.<android.view.View>findRequiredView(source, 2131492971, "field 'buttons'"),
        finder.<android.view.View>findRequiredView(source, 2131492972, "field 'buttons'"),
        finder.<android.view.View>findRequiredView(source, 2131492974, "field 'buttons'")
    );
  }

  @Override public void unbind(T target) {
    target.cropView = null;
    target.pickButton = null;
    target.buttons = null;
  }
}
