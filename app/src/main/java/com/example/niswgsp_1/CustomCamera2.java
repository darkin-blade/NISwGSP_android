package com.example.niswgsp_1;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import Jama.Matrix;

import static com.example.niswgsp_1.MainActivity.PERMISSION_CAMERA_REQUEST_CODE;
import static com.example.niswgsp_1.MainActivity.appPath;

public class CustomCamera2 extends DialogFragment {
    Button btnCapture;
    Button btnStitch;
    Button btnBack;
    Button btnDebug;
    TextureView cameraPreview;
    TextView text1_1, text1_2, text1_3, text1_4;
    TextView text2_1, text2_2, text2_3, text2_4;
    TextView photoNum;

    // 实时镜头
    ImageView myImageView;
    Bitmap myBitmap;
    Canvas myCanvas;
    Paint myPaint;

    CameraDevice mCameraDevice;// 摄像头设备,(参数:预览尺寸,拍照尺寸等)
    CameraCaptureSession mCameraCaptureSession;// 相机捕获会话,用于处理拍照和预览的工作
    CaptureRequest.Builder captureRequestBuilder;// 捕获请求,定义输出缓冲区及显示界面(TextureView或SurfaceView)

    Size previewSize;// 在textureView预览的尺寸
    Size captureSize;// 拍摄的尺寸

    ImageReader mImageReader;
    Handler backgroundHandler;
    HandlerThread backgroundThread;// TODO 用于保存照片的线程

    static public ArrayList<String> photo_name = new ArrayList<>();// 图片地址list
    static public ArrayList<ArrayList<Double> > photo_rotation = new ArrayList<>();// 图片角度list
    static public int photo_num;// 照片总数
    static public int photo_index;// 用于照片命名
    static public int photo_var;// 信号量
    int capture_times;

    // 传感器
    SensorManager mSensorManager;
    Sensor mAccelerator;// 加速度传感器
    Sensor mGravity;// 重力传感器
    Sensor mMagnet;// 地磁场传感器
    Sensor mRotation;// 旋转传感器

    // 当前图片
    File file;// 图片文件
    double gravity_theta;// 手机在球面切面上的旋转角度
    double this_longitude;// 经度
    double this_latitude;// 纬度
    float acceleratorValue[] = new float[3];
    float magnetValue[] = new float[3];
    float rotationMatrix[] = new float[9];// 旋转矩阵
    float orientationValue[] = new float[3];// 手机方向
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
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER
            || sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    // 加速度
                    System.arraycopy(sensorEvent.values, 0, acceleratorValue, 0, acceleratorValue.length);
                } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    // 地磁场
                    System.arraycopy(sensorEvent.values, 0, magnetValue, 0, magnetValue.length);
                }
                // 更新手机方位角
                SensorManager.getRotationMatrix(rotationMatrix, null, acceleratorValue, magnetValue);
                SensorManager.getOrientation(rotationMatrix, orientationValue);

                long cur_time = System.currentTimeMillis();
                long time_interval = cur_time - last_time_1;
                if (time_interval > 500) {
                    last_time_1 = cur_time;
                    text1_2.setText("" + (int) Math.toDegrees(orientationValue[0]));
                    text1_3.setText("" + (int) Math.toDegrees(orientationValue[1]));
                    text1_4.setText("" + (int) Math.toDegrees(orientationValue[2]));
                }
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
                this_latitude = Math.acos(sensorEvent.values[2] / gravity) - Math.PI / 2;// cos = z / g

                long cur_time = System.currentTimeMillis();
                long time_interval = cur_time - last_time_2;
                if (time_interval > 500) {
                    last_time_2 = cur_time;
//                    text2_1.setText("" + sensorEvent.values[0]);
                    text2_2.setText("水平: " + (int) Math.toDegrees(this_longitude));
                    text2_3.setText("仰角: " + (int) Math.toDegrees(this_latitude));
                    text2_4.setText("切面: " + (int) Math.toDegrees(gravity_theta));
                }
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // 旋转
                // 水平角度调整
                this_longitude = Math.atan(sensorEvent.values[1] / sensorEvent.values[0]) * 2 + gravity_theta;// tan = y / x
                if (this_longitude < - Math.PI) {
                    this_longitude += 2 * Math.PI;
                } else if (this_longitude > Math.PI) {
                    this_longitude -= 2 * Math.PI;
                }
            }
            // 球面距离计算
            long cur_time = System.currentTimeMillis();
            long time_interval = cur_time - last_time_4;
            if (time_interval > 300) {
                last_time_4 = cur_time;
                double last_longitude = 0;
                double last_latitude = - Math.PI / 2;
                if (photo_num != 0) {
                    last_longitude = photo_rotation.get(photo_num - 1).get(0);
                    last_latitude = photo_rotation.get(photo_num - 1).get(1);
                }
                double sphere_dis = 1000 * Math.acos(Math.cos(last_latitude) * Math.cos(this_latitude) * Math.cos(this_longitude - last_longitude)
                + Math.sin(last_latitude) * Math.sin(this_latitude));
                text2_1.setText("" + (int) sphere_dis);

                if (capture_times > 0) {
                    // 按下快门
                    if (photo_num == 0 || sphere_dis > 100) {
                        // 拍摄照片
                        takePictures();
                        // 照片去重
                        removeRepeat();
                    }
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
        dismiss_result = 0;
        photo_name.clear();
        photo_rotation.clear();
        photo_num = 0;
        photo_index = 0;
        photo_var = 0;
        capture_times = 0;
    }

    void initSensor() {
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mAccelerator = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);// 加速度
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);// 重力
        mMagnet = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);// 地磁场
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);// 旋转
        // 注册监听
//        mSensorManager.registerListener(mSensorEventListener, mAccelerator, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSensorEventListener, mRotation, SensorManager.SENSOR_DELAY_UI);
//        mSensorManager.registerListener(mSensorEventListener, mMagnet, SensorManager.SENSOR_DELAY_UI);
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
        myImageView = view.findViewById(R.id.my_canvas);

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
                // TODO
            }
        });

        btnStitch = view.findViewById(R.id.stitch);
        btnStitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (photo_var == 0) {
                    dismiss_result = 1;
                    dismiss();
                }
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
                // 获取canvas
                if (myBitmap == null) {
                    myBitmap = Bitmap.createBitmap(myImageView.getWidth(), myImageView.getHeight(), Bitmap.Config.ARGB_8888);
                    myCanvas = new Canvas(myBitmap);
                    myPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    myPaint.setStrokeWidth(5);
                    myPaint.setColor(Color.WHITE);
                }
                myCanvas.drawCircle(200, 200, 50, myPaint);
                myImageView.setImageBitmap(myBitmap);
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
                                photo_var ++;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    if (backgroundThread == null) {
                        backgroundThread = new HandlerThread("camera background");
                        backgroundThread.start();
                        backgroundHandler = new Handler(backgroundThread.getLooper());
                    }
                    backgroundHandler.post(new ImageSaver(bytes));
                } finally {
                    if (image != null) {
                        image.close();// 画面会卡住
                    }
                }

            }
        }, backgroundHandler);
    }

    void takePictures() {
        photo_num ++;
        photo_index ++;
        photo_var --;
        photoNum.setText("photos: " + photo_num);
        // 保存到图片list
        String timeStamp = photo_index + ".jpg";
        file = new File(appPath, timeStamp);
        photo_name.add(file.getAbsolutePath());
        // 记录照片的角度
        ArrayList<Double> tmp_rotation = new ArrayList<>();
        tmp_rotation.add(this_longitude);
        tmp_rotation.add(this_latitude);
        tmp_rotation.add(gravity_theta);
        photo_rotation.add(tmp_rotation);

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

    void removeRepeat() {
        // 删除重复度较高的照片
        double point[][] = new double[4][3];// A(x, y, z), B(x, y, z), C(x, y, z), C'(x, y, z)
        double distance_1;// AB
        double distance_2;// CC' / 2
        if (photo_num >= 3) {
            // 计算3点的空间直角坐标(x与经度0的方向平行)(右手系)
            ArrayList<Double> tmp;
            double xy;// sqrt(x^2 + y^2)
            // A
            tmp = photo_rotation.get(photo_num - 3);
            point[0][2] = Math.sin(tmp.get(1));// 纬度计算z
            xy = Math.sqrt(1 - point[0][2] * point[0][2]);
            point[0][0] = xy * Math.cos(tmp.get(0));// 经度计算x
            point[0][1] = xy * Math.sin(tmp.get(0));// 经度计算y
            // B
            tmp = photo_rotation.get(photo_num - 2);
            point[1][2] = Math.sin(tmp.get(1));// 纬度计算z
            xy = Math.sqrt(1 - point[1][2] * point[1][2]);// sqrt(x^2 + y^2)
            point[1][0] = xy * Math.cos(tmp.get(0));// 经度计算x
            point[1][1] = xy * Math.sin(tmp.get(0));// 经度计算y
            // C
            tmp = photo_rotation.get(photo_num - 1);
            point[2][2] = Math.sin(tmp.get(1));// 纬度计算z
            xy = Math.sqrt(1 - point[2][2] * point[2][2]);// sqrt(x^2 + y^2)
            point[2][0] = xy * Math.cos(tmp.get(0));// 经度计算x
            point[2][1] = xy * Math.sin(tmp.get(0));// 经度计算y

            // 计算弧AB长度
            distance_1 = distance(point[0], point[1]);// AB
            if (distance_1 >= 300) {
                // 保留B点
                return;
            }

            // 计算C在OAB上的垂足D
            Matrix A = new Matrix(new double[][]{
                    {point[0][0]*point[0][0] + point[0][1]*point[0][1] + point[0][2]*point[0][2],
                            point[0][0]*point[1][0] + point[0][1]*point[1][1] + point[0][2]*point[1][2]},
                    {point[0][0]*point[1][0] + point[0][1]*point[1][1] + point[0][2]*point[1][2],
                            point[1][0]*point[1][0] + point[1][1]*point[1][1] + point[1][2]*point[1][2]}
            });
            Matrix b = new Matrix(new double[][]{
                    {point[0][0]*point[2][0] + point[0][1]*point[2][1] + point[0][2]*point[2][2]},
                    {point[1][0]*point[2][0] + point[1][1]*point[2][1] + point[1][2]*point[2][2]},
            });
            Matrix x = A.solve(b);

            // 计算C到关于D的对称点C'的弧长
            double delta_x = x.get(0, 0) * point[0][0] + x.get(1, 0) * point[1][0] - point[2][0];// dx - cx
            double delta_y = x.get(0, 0) * point[0][1] + x.get(1, 0) * point[1][1] - point[2][1];// dy - cy
            double delta_z = x.get(0, 0) * point[0][2] + x.get(1, 0) * point[1][2] - point[2][2];// dz - cz
            point[3][0] = point[2][0] + 2 * delta_x;
            point[3][1] = point[2][1] + 2 * delta_y;
            point[3][2] = point[2][2] + 2 * delta_z;

            // 计算C到AB的距离
            distance_2 = distance(point[2], point[3])/2;// C to AB
//            infoLog("(" + photo_num + "): [" + distance_1 + ", " + distance_2 + "]");
            if (distance_2 <= 80) {// TODO 阈值
                // 删除B点
                photo_name.remove(photo_num - 2);
                photo_rotation.remove(photo_num - 2);
                photo_num --;
                infoLog("photo num: " + photo_name.size());
                for (int i = 0; i < photo_name.size(); i ++) {
                    infoLog((i + 1) + "/" + photo_name.size() + ": " + photo_name.get(i));
                }
            }
        }

    }

    double distance(double point_1[], double point_2[]) {
        double longitude_1, latitude_1;
        double longitude_2, latitude_2;
        longitude_1 = Math.atan(point_1[1] / point_1[0]);// tan = y / x
        latitude_1 = Math.asin(point_1[2]);// sin = z
        longitude_2 = Math.atan(point_2[1] / point_2[0]);// tan = y / x
        latitude_2 = Math.asin(point_2[2]);// sin = z
        double sphere_dis = 1000 * Math.acos(Math.cos(latitude_1) * Math.cos(latitude_2) * Math.cos(longitude_2 - longitude_1)
                + Math.sin(latitude_1) * Math.sin(latitude_2));
        return sphere_dis;
    }

    class ImageSaver implements Runnable {
        byte[] bytes;
        public ImageSaver(byte[] b) {
            bytes = b;
        }

        @Override
        public void run() {
            OutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(file);
                outputStream.write(bytes);
                infoLog("save photo " + file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
