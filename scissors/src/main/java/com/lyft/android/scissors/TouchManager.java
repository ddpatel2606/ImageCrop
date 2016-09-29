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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

public class TouchManager {

    private final int maxNumberOfTouchPoints;
    private final CropViewConfig cropViewConfig;

    private final TouchPoint[] points;
    private final TouchPoint[] previousPoints;

    private float minimumScale;
    private float maximumScale;
    private Rect imageBounds;
    private float aspectRatio;
    private int viewportWidth;
    private int viewportHeight;
    private int bitmapWidth;
    private int bitmapHeight;

    private int mTouchPadding = 0;
    private int verticalLimit;
    private int horizontalLimit;
    public RectF mFrameRect, mScreenFrameRect;
    public int mHandleSize;
    private float scale = -1.0f;
    private TouchPoint position = new TouchPoint();
    public static final int HANDLE_SIZE_IN_DP = 10;
    private static final int TOUCH_PADDING = 20;
    private float mMinFrameWidth, mMinFrameHeight;
    private int mSelectedRatioX = 16, mSelectedRatioY = 9;
    private float mLastX, mLastY;

    private enum TouchArea {
        OUT_OF_BOUNDS, CENTER, LEFT_TOP, RIGHT_TOP, LEFT_BOTTOM, RIGHT_BOTTOM
    }

    private TouchArea mTouchArea = TouchArea.OUT_OF_BOUNDS;
    public CropView mCropView;

    public TouchManager(final int maxNumberOfTouchPoints, final CropViewConfig cropViewConfig, CropView CropView,
                        int minFrameW, int minframeH, int mRatioX, int mRatioY) {
        this.maxNumberOfTouchPoints = maxNumberOfTouchPoints;
        this.cropViewConfig = cropViewConfig;
        mCropView = CropView;

        points = new TouchPoint[maxNumberOfTouchPoints];
        previousPoints = new TouchPoint[maxNumberOfTouchPoints];
        minimumScale = cropViewConfig.getMinScale();
        maximumScale = cropViewConfig.getMaxScale();

        DisplayMetrics density = getDisplayMetrics();
        mHandleSize = (int) (density.density * HANDLE_SIZE_IN_DP);
        mMinFrameWidth = minFrameW;
        mMinFrameHeight = minframeH;
        mTouchPadding = (int) (density.density * TOUCH_PADDING);
        mScreenFrameRect = new RectF(0, 0, mCropView.getWidth(), mCropView.getHeight());
        this.mSelectedRatioX = mRatioX;
        this.mSelectedRatioY = mRatioY;

    }


    public DisplayMetrics getDisplayMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) mCropView.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay()
                .getMetrics(displayMetrics);
        return displayMetrics;
    }

    private void setViewport(int bitmapWidth, int bitmapHeight, int availableWidth, int availableHeight) {
        final float imageAspect = (float) bitmapWidth / bitmapHeight;
        final float viewAspect = (float) availableWidth / availableHeight;

        float ratio = cropViewConfig.getViewportRatio();
        if (Float.compare(0f, ratio) == 0) {
            // viewport ratio of 0 means match native ratio of bitmap
            ratio = imageAspect;
        }

        if (ratio > viewAspect) {
            // viewport is wider than view
            viewportWidth = availableWidth; //- cropViewConfig.getViewportOverlayPadding() * 2;
            viewportHeight = (int) ((viewportWidth) * (1 / ratio));
        } else {
            // viewport is taller than view
            viewportHeight = availableHeight; //- cropViewConfig.getViewportOverlayPadding() * 2 ;
            viewportWidth = (int) ((viewportHeight) * ratio);
        }

        final int left = (mCropView.getWidth() - viewportWidth) / 2;
        final int top = (mCropView.getHeight() - viewportHeight) / 2;

        mFrameRect = new RectF(left, top, left + viewportWidth, top + viewportHeight);

        mScreenFrameRect = new RectF(0, 0, mCropView.getWidth(), mCropView.getHeight());
    }


    public float getRatioX() {
        return mSelectedRatioX;
    }

    public float getRatioY() {
        return mSelectedRatioY;
    }

    public void setRatioX(int mSelectedRatioX) {
        this.mSelectedRatioX = mSelectedRatioX;
    }

    public void setRatioY(int mSelectedRatioY) {
        this.mSelectedRatioY = mSelectedRatioY;
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    public void onEvent(MotionEvent event) {
        // New One
        int index = event.getActionIndex();
        if (index >= maxNumberOfTouchPoints) {
            return; // We don't care about this pointer, ignore it.
        }

        if (isUpAction(event.getActionMasked())) {
            if (mTouchArea != TouchArea.OUT_OF_BOUNDS) {
                mCropView.getParent().requestDisallowInterceptTouchEvent(false);
            }
            previousPoints[index] = null;
            points[index] = null;
        }

        handleDragGesture();
        handlePinchGesture();

        if (isUpAction(event.getActionMasked())) {
            ensureInsideViewport();
            onUp(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                onDown(event);
                updateCurrentAndPreviousPoints(event);
                break;
            case MotionEvent.ACTION_MOVE:
                onMove(event);
                if (mTouchArea != TouchArea.OUT_OF_BOUNDS) {
                    mCropView.getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mTouchArea != TouchArea.OUT_OF_BOUNDS) {
                    mCropView.getParent().requestDisallowInterceptTouchEvent(false);
                }
                onCancel();
                break;
        }
    }

    private void onUp(MotionEvent e) {
        mTouchArea = TouchArea.OUT_OF_BOUNDS;
        mCropView.invalidate();
        ensureInsideViewport();
    }

    private void onCancel() {
        mTouchArea = TouchArea.OUT_OF_BOUNDS;
        mCropView.invalidate();
    }

    private void onDown(MotionEvent e) {
        mCropView.invalidate();
        mLastX = e.getX();
        mLastY = e.getY();
        checkTouchArea(e, e.getX(), e.getY());

        viewportWidth = (int) (mFrameRect.right - mFrameRect.left);
        viewportHeight = (int) (mFrameRect.bottom - mFrameRect.top);
    }

    private void checkTouchArea(MotionEvent event, float x, float y) {
        if (isInsideCornerLeftTop(x, y)) {
            mTouchArea = TouchArea.LEFT_TOP;
            return;
        }
        if (isInsideCornerRightTop(x, y)) {
            mTouchArea = TouchArea.RIGHT_TOP;
            return;
        }
        if (isInsideCornerLeftBottom(x, y)) {
            mTouchArea = TouchArea.LEFT_BOTTOM;
            return;
        }
        if (isInsideCornerRightBottom(x, y)) {
            mTouchArea = TouchArea.RIGHT_BOTTOM;
            return;
        }
        if (isInsideFrame(x, y)) {
            mTouchArea = TouchArea.CENTER;
            return;
        }
        mTouchArea = TouchArea.OUT_OF_BOUNDS;
        updateCurrentAndPreviousPoints(event);

        viewportWidth = (int) (mFrameRect.right - mFrameRect.left);
        viewportHeight = (int) (mFrameRect.bottom - mFrameRect.top);
    }

    private void onMove(MotionEvent e) {
        float diffX = e.getX() - mLastX;
        float diffY = e.getY() - mLastY;
        switch (mTouchArea) {
            case CENTER:
                moveFrame(diffX, diffY);
                break;
            case LEFT_TOP:
                moveHandleLT(diffX, diffY);
                break;
            case RIGHT_TOP:
                moveHandleRT(diffX, diffY);
                break;
            case LEFT_BOTTOM:
                moveHandleLB(diffX, diffY);
                break;
            case RIGHT_BOTTOM:
                moveHandleRB(diffX, diffY);
                break;
            case OUT_OF_BOUNDS:
                updateCurrentAndPreviousPoints(e);
                break;
        }
        mCropView.invalidate();
        mLastX = e.getX();
        mLastY = e.getY();

        viewportWidth = (int) (mFrameRect.right - mFrameRect.left);
        viewportHeight = (int) (mFrameRect.bottom - mFrameRect.top);
    }

    private boolean isInsideFrame(float x, float y) {
        if (mFrameRect.left <= x && mFrameRect.right >= x) {
            if (mFrameRect.top <= y && mFrameRect.bottom >= y) {
                mTouchArea = TouchArea.CENTER;
                return true;
            }
        }
        return false;
    }

    private float sq(float value) {
        return value * value;
    }

    private boolean isInsideCornerLeftTop(float x, float y) {
        float dx = x - mFrameRect.left;
        float dy = y - mFrameRect.top;
        float d = dx * dx + dy * dy;
        return sq(mHandleSize + mTouchPadding) >= d;
    }

    private boolean isInsideCornerRightTop(float x, float y) {
        float dx = x - mFrameRect.right;
        float dy = y - mFrameRect.top;
        float d = dx * dx + dy * dy;
        return sq(mHandleSize + mTouchPadding) >= d;
    }

    private boolean isInsideCornerLeftBottom(float x, float y) {
        float dx = x - mFrameRect.left;
        float dy = y - mFrameRect.bottom;
        float d = dx * dx + dy * dy;
        return sq(mHandleSize + mTouchPadding) >= d;
    }

    private boolean isInsideCornerRightBottom(float x, float y) {
        float dx = x - mFrameRect.right;
        float dy = y - mFrameRect.bottom;
        float d = dx * dx + dy * dy;
        return sq(mHandleSize + mTouchPadding) >= d;
    }

    private void moveFrame(float x, float y) {
        mFrameRect.left += x;
        mFrameRect.right += x;
        mFrameRect.top += y;
        mFrameRect.bottom += y;
        checkMoveBounds();

        viewportWidth = (int) (mFrameRect.right - mFrameRect.left);
        viewportHeight = (int) (mFrameRect.bottom - mFrameRect.top);

    }


    private float getFrameW() {
        return (mFrameRect.right - mFrameRect.left);
    }

    private float getFrameH() {
        return (mFrameRect.bottom - mFrameRect.top);
    }

    private boolean isWidthTooSmall() {
        return getFrameW() < mMinFrameWidth;
    }

    private boolean isHeightTooSmall() {
        return getFrameH() < mMinFrameHeight;
    }

    public void applyPositioningAndScale(Matrix matrix) {
        matrix.postTranslate(-bitmapWidth / 2.0f, -bitmapHeight / 2.0f);
        matrix.postScale(scale, scale);
        matrix.postTranslate(position.getX(), position.getY());
    }

    public void resetFor(int bitmapWidth, int bitmapHeight, int availableWidth, int availableHeight) {
        aspectRatio = cropViewConfig.getViewportRatio();
        imageBounds = new Rect(0, 0, availableWidth / 2, availableHeight / 2);
        setViewport(bitmapWidth, bitmapHeight, availableWidth, availableHeight);

        this.bitmapWidth = bitmapWidth;
        this.bitmapHeight = bitmapHeight;
        if (bitmapWidth > 0 && bitmapHeight > 0) {
            setMinimumScale();
            setLimits();
            resetPosition();
            ensureInsideViewport();
        }
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public void setViewportWidth(int width) {
        viewportWidth = width;
    }

    public void setViewportHeight(int height) {
        viewportHeight = height;
    }

    public float getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(float ratio) {
        aspectRatio = ratio;
        cropViewConfig.setViewportRatio(ratio);
    }

    private void handleDragGesture() {
        if (getDownCount() != 1) {
            return;
        }
        position.add(moveDelta(0));
    }

    private void handlePinchGesture() {
        if (getDownCount() != 2) {
            return;
        }
        updateScale();
        horizontalLimit = computeLimit((int) (bitmapWidth * scale), viewportWidth);
        verticalLimit = computeLimit((int) (bitmapHeight * scale), viewportHeight);
    }

    private void ensureInsideViewport() {
        if (imageBounds == null) {
            return;
        }

        float newY = position.getY();
        //Movable image everywhere.
       /* int bottom = imageBounds.bottom;

        if (bottom - newY >= verticalLimit) {
            newY = bottom - verticalLimit;
        } else if (newY - bottom >= verticalLimit) {
            newY = bottom + verticalLimit;
        }*/

        float newX = position.getX();
       /* int right = imageBounds.right;
        if (newX <= right - horizontalLimit) {
            newX = right - horizontalLimit;
        } else if (newX > right + horizontalLimit) {
            newX = right + horizontalLimit;
        }
*/
        position.set(newX, newY);
    }

    private void checkMoveBounds() {
        float diff = mFrameRect.left - mScreenFrameRect.left; //mFrameRect.left - mHandleSize;
        if (diff < 0) {
            mFrameRect.left -= diff;
            mFrameRect.right -= diff;
        }
        diff = mFrameRect.right - mScreenFrameRect.right; //mFrameRect.right - mCropView.getWidth() + mHandleSize;
        if (diff > 0) {
            mFrameRect.left -= diff;
            mFrameRect.right -= diff;
        }
        diff = mFrameRect.top - mScreenFrameRect.top; //mFrameRect.top - mHandleSize;
        if (diff < 0) {
            mFrameRect.top -= diff;
            mFrameRect.bottom -= diff;
        }
        diff = mFrameRect.bottom - mScreenFrameRect.bottom; //mFrameRect.bottom - mCropView.getHeight() + mHandleSize;
        if (diff > 0) {
            mFrameRect.top -= diff;
            mFrameRect.bottom -= diff;
        }
        viewportWidth = (int) (mFrameRect.right - mFrameRect.left);
        viewportHeight = (int) (mFrameRect.bottom - mFrameRect.top);
    }


    private boolean isInsideHorizontal(float x) {
        return mScreenFrameRect.left <= x && mScreenFrameRect.right >= x;
    }

    private boolean isInsideVertical(float y) {
        return mScreenFrameRect.top <= y && mScreenFrameRect.bottom >= y;
    }


    private void moveHandleRT(float diffX, float diffY) {
        float dx = diffX;
        float dy = diffX * getRatioY() / getRatioX();
        mFrameRect.right += dx;
        mFrameRect.top -= dy;
        if (isWidthTooSmall()) {
            float offsetX = mMinFrameWidth - getFrameW();
            mFrameRect.right += offsetX;
            float offsetY = offsetX * getRatioY() / getRatioX();
            mFrameRect.top -= offsetY;
        }
        if (isHeightTooSmall()) {
            float offsetY = mMinFrameHeight - getFrameH();
            mFrameRect.top -= offsetY;
            float offsetX = offsetY * getRatioX() / getRatioY();
            mFrameRect.right += offsetX;
        }
        float ox, oy;

        if (!isInsideHorizontal(mFrameRect.right)) {
            ox = mFrameRect.right - mScreenFrameRect.right;
            mFrameRect.right -= ox;
            oy = ox * getRatioY() / getRatioX();
            mFrameRect.top += oy;
        }
        if (!isInsideVertical(mFrameRect.top)) {
            oy = mScreenFrameRect.top - mFrameRect.top;
            mFrameRect.top += oy;
            ox = oy * getRatioX() / getRatioY();
            mFrameRect.right -= ox;
        }


      /*  float diffHorizontal = mFrameRect.right - mCropView.getWidth() + mHandleSize;

        if (diffHorizontal > 0)
        {  //!isInsideHorizontal(mFrameRect.right)) {
            ox = mFrameRect.right - mCropView.getWidth() + mHandleSize;
            mFrameRect.right -= ox;
            oy = ox * getRatioY() / getRatioX();
            mFrameRect.top += oy;
        }

        float diffs = mFrameRect.top - mHandleSize;

        if (diffs < 0 && !isInsideVertical(mFrameRect.top))
        {
          oy = mFrameRect.top;
            mFrameRect.top += oy;
            ox = oy * getRatioX() / getRatioY();
            mFrameRect.right -= ox;

        }*/

        viewportWidth = (int) (mFrameRect.right - mFrameRect.left);
        viewportHeight = (int) (mFrameRect.bottom - mFrameRect.top);

        Log.e("CropView", "moveHandleRT frame : " + mFrameRect);
    }

    private void moveHandleLB(float diffX, float diffY) {
        float dx = diffX;
        float dy = diffX * getRatioY() / getRatioX();
        mFrameRect.left += dx;
        mFrameRect.bottom -= dy;
        if (isWidthTooSmall()) {
            float offsetX = mMinFrameWidth - getFrameW();
            mFrameRect.left -= offsetX;
            float offsetY = offsetX * getRatioY() / getRatioX();
            mFrameRect.bottom += offsetY;
        }
        if (isHeightTooSmall()) {
            float offsetY = mMinFrameHeight - getFrameH();
            mFrameRect.bottom += offsetY;
            float offsetX = offsetY * getRatioX() / getRatioY();
            mFrameRect.left -= offsetX;
        }

        float ox, oy;
        if (!isInsideHorizontal(mFrameRect.left)) {
            ox = mScreenFrameRect.left - mFrameRect.left;
            mFrameRect.left += ox;
            oy = ox * getRatioY() / getRatioX();
            mFrameRect.bottom -= oy;
        }
        if (!isInsideVertical(mFrameRect.bottom)) {
            oy = mFrameRect.bottom - mScreenFrameRect.bottom;
            mFrameRect.bottom -= oy;
            ox = oy * getRatioX() / getRatioY();
            mFrameRect.left += ox;
        }


       /* float diffleft = mFrameRect.left - mHandleSize;
        if (diffleft < 0 )//&& !isInsideHorizontal(mFrameRect.left))
        {
            ox =  mFrameRect.left;
            mFrameRect.left += ox;
            oy = ox * getRatioY() / getRatioX();
            mFrameRect.bottom -= oy;

        }

        float diff = mFrameRect.bottom - mCropView.getHeight() + mHandleSize;

        if ( diff > 0)//!isInsideVertical(mFrameRect.bottom)) {
        { oy = mFrameRect.bottom - mCropView.getHeight() + mHandleSize;
            mFrameRect.bottom -= oy;
            ox = oy * getRatioX() / getRatioY();
            mFrameRect.left += ox;

        }*/

        viewportWidth = (int) (mFrameRect.right - mFrameRect.left);
        viewportHeight = (int) (mFrameRect.bottom - mFrameRect.top);
        Log.e("CropView", "moveHandleLB frame : " + mFrameRect);
    }

    private void moveHandleRB(float diffX, float diffY) {
        float dx = diffX;
        float dy = diffX * getRatioY() / getRatioX();
        mFrameRect.right += dx;
        mFrameRect.bottom += dy;
        if (isWidthTooSmall()) {
            float offsetX = mMinFrameWidth - getFrameW();
            mFrameRect.right += offsetX;
            float offsetY = offsetX * getRatioY() / getRatioX();
            mFrameRect.bottom += offsetY;
        }
        if (isHeightTooSmall()) {
            float offsetY = mMinFrameHeight - getFrameH();
            mFrameRect.bottom += offsetY;
            float offsetX = offsetY * getRatioX() / getRatioY();
            mFrameRect.right += offsetX;
        }

        float ox, oy;
        /*float diffHorizontal = mFrameRect.right - mCropView.getWidth() + mHandleSize;

        if (diffHorizontal > 0)
        {
            ox =  mFrameRect.right - mCropView.getWidth() + mHandleSize;
            mFrameRect.right -= ox;
            oy = ox * getRatioY() / getRatioX();
            mFrameRect.bottom -= oy;
        }

        float diff = mFrameRect.bottom - mCropView.getHeight() + mHandleSize;
        if ( diff > 0)
        {
            oy = mFrameRect.bottom - mCropView.getHeight() + mHandleSize;
            mFrameRect.bottom -= oy;
            ox = oy * getRatioX() / getRatioY();
            mFrameRect.right -= ox;
        }
*/
        if (!isInsideHorizontal(mFrameRect.right)) {
            ox = mFrameRect.right - mScreenFrameRect.right;
            mFrameRect.right -= ox;
            oy = ox * getRatioY() / getRatioX();
            mFrameRect.bottom -= oy;
        }
        if (!isInsideVertical(mFrameRect.bottom)) {
            oy = mFrameRect.bottom - mScreenFrameRect.bottom;
            mFrameRect.bottom -= oy;
            ox = oy * getRatioX() / getRatioY();
            mFrameRect.right -= ox;
        }

        viewportWidth = (int) (mFrameRect.right - mFrameRect.left);
        viewportHeight = (int) (mFrameRect.bottom - mFrameRect.top);

        Log.e("CropView", "moveHandleRB frame : " + mFrameRect);
    }

    private void moveHandleLT(float diffX, float diffY) {
        float dx = diffX;
        float dy = diffX * getRatioY() / getRatioX();
        mFrameRect.left += dx;
        mFrameRect.top += dy;
        if (isWidthTooSmall()) {
            float offsetX = mMinFrameWidth - getFrameW();
            mFrameRect.left -= offsetX;
            float offsetY = offsetX * getRatioY() / getRatioX();
            mFrameRect.top -= offsetY;
        }
        if (isHeightTooSmall()) {
            float offsetY = mMinFrameHeight - getFrameH();
            mFrameRect.top -= offsetY;
            float offsetX = offsetY * getRatioX() / getRatioY();
            mFrameRect.left -= offsetX;
        }
        float ox, oy;

        /*float diff = mFrameRect.left - mHandleSize;
        if (diff< 0 && diff <= -10  && mFrameRect.left <=mHandleSize && !isInsideHorizontal(diff))
        {
            ox = diff+mHandleSize;
            mFrameRect.left += ox;
            oy = ox * getRatioY() / getRatioX();
            mFrameRect.top += oy;
            Log.v("crop", "inside horizontal");
        }

        float diffs = mFrameRect.top - mHandleSize;

        if (diffs < 0 && !isInsideVertical(mFrameRect.top))
        {
            oy = mFrameRect.top;
            mFrameRect.top += oy;
            ox = oy * getRatioX() / getRatioY();
            mFrameRect.left += ox;
            Log.w("crop", "inside verical");
        }*/

        if (!isInsideHorizontal(mFrameRect.left)) {
            ox = mScreenFrameRect.left - mFrameRect.left;
            mFrameRect.left += ox;
            oy = ox * getRatioY() / getRatioX();
            mFrameRect.top += oy;
        }
        if (!isInsideVertical(mFrameRect.top)) {
            oy = mScreenFrameRect.top - mFrameRect.top;
            mFrameRect.top += oy;
            ox = oy * getRatioX() / getRatioY();
            mFrameRect.left += ox;
        }

        viewportWidth = (int) (mFrameRect.right - mFrameRect.left);
        viewportHeight = (int) (mFrameRect.bottom - mFrameRect.top);

        Log.e("CropView", "moveHandleLT frame : " + mFrameRect);
    }


    private void updateCurrentAndPreviousPoints(MotionEvent event) {
        for (int i = 0; i < maxNumberOfTouchPoints; i++) {
            if (i < event.getPointerCount()) {
                final float eventX = event.getX(i);
                final float eventY = event.getY(i);

                if (points[i] == null) {
                    points[i] = new TouchPoint(eventX, eventY);
                    previousPoints[i] = null;
                } else {
                    if (previousPoints[i] == null) {
                        previousPoints[i] = new TouchPoint();
                    }
                    previousPoints[i].copy(points[i]);
                    points[i].set(eventX, eventY);
                }
            } else {
                previousPoints[i] = null;
                points[i] = null;
            }
        }
    }


    private void setLimits() {

        horizontalLimit = computeLimit((int) (bitmapWidth * minimumScale), viewportWidth);
        verticalLimit = computeLimit((int) (bitmapHeight * minimumScale), viewportHeight);

        Log.e("Cropview", " horizontal " + horizontalLimit + " vertical " + verticalLimit);
    }

    private void resetPosition() {
        position.set(imageBounds.right, imageBounds.bottom);
    }

    private void setMinimumScale() {
        final float fw = (float) viewportWidth  / bitmapWidth;
        final float fh = (float) viewportHeight / bitmapHeight;
        minimumScale = Math.min(fw, fh);//CropViewConfig.DEFAULT_MINIMUM_SCALE; //Math.max(fw, fh);
        scale = Math.max(scale, minimumScale);

        Log.e("CropView", " " + scale + " fw " + fw + " fh " + fh + " minscale " + minimumScale);
    }

    private void updateScale() {
        TouchPoint current = vector(points[0], points[1]);
        TouchPoint previous = previousVector(0, 1);
        float currentDistance = current.getLength();
        float previousDistance = previous.getLength();

        float newScale = scale;
        if (previousDistance != 0) {
            newScale *= currentDistance / previousDistance;
        }
        // disable minimum scale
        newScale = newScale < minimumScale ? minimumScale : newScale;
        newScale = newScale > maximumScale ? maximumScale : newScale;
        scale = newScale;
    }

    private boolean isPressed(int index) {
        return points[index] != null;
    }

    private int getDownCount() {
        int count = 0;
        for (TouchPoint point : points) {
            if (point != null) {
                count++;
            }
        }
        return count;
    }

    private TouchPoint moveDelta(int index) {
        if (isPressed(index)) {
            TouchPoint previous =
                    previousPoints[index] != null ? previousPoints[index] : points[index];
            return TouchPoint.subtract(points[index], previous);
        } else {
            return new TouchPoint();
        }
    }

    private TouchPoint previousVector(int indexA, int indexB) {
        return previousPoints[indexA] == null || previousPoints[indexB] == null
                ? vector(points[indexA], points[indexB])
                : vector(previousPoints[indexA], previousPoints[indexB]);
    }

    private static int computeLimit(int bitmapSize, int viewportSize) {
        return (bitmapSize - viewportSize) / 2;
    }

    private static TouchPoint vector(TouchPoint a, TouchPoint b) {
        return TouchPoint.subtract(b, a);
    }

    private static boolean isUpAction(int actionMasked) {
        return actionMasked == MotionEvent.ACTION_POINTER_UP || actionMasked == MotionEvent.ACTION_UP;
    }
}
