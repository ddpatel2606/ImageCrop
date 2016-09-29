// Generated code from Butter Knife. Do not modify!
package com.lyft.android.scissorssample;

import android.view.View;
import butterknife.ButterKnife.Finder;
import butterknife.ButterKnife.ViewBinder;

public class CropResultActivity$$ViewBinder<T extends com.lyft.android.scissorssample.CropResultActivity> implements ViewBinder<T> {
  @Override public void bind(final Finder finder, final T target, Object source) {
    View view;
    view = finder.findRequiredView(source, 2131558505, "field 'resultView'");
    target.resultView = finder.castView(view, 2131558505, "field 'resultView'");
  }

  @Override public void unbind(T target) {
    target.resultView = null;
  }
}
