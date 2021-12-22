package com.demo.camera.CameraDemo.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.demo.camera.CameraDemo.R;
import com.hjq.permissions.OnPermission;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.util.List;

public class PermissionUtil {

    private Activity activity;

    private static final int REQUEST_VIDEO_PERMISSION = 1;

    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public PermissionUtil(Activity activity) {
        this.activity = activity;
    }

    public boolean hasPermissionGtranted() {
        return hasPermissionGtranted(CAMERA_PERMISSIONS);
    }

    public boolean hasPermissionGtranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    public boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return false;
            }
        }
        return true;
    }

    public void requestRequiredPermissions() {
        requestRequiredPermissions(CAMERA_PERMISSIONS, R.string.need_permissions, REQUEST_VIDEO_PERMISSION);
    }

    public void requestRequiredPermissions(final String[] permissins, int message, final int requestCode) {
        if (shouldShowRequestPermissionRationale(permissins)) {
            new AlertDialog.Builder(activity)
                    .setMessage(message)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(activity, permissins, requestCode);
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.finish();
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(activity, permissins, requestCode);
        }
    }
}
