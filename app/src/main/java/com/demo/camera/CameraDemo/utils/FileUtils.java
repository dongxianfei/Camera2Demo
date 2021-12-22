package com.demo.camera.CameraDemo.utils;

import android.content.Context;
import android.media.Image;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;

public class FileUtils {
    private static final LogUtil.Tag TAG = new LogUtil.Tag(FileUtils.class.getSimpleName());
    public static int SAVE_SUCCESS = 0;
    public static int NOSDCARD_ERROR = 1;
    public static int SAVEFILE_ERROR = 2;

    //Environment.getDataDirectory() = /data
    //Environment.getRootDirectory() = /system

    //storage/emulated/0
    public static final String mExternalStoragePath = Environment.
            getExternalStorageDirectory().getAbsolutePath();

    //storage/emulated/0/DCIM
    public static final String DCIM_CAMERA_FOLDER_ABSOLUTE_PATH = Environment.
            getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();

    public static boolean isSDCardMounted() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public static void getExternalDirs(Context context) {

        File[] files;
        //storage/emulated/0/Android/data/com.demo.camera.CameraDemo/files/mounted
        //storage/6AAB-1BF4/Android/data/com.demo.camera.CameraDemo/files/mounted
        files = context.getExternalFilesDirs(Environment.MEDIA_MOUNTED);
        for (File file : files) {
            LogHelper.i(TAG, "getExternalDirs = " + file.getAbsolutePath());
        }
    }

    public static String getInternalDirectoryPath() {
        if (isSDCardMounted()) {
            return mExternalStoragePath;
        }
        return "";
    }

    public static String getInternalDirectoryDCIM() {
        if (isSDCardMounted()) {
            return DCIM_CAMERA_FOLDER_ABSOLUTE_PATH;
        }
        return "";
    }

    public static String getSDcardDirectoryPath1(Context context) {
        try {
            StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            // 7.0才有的方法
            List<StorageVolume> storageVolumes = storageManager.getStorageVolumes();
            Class<?> volumeClass = Class.forName("android.os.storage.StorageVolume");
            Method getPath = volumeClass.getDeclaredMethod("getPath");
            Method isRemovable = volumeClass.getDeclaredMethod("isRemovable");
            getPath.setAccessible(true);
            isRemovable.setAccessible(true);
            for (int i = 0; i < storageVolumes.size(); i++) {
                StorageVolume storageVolume = storageVolumes.get(i);
                String mPath = (String) getPath.invoke(storageVolume);
                Boolean isRemove = (Boolean) isRemovable.invoke(storageVolume);
                //mPath = /storage/emulated/0,isRemoveble = false
                //mPath = /storage/6AAB-1BF4,isRemoveble = true
                LogHelper.i(TAG, "mPath = " + mPath + ",isRemoveble = " + isRemove);
                if (isRemove) {
                    return mPath;
                }
            }
        } catch (Exception e) {
            LogHelper.e(TAG, e.getMessage());
        }
        return "";
    }

    public static String getSDcardDirectoryPath2(Context context) {
        int TYPE_PUBLIC = 0;
        File file = null;
        String path = null;
        StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        Class<?> mVolumeInfo = null;
        try {
            mVolumeInfo = Class.forName("android.os.storage.VolumeInfo");
            Method getVolumes = mStorageManager.getClass().getMethod("getVolumes");
            Method volType = mVolumeInfo.getMethod("getType");
            Method isMount = mVolumeInfo.getMethod("isMountedReadable");
            Method getPath = mVolumeInfo.getMethod("getPath");
            List<Object> mListVolumeinfo = (List<Object>) getVolumes.invoke(mStorageManager);
            for (int i = 0; i < mListVolumeinfo.size(); i++) {
                int mType = (Integer) volType.invoke(mListVolumeinfo.get(i));
                if (mType == TYPE_PUBLIC) {
                    boolean misMount = (Boolean) isMount.invoke(mListVolumeinfo.get(i));
                    if (misMount) {
                        file = (File) getPath.invoke(mListVolumeinfo.get(i));
                        if (file != null) {
                            path = file.getAbsolutePath();
                            //mType = 0,misMount = true,path = /storage/6AAB-1BF4
                            LogHelper.i(TAG, "mType = " + mType + ",misMount = " + misMount + ",path = " + path);
                            return path;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    public static String createFilePath(Context context, String dir) {
        String directoryPath = "";
        if (isSDCardMounted()) {
            directoryPath = context.getExternalFilesDir(dir).getAbsolutePath();
        } else {
            directoryPath = context.getFilesDir() + File.separator + dir;
        }
        LogHelper.i(TAG, "getFilePath = " + directoryPath);
        File file = new File(directoryPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        return directoryPath;
    }

    // 获取SD卡的完整空间大小，返回MB
    public static long getSDCardSize() {
        StatFs fs = new StatFs(getInternalDirectoryPath());
        long count = fs.getBlockCountLong();
        long size = fs.getBlockSizeLong();
        return count * size / 1024 / 1024;
    }

    // 获取SD卡的剩余空间大小
    public static long getSDCardFreeSize() {
        StatFs fs = new StatFs(getInternalDirectoryPath());
        long count = fs.getFreeBlocksLong();
        long size = fs.getBlockSizeLong();
        return count * size / 1024 / 1024;
    }

    // 获取SD卡的可用空间大小
    public static long getSDCardAvailableSize() {
        StatFs fs = new StatFs(getInternalDirectoryPath());
        long count = fs.getAvailableBlocksLong();
        long size = fs.getBlockSizeLong();
        return count * size / 1024 / 1024;
    }

    public static String getFilePath() {
        String path = getInternalDirectoryDCIM() + "/Camera/";
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }

        return path;
    }

    public static String getVideoFilePath() {
        return getFilePath() + "VID_" + System.currentTimeMillis() + ".mp4";
    }

    public static String getPictureFilePath() {
        return getFilePath() + "IMG_" + System.currentTimeMillis() + ".jpg";
    }

    public static int saveImage(Image image) {
        if (!isSDCardMounted()) {
            return NOSDCARD_ERROR;
        }

        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        File file = new File(getFilePath() + CameraUtils.getFilePath(false, System.currentTimeMillis()));
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(data);
            fileOutputStream.close();
            return SAVE_SUCCESS;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return SAVEFILE_ERROR;
        } catch (IOException e) {
            e.printStackTrace();
            return SAVEFILE_ERROR;
        } finally {
            LogHelper.d(TAG, "saveImage: finally");
            image.close();
        }
    }
}
