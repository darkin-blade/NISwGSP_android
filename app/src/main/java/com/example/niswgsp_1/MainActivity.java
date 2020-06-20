package com.example.niswgsp_1;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
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
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    static String appPath;

    public ImageView photo_result;
    public LinearLayout photos;
    public Button button_save, button_camera, button_delete, button_stitch, button_clear;
    public TextView stitch_log;
    public ProgressBar stitch_progress;

    public static final int PERMISSION_CAMERA_REQUEST_CODE = 0x00000012;// 相机权限的 request code
    Uri photoUri = null;
    String photoPath = null;

    ArrayList<Bitmap> photo_list = new ArrayList<>();
    ArrayList<Integer> photo_selected = new ArrayList<>();
    ArrayList<String> photo_name = new ArrayList<>();
    Bitmap bmp_result = null;

    // 从jni更新UI
    static MainHandler mainHandler;

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

//        stitch_1();
        initUI();
        initApp();
    }

    void initUI() {
        photo_result = findViewById(R.id.photo_result);
        photos = findViewById(R.id.photos);
        stitch_log = findViewById(R.id.stitch_log);
        button_save = findViewById(R.id.save_button);
        button_camera = findViewById(R.id.camera_button);
        button_delete = findViewById(R.id.delete_button);
        button_stitch = findViewById(R.id.stitch_button);
        button_clear = findViewById(R.id.clear_button);
        stitch_progress = findViewById(R.id.stitch_progress);

        photos.removeAllViews();// 移除所有子元素

        button_clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearLog();
            }
        });

        button_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });

        button_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deletePhoto();
            }
        });

        button_stitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stitch_1();
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

        int hasCameraPermission = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.CAMERA);
        if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
            // 有调用相机权限
            addToLog("camera is ready");
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // 权限处理回调
        if (requestCode == PERMISSION_CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 允许权限
                addToLog("camera is ready");
            } else {
                // TODO 权限被拒绝
                addToLog("get camera permission failed");
            }
        }
    }

    void savePhoto() {
        Thread save_bmp = new Thread(new Runnable() {
            @Override
            public void run() {
               if (bmp_result != null) {
                   File file;
                   for (int i = 0; i < 1000; i ++) {
                       file = new File(appPath + "/result_" + i + ".jpg");
                       if (file.exists() == false) {
                           try {
                               file.createNewFile();
                               FileOutputStream stream = new FileOutputStream(file);
                               bmp_result.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                               stream.flush();
                               infoLog("save succeed");
                               return;
                           } catch (IOException e) {
                               e.printStackTrace();
                           }
                       }
                   }
                   infoLog("save failed");
               }
            }
        });
        save_bmp.start();
    }

    void openCamera() {
        // 拍照
        addToLog("open camera");
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
                startActivityForResult(intent, PERMISSION_CAMERA_REQUEST_CODE);// TODO
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_CAMERA_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                addToLog("get photo [" + photoPath + "]");
                if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // Android 10 以上F
                } else {
                    // Android 10 以下
                    try {
                        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(photoUri));
                        addPhoto(bitmap);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                addToLog("canceled");
            }
        }
    }

    void addPhoto(Bitmap bitmap) {
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
        photo_name.add(photoPath);// TODO 添加图片路径


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

    void deletePhoto() {
        for (int i = 0; i < photo_selected.size(); i ++) {
            int tmp = photo_selected.get(i);
            if (tmp == 1) {
                // 被选中
                photo_list.remove(i);
                photo_selected.remove(i);
                photo_name.remove(i);
                photos.removeViewAt(i);// TODO
                i --;
            }
        }
    }

    void addToLog(String log) {
        String old_log = (String) stitch_log.getText();
        stitch_log.setText(old_log + log + "\n");
    }

    void clearLog() {
        stitch_log.setText("");
    }

    void stitch_1() {
        if (photo_list.size() < 2) {
            addToLog("need at least 2 photos");
            return;// 图片数目不够
        }
        jniProgress(1);

        Thread run_test = new Thread(new Runnable() {
            @Override
            public void run() {
                int photo_num = photo_list.size();
                String[] imgPaths = new String[photo_num];
                for (int i = 0; i < photo_num; i ++) {
                    imgPaths[i] = photo_name.get(i);
                }
                Mat matBGR = new Mat();

                int result = main_test(
                        imgPaths,
                        matBGR.getNativeObjAddr()
                );

                if (result != 0) {
                    infoLog("failed");
                    return;
                } else {
                    infoLog("mat size: " + matBGR.cols() + ", " + matBGR.rows());
                    if (matBGR.cols() * matBGR.rows() == 0) {
                        return;
                    }
                }

                bmp_result = Bitmap.createBitmap(matBGR.cols(), matBGR.rows(), Bitmap.Config.ARGB_8888);// TODO final

                // BGR转RGB
                Mat matRGB = new Mat();
                Imgproc.cvtColor(matBGR, matRGB, Imgproc.COLOR_BGR2RGB);
                Utils.matToBitmap(matRGB, bmp_result);

                // 显示图片
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 压缩图片并显示
                        Matrix matrix = new Matrix();
                        matrix.setScale(0.2f, 0.2f);
                        Bitmap tmp = Bitmap.createBitmap(bmp_result, 0, 0, bmp_result.getWidth(), bmp_result.getHeight(), matrix, true);
                        photo_result.setImageBitmap(bmp_result);
                    }
                });
            }
        });

        run_test.start();
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

            // TODO 修改进度
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
    public native int main_test(String[] imgPaths, long matBGR);

    static public void infoLog(String log) {
        Log.i("fuck", log);
    }
}
