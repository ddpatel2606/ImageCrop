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

import android.Manifest;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.lyft.android.scissors.CropView;
import com.lyft.android.scissors.svgandroid.SVG;
import com.lyft.android.scissors.svgandroid.SVGParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTouch;
import permissionhelper.PermissionHelper;
import permissionhelper.annotations.PermissionDenied;
import permissionhelper.annotations.PermissionGranted;
import rx.Observable;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

import static android.graphics.Bitmap.CompressFormat.PNG;
import static rx.android.schedulers.AndroidSchedulers.mainThread;
import static rx.schedulers.Schedulers.io;

public class MainActivity extends Activity {

    int PICK_IMAGE_FROM_GALLERY = 10001;

    private static final float[] ASPECT_RATIOS = {0f, 1f, 9f / 16f, 16f / 9f, 16f / 10f, 32f / 29f, 65f / 64f, 5f / 3f};

    private static final String[] ASPECT_LABELS = {"\u00D8", "1:1", "9:16", "16:9", "16:10", "32:29", "65:64", "5:3"};

    @Bind(R.id.myMainLayout)
    FrameLayout myMainLayout;

    @Bind(R.id.crop_view)
    CropView cropView;

    @Bind({R.id.crop_fab, R.id.pick_mini_fab, R.id.ratio_fab})
    List<View> buttons;

    @Bind(R.id.pick_fab)
    View pickButton;

    public int myMainLayoutId = 0;

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
        mActivity = this;
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        // Android M Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!PermissionHelper.getInstance().hasPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PermissionHelper.getInstance().requestPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                onCreateCall();

            }
        }
        else
        {
            onCreateCall();

        }
    }

    @PermissionGranted(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void GET_ACCOUNTS_Granted() {

        onCreateCall();
    }

    @PermissionDenied(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void GET_ACCOUNTS_Denied() {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.getInstance().onRequestPermissionsResult(this, permissions, grantResults);
    }

    public void onCreateCall()
    {
        pickButton.setVisibility(View.VISIBLE);

        myMainLayoutId = com.lyft.android.scissors.R.raw.circle_stroke;
        cropView.setImageSVGId(myMainLayoutId);

        ListView mListView=null;
        HorizontalListView HorizontalListView=null;
        if (isTablet(MainActivity.this)) {
            mListView = new ListView(MainActivity.this);
            mListView.setScrollbarFadingEnabled(true);
            mListView.setVerticalFadingEdgeEnabled(false);

            final SvgImagesAdapter mSvgImagesAdapter = new SvgImagesAdapter(MainActivity.this);

            mListView.setAdapter(mSvgImagesAdapter);

            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    myMainLayoutId = mSvgImagesAdapter.getItem(position);

                    cropView.setImageSVGId(myMainLayoutId);

                    cropView.invalidate();
                }
            });

        }
        else
        {
            HorizontalListView =new HorizontalListView(MainActivity.this);

            final SvgImagesAdapter mSvgImagesAdapter = new SvgImagesAdapter(MainActivity.this);

            HorizontalListView.setAdapter(mSvgImagesAdapter);

            HorizontalListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    myMainLayoutId = mSvgImagesAdapter.getItem(position);

                    cropView.setImageSVGId(myMainLayoutId);

                    cropView.invalidate();
                }
            });

        }

        DisplayMetrics density = getDisplayMetrics();
        if (isTablet(MainActivity.this)) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(150, density.heightPixels - 150);
            params.gravity = Gravity.RIGHT;

            if(mListView !=null)
                myMainLayout.addView(mListView, params);

            FrameLayout.LayoutParams paramsCrop = (FrameLayout.LayoutParams) cropView.getLayoutParams();
            paramsCrop.setMargins(0, 0, 150, 0);
            cropView.setLayoutParams(paramsCrop);
        } else {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(density.widthPixels,150);
            params.gravity = Gravity.TOP;

            if(HorizontalListView !=null)
                myMainLayout.addView(HorizontalListView, params);

            FrameLayout.LayoutParams paramsCrop = (FrameLayout.LayoutParams) cropView.getLayoutParams();
            paramsCrop.setMargins(0, 150, 0, 0);
            cropView.setLayoutParams(paramsCrop);
        }

    }


    public DisplayMetrics getDisplayMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) MainActivity.this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay()
                .getMetrics(displayMetrics);
        return displayMetrics;
    }


    private class SvgImagesAdapter extends BaseAdapter {
        private List<Integer> mSvgRawResourceIds = new ArrayList<>();

        private Context mContext;

        public SvgImagesAdapter(Context context) {
            mContext = context;

            mSvgRawResourceIds.add(R.raw.circle_stroke);
            mSvgRawResourceIds.add(R.raw.circle);
            mSvgRawResourceIds.add(R.raw.triangle);
            mSvgRawResourceIds.add(R.raw.shape_star);
            mSvgRawResourceIds.add(R.raw.shape_heart);
            mSvgRawResourceIds.add(R.raw.shape_flower);
            mSvgRawResourceIds.add(R.raw.shape_star_2);
            mSvgRawResourceIds.add(R.raw.bubble);
            mSvgRawResourceIds.add(R.raw.clubs);
            mSvgRawResourceIds.add(R.raw.spades);
            mSvgRawResourceIds.add(R.raw.star_full);
            mSvgRawResourceIds.add(R.raw.stop2);
            mSvgRawResourceIds.add(R.raw.diamonds);

            mSvgRawResourceIds.add(R.raw.shape_star_3);
            mSvgRawResourceIds.add(R.raw.shape_circle_2);
            mSvgRawResourceIds.add(R.raw.shape_5);
            mSvgRawResourceIds.add( com.lyft.android.scissors.R.raw.mask);
        }

        @Override
        public int getCount() {
            return mSvgRawResourceIds.size();
        }

        @Override
        public Integer getItem(int i) {
            return mSvgRawResourceIds.get(i);
        }

        @Override
        public long getItemId(int i) {
            return mSvgRawResourceIds.get(i);
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ImageView imageView = new ImageView(mContext);

            imageView.setImageBitmap(getBitmap(mContext, getItem(i)));
            if (!isTablet(MainActivity.this))
                imageView.setPadding(10,10,10,10);
            imageView.setBackgroundColor(Color.parseColor("#BBFFFFFF"));

            return imageView;
        }

        public Bitmap getBitmap(Context context, int svgRawResourceId) {

            Bitmap bitmap = Bitmap.createBitmap(150, 150,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK);

            if (svgRawResourceId > 0) {

                if(svgRawResourceId== com.lyft.android.scissors.R.raw.mask)
                {
                    InputStream is = MainActivity.this.getResources().openRawResource(svgRawResourceId);
                    Bitmap originalBitmap = BitmapFactory.decodeStream(is);
                    canvas.drawBitmap(getResizedBitmap(originalBitmap,150,150),0,0,paint);
                }
                else
                {
                    SVG svg = SVGParser.getSVGFromInputStream(
                            context.getResources().openRawResource(svgRawResourceId), 150, 150);
                    canvas.drawPicture(svg.getPicture());
                }
            } else {
                canvas.drawRect(new RectF(0.0f, 0.0f, 100, 100), paint);
            }

            return bitmap;
        }

    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    public static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
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
    ProgressDialog progress;

    @OnClick(R.id.crop_fab)
    public void onCropClicked() {

        croppedFile = new File(Environment.getExternalStorageDirectory(), "cropped.png");


        progress = new ProgressDialog(this);
        progress.setMessage("Preparing...");
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.setCanceledOnTouchOutside(false);
        progress.setCancelable(false);
        progress.show();

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

                        if(progress !=null && progress.isShowing())
                        {
                            progress.dismiss();
                        }

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
    public void onRatioClicked() {
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
