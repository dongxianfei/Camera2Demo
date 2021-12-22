package com.demo.camera.CameraDemo.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.CameraProfile;
import android.media.MediaMetadataRetriever;
import android.util.Size;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;

public class BitmapCreator {

    private static final LogUtil.Tag TAG = new LogUtil.Tag(BitmapCreator.class.getSimpleName());

    /**
     * decode bitmap from the jpeg data, will decode the full image.
     *
     * @param jpeg        the image data.
     * @param targetWidth the view width where the bitmap shows.
     * @return the bitmap decode from jpeg data. if jpeg data is null or out of
     * memory when decode, will return null.
     */
    public static Bitmap decodeBitmapFromJpeg(byte[] jpeg, int targetWidth) {
        if (jpeg != null) {
            android.media.ExifInterface exif = getExif(jpeg);
            int orientation = CameraUtils.getOrientationFromSdkExif(jpeg);
            int jpegWidth = CameraUtils.getSizeFromSdkExif(jpeg).getWidth();
            int ratio = (int) Math.ceil((double) jpegWidth / targetWidth);
            int inSampleSize = Integer.highestOneBit(ratio);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = inSampleSize;
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0,
                        jpeg.length, options);
                return rotateBitmap(bitmap, orientation);
            } catch (OutOfMemoryError e) {
                LogHelper.e(TAG, "decodeBitmapFromJpeg fail", e);
                return null;
            }
        }
        return null;
    }

    /**
     * create bitmap from the jpeg data.
     *
     * @param jpeg        the image data.
     * @param targetWidth the view width where the bitmap shows.
     * @return the bitmap decode from jpeg data. if jpeg data is null or out of
     * memory when decode, will return null.
     */
    public static Bitmap createBitmapFromJpeg(byte[] jpeg, int targetWidth) {
        LogHelper.d(TAG, "[createBitmapFromJpeg] jpeg = " + jpeg
                + ", targetWidth = " + targetWidth);
        if (jpeg != null) {
            android.media.ExifInterface exif = getExif(jpeg);
            int orientation = CameraUtils.getOrientationFromSdkExif(jpeg);
            Bitmap thumbnailBitmap = exif.getThumbnailBitmap();
            if (exif.hasThumbnail() && thumbnailBitmap != null) {
                LogHelper.d(TAG, "create bitmap from exif thumbna`il");
                return rotateBitmap(thumbnailBitmap, orientation);
            } else {
                int jpegWidth = getJpegWidth(jpeg);
                int ratio = (int) Math.ceil((double) jpegWidth / targetWidth);
                int inSampleSize = Integer.highestOneBit(ratio);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = inSampleSize;
                try {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0,
                            jpeg.length, options);
                    LogHelper.d(TAG, "[createBitmapFromJpeg] end");
                    return rotateBitmap(bitmap, orientation);
                } catch (OutOfMemoryError e) {
                    LogHelper.e(TAG, "createBitmapFromJpeg fail", e);
                    return null;
                }
            }
        }
        return null;
    }

    public static Bitmap createBitmapFromJpegWithoutExif(byte[] jpeg, int jpegWidth,
                                                         int targetWidth) {
        LogHelper.d(TAG, "[createBitmapFromJpegWithoutExif] jpeg = " + jpeg
                + ", jpegWidth = " + jpegWidth + ", targetWidth = " + targetWidth);
        if (jpeg == null) {
            return null;
        }

        int ratio = (int) Math.ceil((double) jpegWidth / targetWidth);
        int inSampleSize = Integer.highestOneBit(ratio);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = inSampleSize;

        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
            return bitmap;
        } catch (OutOfMemoryError e) {
            LogHelper.e(TAG, "[createBitmapFromJpegWithoutExif] OutOfMemoryError", e);
            return null;
        }
    }

    /**
     * create bitmap from the YUV data.
     *
     * @param yuvData     the YUV data.
     * @param targetWidth the view width where the bitmap shows.
     * @param yuvWidth    the width of YUV image.
     * @param yuvHeight   the height of YUV image.
     * @param orientation the orientation of YUV image.
     * @param imageFormat the image format of YUV image, must be NV21 OR YUY2.
     * @return the bitmap decode from YUV data.
     */
    public static Bitmap createBitmapFromYuv(byte[] yuvData, int imageFormat,
                                             int yuvWidth, int yuvHeight, int targetWidth, int orientation) {
        LogHelper.d(TAG, "[createBitmapFromYuv] yuvData = " + yuvData
                + ", yuvWidth = " + yuvWidth + ", yuvHeight = " + yuvHeight
                + ", orientation = " + orientation + ", imageFormat = "
                + imageFormat);
        if (isNeedDumpYuv()) {
            dumpYuv("/sdcard/postView.yuv", yuvData);
        }
        if (yuvData != null) {
            byte[] jpeg = covertYuvDataToJpeg(yuvData, imageFormat, yuvWidth,
                    yuvHeight);
            int ratio = (int) Math.ceil((double) Math.min(yuvWidth, yuvHeight)
                    / targetWidth);
            int inSampleSize = Integer.highestOneBit(ratio);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = inSampleSize;
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0,
                        jpeg.length, options);
                LogHelper.d(TAG, "[createBitmapFromYuv] end");
                return rotateBitmap(bitmap, orientation);
            } catch (OutOfMemoryError e) {
                LogHelper.e(TAG, "createBitmapFromYuv fail", e);
                return null;
            }
        }
        return null;
    }

    /**
     * create bitmap from a video.
     *
     * @param filePath    the video saving path.
     * @param targetWidth the view width where the bitmap shows.
     * @return the bitmap decode from a video. maybe return null if the frame
     * get from MediaMetadataRetriever is null.
     */
    public static Bitmap createBitmapFromVideo(String filePath, int targetWidth) {
        return createBitmapFromVideo(filePath, null, targetWidth);
    }

    /**
     * Encode YUV to jpeg, and crop it.
     *
     * @param data        the yuv data.
     * @param imageFormat the yuv format.
     * @param yuvWidth    the yuv width.
     * @param yuvHeight   the yuv height.
     * @return the jpeg data.
     */
    public static byte[] covertYuvDataToJpeg(byte[] data, int imageFormat,
                                             int yuvWidth, int yuvHeight) {
        byte[] jpeg;
        Rect rect = new Rect(0, 0, yuvWidth, yuvHeight);
        YuvImage yuvImg = new YuvImage(data, imageFormat, yuvWidth, yuvHeight,
                null);
        ByteArrayOutputStream outputstream = new ByteArrayOutputStream();
        int jpegQuality = CameraProfile
                .getJpegEncodingQualityParameter(CameraProfile.QUALITY_HIGH);
        yuvImg.compressToJpeg(rect, jpegQuality, outputstream);
        jpeg = outputstream.toByteArray();
        return jpeg;
    }


    private static Bitmap createBitmapFromVideo(String filePath,
                                                FileDescriptor fd, int targetWidth) {
        Bitmap bitmap = null;
        LogHelper.d(TAG, "[createBitmapFromVideo] filePath = " + filePath
                + ", targetWidth = " + targetWidth);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (filePath != null) {
                retriever.setDataSource(filePath);
            } else {
                retriever.setDataSource(fd);
            }
            // -1 means any frame that the implementation considers as
            // representative may be returned.
            bitmap = retriever.getFrameAtTime(-1);
        } catch (IllegalArgumentException ex) {
            // Assume this is a corrupt video file
            ex.printStackTrace();
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
            ex.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
                ex.printStackTrace();
            }
        }
        if (bitmap == null) {
            return null;
        }

        // Scale down the bitmap if it is bigger than we need.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        LogHelper.v(TAG, "[createBitmapFromVideo] bitmap = " + width + "x" + height);
        if (width > targetWidth) {
            float scale = (float) targetWidth / width;
            int w = Math.round(scale * width);
            int h = Math.round(scale * height);
            LogHelper.v(TAG, "[createBitmapFromVideo] w = " + w + "h" + h);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        return bitmap;
    }


    private static android.media.ExifInterface getExif(byte[] jpegData) {
        if (jpegData != null) {
            android.media.ExifInterface exif = null;
            try {
                exif = new android.media.ExifInterface(new ByteArrayInputStream(jpegData));
            } catch (IOException e) {
                LogHelper.w(TAG, "Failed to read EXIF data", e);
            }
            return exif;
        }
        LogHelper.w(TAG, "JPEG data is null, can not get exif");
        return null;
    }

    private static android.media.ExifInterface getExif(String filePath) {
        if (filePath != null) {
            android.media.ExifInterface exif = null;
            try {
                exif = new android.media.ExifInterface(filePath);
            } catch (IOException e) {
                LogHelper.w(TAG, "Failed to read EXIF data", e);
            }
            return exif;
        }
        LogHelper.w(TAG, "filePath is null, can not get exif");
        return null;
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        if (orientation != 0) {
            // We only rotate the thumbnail once even if we get OOM.
            Matrix m = new Matrix();
            m.setRotate(orientation, bitmap.getWidth() / 2,
                    bitmap.getHeight() / 2);
            try {
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), m, true);
                return rotated;
            } catch (IllegalArgumentException t) {
                LogHelper.w(TAG, "Failed to rotate bitmap", t);
            }
        }
        return bitmap;
    }

    private static int getJpegWidth(byte[] data) {
        if (data != null) {
            Size sizeFromSdkExif = CameraUtils.getSizeFromSdkExif(data);
            int width = sizeFromSdkExif.getWidth();
            int height = sizeFromSdkExif.getHeight();
            return Math.min(width, height);
        }
        LogHelper.w(TAG, "exif is null, can not get JpegWidth");
        return 0;
    }

    private static boolean isNeedDumpYuv() {
        boolean enable = PropertyUtils.getIntMethod(
                "vendor.debug.thumbnailFromYuv.enable", 0) == 1 ? true : false;
        LogHelper.d(TAG, "[isNeedDumpYuv] return :" + enable);
        return enable;
    }

    private static void dumpYuv(String filePath, byte[] data) {
        FileOutputStream out = null;
        try {
            LogHelper.d(TAG, "[dumpYuv] begin");
            out = new FileOutputStream(filePath);
            out.write(data);
            out.close();
        } catch (IOException e) {
            LogHelper.e(TAG, "[dumpYuv]Failed to write image,ex:", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    LogHelper.e(TAG, "[dumpYuv]IOException:", e);
                }
            }
        }
        LogHelper.d(TAG, "[dumpYuv] end");
    }
}
