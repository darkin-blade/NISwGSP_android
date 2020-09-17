package com.example.niswgsp_1;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements DialogInterface.OnDismissListener {
    static String appPath;

    public ImageView niswgsp_result, opencv_result;
    public static LinearLayout photos;
    public Button button_save, button_camera, button_delete;
    public Button button_niswgsp, button_opencv;
    public ProgressBar stitch_progress;
//    public TextView stitch_log;

    public static final int PERMISSION_CAMERA_REQUEST_CODE = 0x00000012;// 相机权限的 request code
    Uri photoUri = null;
    String photoPath = null;

    ArrayList<Bitmap> photo_list = new ArrayList<>();// 图片list
    ArrayList<String> photo_name = new ArrayList<>();// 图片地址list
    ArrayList<Double> photo_rotation = new ArrayList<>();// 图片旋转角度
    ArrayList<Integer> photo_selected = new ArrayList<>();
    // 拼接结果
    Bitmap niswgsp_bmp = null;
    Bitmap opencv_bmp = null;

    Thread stitch_thread;
    // 从jni更新UI
    static MainHandler mainHandler;

    // 相机部件
    CustomCamera1 customCamera1 = new CustomCamera1();
    CustomCamera2 customCamera2 = new CustomCamera2();

    // 初始化opencv java
    static {
        if (!OpenCVLoader.initDebug()) {
            infoLog("opencv init failed");
        } else {
            infoLog("opencv init succeed");
        }
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("entry");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appPath = getExternalFilesDir("").getAbsolutePath();

        initUI();
        initApp();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // 权限处理回调
        if (requestCode == PERMISSION_CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 允许权限
                addToLog("camera is ready");
            } else {
                // 权限被拒绝
                addToLog("get camera permission failed");
            }
        }
    }

    // 接收系统拍摄的相片
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_CAMERA_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                addToLog("get photo [" + photoPath + "]");
                addPhoto(photoPath);// 直接根据路径添加图片
//                if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
//                    // Android 10 以上F
//                } else {
//                    // Android 10 以下
//                    try {
//                        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(photoUri));
//                        addPhoto(photoPath);
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    }
//                }
            } else {
                addToLog("canceled");
            }
        }
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        infoLog("dismiss");
        if (customCamera2.dismiss_result == 0) {
            // 返回
            return;
        }

        // 删除所有照片及ImageView
        int view_count = photos.getChildCount();
        for (int i = 0; i < view_count; i ++) {
            deletePhoto(0);
            photos.removeViewAt(0);
        }

        infoLog("photo num: " + customCamera2.photo_name.size() + "/" + customCamera2.photo_num);
        for (int i = 0; i < customCamera2.photo_name.size(); i ++) {
//            infoLog("add photo: " + customCamera2.photo_name.get(i));
            addPhoto(customCamera2.photo_name.get(i));
        }
        // 获取相机旋转角度
        photo_rotation.clear();// 先清空
        for (int i = 0; i < customCamera2.photo_rotation.size(); i ++) {
            double tmp_rotation = customCamera2.photo_rotation.get(i).get(2);// 获取屏幕角度
            photo_rotation.add(tmp_rotation);
        }
    }

    void initUI() {
        niswgsp_result = findViewById(R.id.niswgsp_result);
        opencv_result = findViewById(R.id.opencv_result);
        photos = findViewById(R.id.photos);
//        stitch_log = findViewById(R.id.stitch_log);
        button_save = findViewById(R.id.save_button);
        button_camera = findViewById(R.id.camera_button);
        button_delete = findViewById(R.id.delete_button);
        button_niswgsp = findViewById(R.id.niswgsp_button);
        button_opencv = findViewById(R.id.opencv_button);
        stitch_progress = findViewById(R.id.stitch_progress);

        photos.removeAllViews();// 移除所有子元素

        button_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCustomCamera();
            }
        });

        button_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSelected();
            }
        });

        button_opencv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                opencvStitch();
            }
        });

        button_niswgsp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                niswgspStitch();
            }
        });

        button_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePhoto();
            }
        });
    }

    void initApp() {
        mainHandler = new MainHandler();

        // 只适用 SDK > 23
        int hasCameraPermission = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.CAMERA);
        if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
            // 有调用相机权限
            addToLog("camera is ready");
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQUEST_CODE);
        }
    }

    void openCustomCamera() {
        // 自定义相机
        addToLog("open custom camera");
//        customCamera1.show(getSupportFragmentManager(), "custom camera");
        customCamera2.show(getSupportFragmentManager(), "custom camera");
    }

    void openSystemCamera() {
        // 系统相机
        addToLog("open system camera");
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10 以上
                addToLog("android 10");
            } else {
                // Android 10 以下
                addToLog("not android 10");

                String photoName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
                photoFile = new File(appPath, photoName);
                photoPath = photoFile.getAbsolutePath();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7 以上
                    addToLog("android 7");
                    photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                } else {
                    // Android 7 以下
                    addToLog("not android 7");
                }
            }

            if (photoUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(intent, PERMISSION_CAMERA_REQUEST_CODE);
            }
        }
    }

    void savePhoto() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                saveNISwGSP();
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                saveOpenCV();
            }
        }).start();
    }

    void addPhoto(String path) {
        Bitmap bitmap = BitmapFactory.decodeFile(path);

        final LinearLayout photo_border = new LinearLayout(this);
        ImageView photo_item = new ImageView(this);

        final LinearLayout.LayoutParams param_border = new LinearLayout.LayoutParams(300, 300);
        LinearLayout.LayoutParams param_item = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        param_item.setMargins(20, 20, 20, 20);

        photo_border.setLayoutParams(param_border);
        photo_item.setLayoutParams(param_item);

        photo_border.addView(photo_item);
        photos.addView(photo_border);

        // 添加至列表
        photo_list.add(bitmap);
        photo_selected.add(0);
        photo_name.add(path);// 添加图片路径

        // 压缩图片并显示
        Matrix matrix = new Matrix();
        matrix.setScale(0.1f, 0.1f);
        Bitmap tmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        photo_item.setImageBitmap(tmp);

        // 添加选定功能
        photo_border.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int index = photos.indexOfChild(photo_border);
                if (photo_selected.get(index) == 0) {
                    photo_selected.set(index, 1);
                    photo_border.setBackgroundResource(R.color.greyC);
                } else {
                    photo_selected.set(index, 0);
                    photo_border.setBackgroundResource(R.color.white);
                }
            }
        });
    }

    void saveOpenCV() {
        if (opencv_bmp != null) {
            File file;
            for (int i = 0; i < 1000; i ++) {
                file = new File(appPath + "/opencv_" + i + "_" + photo_list.size() + ".png");
                if (file.exists() == false) {
                    try {
                        file.createNewFile();
                        FileOutputStream stream = new FileOutputStream(file);
                        opencv_bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                        stream.flush();
                        infoLog("save opencv succeed");
                        return;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    void saveNISwGSP() {
        if (niswgsp_bmp != null) {
            File file;
            for (int i = 0; i < 1000; i ++) {
                file = new File(appPath + "/niswgsp_" + i + "_" + photo_list.size() + ".png");
                if (file.exists() == false) {
                    try {
                        file.createNewFile();
                        FileOutputStream stream = new FileOutputStream(file);
                        niswgsp_bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                        stream.flush();
                        infoLog("save niswgsp succeed");
                        return;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    void opencvStitch() {
        if (photo_list.size() < 2) {
            addToLog("need at least 2 photos");
            return;// 图片数目不够
        }

        int photo_num = photo_list.size();
        final String[] imgPaths = new String[photo_num];
        final double[] imgRotations = new double[photo_num];
        for (int i = 0; i < photo_num; i ++) {
            imgPaths[i] = photo_name.get(i);
            imgRotations[i] = photo_rotation.get(i);
            infoLog(imgPaths[i] + ": " + imgRotations[i]);
        }

        // 调用OpenCV方法
        new Thread(new Runnable() {
            @Override
            public void run() {
                Mat matBGR = new Mat();
                int result = main_test(
                        imgPaths,
                        imgRotations,
                        matBGR.getNativeObjAddr(),
                        1
                );

                if (result != 0) {
                    infoLog("failed");
                } else {
                    opencv_bmp = Bitmap.createBitmap(matBGR.cols(), matBGR.rows(), Bitmap.Config.ARGB_8888);

                    // BGR转RGB
                    Mat matRGBA = new Mat();
                    Imgproc.cvtColor(matBGR, matRGBA, Imgproc.COLOR_BGR2RGBA);
                    Utils.matToBitmap(matRGBA, opencv_bmp);
                    saveOpenCV();

                    // 显示图片
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 压缩图片并显示
                            Matrix matrix = new Matrix();
                            matrix.setScale(0.2f, 0.2f);
                            Bitmap bmp_thumbnail = Bitmap.createBitmap(opencv_bmp, 0, 0, opencv_bmp.getWidth(), opencv_bmp.getHeight(), matrix, true);
                            opencv_result.setImageBitmap(bmp_thumbnail);
                        }
                    });
                }
            }
        }).start();
    }

    void niswgspStitch() {
        if (photo_list.size() < 2) {
            addToLog("need at least 2 photos");
            return;// 图片数目不够
        }
        jniProgress(1);// 重置进度条

        int photo_num = photo_list.size();
        final String[] imgPaths = new String[photo_num];
        final double[] imgRotations = new double[photo_num];
        for (int i = 0; i < photo_num; i ++) {
            imgPaths[i] = photo_name.get(i);
            imgRotations[i] = photo_rotation.get(i);
            infoLog(imgPaths[i] + ": " + imgRotations[i]);
        }

        // 调用NISwGSP方法
        new Thread(new Runnable() {
            @Override
            public void run() {
                Mat matBGR = new Mat();
                int result = main_test(
                        imgPaths,
                        imgRotations,
                        matBGR.getNativeObjAddr(),
                        0
                );

                if (result != 0) {
                    infoLog("failed");
                    return;
                } else {
                    niswgsp_bmp = Bitmap.createBitmap(matBGR.cols(), matBGR.rows(), Bitmap.Config.ARGB_8888);

                    // BGR转RGB
                    Mat matRGBA = new Mat();
                    Imgproc.cvtColor(matBGR, matRGBA, Imgproc.COLOR_BGR2RGBA);
                    Utils.matToBitmap(matRGBA, niswgsp_bmp);
                    saveNISwGSP();

                    // 显示图片
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 压缩图片并显示
                            Matrix matrix = new Matrix();
                            matrix.setScale(0.2f, 0.2f);
                            Bitmap bmp_thumbnail = Bitmap.createBitmap(niswgsp_bmp, 0, 0, niswgsp_bmp.getWidth(), niswgsp_bmp.getHeight(), matrix, true);
                            niswgsp_result.setImageBitmap(bmp_thumbnail);
                        }
                    });
                }
            }
        }).start();
    }

    void deleteSelected() {
        for (int i = 0; i < photo_selected.size(); i ++) {
            int tmp = photo_selected.get(i);
            if (tmp == 1) {
                // 被选中
                deletePhoto(i);
                photos.removeViewAt(i);
                i --;
            }
        }
    }

    void deletePhoto(int index) {
        // 删除对应索引的图片, 不包括ImageView
        photo_list.remove(index);
        photo_selected.remove(index);
        photo_name.remove(index);
    }

    void addToLog(String log) {
//        String old_log = (String) stitch_log.getText();
//        stitch_log.setText(old_log + log + "\n");
    }

    class MainHandler extends Handler {
        public MainHandler(){}
        public MainHandler(Looper L) {
            super(L);
        }
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            Bundle bundle = msg.getData();

            // 打印log
            String log = bundle.getString("log");
            if (log != null) {
                addToLog(log);
            }

            // 修改进度
            int progress = bundle.getInt("progress");
            if (progress != 0) {
                stitch_progress.setProgress(progress);
            }
       }
    }

    public static void jniLog(String log) {
        Message message = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("log", log);
        message.setData(bundle);
        mainHandler.sendMessage(message);
    }

    public static void jniProgress(int progress) {
        Message message = new Message();
        Bundle bundle = new Bundle();
        bundle.putInt("progress", progress);
        message.setData(bundle);
        mainHandler.sendMessage(message);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native int main_test(String[] imgPaths, double[] imgRotations, long matBGR, int mode);

    static public void infoLog(String log) {
        Log.i("fuck", log);
    }
}
