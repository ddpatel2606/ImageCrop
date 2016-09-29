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
package com.lyft.android.scissors;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.widget.ImageView;

import com.lyft.android.scissors.CropViewExtensions.CropRequest;
import com.lyft.android.scissors.CropViewExtensions.LoadRequest;

import java.io.File;
import java.io.OutputStream;

/**
 * An {@link ImageView} with a fixed viewport and cropping capabilities.
 */
public class CropView extends ImageView {

    private static final int MAX_TOUCH_POINTS = 2;
    public TouchManager touchManager;

    private Paint viewportPaint = new Paint();

    private Paint viewportBorderPaint = new Paint();
    private Paint viewportguidelinePaint = new Paint();
    Paint mBorderPaint = new Paint();
    private Paint bitmapPaint = new Paint();
    Paint mPaintDebug =new Paint();
    Paint mPaintFrame=new Paint();
    public RectF mImageRect;
    public Bitmap bitmap;
    public Matrix transform = new Matrix();
    private Extensions extensions;
    int mViewWidth,mViewHeight;
    CropViewConfig config;
    Uri mImageUri;
    int mAngle=0;

    private static final int TRANSLUCENT_WHITE = 0xBBFFFFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TRANSLUCENT_BLACK = 0x60000000;
    private static final int LINE_WIDTH = 5;

    public static int FRAME_WIDTH = 200;
    public static int FRAME_HEIGHT = 113;

    public static int RATIO_X = 16;
    public static int RATIO_Y= 9;

    public CropView(Context context) {
        super(context);
        initCropView(context, null);
    }

    public CropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initCropView(context, attrs);
    }

    void initCropView(Context context, AttributeSet attrs) {
        config = CropViewConfig.from(context, attrs);

        touchManager = new TouchManager
                (MAX_TOUCH_POINTS, config,this,FRAME_WIDTH,FRAME_HEIGHT,RATIO_X,RATIO_Y);

        bitmapPaint.setFilterBitmap(true);
        viewportPaint.setColor(config.getViewportOverlayColor());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap == null) {
            return;
        }

        drawBitmap(canvas);
        drawOverlay(canvas);
        drawCorners(canvas);
        //handlers
        //drawHandles(canvas);
        drawGuidelines(canvas);
        drawBorders(canvas);

        //Debug info
        //drawDebugInfo(canvas);
    }

    private void drawDebugInfo(Canvas canvas) {
        mPaintDebug = new Paint();
        mPaintDebug.setAntiAlias(true);
        mPaintDebug.setStyle(Paint.Style.STROKE);
        mPaintDebug.setColor(WHITE);
        mPaintDebug.setTextSize((float) 15 * touchManager.getDisplayMetrics().density);

        Paint.FontMetrics fontMetrics = mPaintDebug.getFontMetrics();
        mPaintDebug.measureText("W");
        int textHeight = (int) (fontMetrics.descent - fontMetrics.ascent);
        int x = (int) (mImageRect.left + (float) touchManager.mHandleSize * 0.5f * touchManager.getDisplayMetrics().density);
        int y = (int) (mImageRect.top + textHeight + (float) touchManager.mHandleSize * 0.5f * touchManager.getDisplayMetrics().density);
        StringBuilder builder = new StringBuilder();
        builder.append("LOADED FROM: ")
                .append(mImageUri != null ? "Uri" : "Bitmap");
        canvas.drawText(builder.toString(), x, y, mPaintDebug);
        builder = new StringBuilder();

        if (mImageUri == null) {
            builder.append("INPUT_IMAGE_SIZE: ")
                    .append((int) bitmap.getWidth())
                    .append("x")
                    .append((int) bitmap.getHeight());
            y += textHeight;
            canvas.drawText(builder.toString(), x, y, mPaintDebug);
            builder = new StringBuilder();
        } else {
            builder = new StringBuilder()
                    .append("INPUT_IMAGE_SIZE: ")
                    .append(bitmap.getWidth())
                    .append("x")
                    .append(bitmap.getHeight());
            y += textHeight;
            canvas.drawText(builder.toString(), x, y, mPaintDebug);
            builder = new StringBuilder();
        }
        builder.append("LOADED_IMAGE_SIZE: ")
                .append(bitmap.getWidth())
                .append("x")
                .append(bitmap.getHeight());
        y += textHeight;
        canvas.drawText(builder.toString(), x, y, mPaintDebug);
        builder = new StringBuilder();
        final int viewportWidth = touchManager.getViewportWidth();
        final int viewportHeight = touchManager.getViewportHeight();

        Rect overlayRect = new Rect((int) Math.floor(touchManager.mFrameRect.left),
                (int) Math.floor(touchManager.mFrameRect.top),
                (int) Math.ceil(touchManager.mFrameRect.right),
                (int) Math.ceil(touchManager.mFrameRect.bottom));

        if (viewportWidth > 0 && viewportHeight > 0) {
            builder.append("OUTPUT_IMAGE_SIZE: ")
                    .append(viewportWidth)
                    .append("x")
                    .append(viewportHeight);
            y += textHeight;
            canvas.drawText(builder.toString(), x, y, mPaintDebug);
            builder = new StringBuilder()
                    .append("RECT DIMENS: ")
                    .append(overlayRect +"  W :"+(overlayRect.right-overlayRect.left)+" H :"+(overlayRect.bottom - overlayRect.top));
            y += textHeight;
            canvas.drawText(builder.toString(), x, y, mPaintDebug);
            builder = new StringBuilder()
                    .append("HANDLER: ")
                    .append(touchManager.mHandleSize);
            y += textHeight;
            canvas.drawText(builder.toString(), x, y, mPaintDebug);
        }
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int viewHeight = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(viewWidth, viewHeight);

        mViewWidth = viewWidth - getPaddingLeft() - getPaddingRight();
        mViewHeight = viewHeight - getPaddingTop() - getPaddingBottom();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getDrawable() != null) {
            resetTouchManager();
            invalidate();
        }
    }

    private void drawOverlay(Canvas canvas) {

      /*  Path path = new Path();

            path.addRect(
                    overlayRect,
                    Path.Direction.CW);
            //path.addRect(touchManager.mFrameRect, Path.Direction.CCW);
            canvas.drawPath(path, viewportPaint);
        */

        RectF overlayRect = new RectF((float) Math.floor(touchManager.mFrameRect.left),
                (float) Math.floor(touchManager.mFrameRect.top),
                (float) Math.ceil(touchManager.mFrameRect.right),
                (float) Math.ceil(touchManager.mFrameRect.bottom));

        canvas.drawRect(0,  overlayRect.top, overlayRect.left, overlayRect.bottom, viewportPaint); //left
        canvas.drawRect(0, 0, getWidth(), overlayRect.top, viewportPaint); //top
        canvas.drawRect(overlayRect.right, overlayRect.top, getWidth(), overlayRect.bottom, viewportPaint); //right
        canvas.drawRect(0, overlayRect.bottom, getWidth(), getHeight(), viewportPaint); //bottom

//        canvas.drawRect(0, top, left, getHeight() - top, viewportPaint); //left
//        canvas.drawRect(0, 0, getWidth(), top, viewportPaint); //top
//        canvas.drawRect(getWidth() - left, top, getWidth(), getHeight() - top, viewportPaint); //right
//        canvas.drawRect(0, getHeight() - top, getWidth(), getHeight(), viewportPaint); //bottom*/

    }

    private void drawHandles(Canvas canvas) {
        drawHandleShadows(canvas);
        mPaintFrame.setAntiAlias(true);
        mPaintFrame.setStyle(Paint.Style.FILL);
        mPaintFrame.setColor(WHITE);

        RectF overlayRect = new RectF((float) Math.floor(touchManager.mFrameRect.left),
                (float) Math.floor(touchManager.mFrameRect.top),
                (float) Math.ceil(touchManager.mFrameRect.right),
                (float) Math.ceil(touchManager.mFrameRect.bottom));

        canvas.drawCircle(overlayRect.left, overlayRect.top, touchManager.mHandleSize, mPaintFrame);
        canvas.drawCircle(overlayRect.right, overlayRect.top, touchManager.mHandleSize, mPaintFrame);
        canvas.drawCircle(overlayRect.left, overlayRect.bottom, touchManager.mHandleSize, mPaintFrame);
        canvas.drawCircle(overlayRect.right, overlayRect.bottom, touchManager.mHandleSize, mPaintFrame);
    }

    private void drawHandleShadows(Canvas canvas) {
        mPaintFrame.setStyle(Paint.Style.FILL);
        mPaintFrame.setAntiAlias(true);
        mPaintFrame.setColor(TRANSLUCENT_BLACK);
        RectF rect = new RectF((float) Math.floor(touchManager.mFrameRect.left),
                (float) Math.floor(touchManager.mFrameRect.top),
                (float) Math.ceil(touchManager.mFrameRect.right),
                (float) Math.ceil(touchManager.mFrameRect.bottom));

        rect.offset(0, 1);
        canvas.drawCircle(rect.left, rect.top, touchManager.mHandleSize, mPaintFrame);
        canvas.drawCircle(rect.right, rect.top, touchManager.mHandleSize, mPaintFrame);
        canvas.drawCircle(rect.left, rect.bottom, touchManager.mHandleSize, mPaintFrame);
        canvas.drawCircle(rect.right, rect.bottom, touchManager.mHandleSize, mPaintFrame);
    }

    protected int dp2px(float dp) {
        final float scale = getContext().getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    /**
     * Draw the corner of crop overlay.
     */
    private void drawCorners(Canvas canvas) {

        DisplayMetrics dm = getResources().getDisplayMetrics() ;
        float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, dm);

        viewportBorderPaint.setColor(Color.parseColor("#ffffff"));
        viewportBorderPaint.setStrokeWidth(strokeWidth);
        viewportBorderPaint.setStyle(Paint.Style.STROKE);
        viewportBorderPaint.setAntiAlias(true);

        float lineWidth =dp2px(LINE_WIDTH);
        float cornerWidth = dp2px(LINE_WIDTH * 2);
        float w = cornerWidth / 2 + 15;

        RectF rect = new RectF((float) Math.floor(touchManager.mFrameRect.left),
                (float) Math.floor(touchManager.mFrameRect.top),
                (float) Math.ceil(touchManager.mFrameRect.right),
                (float) Math.ceil(touchManager.mFrameRect.bottom));

        rect.inset(w, w);

        float cornerExtension = cornerWidth / 2 + lineWidth;

        // Top left
        canvas.drawLine(rect.left - lineWidth, rect.top - cornerExtension, rect.left - lineWidth, rect.top + 40, viewportBorderPaint);
        canvas.drawLine(rect.left - cornerExtension, rect.top - lineWidth, rect.left + 40, rect.top - lineWidth, viewportBorderPaint);

        // Top right
        canvas.drawLine(rect.right + lineWidth, rect.top - cornerExtension, rect.right + lineWidth, rect.top + 40, viewportBorderPaint);
        canvas.drawLine(rect.right + cornerExtension, rect.top - lineWidth, rect.right - 40, rect.top - lineWidth, viewportBorderPaint);

        // Bottom left
        canvas.drawLine(rect.left - lineWidth, rect.bottom + cornerExtension, rect.left - lineWidth, rect.bottom - 40, viewportBorderPaint);
        canvas.drawLine(rect.left - cornerExtension, rect.bottom + lineWidth, rect.left + 40, rect.bottom + lineWidth, viewportBorderPaint);

        // Bottom left
        canvas.drawLine(rect.right + lineWidth, rect.bottom + cornerExtension, rect.right + lineWidth, rect.bottom - 40, viewportBorderPaint);
        canvas.drawLine(rect.right + cornerExtension, rect.bottom + lineWidth, rect.right - 40, rect.bottom + lineWidth, viewportBorderPaint);

    }

    /**
     * Draw 2 veritcal and 2 horizontal guidelines inside the cropping area to split it into 9 equal parts.
     */
    private void drawGuidelines(Canvas canvas) {

        DisplayMetrics dm = getResources().getDisplayMetrics() ;
        float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0.5f, dm);

        viewportguidelinePaint.setColor(TRANSLUCENT_WHITE);
        viewportguidelinePaint.setStrokeWidth(strokeWidth);
        viewportguidelinePaint.setStyle(Paint.Style.STROKE);
        viewportguidelinePaint.setAntiAlias(true);

        if (viewportguidelinePaint != null) {
            float sw = viewportguidelinePaint != null ? viewportguidelinePaint.getStrokeWidth() : 0;

            RectF rect = new RectF((float) Math.floor(touchManager.mFrameRect.left),
                    (float) Math.floor(touchManager.mFrameRect.top),
                    (float) Math.ceil(touchManager.mFrameRect.right),
                    (float) Math.ceil(touchManager.mFrameRect.bottom));

            rect.inset(sw, sw);

            float oneThirdCropWidth = rect.width() / 3;
            float oneThirdCropHeight = rect.height() / 3;

            // Draw vertical guidelines.
            float x1 = rect.left + oneThirdCropWidth;
            float x2 = rect.right - oneThirdCropWidth;
            canvas.drawLine(x1, rect.top, x1, rect.bottom, viewportguidelinePaint);
            canvas.drawLine(x2, rect.top, x2, rect.bottom, viewportguidelinePaint);

            // Draw horizontal guidelines.
            float y1 = rect.top + oneThirdCropHeight;
            float y2 = rect.bottom - oneThirdCropHeight;
            canvas.drawLine(rect.left, y1, rect.right, y1, viewportguidelinePaint);
            canvas.drawLine(rect.left, y2, rect.right, y2, viewportguidelinePaint);
        }
    }

    /**
     * Draw borders of the crop area.
     */
    private void drawBorders(Canvas canvas) {

        DisplayMetrics dm = getResources().getDisplayMetrics() ;
        float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, dm);

        mBorderPaint.setColor(Color.WHITE);
        mBorderPaint.setStrokeWidth(strokeWidth);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setAntiAlias(true);

        if (mBorderPaint != null) {
            float w = mBorderPaint.getStrokeWidth();

            final int viewportWidth = touchManager.getViewportWidth();
            final int viewportHeight = touchManager.getViewportHeight();
            final int left = (getWidth() - viewportWidth) / 2;
            final int top = (getHeight() - viewportHeight) / 2;

            RectF rect = new RectF((float) Math.floor(touchManager.mFrameRect.left),
                    (float) Math.floor(touchManager.mFrameRect.top),
                    (float) Math.ceil(touchManager.mFrameRect.right),
                    (float) Math.ceil(touchManager.mFrameRect.bottom));

           // RectF rect = new RectF(left, top, left + viewportWidth, top + viewportHeight);
            rect.inset(w / 2, w / 2);

            // Draw rectangle crop window border.
            canvas.drawRect(rect, mBorderPaint);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetTouchManager();
    }

    /**
     * Returns the native aspect ratio of the image.
     *
     * @return The native aspect ratio of the image.
     */
    public float getImageRatio() {
        Bitmap bitmap = getImageBitmap();
        return bitmap != null ? (float) bitmap.getWidth() / (float) bitmap.getHeight() : 0f;
    }

    public void setImageUri(Uri uri)
    {
        mImageUri=uri;
    }

    /**
     * Returns the aspect ratio of the viewport and crop rect.
     *
     * @return The current viewport aspect ratio.
     */
    public float getViewportRatio() {
        return touchManager.getAspectRatio();
    }

    public void setViewportWidth(int width) {
        touchManager.setViewportWidth(width);
    }

    public void setViewportHeight(int height) {
        touchManager.setViewportHeight(height);
    }

    /**
     * Sets the aspect ratio of the viewport and crop rect.  Defaults to
     * the native aspect ratio if <code>ratio == 0</code>.
     *
     * @param ratio The new aspect ratio of the viewport.
     */
    public void setViewportRatio(float ratio) {
        if (Float.compare(ratio, 0) == 0) {
            ratio = getImageRatio();
        }
        touchManager.setAspectRatio(ratio);
        resetTouchManager();
        invalidate();
    }

    @Override
    public void setImageResource(@DrawableRes int resId) {
        final Bitmap bitmap = resId > 0
                ? BitmapFactory.decodeResource(getResources(), resId)
                : null;
        setImageBitmap(bitmap);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        final Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            bitmap = bitmapDrawable.getBitmap();
        } else if (drawable != null) {
            bitmap = Utils.asBitmap(drawable, getWidth(), getHeight());
        } else {
            bitmap = null;
        }

        setImageBitmap(bitmap);
    }

    @Override
    public void setImageURI(@Nullable Uri uri) {
        extensions().load(uri);
    }

    @Override
    public void setImageBitmap(@Nullable Bitmap bitmap) {
        this.bitmap = bitmap;
        resetTouchManager();
        invalidate();
    }

    /**
     * @return Current working Bitmap or <code>null</code> if none has been set yet.
     */
    @Nullable
    public Bitmap getImageBitmap() {
        return bitmap;
    }

    private void resetTouchManager() {
        final boolean invalidBitmap = bitmap == null;
        final int bitmapWidth = invalidBitmap ? 0 : bitmap.getWidth();
        final int bitmapHeight = invalidBitmap ? 0 : bitmap.getHeight();
        mImageRect = new RectF(0f, 0f, bitmapWidth, bitmapHeight);
        touchManager.resetFor(bitmapWidth, bitmapHeight, getWidth(), getHeight());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        super.dispatchTouchEvent(event);

        touchManager.onEvent(event);
        invalidate();
        return true;
    }

    /**
     * Performs synchronous image cropping based on configuration.
     *
     * @return A {@link Bitmap} cropped based on viewport and user panning and zooming or <code>null</code> if no {@link Bitmap} has been
     * provided.
     */
    @Nullable
    public Bitmap crop() {
        return crop(1.0f);
     }

    private Bitmap getRotatedBitmap(Bitmap bitmap) {
        Matrix rotateMatrix = new Matrix();
        rotateMatrix.setRotate(mAngle, bitmap.getWidth() / 2, bitmap.getHeight() / 2);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                rotateMatrix, true);
    }


    /**
          * Performs synchronous image cropping based on configuration.
          *
          * @param outputScale multiplied with viewport size for calculating bitmap size.
          * @return A {@link Bitmap} cropped based on viewport and user panning and zooming or <code>null</code> if no {@link Bitmap} has been
          * provided.
          */
        @Nullable
        public Bitmap crop(float outputScale) {
            if (bitmap == null) {
                return null;
            }

            final int viewportWidth = touchManager.getViewportWidth();
            final int viewportHeight = touchManager.getViewportHeight();

            Bitmap src = bitmap;
            Bitmap.Config srcConfig = src.getConfig();
            Bitmap.Config config = Bitmap.Config.ARGB_8888; //srcConfig == null ? Bitmap.Config.ARGB_8888 : srcConfig;

            RectF rect = new RectF((float) Math.floor(touchManager.mFrameRect.left),
                    (float) Math.floor(touchManager.mFrameRect.top),
                    (float) Math.ceil(touchManager.mFrameRect.right),
                    (float) Math.ceil(touchManager.mFrameRect.bottom));

            Bitmap dst = Bitmap.createBitmap((int) (touchManager.mFrameRect.width() * outputScale), (int) (touchManager.mFrameRect.height() * outputScale), config);

            Log.e("CropView", "OutPut " + rect + " Viewport width " + viewportWidth + " Height " + viewportHeight);

            dst.eraseColor(Color.TRANSPARENT);

            Canvas canvas = new Canvas(dst);
            canvas.translate(-touchManager.mFrameRect.left * outputScale, -touchManager.mFrameRect.top * outputScale);

            Matrix transform = new Matrix();
            touchManager.applyPositioningAndScale(transform);
            transform.postScale(outputScale, outputScale);

            canvas.drawBitmap(bitmap, transform, bitmapPaint);

            return dst;
        }

    float calculateOutputScale(int width, int height) {
        final float viewportHeight = touchManager.getViewportHeight();
        final float viewportWidth = touchManager.getViewportWidth();

        if (width / (float) height > viewportWidth / viewportHeight) {
            return height / viewportHeight; //fit to height
        } else {
            return width / viewportWidth; //fit to width
        }
    }


    private void drawBitmap(Canvas canvas) {
        transform.reset();
        touchManager.applyPositioningAndScale(transform);

        canvas.drawBitmap(bitmap, transform, bitmapPaint);
    }

    /**
     * Obtain current viewport width.
     *
     * @return Current viewport width.
     * <p>Note: It might be 0 if layout pass has not been completed.</p>
     */
    public int getViewportWidth() {
        return touchManager.getViewportWidth();
    }

    /**
     * Obtain current viewport height.
     *
     * @return Current viewport height.
     * <p>Note: It might be 0 if layout pass has not been completed.</p>
     */
    public int getViewportHeight() {
        return touchManager.getViewportHeight();
    }

    /**
     * Offers common utility extensions.
     *
     * @return Extensions object used to perform chained calls.
     */
    public Extensions extensions() {
        if (extensions == null) {
            extensions = new Extensions(this);
        }
        return extensions;
    }

    /**
     * Optional extensions to perform common actions involving a {@link CropView}
     */
    public static class Extensions {

        private final CropView cropView;

        Extensions(CropView cropView) {
            this.cropView = cropView;
        }

        /**
         * Load a {@link Bitmap} using an automatically resolved {@link BitmapLoader} which will attempt to scale image to fill view.
         *
         * @param model Model used by {@link BitmapLoader} to load desired {@link Bitmap}
         */
        public void load(@Nullable Object model) {
            new LoadRequest(cropView)
                    .load(model);
        }

        /**
         * Load a {@link Bitmap} using given {@link BitmapLoader}, you must call {@link LoadRequest#load(Object)} afterwards.
         *
         * @param bitmapLoader {@link BitmapLoader} used to load desired {@link Bitmap}
         */
        public LoadRequest using(@Nullable BitmapLoader bitmapLoader) {
            return new LoadRequest(cropView).using(bitmapLoader);
        }

        /**
         * Perform an asynchronous crop request.
         *
         * @return {@link CropRequest} used to chain a configure cropping request, you must call either one of:
         * <ul>
         * <li>{@link CropRequest#into(File)}</li>
         * <li>{@link CropRequest#into(OutputStream, boolean)}</li>
         * </ul>
         */
        public CropRequest crop() {
            return new CropRequest(cropView);
        }

        /**
         * Perform a pick image request using {@link Activity#startActivityForResult(Intent, int)}.
         */
        public void pickUsing(@NonNull Activity activity, int requestCode) {
            CropViewExtensions.pickUsing(activity, requestCode);
        }

        /**
         * Perform a pick image request using {@link Fragment#startActivityForResult(Intent, int)}.
         */
        public void pickUsing(@NonNull Fragment fragment, int requestCode) {
            CropViewExtensions.pickUsing(fragment, requestCode);
        }
    }
}
