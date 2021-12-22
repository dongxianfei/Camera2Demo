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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.demo.camera.CameraDemo.R;
import com.demo.camera.CameraDemo.utils.AssistUtils;
import com.demo.camera.CameraDemo.utils.FileUtils;
import com.demo.camera.CameraDemo.utils.LogHelper;
import com.demo.camera.CameraDemo.utils.LogUtil;
import com.demo.camera.CameraDemo.utils.PermissionUtil;
import com.demo.camera.CameraDemo.view.PreviewTextureView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TestFragment extends Fragment implements View.OnClickListener {

    private static final LogUtil.Tag TAG = new LogUtil.Tag(TestFragment.class.getSimpleName());

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    private PermissionUtil mPermissionUtil;
    private PreviewTextureView mTextureView;
    private ImageButton mRecordBtn;
    private ImageButton mImageBtn;
    private ImageButton mSwitchBtn;
    private LinearLayout mTimerLayout;
    private Chronometer mChronometer;

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraSession;
    private CaptureRequest.Builder mRequest;

    private String mCameraId;
    private Size mPreviewSize;
    private Size mPictureSize;
    private Size mVideoSize;
    private MediaRecorder mMediaRecorder = null;
    private Surface mPreviewSurface = null;
    private Surface mRecordSurface = null;
    private ImageReader mImageReader = null;

    private boolean mIsRecordingVideo = false;
    private boolean mIsTakePicture = false;
    private int mSensorOrientation;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

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
            openCamera(width, height);
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
        return inflater.inflate(R.layout.camera_test_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPermissionUtil = new PermissionUtil(getActivity());
        mTextureView = view.findViewById(R.id.preview_texture);
        mSwitchBtn = view.findViewById(R.id.switch_btn);
        mImageBtn = view.findViewById(R.id.cap_btn);
        mRecordBtn = view.findViewById(R.id.record_btn);
        mTimerLayout = view.findViewById(R.id.time_layout);
        mChronometer = view.findViewById(R.id.record_timer);

        mRecordBtn.setOnClickListener(this);
        mImageBtn.setOnClickListener(this);
        mSwitchBtn.setOnClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        stopBackgroundThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        closeCamera();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.cap_btn:
                takePicture();
                break;

            case R.id.record_btn:
                PressRecordBtn();
                break;
        }
    }

    private void openCamera(int width, int height) {

        if (!mPermissionUtil.hasPermissionGtranted()) {
            mPermissionUtil.requestRequiredPermissions();
            return;
        }

        LogHelper.i(TAG, "[openCamera] width = " + width + ",height = " + height);

        initCamera(width, height);

        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            LogHelper.i(TAG, "[openCamera] mCameraId = " + mCameraId);

            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            throw new RuntimeException("SecurityException openCamera", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("RuntimeException openCamera", e);
        }
    }


    private void initCamera(int width, int height) {

        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Get all the supported camera ids.
            mCameraId = manager.getCameraIdList()[0];

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }

            mPictureSize = AssistUtils.choosePictureSize(map.getOutputSizes(ImageFormat.JPEG));
            LogHelper.i(TAG, "mPictureSize = " + mPictureSize.toString());

            mVideoSize = AssistUtils.chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            LogHelper.i(TAG, "mVideoSize = " + mVideoSize.toString());

            mPreviewSize = AssistUtils.choosePreviewSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mPictureSize);
            LogHelper.i(TAG, "mPreviewSize = " + mPreviewSize.toString());

            initRecorder();

            if (mRecordSurface == null) {
                mRecordSurface = MediaCodec.createPersistentInputSurface();
            }
            mMediaRecorder.setInputSurface(mRecordSurface);

            prepareRecorder();

            if (mRecordSurface == null) {
                mRecordSurface = mMediaRecorder.getSurface();
            }

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

            if (mRecordSurface != null) {
                surfaces.add(mRecordSurface);
                LogHelper.i(TAG, "mRecordSurface = " + mRecordSurface);
            } else {
                LogHelper.i(TAG, "mRecordSurface == null");
            }

            mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCameraSession = session;
                            setRepeatingRequest(false, false);
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

    private void setRepeatingRequest(boolean isTakePicture, boolean isRecording) {

        try {

            if (mCameraSession != null) {
                mCameraSession.stopRepeating();
            }
            mRequest = null;
            if (isTakePicture) {
                mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                mRequest.addTarget(mImageReader.getSurface());
            } else if (isRecording) {
                mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                mRequest.addTarget(mRecordSurface);

            } else {
                mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }

            mRequest.addTarget(mPreviewSurface);

            setPreviewRequestBuilder();

            //拍照不需要RepeatingRequest
            if (!isTakePicture) {
                mCameraSession.setRepeatingRequest(mRequest.build(), null, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setPreviewRequestBuilder() {

    }

    private void initRecorder() {
        LogHelper.i(TAG, "initMediaRecorder");

        releaseRecorder();

        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //mMediaRecorder.setMaxDuration(2 * 60 * 1000);
        //mMediaRecorder.setMaxFileSize(100 * 1024 * 1024);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setOutputFile(FileUtils.getVideoFilePath());
        mMediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int what, int extra) {
                LogHelper.e(TAG, "what = " + what + ",extra = " + extra);
            }
        });
        mMediaRecorder.setOnInfoListener(null);
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
    }

    private void prepareRecorder() {
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void releaseRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    public void takePicture() {
        LogHelper.i(TAG, "takePicture");
        if (mIsTakePicture) {
            LogHelper.i(TAG, "mIsTakePicture == true");
            return;
        }
        try {
            mIsTakePicture = true;
            setRepeatingRequest(true, false);

            mCameraSession.capture(mRequest.build(), null, mBackgroundHandler);

            setRepeatingRequest(false, false);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void PressRecordBtn() {
        if (!mIsRecordingVideo) {

            initRecorder();

            prepareRecorder();

            startRecording();

            mRecordBtn.setBackgroundResource(R.drawable.btn_shutter_video_recording);
            mImageBtn.setBackgroundResource(R.drawable.btn_shutter_pressed_disabled);
            mImageBtn.setEnabled(false);
            mSwitchBtn.setEnabled(false);
            mTimerLayout.setVisibility(View.VISIBLE);
            mChronometer.setBase(SystemClock.elapsedRealtime());
            int hour = (int) ((SystemClock.elapsedRealtime() - mChronometer.getBase()) / 1000 / 60);
            mChronometer.setFormat("0" + String.valueOf(hour) + ":%s");
            LogHelper.i(TAG, "startRecording");
            mChronometer.start();
        } else {
            stopRecording();
            mRecordBtn.setBackgroundResource(R.drawable.btn_shutter_video_default);
            mImageBtn.setBackgroundResource(R.drawable.btn_shutter_default);
            mImageBtn.setEnabled(true);
            mSwitchBtn.setEnabled(true);
            mChronometer.stop();
            LogHelper.i(TAG, "stopRecording");
            mTimerLayout.setVisibility(View.GONE);
        }
    }

    private void startRecording() {

        //setRepeatingRequest(false, true);
        mMediaRecorder.start();

        mIsRecordingVideo = true;
    }

    private void stopRecording() {
        mIsRecordingVideo = false;

        mMediaRecorder.stop();
        releaseRecorder();


        showToast("Video saved success!");
        setRepeatingRequest(false, false);
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

            releaseRecorder();

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

            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
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
