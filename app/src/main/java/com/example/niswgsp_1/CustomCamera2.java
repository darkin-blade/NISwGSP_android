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
    Button btnDebug;
    TextureView cameraPreview;
    TextView text1_1, text1_2, text1_3, text1_4;
    TextView text2_1, text2_2, text2_3, text2_4;
    TextView photoNum;

    CameraDevice mCameraDevice;// 摄像头设备,(参数:预览尺寸,拍照尺寸等)
    CameraCaptureSession mCameraCaptureSession;// 相机捕获会话,用于处理拍照和预览的工作
    CaptureRequest.Builder captureRequestBuilder;// 捕获请求,定义输出缓冲区及显示界面(TextureView或SurfaceView)

    Size previewSize;// 在textureView预览的尺寸
    Size captureSize;// 拍摄的尺寸

    ImageReader mImageReader;
    Handler backgroundHandler;
    HandlerThread backgroundThread;// TODO 用于保存照片的线程

    static public ArrayList<String> photo_name = new ArrayList<>();// 图片地址list
    static public int photo_num;// 照片总数
    int capture_times;// TODO

    // 传感器
    SensorManager mSensorManager;
    Sensor mAccelerator;// 加速度传感器
    Sensor mGravity;// 重力传感器
    Sensor mMagnet;// 地磁场传感器
    Sensor mRotation;// 旋转传感器

    // 当前图片
    File file;// 图片文件
    double gravity_theta;// 手机在球面切面上的旋转角度
    double plane_theta;// 手机与球心的连线在水平面上投影, 相对于水平面上角度0的旋转角度
    double height_theta;// 手机与球心的连线, 与重力方向的平面上, 相对于重力方向的旋转角度(0, 180)
    double acceleratorValue[] = new double[3];
    double magnetValue[] = new double[3];
    double rotationMatrix[] = new double[9];// 旋转矩阵
    double orientationValue[] = new double[3];// 手机方向
    long last_time_1, last_time_2, last_time_3, last_time_4;// 每个传感器的UI刷新时间

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
            // TODO 拍照
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                // 加速度
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GRAVITY) {
                // 重力
                double gravity = Math.sqrt(sensorEvent.values[0] * sensorEvent.values[0]
                        + sensorEvent.values[1] * sensorEvent.values[1]
                        + sensorEvent.values[2] * sensorEvent.values[2]);
                gravity_theta = Math.atan(sensorEvent.values[0] / sensorEvent.values[1]);// tan = x / y
                if (gravity_theta > 0 && sensorEvent.values[0] < 0) {
                    gravity_theta -= Math.PI;
                } else if (gravity_theta < 0 && sensorEvent.values[0] > 0) {
                    gravity_theta += Math.PI;
                }
                height_theta = Math.acos(sensorEvent.values[2] / gravity);// cos = z / g

                long cur_time = System.currentTimeMillis();
                long time_interval = cur_time - last_time_1;
                if (time_interval > 500) {
                    last_time_1 = cur_time;
//                    text2_1.setText("" + sensorEvent.values[0]);
                    text2_2.setText("水平: " + (int) Math.toDegrees(plane_theta));
                    text2_3.setText("仰角: " + (int) Math.toDegrees(height_theta));
                    text2_4.setText("切面: " + (int) Math.toDegrees(gravity_theta));
                }
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                // 地磁场
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // 旋转
                // 水平角度调整
                plane_theta = Math.atan(sensorEvent.values[1] / sensorEvent.values[0]) * 2 + gravity_theta;// tan = y / x
                if (plane_theta < - Math.PI) {
                    plane_theta += 2 * Math.PI;
                } else if (plane_theta > Math.PI) {
                    plane_theta -= 2 * Math.PI;
                }
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
        photo_num = 0;
        capture_times = 0;
    }

    void initSensor() {
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mAccelerator = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);// 加速度
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);// 重力
        mMagnet = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);// 地磁场
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);// 旋转
        // 注册监听
        mSensorManager.registerListener(mSensorEventListener, mAccelerator, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSensorEventListener, mRotation, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSensorEventListener, mMagnet, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSensorEventListener, mGravity, SensorManager.SENSOR_DELAY_UI);
    }

    void destroySensor() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    void initUI(View view) {
        text1_1 = view.findViewById(R.id.text1_1);
        text1_2 = view.findViewById(R.id.text1_2);
        text1_3 = view.findViewById(R.id.text1_3);
        text1_4 = view.findViewById(R.id.text1_4);
        text2_1 = view.findViewById(R.id.text2_1);
        text2_2 = view.findViewById(R.id.text2_2);
        text2_3 = view.findViewById(R.id.text2_3);
        text2_4 = view.findViewById(R.id.text2_4);
        photoNum = view.findViewById(R.id.text2_1);
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
                takePictures();// 无条件拍摄最后一张

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

        btnDebug = view.findViewById(R.id.debug);
        btnDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO 根据两个旋转矩阵, 计算3个方向的旋转角度
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

            // 只适用 SDK > 23
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
                    final byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    // 新建线程保存图片
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
                        image.close();// 画面会卡住
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
        // TODO 记录照片的角度
        // 保存到图片list
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
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
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
}
