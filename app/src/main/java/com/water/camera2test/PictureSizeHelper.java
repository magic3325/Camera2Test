package com.water.camera2test;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PictureSizeHelper {

    public static final double RATIO_16_9 = 16d / 9;
    public static final double RATIO_5_3 = 5d / 3;
    public static final double RATIO_3_2 = 3d / 2;
    public static final double RATIO_4_3 = 4d / 3;
    private static final double RATIOS[] = { RATIO_16_9, RATIO_5_3, RATIO_3_2, RATIO_4_3 };

    private static final String RATIO_16_9_IN_STRING = "(16:9)";
    private static final String RATIO_5_3_IN_STRING = "(5:3)";
    private static final String RATIO_3_2_IN_STRING = "(3:2)";
    private static final String RATIO_4_3_IN_STRING = "(4:3)";
    private static final String RATIOS_IN_STRING[] = { RATIO_16_9_IN_STRING, RATIO_5_3_IN_STRING,
            RATIO_3_2_IN_STRING, RATIO_4_3_IN_STRING };

    public  static final double ASPECT_TOLERANCE = 0.02;
    private static final String PICTURE_RATIO_4_3 = "1.3333";

    private static List<Double> sDesiredAspectRatios = new ArrayList<>();
    private static List<String> sDesiredAspectRatiosInStr = new ArrayList<>();


    /**
     * Compute full screen aspect ratio.
     *
     * @param context The instance of {@link Context}.
     * @return The full screen aspect ratio.
     */
    public static double findFullScreenRatio(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getRealMetrics(dm);
        int width = Math.max(dm.widthPixels, dm.heightPixels);
        int height = Math.min(dm.widthPixels, dm.heightPixels);
        double displayRatio = (double) width / (double) height;

        double find = RATIO_4_3;
        for (int i = 0; i < RATIOS.length; i++) {
            double ratio = RATIOS[i];
            if (Math.abs(ratio - displayRatio) < Math.abs(find - displayRatio)) {
                find = ratio;
            }
        }
        return find;
    }


    public static Size getFullScreenSize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getRealMetrics(dm);
        return new Size(dm.widthPixels,dm.heightPixels);
    }

    public static double getStandardAspectRatio(Size size) {
        double ratio = (double) size.getWidth() / (double) size.getHeight();
        for (int i = 0; i < sDesiredAspectRatios.size(); i++) {
            double standardRatio = sDesiredAspectRatios.get(i);
            if (Math.abs(ratio - standardRatio) < ASPECT_TOLERANCE) {
                return standardRatio;
            }
        }
        return ratio;
    }

    public static double getStandardAspectRatio(String value) {
        Size size = valueToSize(value);
        double ratio = (double) size.getWidth() / (double) size.getHeight();
        for (int i = 0; i < sDesiredAspectRatios.size(); i++) {
            double standardRatio = sDesiredAspectRatios.get(i);
            if (Math.abs(ratio - standardRatio) < ASPECT_TOLERANCE) {
                return standardRatio;
            }
        }
        return ratio;
    }
    private static Size valueToSize(String value) {
        int index = value.indexOf('x');
        int width = Integer.parseInt(value.substring(0, index));
        int height = Integer.parseInt(value.substring(index + 1));
        Size size = new Size(width,height);
        return size;
    }

    public static double getDefaultRatio() {
        //return RATIO_4_3;
        return RATIO_16_9;
    }

    public static  Size getPreviewSize(Activity activity, List<Size> sizes){
        double previewRatio = getDefaultRatio();
        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point point = new Point();
        display.getRealSize(point);
        int panelHeight = Math.min(point.x, point.y);
        int panelWidth = (int) (previewRatio * panelHeight);

        List<Size> sizeEqualRatio = new ArrayList<>();
        for (Size size : sizes) {
            if (Math.abs((double) size.getWidth() / size.getHeight() - previewRatio) < ASPECT_TOLERANCE) {
                sizeEqualRatio.add(size);
            }
        }
        Size optimalSize = null;
            optimalSize = Collections.max(sizeEqualRatio,new CompareSizesByArea());
        if (optimalSize != null) {
            return optimalSize;
        }
        double minDiffHeight = Double.MAX_VALUE;
        if (optimalSize == null) {
            previewRatio = Double.parseDouble(PICTURE_RATIO_4_3);
            for (Size size : sizes) {
                double ratio = (double) size.getWidth() / size.getHeight();
                if (Math.abs(ratio - previewRatio) > ASPECT_TOLERANCE) {
                    continue;
                }
                if (Math.abs(size.getHeight() - panelHeight) < minDiffHeight) {
                    optimalSize = size;
                    minDiffHeight = Math.abs(size.getHeight() - panelHeight);
                }
            }
        }
        return optimalSize;
    }

    public static  Size getTextureSize(Activity activity, Size size){
        double previewRatio = (double) size.getWidth() / (double) size.getHeight();
        for(double ratio:RATIOS){
            if (Math.abs(ratio - previewRatio) < ASPECT_TOLERANCE) {
                previewRatio = ratio;
                break;
            }
        }

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point point = new Point();
        display.getRealSize(point);
        int panelWidth = Math.min(point.x, point.y);
        int panelHeight = (int) (previewRatio * panelWidth);

        return new Size(panelWidth,panelHeight);
    }

    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }


    public static Size getOptimalPreviewSize(Activity activity,
                                             List<Size> sizes,
                                             double previewRatio,
                                             boolean needMatchTargetPanelSize) {
        //split preview sizes to two group equal ratio and nearly ratio
        List<Size> sizeEqualRatio = new ArrayList<>();
        List<Size> sizeNearRatio = new ArrayList<>();
        for (Size size : sizes) {
            if ((double) size.getWidth() / size.getHeight() == previewRatio) {
                sizeEqualRatio.add(size);
            } else {
                sizeNearRatio.add(size);
            }
        }

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point point = new Point();
        display.getRealSize(point);
        int panelHeight = Math.min(point.x, point.y);
        int panelWidth = (int) (previewRatio * panelHeight);

        Size optimalSize = null;
        if (needMatchTargetPanelSize) {
            optimalSize = findBestMatchPanelSize(
                    sizeEqualRatio, previewRatio, panelWidth, panelHeight);
            if (optimalSize == null) {
                optimalSize = findBestMatchPanelSize(
                        sizeNearRatio, previewRatio, panelWidth, panelHeight);
            }
            if (optimalSize != null) {
                return optimalSize;
            }
        }

        optimalSize = findClosestPanelSize(
                sizeEqualRatio, previewRatio, panelWidth, panelHeight);
        if (optimalSize == null) {
            optimalSize = findClosestPanelSize(
                    sizeNearRatio, previewRatio, panelWidth, panelHeight);
        }
        if (optimalSize != null) {
            return optimalSize;
        }

        double minDiffHeight = Double.MAX_VALUE;
        if (optimalSize == null) {

            previewRatio = Double.parseDouble(PICTURE_RATIO_4_3);
            for (Size size : sizes) {
                double ratio = (double) size.getWidth() / size.getHeight();
                if (Math.abs(ratio - previewRatio) > ASPECT_TOLERANCE) {
                    continue;
                }
                if (Math.abs(size.getHeight() - panelHeight) < minDiffHeight) {
                    optimalSize = size;
                    minDiffHeight = Math.abs(size.getHeight() - panelHeight);
                }
            }
        }
        return optimalSize;
    }

    //find the preview size which is closest to panel size
    private static Size findClosestPanelSize(List<Size> sizes,
                                             double targetRatio, int panelWidth, int panelHeight) {

        //find out a size match aspect ratio and size
        double minDiffHeight = Double.MAX_VALUE;
        double minDiffWidth = Double.MAX_VALUE;
        Size optimalSize = null;
        for (Size size : sizes) {
            double ratio = (double) size.getWidth() / size.getHeight();
            if (Math.abs(targetRatio - ratio) > ASPECT_TOLERANCE) {
                continue;
            }
            if (Math.abs(size.getHeight() - panelHeight) < minDiffHeight) {
                optimalSize = size;
                minDiffHeight = Math.abs(size.getHeight() - panelHeight);
                minDiffWidth = Math.abs(size.getWidth() - panelWidth);
            } else if (Math.abs(size.getHeight() - panelHeight) == minDiffHeight && Math.abs(size
                    .getWidth() - panelWidth) < minDiffWidth) {
                optimalSize = size;
                minDiffWidth = Math.abs(size.getWidth() - panelWidth);
            }
        }
        return optimalSize;
    }

    //find the preview size which is greater or equal to panel size and closest to panel size
    private static Size findBestMatchPanelSize(List<Size> sizes,
                                               double targetRatio, int panelWidth, int panelHeight) {
        long minDiff;
        long minDiffMax = Integer.MAX_VALUE;
        double minRatio = Double.MAX_VALUE;
        Size bestMatchSize = null;
        for (Size size : sizes) {
            double ratio = (double) size.getWidth() / size.getHeight();
            // filter out the size which not tolerated by target ratio
            if (Math.abs(ratio - targetRatio) <= minRatio) {
                minRatio = Math.abs(ratio - targetRatio);
                // find the size closest to panel size
                minDiff = Math.abs(size.getHeight() - panelHeight)
                        + Math.abs(size.getWidth() - panelWidth);
                if (minDiff <= minDiffMax) {
                    minDiffMax = minDiff;
                    bestMatchSize = size;
                }
            }
        }
        return bestMatchSize;
    }


}
