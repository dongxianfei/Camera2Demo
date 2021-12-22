package com.demo.camera.CameraDemo.view;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.Display;
import android.view.TextureView;
import android.view.WindowManager;

public class PreviewTextureView extends TextureView {

    private static final double ASPECT_TOLERANCE = 0.03;
    private double mAspectRatio = 0.0;

    public PreviewTextureView(Context context) {
        this(context, null);
    }

    public PreviewTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreviewTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAspectRatio(double aspectRatio) {
        if (mAspectRatio != aspectRatio) {
            mAspectRatio = aspectRatio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int previewWidth = MeasureSpec.getSize(widthMeasureSpec);
        int previewHeight = MeasureSpec.getSize(heightMeasureSpec);
        boolean widthLonger = previewWidth > previewHeight;
        int longSide = (widthLonger ? previewWidth : previewHeight);
        int shortSide = (widthLonger ? previewHeight : previewWidth);
        if (mAspectRatio > 0) {
            double fullScreenRatio = findFullscreenRatio(getContext());
            if (Math.abs((mAspectRatio - fullScreenRatio)) <= ASPECT_TOLERANCE) {
                // full screen preview case
                if (longSide < shortSide * mAspectRatio) {
                    longSide = Math.round((float) (shortSide * mAspectRatio) / 2) * 2;
                } else {
                    shortSide = Math.round((float) (longSide / mAspectRatio) / 2) * 2;
                }
            } else {
                // standard (4:3) preview case
                if (longSide > shortSide * mAspectRatio) {
                    longSide = Math.round((float) (shortSide * mAspectRatio) / 2) * 2;
                } else {
                    shortSide = Math.round((float) (longSide / mAspectRatio) / 2) * 2;
                }
            }
        }
        if (widthLonger) {
            previewWidth = longSide;
            previewHeight = shortSide;
        } else {
            previewWidth = shortSide;
            previewHeight = longSide;
        }
        setMeasuredDimension(previewWidth, previewHeight);
    }

    private static double findFullscreenRatio(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point point = new Point();
        display.getRealSize(point);

        double fullscreen;
        if (point.x > point.y) {
            fullscreen = (double) point.x / point.y;
        } else {
            fullscreen = (double) point.y / point.x;
        }
        return fullscreen;
    }
}
