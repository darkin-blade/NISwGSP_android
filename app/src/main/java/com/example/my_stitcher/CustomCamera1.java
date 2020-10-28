package com.example.my_stitcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.example.my_stitcher.MainActivity.PERMISSION_CAMERA_REQUEST_CODE;
import static java.lang.Math.min;

public class CustomCamera1 extends DialogFragment {
    Button mButton;
    TextureView mTextureView;

    Handler mHandler;
    Handler mUIHandler;

    ImageReader mImageReader;// TODO
    CaptureRequest.Builder mPreViewBuilder;// TODO
    CameraCaptureSession mCameraSession;// TODO 可以用来发送预览和拍照请求
    CameraCharacteristics mCameraCharacteristics;// TODO

    Surface surface;// TODO
    Size mPreViewSize;

    static final int MAX_PREVIEW_WIDTH = 1920;
    static final int MAX_PREVIEW_HEIGHT = 1080;
    Rect maxZoomRect;
    Rect picRect;
    int maxRealRadio;
    Integer mSensorOrientation;

    @Override
    public void show(FragmentManager fragmentManager, String tag) {
        super.show(fragmentManager, tag);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, android.R.style.Theme);// 关闭背景(点击外部不能取消)
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.custom_camera, container);
        getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));// 背景透明

        initUI(view);// 初始化按钮

        return view;
    }

    void initUI(View view) {
        mButton = view.findViewById(R.id.capture);
        mTextureView = view.findViewById(R.id.camera_preview);

        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO 拍照
            }
        });
    }

    static public void infoLog(String log) {
        Log.i("fuck", log);
    }

    // TODO 相机会话的监听器,通过它得到mCameraSession对象
    CameraCaptureSession.StateCallback mSessionStateCallBack = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            try {
                mCameraSession = cameraCaptureSession;
                cameraCaptureSession.setRepeatingRequest(mPreViewBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

        }
    };

    // TODO 相机打开时候的监听器,通过它可以得到相机instance,这个instance可以创建请求builder
    CameraDevice.StateCallback cameraOpenCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            infoLog("camera is opened");
            try {
                mPreViewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                SurfaceTexture texture = mTextureView.getSurfaceTexture();
                texture.setDefaultBufferSize(mPreViewSize.getWidth(), mPreViewSize.getHeight());
                surface = new Surface(texture);
                mPreViewBuilder.addTarget(surface);
                cameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionStateCallBack, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            infoLog("camera is disconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            infoLog("camera opened failed");
        }
    };

    // TODO 预览图片显示控件的监听器,可以监听这个surface的状态
    TextureView.SurfaceTextureListener mSurfacetextlistener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            HandlerThread thread = new HandlerThread("camera3");
            thread.start();
            mHandler = new Handler(thread.getLooper());
            CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
            String camera_id = CameraCharacteristics.LENS_FACING_FRONT + "";// TODO
            try {
                // TODO 只适用 SDK > 23
                int hasCameraPermission = ContextCompat.checkSelfPermission(getActivity().getApplication(), Manifest.permission.CAMERA);
                if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
                    // 有调用相机权限
                    infoLog("has permission of camera");
                } else {
                    ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQUEST_CODE);
                }

                mCameraCharacteristics = manager.getCameraCharacteristics(camera_id);
                maxZoomRect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);// 画面传感器的面积,单位是像素
                maxRealRadio = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue();// 最大的数字缩放

                picRect = new Rect(maxZoomRect);
                StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);// TODO
                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizeByArea());
                mPreViewSize = map.getOutputSizes(SurfaceTexture.class)[0];
                choosePreSize(i, i1, map, largest);
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 5);// TODO
                mImageReader.setOnImageAvailableListener(onImageAvaiableListener, mHandler);
                manager.openCamera(camera_id, cameraOpenCallBack, mHandler);
                // TODO 点击拍照
                mButton.setOnTouchListener(onTouchListener);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        void choosePreSize(int i, int i1, StreamConfigurationMap map, Size largest) {
            int displayRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            boolean swappedDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
                default:
                    infoLog("invalid rotation: " + displayRotation);
            }
            android.graphics.Point displaySize = new android.graphics.Point();
            getActivity().getWindowManager().getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = 1;
            int rotatedPreviewHeight = i1;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedPreviewWidth = i1;
                rotatedPreviewHeight = i;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            maxPreviewWidth = min(maxPreviewWidth, MAX_PREVIEW_WIDTH);
            maxPreviewHeight = min(maxPreviewHeight, MAX_PREVIEW_HEIGHT);
            mPreViewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);
        }

        Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
            // Collect the supported resolutions that are at least as big as the preview Surface
            List<Size> bigEnough = new ArrayList<>();
            // Collect the supported resolutions that are smaller than the preview Surface
            List<Size> notBigEnough = new ArrayList<>();
            int w = aspectRatio.getWidth();
            int h = aspectRatio.getHeight();
            for (Size option : choices) {
                if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                        option.getHeight() == option.getWidth() * 3 / 4) {
                    if (option.getWidth() >= textureViewWidth &&
                            option.getHeight() >= textureViewHeight) {
                        bigEnough.add(option);
                    } else {
                        notBigEnough.add(option);
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            if (bigEnough.size() > 0) {
                return Collections.min(bigEnough, new CompareSizeByArea());
            } else if (notBigEnough.size() > 0) {
                return Collections.max(notBigEnough, new CompareSizeByArea());
            } else {
                infoLog("couldn't find any suitable preview size");
                return choices[0];
            }

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    // TODO
    ImageReader.OnImageAvailableListener onImageAvaiableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            mHandler.post(new ImageSaver(imageReader.acquireNextImage()));
        }
    };

    // 拍照
    View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            // TODO 拍照
            return false;
        }
    };

    void updateCameraPreviewSession() throws CameraAccessException {
        // TODO
    }

    CaptureRequest.Builder initDngBuilder() {
        CaptureRequest.Builder captureBuilder = null;
        // TODO
        return captureBuilder;
    };

    class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    class ImageSaver implements Runnable {
        Image reader;

        ImageSaver(Image reader) {
            this.reader = reader;
        }

        @Override
        public void run() {
            // TODO
        }
    }

    class InnerCallBack implements Handler.Callback {
        @Override
        public boolean handleMessage(@NonNull Message message) {
            // TODO
            return false;
        }
    }
}
