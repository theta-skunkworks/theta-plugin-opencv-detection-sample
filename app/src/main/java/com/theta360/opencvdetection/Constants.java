package com.theta360.opencvdetection;

import android.os.Environment;

public final class Constants {
    private Constants() {}

    // Storage
    public static final String DCIM = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DCIM).getPath();
    public static final String PLUGIN_DIRECTORY = DCIM + "/MotionDetector";

    // Size
    // still picture size
    // in case of using Z1, you should set these size 6720x3360.
    public static final int STILL_SIZE_WIDTH = 5376;
    public static final int STILL_SIZE_HEIGHT = 2688;

    // the preview size according to the "RicMoviePreviewXXXX" parameter
    public static final int PREVIEW_SIZE_WIDTH = 640;
    public static final int PREVIEW_SIZE_HEIGHT = 320;

    // thumbnail size
    public static final int THUMBNAIL_SIZE_WIDTH = 320;
    public static final int THUMBNAIL_SIZE_HEIGHT = 160;


    // Camera API parameters
    // Shooting Mode
    public static final String RIC_MOVIE_PREVIEW = "RicMoviePreview640";
    public static final String RIC_STILL_CAPTURE_STD = "RicStillCaptureStd";

    // Stitching
    public static final String RIC_STATIC_STITCHING = "RicStaticStitching";
    public static final String RIC_DYNAMIC_STITCHING_AUTO = "RicDynamicStitchingAuto";

    // Exposure Program
    public static final String RIC_AUTO_EXPOSURE_P = "RicAutoExposureP";


    // Image Processing parameters
    public static final int THRESHOLD_BINARIZATION = 3;
    public static final int APPROXIMATED_ACCURACY_PARAM = 40;
    public static final double THRESHOLD_AREA_SIZE = 50.0;
    public static final int SHOOTING_INTERVAL = 5000;
}
