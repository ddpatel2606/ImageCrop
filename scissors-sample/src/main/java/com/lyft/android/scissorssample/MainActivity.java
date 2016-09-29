/*
 * Copyright (C) 2015 Lyft, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lyft.android.scissorssample;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.lyft.android.scissors.CropView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTouch;
import rx.Observable;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

import static android.graphics.Bitmap.CompressFormat.PNG;
import static rx.android.schedulers.AndroidSchedulers.mainThread;
import static rx.schedulers.Schedulers.io;

public class MainActivity extends Activity {

    int PICK_IMAGE_FROM_GALLERY = 10001;

    private static final float[] ASPECT_RATIOS = {0f, 1f, 9f/16f, 16f / 9f, 16f / 10f, 32f / 29f, 65f / 64f, 5f / 3f};

    private static final String[] ASPECT_LABELS = {"\u00D8","1:1","9:16", "16:9", "16:10", "32:29", "65:64", "5:3"};

    @Bind(R.id.crop_view)
    CropView cropView;

    @Bind({R.id.crop_fab, R.id.pick_mini_fab, R.id.ratio_fab})
    List<View> buttons;

    @Bind(R.id.pick_fab)
    View pickButton;

    public static Activity mActivity;

    CompositeSubscription subscriptions = new CompositeSubscription();

    private int selectedRatio = 0;
    private AnimatorListener animatorListener = new AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
            ButterKnife.apply(buttons, VISIBILITY, View.INVISIBLE);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            ButterKnife.apply(buttons, VISIBILITY, View.VISIBLE);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity=this;
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);


    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_FROM_GALLERY
                && resultCode == Activity.RESULT_OK) {
            Uri galleryPictureUri = data.getData();

            cropView.extensions()
                    .load(galleryPictureUri);

            cropView.setImageUri(galleryPictureUri);

            float newRatio = ASPECT_RATIOS[3];
            cropView.setViewportRatio(newRatio);
            //cropView.touchManager.setRatioX(Integer.valueOf(ASPECT_LABELS[3].split(":")[0]));
           // cropView.touchManager.setRatioY(Integer.valueOf(ASPECT_LABELS[3].split(":")[1]));

            updateButtons();
        }
    }

    File croppedFile;

    @OnClick(R.id.crop_fab)
    public void onCropClicked() {

        croppedFile = new File(Environment.getExternalStorageDirectory(), "cropped.png");

        Observable<Void> onSave = Observable.from(cropView.extensions()
                .crop()
                .quality(90)
                .format(PNG)
                .outputSize(1920, 1080)
                .into(croppedFile))
                .subscribeOn(io())
                .observeOn(mainThread());

        subscriptions.add(onSave
                .subscribe(new Action1<Void>() {
                    @Override
                    public void call(Void nothing) {

                      //  File croppedNewFile = ScaleImage(croppedFile.getAbsolutePath(), Environment.getExternalStorageDirectory().getAbsolutePath(),
                           //     "cropped1.png", 1920, 1080);

                       // if (croppedFile.exists())
                       //     croppedFile.delete();

                        CropResultActivity.startUsing(croppedFile, MainActivity.this);
                    }
                }));
    }

    public static File ScaleImage(String mainpath, String FolderPath, String demoimageFilename, int scaleimagewidth, int scaleimageheight) {

        OutputStream outStream = null;
        File outPutfile = null;
        try {
            outPutfile = new File(FolderPath, demoimageFilename);

            Bitmap bm = BitmapFactory.decodeFile(mainpath);

            int width = bm.getWidth();

            int height = bm.getHeight();

            float scaleWidth = ((float) scaleimagewidth) / width;

            float scaleHeight = ((float) scaleimageheight) / height;

            Matrix matrix = new Matrix();

            matrix.postScale(scaleWidth, scaleHeight);

            Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);

            outStream = new FileOutputStream(outPutfile);

            if (getFileTypeFromPath(demoimageFilename).contains("jpeg") || getFileTypeFromPath(demoimageFilename).contains("jpg")) {
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream);
            } else if (getFileTypeFromPath(demoimageFilename).contains("png")) {
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 90, outStream);
            } else {
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 90, outStream);
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        } finally {
            if (outStream != null) {
                try {
                    outStream.flush();
                    outStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return outPutfile;
    }


    public static String getFileTypeFromPath(String filepath) {
        return filepath.substring(filepath.lastIndexOf("."));
    }


    @OnClick({R.id.pick_fab, R.id.pick_mini_fab})
    public void onPickClicked() {
        cropView.extensions()
                .pickUsing(this, PICK_IMAGE_FROM_GALLERY);
    }

    @OnClick(R.id.ratio_fab)
    public void onRatioClicked()
    {
        final float oldRatio = cropView.getImageRatio();
        selectedRatio = (selectedRatio + 1) % ASPECT_RATIOS.length;

        // Since the animation needs to interpolate to the native
        // ratio, we need to get that instead of using 0
        float newRatio = ASPECT_RATIOS[selectedRatio];
        if (Float.compare(0, newRatio) == 0) {
            newRatio = cropView.getImageRatio();
        }

        ObjectAnimator viewportRatioAnimator = ObjectAnimator.ofFloat(cropView, "viewportRatio", oldRatio, newRatio)
                .setDuration(420);
        autoCancel(viewportRatioAnimator);
        viewportRatioAnimator.addListener(animatorListener);
        viewportRatioAnimator.start();

        Toast.makeText(this, ASPECT_LABELS[selectedRatio], Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        subscriptions.unsubscribe();

    }

    @OnTouch(R.id.crop_view)
    public boolean onTouchCropView(MotionEvent event) { // GitHub issue #4
        if (event.getPointerCount() > 1 || cropView.getImageBitmap() == null) {
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                ButterKnife.apply(buttons, VISIBILITY, View.INVISIBLE);
                break;
            default:
                ButterKnife.apply(buttons, VISIBILITY, View.VISIBLE);
                break;
        }
        return true;
    }


    private void updateButtons() {
        ButterKnife.apply(buttons, VISIBILITY, View.VISIBLE);
        pickButton.setVisibility(View.GONE);

    }

    static final ButterKnife.Setter<View, Integer> VISIBILITY = new ButterKnife.Setter<View, Integer>() {
        @Override
        public void set(final View view, final Integer visibility, int index) {
            view.setVisibility(visibility);
        }
    };

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    static void autoCancel(ObjectAnimator objectAnimator) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            objectAnimator.setAutoCancel(true);
        }
    }
}
