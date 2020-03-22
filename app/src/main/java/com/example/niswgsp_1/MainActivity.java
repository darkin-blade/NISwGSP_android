package com.example.niswgsp_1;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends AppCompatActivity {
    static String appPath;

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
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appPath = getExternalFilesDir("").getAbsolutePath();

        stitch_1();
    }

    void stitch_1() {

        Mat matBGR = new Mat();

        ImageView imageView = findViewById(R.id.result);

        int result = stitchTest_1(
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

        Bitmap bitmap = Bitmap.createBitmap(matBGR.cols(), matBGR.rows(), Bitmap.Config.ARGB_8888);

        // BGR转RGB
        Mat matRGB = new Mat();
        Imgproc.cvtColor(matBGR, matRGB, Imgproc.COLOR_BGR2RGB);
        Utils.matToBitmap(matRGB, bitmap);

        // 显示图片
        imageView.setImageBitmap(bitmap);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native int stitchTest_1(String appPath, long matBGR);

    static public void infoLog(String log) {
        Log.i("fuck", log);
    }
}
