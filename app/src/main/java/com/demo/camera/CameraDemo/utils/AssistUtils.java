package com.demo.camera.CameraDemo.utils;


import android.graphics.ImageFormat;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AssistUtils {

    private static final LogUtil.Tag TAG = new LogUtil.Tag(AssistUtils.class.getSimpleName());

    public static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        LogHelper.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    public static Size choosePreviewSize(Size[] preview, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : preview) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            LogHelper.e(TAG, "Couldn't find any suitable preview size");
            return preview[0];
        }
    }

    public static Size choosePictureSize(Size[] picture) {
        Size largest = Collections.max(Arrays.asList(picture), new CompareSizesByArea());
        return largest;
    }


    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
