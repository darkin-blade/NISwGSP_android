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
    TextView orientationX, orientationY, orientationZ;
    TextView acceleratorX, acceleratorY, acceleratorZ;
    TextView rotationTheta;
    TextView photoNum;

    CameraDevice mCameraDevice;// 摄像头设备,(参数:预览尺寸,拍照尺寸等)
    CameraCaptureSession mCameraCaptureSession;// 相机捕获会话,用于处理拍照和预览的工作
    CaptureRequest.Builder captureRequestBuilder;// 捕获请求,定义输出缓冲区及显示界面(TextureView或SurfaceView)

    Size previewSize;// 在textureView预览的尺寸
    Size captureSize;// 拍摄的尺寸

    // 当前图片
    File file;// 图片文件
    ArrayList<Integer> this_orientation = new ArrayList<>();// TODO 当前偏转角度

    ImageReader mImageReader;
    Handler backgroundHandler;
    HandlerThread backgroundThread;// TODO 用于保存照片的线程

    static public ArrayList<String> photo_name = new ArrayList<>();// 图片地址list
    static public ArrayList<ArrayList<Integer> > photo_orientation = new ArrayList<>();// 每张图片的xyz偏转角度
    static public int photo_num;// 照片总数
    int capture_times;// TODO

    // 传感器
    SensorManager mSensorManager;
    Sensor mAccelerator;// 加速度传感器
    Sensor mMagnet;// 地磁传感器
    Sensor mGame;// 游戏传感器
    float[] accelerometerValue = new float[3];// 加速度传感器xyz
    float[] magnetmeterValue = new float[3];// 地磁传感器xyz
    float[] rotationValue = new float[4];// 游戏传感器xyz
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
                System.arraycopy(sensorEvent.values, 0, accelerometerValue, 0, accelerometerValue.length);
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                // 磁场改变
                System.arraycopy(sensorEvent.values, 0, magnetmeterValue, 0, magnetmeterValue.length);
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // TODO
                System.arraycopy(sensorEvent.values, 0, rotationValue, 0, rotationValue.length);
            }

            // 计算旋转角度
            SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValue, magnetmeterValue);
            SensorManager.getOrientation(rotationMatrix, orientationValue);
//            infoLog("orientation: " + orientationValue[0] + ", " + orientationValue[1] + ", " + orientationValue[2]);

            // 将四元数转换为欧拉角
            int roll, pitch, yaw;
            float quaternion_w = rotationValue[3];// cos(theta / 2)
            float quaternion_x = rotationValue[0];
            float quaternion_y = rotationValue[1];
            float quaternion_z = rotationValue[2];
            float sin_r = 2 * (quaternion_w * quaternion_x + quaternion_y * quaternion_z);
            float cos_r = 1 - 2 * (quaternion_x * quaternion_x + quaternion_y * quaternion_y);
            roll = (int) Math.toDegrees(Math.atan2(sin_r, cos_r));// 方位角
            float sin_p = 2 * (quaternion_w * quaternion_y - quaternion_z * quaternion_x);
            pitch = (int) Math.toDegrees(Math.asin(sin_p));
            float sin_y = 2 * (quaternion_w * quaternion_z + quaternion_x * quaternion_y);
            float cos_y = 1 - 2 * (quaternion_y * quaternion_y + quaternion_z * quaternion_z);
            yaw = (int) Math.toDegrees(Math.atan2(sin_y, cos_y));
            // 将四元数转换为轴角 axis angle
            int theta = (int) Math.round(Math.toDegrees(Math.acos(quaternion_w))) * 2;
            float sin_theta = (float) Math.sin(Math.acos(quaternion_w));// sin(theta / 2)
            float axis_x = quaternion_x / sin_theta;
            float axis_y = quaternion_y / sin_theta;
            float axis_z = quaternion_z / sin_theta;

            long cur_time = System.currentTimeMillis();
            long time_interval = cur_time - last_time;
            if (time_interval > 500) {
                // 更新UI
                acceleratorX.setText("" + axis_x);
                acceleratorY.setText("" + axis_y);
                acceleratorZ.setText("" + axis_z);
                rotationTheta.setText("" + theta);

                orientationX.setText("x: " + rotationValue[0]);
                orientationY.setText("y: " + rotationValue[1]);
                orientationZ.setText("z: " + rotationValue[2]);
                last_time = cur_time;
            }

            if (capture_times > 0) {
                // 按下快门, TODO 拍摄条件判断
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
        infoLog((getActivity() == null) + " is null");
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
        photo_orientation.clear();
        photo_num = 0;
        capture_times = 0;
    }

    void initSensor() {
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);// 获得传感器manager
        mAccelerator = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);// 获取加速度传感器
        mMagnet = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);// 地磁传感器
        mGame = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);// 游戏传感器
        // 注册监听
        mSensorManager.registerListener(mSensorEventListener, mAccelerator, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSensorEventListener, mMagnet, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSensorEventListener, mGame, SensorManager.SENSOR_DELAY_UI);
    }

    void destroySensor() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    void initUI(View view) {
        orientationX = view.findViewById(R.id.orientationX);
        orientationY = view.findViewById(R.id.orientationY);
        orientationZ = view.findViewById(R.id.orientationZ);
        acceleratorX = view.findViewById(R.id.rotation_x);
        acceleratorY = view.findViewById(R.id.rotation_y);
        acceleratorZ = view.findViewById(R.id.rotation_z);
        rotationTheta = view.findViewById(R.id.rotation_theta);
        photoNum = view.findViewById(R.id.photo_num);
        photoNum.setText("photos: " + photo_num);

        btnCapture = view.findViewById(R.id.capture);
        btnCapture.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                capture_times ++;
                return false;
            }
        });
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss_result = 1;
                capture_times = 0;
                takePictures();// TODO 无条件拍摄最后一张

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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

    void setupImageReader() {
        mImageReader = ImageReader.newInstance(captureSize.getWidth(), captureSize.getHeight(), ImageFormat.JPEG, 2);
        // 对内容进行监听
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                // 有新的照片
                Image image = reader.acquireLatestImage();
//                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
                try {
                    // 将帧数据转成字节数组,类似回调的预览数据
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    final byte[] bytes = new byte[buffer.capacity()];// TODO final
                    buffer.get(bytes);

                    // TODO 新建线程保存图片
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                OutputStream outputStream = new FileOutputStream(file);
                                outputStream.write(bytes);

                                infoLog("save photo " + file);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
//                    if (backgroundThread == null) {
//                        backgroundThread = new HandlerThread("camera background");
//                        backgroundThread.start();
//                        backgroundHandler = new Handler(backgroundThread.getLooper());
//                    }
//                    backgroundHandler.post(new ImageSaver(bytes));
                } finally {
                    if (image != null) {
                        image.close();// TODO 画面会卡住
                    }
                }

            }
        }, backgroundHandler);
    }

    void takePictures() {
//        infoLog(capture_times + "");
//        if (capture_times % 15 != 1) return;
        photo_num ++;
        photoNum.setText("photos: " + photo_num);
        photo_orientation.add((ArrayList<Integer>) this_orientation.clone());// TODO 记录照片的角度
        // TODO 保存到图片list
        String timeStamp = photo_num + ".jpg";
        file = new File(appPath, timeStamp);
        photo_name.add(file.getAbsolutePath());

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
        public ImageSaver(byte[] b) {
            bytes = b;
        }

        @Override
        public void run() {
            try {
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
