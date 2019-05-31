package com.theta360.opencvdetection;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ThetaController extends ThetaView {

    private static final String TAG = "Plug-in::ThetaController";

    private String mPictureFileName;
    private CFCallback mCallback;
    private boolean isCapturing = false;
    private boolean isShutter = false;

    public ThetaController(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void takePicture(final String fileName) {
        if (isCapturing)
            return;

        isCapturing = true;
        isShutter = false;

        Log.d(TAG, "Taking picture");
        this.mPictureFileName = fileName;

        mCamera.setPreviewCallback(null);

        Camera.Parameters params = mCamera.getParameters();
        params.setPictureSize(Constants.STILL_SIZE_WIDTH, Constants.STILL_SIZE_HEIGHT);
        params.set("RIC_SHOOTING_MODE", Constants.RIC_STILL_CAPTURE_STD);
        params.set("RIC_EXPOSURE_MODE", Constants.RIC_AUTO_EXPOSURE_P);
        params.set("RIC_PROC_STITCHING", Constants.RIC_DYNAMIC_STITCHING_AUTO);
        params.set("recording-hint", "false");
        params.setJpegThumbnailSize(Constants.THUMBNAIL_SIZE_WIDTH, Constants.THUMBNAIL_SIZE_HEIGHT);
        mCamera.setParameters(params);

        // PictureCallback is implemented by the current class
        mCamera.takePicture(onShutterCallback, null, onJpegPictureCallback);
    }

    public void setCFCallback(CFCallback callback) {
        this.mCallback = callback;
    }

    private Camera.ShutterCallback onShutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
            // ShutterCallback is called twice.
            if (!isShutter) {
                if (mCallback != null) mCallback.onShutter();
                isShutter = true;
            }
        }
    };

    private Camera.PictureCallback onJpegPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            mCamera.stopPreview();

            // Write the image in a file (in jpeg format)
            try (FileOutputStream fos = new FileOutputStream(mPictureFileName);) {
                fos.write(data);
            } catch (IOException e) {
                Log.e(TAG, "Exception in photoCallback", e);
                e.printStackTrace();
            }

            registerFile(mPictureFileName);

            if (mCallback != null) mCallback.onPictureTaken();

            Camera.Parameters params = mCamera.getParameters();
            params.set("RIC_SHOOTING_MODE", Constants.RIC_MOVIE_PREVIEW);
            params.set("RIC_PROC_STITCHING", Constants.RIC_STATIC_STITCHING);
            mCamera.setParameters(params);

            mCamera.startPreview();
            mCamera.setPreviewCallback(ThetaController.this);
            isCapturing = false;
        }
    };

    private void registerFile(String path) {
        Uri uri = Uri.fromFile(new File(path));
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
        getContext().sendBroadcast(mediaScanIntent);
    }

    public interface CFCallback {
        void onShutter();

        void onPictureTaken();
    }
}
