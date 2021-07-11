package com.example.my_stitcher;

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
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.LayoutInflater;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import static com.example.my_stitcher.MainActivity.PERMISSION_CAMERA_REQUEST_CODE;
import static com.example.my_stitcher.MainActivity.appPath;

public class CustomCamera3 extends DialogFragment {
    Button btnCapture;
    Button btnStitch;
    Button btnBack;
    Button btnDebug;
    TextureView cameraPreview;
    TextView text1_1, text1_2, text1_3, text1_4;

    CameraDevice mCameraDevice;// 摄像头设备,(参数:预览尺寸,拍照尺寸等)
    CameraCaptureSession mCameraCaptureSession;// 相机捕获会话,用于处理拍照和预览的工作
    CaptureRequest.Builder captureRequestBuilder;// 捕获请求,定义输出缓冲区及显示界面(TextureView或SurfaceView)

    Size previewSize;// 在textureView预览的尺寸
    Size captureSize;// 拍摄的尺寸

    ImageReader mImageReader;
    Handler backgroundHandler;

    static public int photo_num;// 照片总数
    static public int photo_index;// 用于照片命名
    // 照片信息
    static public ArrayList<String> photo_name = new ArrayList<>();// 图片地址list
    static public ArrayList<ArrayList<Double>> photo_rotation = new ArrayList<>();// 图片角度list
    // 照片配对
    static public ArrayList<Integer> pairFirst = new ArrayList<>();
    static public ArrayList<Integer> pairSecond = new ArrayList<>();

    // 解决多线程问题
    Queue<File> file_queue = new LinkedList<>();// 文件名

    // 传感器
    SensorManager mSensorManager;
    Sensor mGravity;// 重力传感器
    Sensor mRotation;// 旋转传感器

    // 当前图片的信息
    double gravity_theta;// 手机在球面切面上的旋转角度
    double this_longitude;// 经度
    double this_latitude;// 纬度
    long last_time_1, last_time_2, last_time_3, last_time_4;// 每个传感器的UI刷新时间

    double[] photo_center = new double[2];// 所有照片的中心
    static public int dismiss_result = 0;// 0: 返回, 1: 保存拍摄

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
            if (sensorEvent.sensor.getType() == Sensor.TYPE_GRAVITY) {
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
                    text1_2.setText("水平: " + (int) Math.toDegrees(this_longitude));
                    text1_3.setText("仰角: " + (int) Math.toDegrees(this_latitude));
                    text1_4.setText("切面: " + (int) Math.toDegrees(gravity_theta));
                }
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // 旋转
                // 水平角度调整
                this_longitude = Math.atan(sensorEvent.values[1] / sensorEvent.values[0]) * 2 + gravity_theta;// tan = y / x
                if (this_longitude < -Math.PI) {
                    this_longitude += 2 * Math.PI;
                } else if (this_longitude > Math.PI) {
                    this_longitude -= 2 * Math.PI;
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
        View view = inflater.inflate(R.layout.custom_camera_3, container);
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

    void initCamera() {
        dismiss_result = 0;

        photo_name.clear();
        photo_rotation.clear();
        pairFirst.clear();
        pairSecond.clear();

        photo_num = 0;
        photo_index = 0;
        photo_center[0] = 0;
        photo_center[1] = 0;
    }

    void initSensor() {
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);// 重力
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);// 旋转
        // 注册监听 TODO 使用最短的延迟
        mSensorManager.registerListener(mSensorEventListener, mRotation, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(mSensorEventListener, mGravity, SensorManager.SENSOR_DELAY_FASTEST);
    }

    void destroySensor() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    void initUI(View view) {
        text1_1 = view.findViewById(R.id.text1_1);
        text1_2 = view.findViewById(R.id.text1_2);
        text1_3 = view.findViewById(R.id.text1_3);
        text1_4 = view.findViewById(R.id.text1_4);
        text1_1.setText("photos: " + photo_num);

        btnCapture = view.findViewById(R.id.capture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 手动拍摄
                takePictures();
            }
        });

        btnStitch = view.findViewById(R.id.stitch);
        btnStitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (file_queue.size() == 0) {// 等待其他线程把照片存完
                    dismiss_result = 1;
                    setNewCenter();// TODO 计算照片的中心方位
                    computePairs();// TODO 计算照片的配对关系
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
            public void onClick(View view) { }
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
        CameraManager cameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            String camera_id = cameraManager.getCameraIdList()[CameraCharacteristics.LENS_FACING_FRONT];// 后置摄像头
//            String camera_id = cameraManager.getCameraIdList()[CameraCharacteristics.LENS_FACING_BACK];// 前置摄像头
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
                                File tmp_file = file_queue.poll();// 获取并从队列删除第一个元素
                                OutputStream outputStream = new FileOutputStream(tmp_file);
                                outputStream.write(bytes);

                                infoLog("save photo " + tmp_file);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
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
        text1_1.setText("photos: " + photo_num);
        // 保存到图片list
        File photoFile = new File(appPath, photo_index + ".jpg");
        file_queue.offer(photoFile);
        photo_name.add(photoFile.getAbsolutePath());// 此时file_queue中的文件不一定处理完了
        // 记录照片的角度
        ArrayList<Double> tmp_rotation = new ArrayList<>();
        tmp_rotation.add(this_longitude);
        tmp_rotation.add(this_latitude);
        tmp_rotation.add(gravity_theta);
        photo_rotation.add(tmp_rotation);

        // 保存照片的初始角度 TODO 角度的顺序: 外在旋转的顺序
//        File infoFile = new File(appPath, photo_index + ".txt");
//        try {
//            FileOutputStream stream = new FileOutputStream(infoFile);
//            if (!infoFile.exists()) {
//                infoFile.createNewFile();
//            }
//            String photoInfo = gravity_theta + "\n" + this_latitude + "\n" + this_longitude;
//            byte[] infoInBytes = photoInfo.getBytes();
//            stream.write(infoInBytes);
//            stream.flush();
//            stream.close();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

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

    void setNewCenter() {
        // 计算照片的平均经度和纬度
        // 把所有点转换成球面向量并求解向量和
        double[] tmp_sphere = new double[3];
        double[] tmp_coordinate = new double[3];
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        for (int i = 0; i < photo_num; i ++) {
            ArrayList<Double> tmp_rotation = photo_rotation.get(i);
            tmp_sphere[0] = tmp_rotation.get(0);// 经度
            tmp_sphere[1] = tmp_rotation.get(1);
            sphere2Coordinate(tmp_sphere, tmp_coordinate);
            sumX += tmp_coordinate[0];
            sumY += tmp_coordinate[1];
            sumZ += tmp_coordinate[2];
        }
        // 计算中心位置
        tmp_coordinate[0] = sumX / photo_num;
        tmp_coordinate[1] = sumY / photo_num;
        tmp_coordinate[2] = sumZ / photo_num;
        // 归一化
        double rate = Math.sqrt(1 / (tmp_coordinate[0]*tmp_coordinate[0] + tmp_coordinate[1]*tmp_coordinate[1] + tmp_coordinate[2]*tmp_coordinate[2]));
        tmp_coordinate[0] *= rate;
        tmp_coordinate[1] *= rate;
        tmp_coordinate[2] *= rate;
        coordinate2Sphere(tmp_coordinate, tmp_sphere);
        // 照片中心
        photo_center[0] = tmp_sphere[0];
        photo_center[1] = tmp_sphere[1];
        // 计算新的北极点(只可能在北半球)
        double[] sphereN_ = new double[2];
        sphereN_[1] = photo_center[1] + Math.PI / 2;// 纬度
        if (sphereN_[1] > Math.PI / 2) {
            // 越过了北极点
            sphereN_[1] = Math.PI - sphereN_[1];
            // 计算经度
            if (photo_center[0] > 0) {
                sphereN_[0] = photo_center[0] - Math.PI;
            } else {
                sphereN_[0] = photo_center[0] + Math.PI;
            }
        } else {
            sphereN_[0] = photo_center[0];// 经度
        }
        // 换算成直角坐标系坐标
        double[] pointN_ = new double[3];
        sphere2Coordinate(sphereN_, pointN_);
        // 重新计算所有点的屏幕旋转角度
        for (int i = 0; i < photo_num; i ++) {
            // 将B点转换成直角坐标系坐标
            ArrayList<Double> tmp_rotation = photo_rotation.get(i);
            tmp_sphere[0] = tmp_rotation.get(0);// 经度
            tmp_sphere[1] = tmp_rotation.get(1);// 纬度
            tmp_sphere[2] = tmp_rotation.get(2);// 屏幕旋转角度
            double new_rotation = sphereThetaConvert(pointN_, tmp_sphere);
            tmp_rotation.set(2, new_rotation);
            photo_rotation.set(i, tmp_rotation);
        }

        // 保存调整之后的角度
        for (int i = 0; i < photo_num; i ++) {
            File infoFile = new File(appPath,  (i + 1) + ".txt");
            try {
                FileOutputStream stream = new FileOutputStream(infoFile);
                if (!infoFile.exists()) {
                    infoFile.createNewFile();
                }
                String rotationInfo = photo_rotation.get(i).get(2) + "\n";
                byte[] infoInBytes = rotationInfo.getBytes();
                stream.write(infoInBytes);
                stream.flush();
                stream.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void computePairs() {
        // TODO 计算图片的配对信息
    }

    private void sphere2Coordinate(final double[] sphere, double[] coordinate) {
        // 球面坐标系转空间直角坐标系, OX方向为0经度方向
        coordinate[2] = Math.sin(sphere[1]);// 纬度计算z
        double xy = Math.sqrt(1 - coordinate[2] * coordinate[2]);// sqrt(x^2 + y^2)
        coordinate[0] = xy * Math.cos(sphere[0]);// 经度计算x
        coordinate[1] = xy * Math.sin(sphere[0]);// 经度计算y
    }

    private void coordinate2Sphere(final double[] coordinate, double[] sphere) {
        // 空间直角坐标系转球面坐标系
        sphere[0] = Math.atan(coordinate[1] / coordinate[0]);// TODO tan = y / x
        sphere[1] = Math.asin(coordinate[2]);
        if (coordinate[1] > 0 && sphere[0] < 0) {
            sphere[0] += Math.PI;
        } else if (coordinate[1] < 0 && sphere[0] > 0) {
            sphere[0] -= Math.PI;
        }
    }

    private void rotateByVector(final double[] pointP, final double[] vectorAxis, final double _theta, double[] pointP_) {
        // 计算P点绕axis旋转theta之后的坐标P'
        // 右手大拇指与axis同向, 四指的方向为正方向
        double xP = pointP[0];
        double yP = pointP[1];
        double zP = pointP[2];
        // 向量的坐标
        double xV = vectorAxis[0];
        double yV = vectorAxis[1];
        double zV = vectorAxis[2];
        double c = Math.cos(_theta);
        double s = Math.sin(_theta);
        double xP_ = (xV*xV*(1 - c) + c)*xP + (xV*yV*(1 - c) - zV*s)*yP + (xV*zV*(1 - c) + yV*s)*zP;
        double yP_ = (yV*xV*(1 - c) + zV*s)*xP + (yV*yV*(1- c) + c)*yP + (yV*zV*(1 - c) - xV*s)*zP;
        double zP_ = (zV*xV*(1 - c) - yV*s)*xP + (zV*yV*(1 - c) + xV*s)*yP + (zV*zV*(1 - c) + c)*zP;
        pointP_[0] = xP_;
        pointP_[1] = yP_;
        pointP_[2] = zP_;
    }

    private double sphereThetaConvert(final double[] pointM, final double[] sphereP) {
        double a, b, product, cos;// 临时变量
        // 计算求坐标系变换后, 新的切面旋转角度
        // 输入为: 新北极点(原空间直角坐标系下的坐标), 待变换的点(必须是3维, 包含切面旋转角度)
        final double[] pointN = new double[]{0, 0, 1};// 原北极点
        // 计算在P处的正北方向PR
        double[] pointP = new double[3];
        double[] vectorPR = new double[3];
        sphere2Coordinate(sphereP, pointP);
        // 计算OP与ON的夹角
        cos = (pointN[0]*pointP[0] + pointN[1]*pointP[1] + pointN[2]*pointP[2])/
                (Math.sqrt(pointN[0]*pointN[0] + pointN[1]*pointN[1] + pointN[2]*pointN[2])
                *Math.sqrt(pointP[0]*pointP[0] + pointP[1]*pointP[1] + pointP[2]*pointP[2]));
        if (cos > 1) {
            cos = 1;
        } else if (cos < -1) {
            cos = -1;
        }
        double thetaNOP = Math.acos(cos);
        a = 1/Math.abs(Math.sin(thetaNOP));
        b = -a * Math.cos(thetaNOP);
        vectorPR[0] = a * pointN[0] + b * pointP[0];
        vectorPR[1] = a * pointN[1] + b * pointP[1];
        vectorPR[2] = a * pointN[2] + b * pointP[2];
        if (vectorPR[2] < 0) {
            // 求得方向为正南
            vectorPR[0] = -vectorPR[0];
            vectorPR[1] = -vectorPR[1];
            vectorPR[2] = -vectorPR[2];
        }
        // 计算PQ(手机的朝向): 在坐标系中角度旋转的正方向与函数的相反
        double thetaRPQ = sphereP[2];
        double[] vectorPQ = new double[3];
        rotateByVector(vectorPR, pointP, -thetaRPQ, vectorPQ);
        // 求新直角坐标系下的正北方向PS
        double[] vectorPS = new double[3];
        // 计算OP与OM的夹角
        cos = (pointM[0]*pointP[0] + pointM[1]*pointP[1] + pointM[2]*pointP[2])/
                (Math.sqrt(pointM[0]*pointM[0] + pointM[1]*pointM[1] + pointM[2]*pointM[2])
                *Math.sqrt(pointP[0]*pointP[0] + pointP[1]*pointP[1] + pointP[2]*pointP[2]));
        if (cos > 1) {
            cos = 1;
        } else if (cos < -1) {
            cos = -1;
        }
        double thetaMOP = Math.acos(cos);
        a = 1/Math.abs(Math.sin(thetaMOP));
        b = -a * Math.cos(thetaMOP);
        vectorPS[0] = a * pointM[0] + b * pointP[0];
        vectorPS[1] = a * pointM[1] + b * pointP[1];
        vectorPS[2] = a * pointM[2] + b * pointP[2];
        product = vectorPS[0]*pointM[0] + vectorPS[1]*pointM[1] + vectorPS[2]*pointM[2];
        if (product < 0) {
            // 方向为正南
            vectorPS[0] = -vectorPS[0];
            vectorPS[1] = -vectorPS[1];
            vectorPS[2] = -vectorPS[2];
        }
        // 用OM与OP的叉乘(右手定则)计算P处的正东方向PE
        double[] vectorPE = new double[3];
        vectorPE[0] = pointM[1]*pointP[2] - pointP[1]*pointM[2];
        vectorPE[1] = pointP[0]*pointM[2] - pointM[0]*pointP[2];
        vectorPE[2] = pointM[0]*pointP[1] - pointP[0]*pointM[1];
        // 计算PS与PQ的夹角(0 - PI)
        cos = (vectorPS[0]*vectorPQ[0] + vectorPS[1]*vectorPQ[1] + vectorPS[2]*vectorPQ[2])/
                (Math.sqrt(vectorPS[0]*vectorPS[0] + vectorPS[1]*vectorPS[1] + vectorPS[2]*vectorPS[2])
                *Math.sqrt(vectorPQ[0]*vectorPQ[0] + vectorPQ[1]*vectorPQ[1] + vectorPQ[2]*vectorPQ[2]));
        if (cos > 1) {
            cos = 1;
        } else if (cos < -1) {
            cos = -1;
        }
        double thetaSPQ = Math.acos(cos);
        // 判断PQ是向东(PE)还是向西
        product = vectorPQ[0]*vectorPE[0] + vectorPQ[1]*vectorPE[1] + vectorPQ[2]*vectorPE[2];
        if (product < 0) {
            // PQ朝向西方
            thetaSPQ = -thetaSPQ;
        }
        return thetaSPQ;
    }

}
