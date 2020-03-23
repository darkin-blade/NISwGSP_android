package com.example.niswgsp_1;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    static String appPath;
    public ImageView imageView;
    public Bitmap bitmap = null;
    public Button save_button;

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

        stitch_1();
        initUI();
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

    void initUI() {
        save_button = findViewById(R.id.save_button);
        save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveBmp();
            }
        });
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
