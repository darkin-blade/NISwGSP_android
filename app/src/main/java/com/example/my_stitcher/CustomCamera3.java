package com.example.my_stitcher;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.util.Size;
import android.view.TextureView;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;

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

    static public ArrayList<String> photo_name = new ArrayList<>();// 图片地址list
    static public ArrayList<ArrayList<Double> > photo_rotation = new ArrayList<>();// 图片角度list
    static public int photo_num;// 照片总数
    static public int photo_index;// 用于照片命名
}
