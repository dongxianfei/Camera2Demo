package com.demo.camera.CameraDemo;

import android.Manifest;
import android.content.Context;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.demo.camera.CameraDemo.fragment.CaptureFragment;
import com.demo.camera.CameraDemo.fragment.VideoFragment;
import com.demo.camera.CameraDemo.utils.LogUtil;
import com.demo.camera.CameraDemo.utils.PermissionUtil;
import com.hjq.permissions.OnPermission;
import com.hjq.permissions.XXPermissions;

import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final LogUtil.Tag TAG = new LogUtil.Tag(CameraActivity.class.getSimpleName());

    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
    };

//    private PermissionUtil mPermissionUtil;
    private FragmentTransaction mTransaction;
    private String CameraMode = "Photo";

    private CaptureFragment mCaptureFragment;
    private VideoFragment mVideoFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        mPermissionUtil = new PermissionUtil(this);
//
//        if (!mPermissionUtil.hasPermissionGtranted()) {
//            mPermissionUtil.requestRequiredPermissions();
//            return;
//        }

        getPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public void switchMode(String mode) {
        if (CameraMode.equals(mode))
            return;

        CameraMode = mode;

        mTransaction = getSupportFragmentManager().beginTransaction();

        switch (mode) {
            case "Photo":
                mTransaction.replace(R.id.container, mCaptureFragment).commit();
                break;
            case "Video":
                mTransaction.replace(R.id.container, mVideoFragment).commit();
                break;
        }
    }

    public void addFragment(){
        mCaptureFragment = CaptureFragment.newInstance();
        mVideoFragment = VideoFragment.newInstance();

        mTransaction = getSupportFragmentManager().beginTransaction();
        mTransaction.replace(R.id.container, mCaptureFragment).commit();
    }

    public void getPermission() {
        if (XXPermissions.hasPermission(this, CAMERA_PERMISSIONS)) {
            Log.i("permission", "XXPermissions.hasPermission");

            addFragment();
        } else {
            XXPermissions.with(this).permission(CAMERA_PERMISSIONS).request(new OnPermission() {
                @Override
                public void hasPermission(List<String> granted, boolean all) {
                    if (all) {
                        Log.i("permission", "XXPermissions.all Permission");

                        addFragment();
                    } else {
                        Log.i("permission", "XXPermissions.part Permission");
                    }
                }

                @Override
                public void noPermission(List<String> denied, boolean never) {
                    if (never) {
                        Log.i("permission", "XXPermissions.never Permission");
                    } else {
                        Log.i("permission", "XXPermissions.fail Permission");
                    }
                    XXPermissions.startPermissionActivity(CameraActivity.this, denied);
                }
            });
        }
    }
}
