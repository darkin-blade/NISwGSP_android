package com.example.niswgsp_1;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static com.example.niswgsp_1.MainActivity.PERMISSION_CAMERA_REQUEST_CODE;
import static com.example.niswgsp_1.MainActivity.appPath;

public class CustomCamera2 extends DialogFragment {
    Button btnCapture;
    Button btnBack;
    TextureView cameraPreview;
    View cameraBackground;
    TextView orientationX, orientationY, orientationZ;

    CameraDevice mCameraDevice;// 摄像头设备,(参数:预览尺寸,拍照尺寸等)
    CameraCaptureSession mCameraCaptureSession;// 相机捕获会话,用于处理拍照和预览的工作
    CaptureRequest.Builder captureRequestBuilder;// 捕获请求,定义输出缓冲区及显示界面(TextureView或SurfaceView)

    Size previewSize;// 在textureView预览的尺寸
    Size captureSize;// 拍摄的尺寸

    File file;
    ImageReader mImageReader;
    static final int REQUEST_CAMERA_PERMISSION = 200;
    Handler backgroundHandler;
    HandlerThread backgroundThread;// TODO

    static public ArrayList<String> photo_name = new ArrayList<>();// 图片地址list
    static public int photo_num;// 照片索引
    int capture_times;// 按钮按下的时间

    // 传感器
    SensorManager mSensorManager;
    Sensor mAccelerator;// 加速度传感器
    Sensor mMagnet;// 地磁传感器
    float[] accelerometerValue = new float[3];// 加速度传感器xyz
    float[] magnetmeterValue = new float[3];// 地磁传感器xyz
    float[] rotationMatrix = new float[9];// 旋转矩阵
    float[] orientationValue = new float[3];// 旋转角度xyz
    long last_time;

    static public int dismiss_result = 0;// 0: 返回, 1: 拍照

    static final SparseArray<Integer> ORIENTATIONS = new SparseArray<>();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                // 加速度改变
//                infoLog("accelerator");
                System.arraycopy(sensorEvent.values, 0, accelerometerValue, 0, accelerometerValue.length);
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                // 磁场改变
//                infoLog("magnet");
                System.arraycopy(sensorEvent.values, 0, magnetmeterValue, 0, magnetmeterValue.length);
            }

            // 计算旋转角度
            SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValue, magnetmeterValue);
            SensorManager.getOrientation(rotationMatrix, orientationValue);
            infoLog("orientation: " + orientationValue[0] + ", " + orientationValue[1] + ", " + orientationValue[2]);

            long cur_time = System.currentTimeMillis();
            long time_interval = cur_time - last_time;
            if (time_interval > 1000) {
                // 更新UI
                orientationX.setText("x: " + (orientationValue[1] * 180));
                orientationY.setText("y: " + (orientationValue[2] * 180));
                orientationZ.setText("z: " + (orientationValue[0] * 180));
                last_time = cur_time;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        // 打开相机后调用
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;// 获取camera对象
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            camera.close();
            mCameraDevice = null;
        }
    };

    @Override
    public void show(FragmentManager fragmentManager, String tag) {
        super.show(fragmentManager, tag);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);// 返回键不能后退
        setStyle(STYLE_NO_FRAME, android.R.style.Theme);// 关闭背景(点击外部不能取消)
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.custom_camera, container);
        getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));// 背景透明

        initCamera();// 初始化变量
        initSensor();// 初始化传感器
        initUI(view);// 初始化按钮

        return view;
    }

    @Override
    public void onDismiss(final DialogInterface dialogInterface) {
        super.onDismiss(dialogInterface);

        destroySensor();// 取消注册传感器

        Activity activity = getActivity();
        if (activity instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) activity).onDismiss(dialogInterface);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
//        mSensorManager.registerListener(mSensorEventListener, mAccelerator, SensorManager.SENSOR_DELAY_UI);// 最慢,适合普通用户界面UI变化的频率
    }

    @Override
    public void onPause() {
        super.onPause();
//        mSensorManager.unregisterListener(mSensorEventListener);
    }

    void initCamera() {
        photo_name.clear();
        photo_num = 0;
    }

    void initSensor() {
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);// 获得传感器manager
        mAccelerator = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);// 获取加速度传感器
        mMagnet = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);// 地磁传感器
        // 注册监听
        mSensorManager.registerListener(mSensorEventListener, mAccelerator, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSensorEventListener, mMagnet, SensorManager.SENSOR_DELAY_UI);
    }

    void destroySensor() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    void initUI(View view) {
        orientationX = view.findViewById(R.id.orientationX);
        orientationY = view.findViewById(R.id.orientationY);
        orientationZ = view.findViewById(R.id.orientationZ);

        btnCapture = view.findViewById(R.id.capture);
        btnCapture.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                capture_times ++;
                takePictures();
                return false;
            }
        });
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                capture_times = 0;
                dismiss_result = 1;
                dismiss();
            }
        });

        btnBack = view.findViewById(R.id.back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss_result = 0;
                dismiss();
            }
        });

        cameraPreview = view.findViewById(R.id.camera_preview);
        cameraPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                openCamera();
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
        });

        cameraBackground = view.findViewById(R.id.camera_background);
    }

    static public void infoLog(String log) {
        Log.i("fuck", log);
    }

    void openCamera() {
        infoLog("open camera");
        CameraManager cameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            String camera_id = cameraManager.getCameraIdList()[CameraCharacteristics.LENS_FACING_FRONT];// 后置摄像头
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(camera_id);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);// 管理camera的输出格式和尺寸
//            previewSize = map.getOutputSizes(SurfaceTexture.class)[0];
            captureSize = new Size(cameraPreview.getHeight(), cameraPreview.getWidth());
            previewSize = new Size(cameraPreview.getHeight() * 2, cameraPreview.getWidth() * 2);
            infoLog("preview: " + previewSize.getWidth() + ", " + previewSize.getHeight());
            infoLog("capture: " + captureSize.getWidth() + ", " + captureSize.getHeight());

            Size[] imgFormatSizes = map.getOutputSizes(ImageFormat.JPEG);
            // 如果jpegSize通过map.getOutputSizes已被赋值,则captureSize按照赋值结果,否则按照自定义
            if (imgFormatSizes != null && imgFormatSizes.length > 0) {
                captureSize = imgFormatSizes[0];
            }
            setupImageReader();

            // TODO 只适用 SDK > 23
            int hasCameraPermission = ContextCompat.checkSelfPermission(getActivity().getApplication(), Manifest.permission.CAMERA);
            if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
                // 有调用相机权限
                infoLog("has permission of camera");
            } else {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQUEST_CODE);
            }

            cameraManager.openCamera(camera_id, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    void setupImageReader() {
        mImageReader = ImageReader.newInstance(captureSize.getWidth(), captureSize.getHeight(), ImageFormat.JPEG, 2);
        // 对内容进行监听
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireLatestImage();
//                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
                photo_num ++;
                String timeStamp = photo_num + ".jpg";
                file = new File(appPath, timeStamp);
                try {
                    // 将帧数据转成字节数组,类似回调的预览数据
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    // 新建线程保存图片
                    if (backgroundThread == null) {
                        backgroundThread = new HandlerThread("camera background");
                        backgroundThread.start();
                        backgroundHandler = new Handler(backgroundThread.getLooper());
                    }
                    backgroundHandler.post(new ImageSaver(bytes, image.getHeight(), image.getWidth()));
                } finally {
                    if (image != null) {
                        image.close();// TODO 画面会卡住
                    }
                }

            }
        }, backgroundHandler);
    }

    void createCameraPreview() {
        SurfaceTexture surfaceTexture = cameraPreview.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        mCameraCaptureSession = session;
                        mCameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    ;
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    void takePictures() {
//        infoLog(capture_times + "");
        if (capture_times % 15 != 1) return;
        // 进行拍摄
        try {
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(mImageReader.getSurface());// 将captureRequest输出到imageReader
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            builder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    try {
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);// TODO
                        mCameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    class ImageSaver implements Runnable {
        byte[] bytes;
        int rows, cols;
        public ImageSaver(byte[] b) {
            bytes = b;
        }
        public ImageSaver(byte[] b, int r, int c) {
            bytes = b;
            rows = r;
            cols = c;
        }

        @Override
        public void run() {
            try {
                // TODO 对比相似度

                // TODO byte 转 Mat

                OutputStream outputStream = new FileOutputStream(file);
                outputStream.write(bytes);

                // TODO 保存到图片list
                photo_name.add(file.getAbsolutePath());

                infoLog("save photo " + file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
