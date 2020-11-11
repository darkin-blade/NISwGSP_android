package com.example.my_stitcher;

import android.graphics.Bitmap;

import org.opencv.core.Mat;
import org.opencv.features2d.FastFeatureDetector;
import org.opencv.features2d.Feature2D;

import java.util.ArrayList;

public class Translation {
    public ArrayList<Mat> translations = new ArrayList<>();
    public Mat intrinsic = new Mat();// 相机的内在矩阵

    ArrayList<Bitmap> photos = new ArrayList<>();
    ArrayList<Double> rotations = new ArrayList<>();

    public void setParam(ArrayList<Bitmap> photo_list, ArrayList<Double> photo_rotation) {
        photos.clear();
        rotations.clear();
        for (Bitmap photo : photo_list) {
            photos.add(photo);
        }
        for (Double rotation : photo_rotation) {
            rotations.add(rotation);
        }
    }

    public void computeIntrinsic() {
        // 计算相机内在矩阵
    }

    public void computeTranslation() {
        // 平移/旋转矩阵: 外在矩阵
    }
}
