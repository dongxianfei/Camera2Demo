package com.demo.camera.CameraDemo.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.demo.camera.CameraDemo.CameraActivity;
import com.demo.camera.CameraDemo.R;
import com.demo.camera.CameraDemo.utils.AssistUtils;
import com.demo.camera.CameraDemo.utils.CameraUtils;
import com.demo.camera.CameraDemo.utils.FileUtils;
import com.demo.camera.CameraDemo.utils.LogHelper;
import com.demo.camera.CameraDemo.utils.LogUtil;
import com.demo.camera.CameraDemo.view.PreviewTextureView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class PhotoFragment extends Fragment implements View.OnClickListener {

    private static final LogUtil.Tag TAG = new LogUtil.Tag(PhotoFragment.class.getSimpleName());

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private PreviewTextureView mTextureView;
    private ImageButton mImageBtn;
    private ImageButton mSwitchBtn;
    private ImageButton mSwitchMode;

    private CameraManager mCameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraSession;
    private CaptureRequest.Builder mRequest;

    private String mCameraId;
    private boolean mIsBack = true;
    private Size mPreviewSize;
    private Size mPictureSize;
    private Surface mPreviewSurface = null;
    private ImageReader mImageReader = null;

    private boolean mIsTakePicture = false;
    private int mSensorOrientation = 0;
    private int mGsensorOrientation = 0;
    private OrientationEventListener mGsensorListener;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    public static PhotoFragment newInstance() {
        return new PhotoFragment();
    }

    private class OrientationEventListenerImpl extends OrientationEventListener {

        public OrientationEventListenerImpl(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                mGsensorOrientation = 0;
                return;
            }

            if (orientation > 350 || orientation < 10) {
                mGsensorOrientation = 0;
            } else if (orientation > 80 && orientation < 100) {
                mGsensorOrientation = 90;
            } else if (orientation > 170 && orientation < 190) {
                mGsensorOrientation = 180;
            } else if (orientation > 260 && orientation < 280) {
                mGsensorOrientation = 270;
            } else {
                return;
            }
        }
    }

    //onOpend
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            createCaptureSession();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    //SurfaceChange
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            openCamera(mIsBack, width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            mBackgroundHandler.post(new ImageSaver(image));
            mIsTakePicture = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.camera_capture_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTextureView = view.findViewById(R.id.capture_preview_texture);
        mSwitchBtn = view.findViewById(R.id.capture_switchId_btn);
        mSwitchMode = view.findViewById(R.id.capture_switchMode_btn);
        mImageBtn = view.findViewById(R.id.capture_cap_btn);

        mSwitchBtn.setOnClickListener(this);
        mSwitchMode.setOnClickListener(this);
        mImageBtn.setOnClickListener(this);

        mGsensorListener = new OrientationEventListenerImpl(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            openCamera(mIsBack, mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        if (mGsensorListener.canDetectOrientation()) {
            mGsensorListener.enable();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mGsensorListener.disable();

        closeCamera();

        stopBackgroundThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mGsensorListener != null) {
            mGsensorListener.disable();
            mGsensorListener = null;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.capture_cap_btn:
                takePicture();
                break;
            case R.id.capture_switchMode_btn:
                ((CameraActivity) getActivity()).switchMode("Video");
                break;
            case R.id.capture_switchId_btn:
                if (mIsBack) {
                    mIsBack = false;
                } else {
                    mIsBack = true;
                }

                closeCamera();

                if (mTextureView.isAvailable()) {
                    openCamera(mIsBack, mTextureView.getWidth(), mTextureView.getHeight());
                } else {
                    mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
                }

                break;
        }
    }

    private void openCamera(boolean isBack, int width, int height) {

        LogHelper.i(TAG, "[openCamera] width = " + width + ",height = " + height);

        initCamera(isBack, width, height);

        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            LogHelper.i(TAG, "[openCamera] mCameraId = " + mCameraId);

            mCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            throw new RuntimeException("SecurityException openCamera", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("RuntimeException openCamera", e);
        }
    }


    private void initCamera(boolean isBack, int width, int height) {

        Activity activity = getActivity();
        mCameraManager = CameraUtils.getCameraManager(getActivity());
        try {
            // Get all the supported camera ids.
            if (isBack) {
                mCameraId = mCameraManager.getCameraIdList()[0];
            } else {
                mCameraId = mCameraManager.getCameraIdList()[1];
            }

            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = mCameraCharacteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }

            mPictureSize = AssistUtils.choosePictureSize(map.getOutputSizes(ImageFormat.JPEG));
            LogHelper.i(TAG, "mPictureSize = " + mPictureSize.toString());

            mPreviewSize = AssistUtils.choosePreviewSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mPictureSize);
            LogHelper.i(TAG, "mPreviewSize = " + mPreviewSize.toString());

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                //mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                mTextureView.setAspectRatio(mPreviewSize.getWidth() / mPreviewSize.getHeight());
            } else {
                //mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                mTextureView.setAspectRatio(mPreviewSize.getHeight() / mPreviewSize.getWidth());
            }
            configureTransform(width, height);

        } catch (CameraAccessException e) {
            activity.finish();
        }
    }

    private void createCaptureSession() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }

        try {
            List<Surface> surfaces = new ArrayList<>();

            //Preview
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            if (texture != null) {
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                mPreviewSurface = new Surface(texture);
                surfaces.add(mPreviewSurface);
            }

            //Jpeg
            mImageReader = ImageReader.newInstance(mPictureSize.getWidth(), mPictureSize.getHeight(),
                    ImageFormat.JPEG, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler);
            surfaces.add(mImageReader.getSurface());

            mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCameraSession = session;
                            createCaptureRequest(false);
                            setRepeatingRequest();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            LogHelper.e(TAG, "onConfigureFailed");
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CaptureRequest.Builder createCaptureRequest(boolean isTakePicture) {
        try {
            mRequest = null;
            if (isTakePicture) {
                mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                mRequest.addTarget(mImageReader.getSurface());
            } else {
                mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }

            mRequest.addTarget(mPreviewSurface);

            setPreviewRequestBuilder();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return mRequest;
    }

    private void setRepeatingRequest() {
        try {

            if (mCameraSession != null) {
                mCameraSession.stopRepeating();
            }

            mCameraSession.setRepeatingRequest(mRequest.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setPreviewRequestBuilder() {
        if (mRequest != null) {

            //JPEG_ORIENTATION
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            LogHelper.i(TAG, "JPEG_ORIENTATION = " + getOrientation(rotation) + ",rotation = " + rotation);
            mRequest.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            //CONTROL_AF_MODE,自动对焦
            //mRequest.set(CaptureRequest.CONTROL_AF_MODE,
            //        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            //焦距
            //mRequest.set(CaptureRequest.LENS_FOCUS_DISTANCE, num);

            //曝光增益
            //mRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ae);

            //曝光时间
            //mRequest.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ae);

            //ISO敏感度
            //mRequest.set(CaptureRequest.SENSOR_SENSITIVITY, iso);

            //放大缩小区域
            //mRequest.set(CaptureRequest.SCALER_CROP_REGION, newRect2);

            //效果模式
            //mRequest.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_AQUA);

            //Flash on
//            mRequest.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
//            mRequest.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);

            //Scene Mode
            //mRequest.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY);

            //对焦
            //mRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //mRequest.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangleArr);
            //mRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        }
    }

    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    public void takePicture() {
        LogHelper.i(TAG, "takePicture");
        if (mIsTakePicture) {
            LogHelper.i(TAG, "mIsTakePicture == true");
            return;
        }
        try {
            mIsTakePicture = true;
            createCaptureRequest(true);

            mCameraSession.capture(mRequest.build(), null, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();

            if (mCameraSession != null) {
                try {
                    mCameraSession.stopRepeating();
                    mCameraSession.abortCaptures();
                    mCameraSession.close();
                    mCameraSession = null;
                } catch (CameraAccessException e) {
                    LogHelper.e(TAG, "abortCaptures exception");
                }
            }

            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }

        } catch (InterruptedException e) {
            throw new RuntimeException("InterruptedException closeCamera");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    class ImageSaver implements Runnable {
        private Image image;

        public ImageSaver(Image image) {
            this.image = image;
        }

        @Override
        public void run() {
            int saveResult = FileUtils.saveImage(image);
            if (saveResult == FileUtils.SAVE_SUCCESS) {
                showToast("Photo saved success!");
            }
        }

    }

    private void showToast(String text) {
        if (!text.equals("")) {
            Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
        }
    }

}
