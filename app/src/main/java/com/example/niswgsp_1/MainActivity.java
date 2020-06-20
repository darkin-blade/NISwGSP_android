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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    static String appPath;

    public ImageView imageView;
    public Bitmap bitmap = null;
    public Button save_button, camera_button;
    public TextView stitch_log;

    public static final int PERMISSION_CAMERA_REQUEST_CODE = 0x00000012;// 相机权限的 request code
    Uri photoUri = null;

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
        stitch_log = findViewById(R.id.stitch_log);
        save_button = findViewById(R.id.save_button);
        camera_button = findViewById(R.id.camera_button);

        save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveBmp();
            }
        });
        camera_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });
    }

    void initApp() {
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

    void saveBmp() {
        Thread save_bmp = new Thread(new Runnable() {
            @Override
            public void run() {
               if (bitmap != null) {
                   File file;
                   for (int i = 0; i < 1000; i ++) {
                       file = new File(appPath + "/result_" + i + ".jpg");
                       if (file.exists() == false) {
                           try {
                               file.createNewFile();
                               FileOutputStream stream = new FileOutputStream(file);
                               bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
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
            String photoPath = null;
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
            if (requestCode == RESULT_OK) {
                addToLog("get photo");
                if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // Android 10 以上F
                } else {
                    // Android 10 以下
                    try {
                        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(photoUri));
                        imageView.setImageBitmap(bitmap);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    void addToLog(String log) {
        String old_log = (String) stitch_log.getText();
        stitch_log.setText(old_log + log + "\n");
    }

    void stitch_1() {
        Thread run_test = new Thread(new Runnable() {
            @Override
            public void run() {

                Mat matBGR = new Mat();

                int result = main_test(
                        appPath,
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

                bitmap = Bitmap.createBitmap(matBGR.cols(), matBGR.rows(), Bitmap.Config.ARGB_8888);// TODO final

                // BGR转RGB
                Mat matRGB = new Mat();
                Imgproc.cvtColor(matBGR, matRGB, Imgproc.COLOR_BGR2RGB);
                Utils.matToBitmap(matRGB, bitmap);

                // 显示图片
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        imageView = findViewById(R.id.result);
                        imageView.setImageBitmap(bitmap);
                    }
                });
            }
        });

        run_test.start();
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native int main_test(String appPath, long matBGR);

    static public void infoLog(String log) {
        Log.i("fuck", log);
    }
}
