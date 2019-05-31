/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.theta360.opencvdetection;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;

import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.Video;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends PluginActivity implements CvCameraViewListener2, ThetaController.CFCallback {

    private static final String TAG = "Plug-in::MainActivity";

    private ThetaController mOpenCvCameraView;
    private boolean isEnded = false;

    private Mat mOutputFrame;
    private BackgroundSubtractor mBackgroundSubtractor;
    private Mat mMask;
    private Mat mStructuringElement;
    private long mStartProcessingTime;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    mOpenCvCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "OpenCV version: " + Core.VERSION);

        // Set a callback when a button operation event is acquired.
        setKeyCallback(new KeyCallback() {
            @Override
            public void onKeyDown(int keyCode, KeyEvent keyEvent) {

            }

            @Override
            public void onKeyUp(int keyCode, KeyEvent keyEvent) {

            }

            @Override
            public void onKeyLongPress(int keyCode, KeyEvent keyEvent) {
                if (keyCode == KeyReceiver.KEYCODE_MEDIA_RECORD) {
                    Log.d(TAG, "Do end process.");
                    closeCamera();
                }
            }
        });

        notificationCameraClose();

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (ThetaController) findViewById(R.id.opencv_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCFCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found.");
        } else {
            Log.d(TAG, "OpenCV library found inside package.");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        super.onPause();
    }

    @Override
    public void onShutter() {
        notificationAudioShutter();
    }

    @Override
    public void onPictureTaken() {

    }

    public void onCameraViewStarted(int width, int height) {
        mOutputFrame = new Mat(height, width, CvType.CV_8UC3);
        mBackgroundSubtractor = Video.createBackgroundSubtractorKNN();
        mMask = new Mat(height, width, CvType.CV_8UC1);
        mStructuringElement = Imgproc.getStructuringElement(Imgproc.CV_SHAPE_ELLIPSE, new Size(3,3));

        mStartProcessingTime = System.currentTimeMillis();
    }

    public void onCameraViewStopped() {
        mStructuringElement.release();
        mMask.release();
        mOutputFrame.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        // detect moving area
        List<Rect> movingAreaList = getMovingAreaList(inputFrame.gray());
        if (movingAreaList.isEmpty()) {
            return inputFrame.rgba();
        }

        mOutputFrame = inputFrame.rgba();
        for (Rect movingArea : movingAreaList) {
            // draw bounding boxes
            Imgproc.rectangle(mOutputFrame, movingArea.tl(), movingArea.br(), new Scalar(0, 255, 0), 2);
        }

        // wait during starting camera period, and avoid continuous shooting
        if (canProcess()) {
            mStartProcessingTime = System.currentTimeMillis();
            String dateTimeStr = getDateTimeStr();
            /**
             * ATTENTION:
             * 1. During a taking picture process, the preview sequence is stopped.
             * 2. The taken picture is saved slightly later than the detected frame.
             */
            takePicture(dateTimeStr);
            saveProcessWindow(mOutputFrame, dateTimeStr);
        }

        return mOutputFrame;
    }

    private void closeCamera() {
        if (isEnded) {
            return;
        }
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
        close();
        isEnded = true;
    }

    private List<Rect> getMovingAreaList(Mat gray) {
        // do Background Subtractor
        mBackgroundSubtractor.apply(gray, mMask);

        // do binarization and remove noise
        Imgproc.threshold(mMask.clone(), mMask, Constants.THRESHOLD_BINARIZATION, 255, Imgproc.THRESH_BINARY);
        Imgproc.morphologyEx(mMask.clone(), mMask, Imgproc.MORPH_OPEN, mStructuringElement);

        // get boundary rectangle
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mMask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        MatOfPoint2f[] contoursPoly = new MatOfPoint2f[contours.size()];
        Rect[] boundRect = new Rect[contours.size()];
        List<Rect> movingAreaList = new ArrayList<>();

        for (int i = 0; i < contours.size(); i++) {
            contoursPoly[i] = new MatOfPoint2f();
            Imgproc.approxPolyDP(new MatOfPoint2f(contours.get(i).toArray()), contoursPoly[i], Constants.APPROXIMATED_ACCURACY_PARAM, true);
            boundRect[i] = Imgproc.boundingRect(new MatOfPoint(contoursPoly[i].toArray()));

            if(boundRect[i].area() > Constants.THRESHOLD_AREA_SIZE) {
                movingAreaList.add(boundRect[i]);
            }
        }

        return movingAreaList;
    }

    private boolean canProcess() {
        if (System.currentTimeMillis() - mStartProcessingTime > Constants.SHOOTING_INTERVAL)
            return true;
        else
            return false;
    }

    private void takePicture(String dateTimeStr) {
        File outDir = new File(Constants.PLUGIN_DIRECTORY);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        String fileUrl = String.format("%s/%s.jpg", Constants.PLUGIN_DIRECTORY, dateTimeStr);
        mOpenCvCameraView.takePicture(fileUrl);
    }

    private void saveProcessWindow(Mat img, String dateTimeStr) {
        File outDir = new File(Constants.PLUGIN_DIRECTORY);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        String fileUrl = String.format("%s/%s_detect.jpg", Constants.PLUGIN_DIRECTORY, dateTimeStr);

        Mat rgbImage = new Mat();
        Imgproc.cvtColor(img, rgbImage, Imgproc.COLOR_BGR2RGB);
        Imgcodecs.imwrite(fileUrl, rgbImage);
        registerFile(fileUrl);
    }

    private String getDateTimeStr() {
        Date date = new Date(System.currentTimeMillis());

        String format = "yyyyMMddHHmmss";
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        String text = sdf.format(date);
        return text;
    }

    private void registerFile(String path) {
        Uri uri = Uri.fromFile(new File(path));
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
        sendBroadcast(mediaScanIntent);
    }

}
