package com.water.camera2test;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Display;
import android.view.WindowManager;

public class CameraUtil {

    public static final int LENS_FACING_FRONT = 0;
    public static final int LENS_FACING_BACK = 1;


    public static Size getScreenSize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getRealMetrics(dm);
        return new Size(dm.widthPixels,dm.heightPixels);
    }

    public static double getRatio(int id) {
        if(id==LENS_FACING_FRONT){
            return PictureSizeHelper.RATIO_4_3;
        }else{
            return PictureSizeHelper.RATIO_16_9;
        }
    }
    public static double getDefaultRatio() {
        return PictureSizeHelper.RATIO_4_3;
    }
    public static  Size getPreviewSize(Context context, int id) {
        double ratio =getRatio(id);
        Size screenSize = getScreenSize(context);
        int width = Math.min(screenSize.getWidth(), screenSize.getHeight());
        return new Size(width,(int)(width*ratio));
    }




}
