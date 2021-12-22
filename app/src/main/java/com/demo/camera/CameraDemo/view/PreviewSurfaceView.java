package com.demo.camera.CameraDemo.view;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.Display;
import android.view.SurfaceView;
import android.view.WindowManager;

public class PreviewSurfaceView extends SurfaceView {

    private static final double ASPECT_TOLERANCE = 0.03;
    private double mAspectRatio = 0.0;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;

    public PreviewSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
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
        boolean originalPreviewIsLandscape = (mPreviewWidth > mPreviewHeight);
        boolean configurationIsLandscape =
                (getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        // if configuration is changed, swap to view's configuration
//        if (originalPreviewIsLandscape != configurationIsLandscape) {
//            int originalPreviewWidth = previewWidth;
//            previewWidth = previewHeight;
//            previewHeight = originalPreviewWidth;
//        }
        setMeasuredDimension(previewWidth, previewHeight);
    }

    protected boolean isFullScreenPreview(double aspectRatio) {
        double fullScreenRatio = findFullscreenRatio(getContext());
        if (Math.abs((aspectRatio - fullScreenRatio)) <= ASPECT_TOLERANCE) {
            return true;
        } else {
            return false;
        }
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
