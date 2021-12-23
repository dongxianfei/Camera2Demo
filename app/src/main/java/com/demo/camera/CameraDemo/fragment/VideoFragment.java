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
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.demo.camera.CameraDemo.CameraActivity;
import com.demo.camera.CameraDemo.R;
import com.demo.camera.CameraDemo.utils.AssistUtils;
import com.demo.camera.CameraDemo.utils.CameraUtils;
import com.demo.camera.CameraDemo.utils.FileUtils;
import com.demo.camera.CameraDemo.utils.LogHelper;
import com.demo.camera.CameraDemo.utils.LogUtil;
import com.demo.camera.CameraDemo.view.PreviewTextureView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class VideoFragment extends Fragment implements View.OnClickListener {

    private static final LogUtil.Tag TAG = new LogUtil.Tag(VideoFragment.class.getSimpleName());

    private PreviewTextureView mTextureView;
    private ImageButton mRecordBtn;
    private ImageButton mSwitchBtn;
    private ImageButton mSwitchMode;
    private LinearLayout mTimerLayout;
    private Chronometer mChronometer;

    private CameraManager mCameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraSession;
    private CaptureRequest.Builder mRequest;

    private String mCameraId;
    private boolean mIsBack = true;
    private Size mPreviewSize;
    private Size mPictureSize;
    private Size mVideoSize;
    private int mSensorOrientation = 0;
    private int mGsensorOrientation = 0;
    private OrientationEventListener mGsensorListener;
    private MediaRecorder mMediaRecorder = null;
    private Surface mPreviewSurface = null;
    private Surface mRecordSurface = null;

    private boolean mIsRecordingVideo = false;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    public static VideoFragment newInstance() {
        return new VideoFragment();
    }

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
            createCaptureSession(false);
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.camera_video_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTextureView = view.findViewById(R.id.video_preview_texture);
        mSwitchBtn = view.findViewById(R.id.video_switch_btn);
        mSwitchMode = view.findViewById(R.id.video_switchMode_btn);
        mRecordBtn = view.findViewById(R.id.video_record_btn);
        mTimerLayout = view.findViewById(R.id.video_time_layout);
        mChronometer = view.findViewById(R.id.video_record_timer);

        mRecordBtn.setOnClickListener(this);
        mSwitchBtn.setOnClickListener(this);
        mSwitchMode.setOnClickListener(this);

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

        stopBackgroundThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mGsensorListener != null) {
            mGsensorListener.disable();
            mGsensorListener = null;
        }

        closeCamera();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.video_record_btn:
                recordBtn();
                break;
            case R.id.video_switchMode_btn:
                ((CameraActivity) getActivity()).switchMode("Photo");
                break;
            case R.id.video_switch_btn:

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

        mMediaRecorder = new MediaRecorder();

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
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }

            mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            mVideoSize = AssistUtils.chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            LogHelper.i(TAG, "mVideoSize = " + mVideoSize.toString());

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

    private void createCaptureSession(final boolean isRecording) {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }

        closeOldSession();

        try {
            List<Surface> surfaces = new ArrayList<>();

            //Preview
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            if (texture != null) {
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                mPreviewSurface = new Surface(texture);
                surfaces.add(mPreviewSurface);
            }

            //Video
            if (isRecording && mMediaRecorder != null) {
                mRecordSurface = mMediaRecorder.getSurface();
                surfaces.add(mRecordSurface);
            }

            mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCameraSession = session;
                            setRepeatingRequest(isRecording);
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

    private void setRepeatingRequest(boolean isRecording) {
        try {
            if (mCameraSession != null) {
                mCameraSession.stopRepeating();
            }
            mRequest = null;
            if (isRecording) {
                mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                mRequest.addTarget(mRecordSurface);
            } else {
                mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }

            mRequest.addTarget(mPreviewSurface);

            setPreviewRequestBuilder();

            mCameraSession.setRepeatingRequest(mRequest.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setPreviewRequestBuilder() {

    }

    private void recordBtn() {
        if (!mIsRecordingVideo) {

            startRecording();

            mRecordBtn.setBackgroundResource(R.drawable.btn_shutter_video_recording);
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
            mSwitchBtn.setEnabled(true);
            mChronometer.stop();
            LogHelper.i(TAG, "stopRecording");
            mTimerLayout.setVisibility(View.GONE);
        }
    }

    private void initRecorder() {
        LogHelper.i(TAG, "initMediaRecorder");

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //mMediaRecorder.setMaxDuration(2 * 60 * 1000);
        //mMediaRecorder.setMaxFileSize(100 * 1024 * 1024);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setOutputFile(FileUtils.getFilePath() + CameraUtils.getFilePath(true, System.currentTimeMillis()));
        mMediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int what, int extra) {
                LogHelper.e(TAG, "what = " + what + ",extra = " + extra);
            }
        });
        mMediaRecorder.setOnInfoListener(null);
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        LogHelper.i(TAG, "mSensorOrientation = " + mSensorOrientation + ",rotation = " + rotation);
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        //mMediaRecorder.setOrientationHint(90);

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startRecording() {

        initRecorder();

        createCaptureSession(true);

        mMediaRecorder.start();

        mIsRecordingVideo = true;
    }

    private void stopRecording() {
        mIsRecordingVideo = false;

        mMediaRecorder.stop();
        mMediaRecorder.reset();

        showToast("Video saved success!");

        //createCaptureSession(false);
        setRepeatingRequest(false);
    }

    private void closeOldSession() {

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
    }

    private void releaseRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
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

            closeOldSession();

            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            if (null != mMediaRecorder) {
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mMediaRecorder = null;
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

    private void showToast(String text) {
        if (!text.equals("")) {
            Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
        }
    }
}
