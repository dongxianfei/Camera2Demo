package com.demo.camera.CameraDemo.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.os.Build;
import android.os.Process;
import android.util.Size;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class CameraUtils {

    private static final LogUtil.Tag TAG = new LogUtil.Tag(CameraUtils.class.getSimpleName());

    private static final double ASPECT_TOLERANCE = 0.02;
    private static final int TEMPLATE_STILL_CAPTURE = 2;
    public static final int ORIENTATION_UNKNOWN = -1;
    private static final String PICTURE_RATIO_4_3 = "1.3333";

    public static final short TOP_LEFT = 1;
    public static final short TOP_RIGHT = 2;
    public static final short BOTTOM_LEFT = 3;
    public static final short BOTTOM_RIGHT = 4;
    public static final short LEFT_TOP = 5;
    public static final short RIGHT_TOP = 6;
    public static final short LEFT_BOTTOM = 7;
    public static final short RIGHT_BOTTOM = 8;


    public static final double RATIO_16_9 = 16d / 9;
    public static final double RATIO_5_3 = 5d / 3;
    public static final double RATIO_3_2 = 3d / 2;
    public static final double RATIO_4_3 = 4d / 3;
    private static final double RATIOS[] = {RATIO_16_9, RATIO_5_3, RATIO_3_2, RATIO_4_3};

    /**
     * Get CameraCharacteristics object.
     *
     * @param context  current Context.
     * @param cameraId current camera id.
     */
    public static CameraCharacteristics getCameraCharacteristics(Context context, String cameraId) {
        CameraCharacteristics cs = null;
        try {
            CameraManager cameraManager = getCameraManager(context);
            cs = cameraManager.getCameraCharacteristics(cameraId);
        } catch (Exception e) {
            e.printStackTrace();
            LogHelper.e(TAG, "camera process killed due to getCameraCharacteristics() error");
            Process.killProcess(Process.myPid());
        }
        return cs;
    }

    public static CameraManager getCameraManager(Context context) {
        return (CameraManager) context.getSystemService(context.CAMERA_SERVICE);
    }

    /**
     * Calculate current device screen orientation.
     *
     * @param activity Current app activity.
     * @return ActivityInfo's screen orientation value.
     */
    public static int calculateCurrentScreenOrientation(Activity activity) {
        int displayRotation = getDisplayRotation(activity);
        LogHelper.d(TAG, "calculateCurrentScreenOrientation displayRotation = " + displayRotation);
        if (displayRotation == 0) {
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else if (displayRotation == 90) {
            return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        } else if (displayRotation == 180) {
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        } else if (displayRotation == 270) {
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        }
        return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }

    /**
     * Get current camera display rotation.
     *
     * @param activity camera activity.
     * @return the activity orientation.
     */
    public static int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    /**
     * get activity display orientation.
     *
     * @param degrees  activity rotation.
     * @param cameraId the preview camera id.
     * @param context  current context.
     * @return according camera id and display rotation to calculate the orientation.
     */
    public static int getDisplayOrientation(int degrees, String cameraId, Context context) {
        // See android.hardware.Camera.setDisplayOrientation for documentation.
        int result;
        CameraCharacteristics characteristics = getCameraCharacteristics(
                context, cameraId);
        if (characteristics == null) {
            LogHelper.e(TAG, "[getV2DisplayOrientation] characteristics is null");
            return 0;
        }
        if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
            LogHelper.w(TAG, "[getV2DisplayOrientation] unknown  degrees");
            return 0;
        }
        int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (facing == CameraMetadata.LENS_FACING_FRONT) {
            result = (sensorOrientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (sensorOrientation - degrees + 360) % 360;
        }
        return result;
    }

    public static int getSensorOrientation(String cameraId) {
        int orientation = 0;
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(Integer.parseInt(cameraId), info);
            orientation = info.orientation;
        } catch (Exception e) {
            e.printStackTrace();
            LogHelper.e(TAG, "[getCameraInfoOrientation] camera process killed due to" +
                    " getCameraInfo() error");
            Process.killProcess(Process.myPid());
        }
        LogHelper.d(TAG, "[getCameraInfoOrientation] orientation = " + orientation);
        return orientation;
    }

    /**
     * Get the camera orientation from camera info.
     *
     * @param cameraId the target camera id.
     * @param activity current activity.
     * @return orientation value.
     */
    public int getSensorOrientation(String cameraId, Activity activity) {
        CameraCharacteristics characteristics = getCameraCharacteristics(activity, cameraId);
        if (characteristics == null) {
            LogHelper.e(TAG, "[getCameraInfoOrientation] characteristics is null");
            return 0;
        }
        int orientation = characteristics
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
        return orientation;

    }

    /**
     * 500       * get the correct rotation for jpeg.
     * 501       * @param cameraId the camera id.
     * 502       * @param sensorOrientation current g-sensor orientation.
     * 503       * @param context current context.
     * 504       * @return the rotation of the jpeg.
     * 505
     */
    public static int getJpegRotation(String cameraId, int sensorOrientation, Context context) {
        //if sensorOrientation unknown, set the orientation to 0, and then
        if (sensorOrientation == ORIENTATION_UNKNOWN) {
            sensorOrientation = 0;
        }
        //get the sensor install orientation.
        int result;
        CameraCharacteristics characteristics = getCameraCharacteristics(
                context, cameraId);
        if (characteristics == null) {
            LogHelper.e(TAG, "[getJpegRotationFromDeviceSpec] characteristics is null");
            return 0;
        }
        int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        int orientation = characteristics
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (facing == CameraMetadata.LENS_FACING_FRONT) {
            result = (orientation - sensorOrientation + 360) % 360;
        } else {
            result = (orientation + sensorOrientation) % 360;
        }
        return result;
    }

    /**
     * 299       * Check the camera is need mirror or not.
     * 300       * @param cameraId current camera id.
     * 301       * @return true means need mirror.
     * 302
     */
    public boolean isMirror(String cameraId) {
        boolean mirror = false;
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(Integer.parseInt(cameraId), info);
            mirror = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        } catch (Exception e) {
            e.printStackTrace();
            LogHelper.e(TAG, "[isMirror] camera process killed due to getCameraInfo() error");
            Process.killProcess(Process.myPid());
        }
        return mirror;
    }

    /**
     * Check the camera is need mirror or not.
     *
     * @param cameraId current camera id.
     * @param activity current activity.
     * @return true means need mirror.
     */
    public boolean isMirror(String cameraId, Activity activity) {

        CameraCharacteristics characteristics = getCameraCharacteristics(activity, cameraId);
        if (characteristics == null) {
            LogHelper.e(TAG, "[isMirror] characteristics is null");
            return false;
        }
        int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        int sensorOrientation = characteristics
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
        return (facing == CameraMetadata.LENS_FACING_FRONT);
    }

    /**
     * this function is used for get orientation which is set
     * to media recorder ,the return value is compute by GSensor and
     * activity display orientation.
     *
     * @param orientation GSensor orientation
     * @param info        Camera info
     * @return orientation set to recorder
     */
    public static int getRecordingRotation(int orientation, Camera.CameraInfo info) {
        int rotation;
        if (orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - orientation + 360) % 360;
            } else {
                rotation = (info.orientation + orientation) % 360;
            }
        } else {
            rotation = info.orientation;
        }
        LogHelper.d(TAG, "[getRecordingRotation] orientation = " +
                orientation + " info " + info + " rotation = " + rotation);
        return rotation;
    }

    /**
     * Get recording rotation.
     *
     * @param orientation the g-sensor's orientation.
     * @param cameraId    the camera id.
     * @param context     current context.
     * @return the recording rotation.
     */
    public static int getRecordingRotation(int orientation, String cameraId, Context context) {
        LogHelper.d(TAG, "[getRecordingRotation]orientation = " + orientation +
                ",cameraId = " + cameraId);
        CameraCharacteristics characteristics = getCameraCharacteristics(context, cameraId);
        if (characteristics == null) {
            LogHelper.e(TAG, "[getRecordingRotation] characteristics is null");
            return 0;
        }
        return getRecordingRotation(orientation, characteristics);
    }

    /**
     * this function is used for get orientation which is set
     * to media recorder ,the return value is compute by GSensor and
     * activity display orientation and used for api2.
     *
     * @param orientation     GSensor orientation
     * @param characteristics camera characteristics
     * @return result orientation
     */
    public static int getRecordingRotation(int orientation,
                                           CameraCharacteristics characteristics) {
        int rotation;
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean facingFront = characteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraCharacteristics.LENS_FACING_FRONT;
        LogHelper.i(TAG, "orientation = " + orientation + ",sensorOrientation = "
                + sensorOrientation + ",facingFront = " + facingFront);
        if (orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            if (facingFront) {
                rotation = (sensorOrientation - orientation + 360) % 360;
            } else {
                rotation = (sensorOrientation + orientation) % 360;
            }
        } else {
            rotation = sensorOrientation;
        }
        return rotation;
    }

    /**
     * Get the jpeg orientation from the data.
     *
     * @param data the resource data
     * @return the orientation of the data,such as 0/90/180/270;
     */
    public static int getOrientationFromSdkExif(byte[] data) {
        int orientation = 0;
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(data);
            android.media.ExifInterface exif = new android.media.ExifInterface(stream);
            Integer value = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
                    0);
            switch (value.shortValue()) {
                case TOP_LEFT:
                    orientation = 0;
                    break;
                case RIGHT_TOP:
                    orientation = 90;
                    break;
                case BOTTOM_LEFT:
                    orientation = 180;
                    break;
                case RIGHT_BOTTOM:
                    orientation = 270;
                    break;
                default:
                    orientation = 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return orientation;
    }

    /**
     * Get the jpeg size form the jpegData.
     *
     * @param jpeg the resource data
     * @return the size of the jpegData.
     */
    public static Size getSizeFromSdkExif(byte[] jpeg) {
        int width = 0;
        int height = 0;
        try {
            android.media.ExifInterface exifInterface = new android.media.ExifInterface(new ByteArrayInputStream(jpeg));
            width = exifInterface.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_WIDTH, 0);
            height = exifInterface.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_LENGTH, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        LogHelper.d(TAG, "[getSizeFromSdkExif] width = " + width + ",height = " + height);
        return new Size(width, height);
    }

    /**
     * Get the exif from sdk exif interface.
     *
     * @param path the sdcard path.
     * @return the exif.
     */
    public static Size getSizeFromSdkExif(String path) {
        int width = 0;
        int height = 0;
        try {
            android.media.ExifInterface exifInterface = new android.media.ExifInterface(path);
            width = exifInterface.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_WIDTH, 0);
            height = exifInterface.getAttributeInt(android.media.ExifInterface.TAG_IMAGE_LENGTH, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        LogHelper.d(TAG, "[getSizeFromSdkExif] width = " + width + ",height = " + height);
        return new Size(width, height);
    }

    /**
     * Filter supported sizes by peak size.
     *
     * @param originalSupportedSizes original supported sizes.
     * @param peakSize               the peak size.
     * @return result supported sizes.
     */
    public static ArrayList<Size> filterSupportedSizes(ArrayList<Size> originalSupportedSizes,
                                                       Size peakSize) {
        ArrayList<Size> resultSizes = new ArrayList<>();
        if (peakSize == null || originalSupportedSizes == null ||
                originalSupportedSizes.size() <= 0) {
            return resultSizes;
        }
        for (Size size : originalSupportedSizes) {
            if (size.getWidth() <= peakSize.getWidth() &&
                    size.getHeight() <= peakSize.getHeight()) {
                resultSizes.add(size);
            }
        }
        return resultSizes;
    }


    /**
     * Get the best match preview size which's the ratio is closely picture ratio and screen ratio.
     *
     * @param activity                 current activity.
     * @param sizes                    all of supported preview size.
     * @param previewRatio             the picture ratio.
     * @param needMatchTargetPanelSize whether need match the panel sizes.
     * @return the best match preview size.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static Size getOptimalPreviewSize(Activity activity,
                                             List<Size> sizes,
                                             double previewRatio,
                                             boolean needMatchTargetPanelSize) {

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point point = new Point();
        display.getRealSize(point);
        int panelHeight = Math.min(point.x, point.y);
        int panelWidth = (int) (previewRatio * panelHeight);

        Size optimalSize = null;
        if (needMatchTargetPanelSize) {
            LogHelper.i(TAG, "ratio mapping panel size: (" + panelWidth + ", " + panelHeight + ")");
            optimalSize = findBestMatchPanelSize(
                    sizes, previewRatio, panelWidth, panelHeight);
            if (optimalSize != null) {
                return optimalSize;
            }
        }

        double minDiffHeight = Double.MAX_VALUE;
        if (optimalSize == null) {
            LogHelper.w(TAG, "[getPreviewSize] no preview size match the aspect ratio : " +
                    previewRatio + ", then use standard 4:3 for preview");
            previewRatio = Double.parseDouble(PICTURE_RATIO_4_3);
            for (Size size : sizes) {
                double ratio = (double) size.getWidth() / size.getHeight();
                if (Math.abs(ratio - previewRatio) > ASPECT_TOLERANCE) {
                    continue;
                }
                if (Math.abs(size.getHeight() - panelHeight) < minDiffHeight) {
                    optimalSize = size;
                    minDiffHeight = Math.abs(size.getHeight() - panelHeight);
                }
            }
        }
        return optimalSize;
    }

    //find the preview size which is greater or equal to panel size and closest to panel size
    public static Size findBestMatchPanelSize(List<Size> sizes,
                                              double targetRatio, int panelWidth, int panelHeight) {
        double minDiff;
        double minDiffMax = Double.MAX_VALUE;
        Size bestMatchSize = null;
        for (Size size : sizes) {
            double ratio = (double) size.getWidth() / size.getHeight();
            // filter out the size which not tolerated by target ratio
            if (Math.abs(ratio - targetRatio) <= ASPECT_TOLERANCE) {
                // find the size closest to panel size
                minDiff = Math.abs(size.getHeight() - panelHeight);
                if (minDiff <= minDiffMax) {
                    minDiffMax = minDiff;
                    bestMatchSize = size;
                }
            }
        }
        LogHelper.i(TAG, "findBestMatchPanelSize size: "
                + bestMatchSize.getWidth() + " X " + bestMatchSize.getHeight());
        return bestMatchSize;
    }

    /**
     * getAvailableSessionKeys
     *
     * @param characteristics the camera characteristics.
     * @param key             the request key.
     */
    public static CaptureRequest.Key<int[]>
    getAvailableSessionKeys(CameraCharacteristics characteristics, String key) {
        if (characteristics == null) {
            LogHelper.i(TAG, "[getAvailableSessionKeys] characteristics is null");
            return null;
        }
        CaptureRequest.Key<int[]> requestSessionKey = null;
        List<CaptureRequest.Key<?>> requestKeyList =
                characteristics.getAvailableSessionKeys();
        if (requestKeyList == null) {
            LogHelper.i(TAG, "[getAvailableSessionKeys] No keys!");
            return null;
        }
        for (CaptureRequest.Key<?> requestKey : requestKeyList) {
            if (requestKey.getName().equals(key)) {
                LogHelper.i(TAG, "[getAvailableSessionKeys] key :" + key);
                requestSessionKey =
                        (CaptureRequest.Key<int[]>) requestKey;
            }
        }
        return requestSessionKey;
    }

    /**
     * getRequestKey
     *
     * @param characteristics the camera characteristics.
     * @param key             the request key.
     */
    public static CaptureRequest.Key<int[]>
    getRequestKey(CameraCharacteristics characteristics, String key) {
        if (characteristics == null) {
            LogHelper.i(TAG, "[getRequestKey] characteristics is null");
            return null;
        }
        CaptureRequest.Key<int[]> keyRequest = null;
        List<CaptureRequest.Key<?>> requestKeyList =
                characteristics.getAvailableCaptureRequestKeys();
        if (requestKeyList == null) {
            LogHelper.i(TAG, "[getRequestKey] No keys!");
            return null;
        }
        for (CaptureRequest.Key<?> requestKey : requestKeyList) {
            if (requestKey.getName().equals(key)) {
                LogHelper.i(TAG, "[getRequestKey] key :" + key);
                keyRequest = (CaptureRequest.Key<int[]>) requestKey;
            }
        }
        return keyRequest;
    }

    /**
     * getResultKey
     *
     * @param characteristics the camera characteristics.
     * @param key             the result key.
     */
    public static CaptureResult.Key<int[]>
    getResultKey(CameraCharacteristics characteristics, String key) {
        if (characteristics == null) {
            LogHelper.i(TAG, "[getResultKey] characteristics is null");
            return null;
        }
        CaptureResult.Key<int[]> keyResult = null;
        List<CaptureResult.Key<?>> resultKeyList =
                characteristics.getAvailableCaptureResultKeys();
        if (resultKeyList == null) {
            LogHelper.i(TAG, "[getResultKey] No keys!");
            return null;
        }
        for (CaptureResult.Key<?> resultKey : resultKeyList) {
            if (resultKey.getName().equals(key)) {
                LogHelper.i(TAG, "[getResultKey] key : " + key);
                keyResult = (CaptureResult.Key<int[]>) resultKey;
            }
        }
        return keyResult;
    }

    /**
     * Get static metadata key collect.
     *
     * @param characteristics the camera characteristics.
     * @param key             the support key.
     */
    public static int[] getStaticKeyResult(CameraCharacteristics characteristics, String key) {
        if (characteristics == null) {
            LogHelper.i(TAG, "[getStaticKeyResult] characteristics is null");
            return null;
        }
        int[] availableKeys = null;
        List<CameraCharacteristics.Key<?>> keyList = characteristics.getKeys();
        if (keyList == null) {
            LogHelper.i(TAG, "[getStaticKeyResult] No keys!");
            return null;
        }
        for (CameraCharacteristics.Key<?> tempKey : keyList) {
            if (tempKey.getName().equals(key)) {
                LogHelper.i(TAG, "[getStaticKeyResult] key: " + key);
                CameraCharacteristics.Key<int[]> availableKey =
                        (CameraCharacteristics.Key<int[]>) tempKey;
                availableKeys = characteristics.get(availableKey);
            }
        }
        return availableKeys;
    }

    /**
     * Judge if current device has navigation bar or not.
     *
     * @param activity The activity instance.
     * @return True if device has navigation bar.
     * False if device has no navigation bar.
     */
    public static boolean isHasNavigationBar(Activity activity) {
        Point size = new Point();
        Point realSize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(size);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            activity.getWindowManager().getDefaultDisplay().getRealSize(realSize);
        } else {
            Display display = activity.getWindowManager().getDefaultDisplay();

            Method mGetRawH = null;
            Method mGetRawW = null;

            int realWidth = 0;
            int realHeight = 0;

            try {
                mGetRawW = Display.class.getMethod("getRawWidth");
                mGetRawH = Display.class.getMethod("getRawHeight");
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }

            try {
                realWidth = (Integer) mGetRawW.invoke(display);
                realHeight = (Integer) mGetRawH.invoke(display);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            realSize.set(realWidth, realHeight);
        }
        if (realSize.equals(size)) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Get current device navigation bar height.
     *
     * @param activity The activity instance.
     * @return If current device has a navigation bar ,return it's height.
     * If current device has no navigation bar, return -1.
     */
    public static int getNavigationBarHeight(Activity activity) {
        if (isHasNavigationBar(activity)) {
            //get navigation bar height.
            int resourceId = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            int navigationBarHeight = activity.getResources()
                    .getDimensionPixelSize(resourceId);
            return navigationBarHeight;
        } else {
            return -1;
        }
    }

    /**
     * Whether has focuser and can do auto focus.
     *
     * @param characteristics The characteristics.
     * @return True if there is focuser.
     */
    public static boolean hasFocuser(CameraCharacteristics characteristics) {
        if (characteristics == null) {
            LogHelper.w(TAG, "[hasFocuser] characteristics is null");
            return false;
        }
        Float minFocusDistance = characteristics.get(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        if (minFocusDistance != null && minFocusDistance > 0) {
            return true;
        }

        // Check available AF modes
        int[] availableAfModes = characteristics.get(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);

        if (availableAfModes == null) {
            return false;
        }

        // Assume that if we have an AF mode which doesn't ignore AF trigger, we have a focuser
        boolean hasFocuser = false;
        loop:
        for (int mode : availableAfModes) {
            switch (mode) {
                case CameraMetadata.CONTROL_AF_MODE_AUTO:
                case CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
                case CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                case CameraMetadata.CONTROL_AF_MODE_MACRO:
                    hasFocuser = true;
                    break loop;
                default:
                    break;
            }
        }
        LogHelper.d(TAG, "[hasFocuser] hasFocuser = " + hasFocuser);
        return hasFocuser;
    }


    public static boolean hasFlash(CameraCharacteristics characteristics) {
        boolean hasFlash = false;

        if (characteristics == null) {
            LogHelper.w(TAG, "[hasFlash] characteristics is null");
            return false;
        }

        hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        if (hasFlash) {
            LogHelper.d(TAG, "hasFlash1 = " + hasFlash);
            return hasFlash;
        }

        int[] availableAeModes = characteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
        if (availableAeModes == null) {
            return false;
        }
        loop:
        for (int mode : availableAeModes) {
            switch (mode) {
                case CameraMetadata.CONTROL_AE_MODE_ON_EXTERNAL_FLASH:
                    hasFlash = true;
                    break loop;
                default:
                    break;
            }
        }
        LogHelper.d(TAG, "hasFlash2 = " + hasFlash);
        return hasFlash;
    }

    /**
     * Check the camera id is facing front or not
     *
     * @param context  Current application context
     * @param cameraId The camera id you want to check
     * @return Return true if facing front, return false if facing back
     */
    public static boolean isCameraFacingFront(Context context, String cameraId) {
        CameraCharacteristics characteristics =
                getCameraCharacteristics(context, cameraId);
        if (characteristics == null) {
            return false;
        }
        int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        return facing == CameraMetadata.LENS_FACING_FRONT;
    }

    /**
     * to check whether it is still capture.
     *
     * @param result the CaptureResult.
     * @return true. it is.
     */

    public static boolean isStillCaptureTemplate(CaptureResult result) {
        try {
            if (TEMPLATE_STILL_CAPTURE == result.get(result.CONTROL_CAPTURE_INTENT)) {
                return true;
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "[isStillCaptureTemplate] frame = " + result.getFrameNumber());
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
        return false;

    }

    /**
     * to check whether it is still capture.
     *
     * @param request the CaptureRequest.
     * @return true. it is.
     */

    public static boolean isStillCaptureTemplate(CaptureRequest request) {
        try {
            if (TEMPLATE_STILL_CAPTURE == request.get(request.CONTROL_CAPTURE_INTENT)) {
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());

        }
        return false;

    }

    /**
     * to check whether it is still capture.
     *
     * @param builder the CaptureRequest Builder.
     * @return true. it is.
     */

    public static boolean isStillCaptureTemplate(CaptureRequest.Builder builder) {
        try {
            if (TEMPLATE_STILL_CAPTURE == builder.get(CaptureRequest.CONTROL_CAPTURE_INTENT)) {
                return true;

            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
        return false;
    }

    public static String getFilePath(boolean isVideo, long dateTaken) {
        return createFileName(isVideo, createFileTitle(isVideo, dateTaken));
    }

    public static String createFileTitle(boolean isVideo, long dateTaken) {
        SimpleDateFormat format;
        Date date = new Date(dateTaken);
        if (isVideo) {
            format = new SimpleDateFormat("'VID'_yyyyMMdd_HHmmss");
        } else {
            format = new SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss_S");
        }
        return format.format(date);
    }

    public static String createFileName(boolean isVideo, String title) {
        String fileName;
        if (isVideo) {
            fileName = title + ".mp4";
        } else {
            fileName = title + ".jpg";
        }
        return fileName;
    }

    private void deleteVideoFile(String fileName) {
        File f = new File(fileName);
        if (!f.delete()) {
            LogHelper.i(TAG, "[deleteVideoFile] Could not delete " + fileName);
        }
    }
}
