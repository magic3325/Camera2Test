package com.water.camera2test;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2Helper {
    private  static Activity mActivity;
    private  static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private  static final int STATE_PREVIEW = 0;
    private  static final int STATE_WAITING_LOCK = 1;
    private  static final int STATE_WAITING_PRECAPTURE = 2;
    private  static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private  static final int STATE_PICTURE_TAKEN = 4;
    private  static final int MAX_PREVIEW_WIDTH = 1920;
    private  static final int MAX_PREVIEW_HEIGHT = 1080;

    private AutoFitTextureView mTextureView;
    private String mCameraId;
    private CameraCaptureSession mCameraCaptureSession;
    private static CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private static File mFile =  null;
    private Semaphore mCameraOpenCloseLock  = new Semaphore(1);
    private boolean mFlashSupported;
    private int mSensorOrientation;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private int mState = STATE_PREVIEW;
    private static CameraManager mCameraManager;
    private AfterDoListener mListener;
    private boolean isNeedHIdeProgressbar = true;



    static{
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =new ImageReader.OnImageAvailableListener(){


        @Override
        public void onImageAvailable(ImageReader reader) {
                    mBackgroundHandler.post(new Camera2Helper.ImageSaver(reader.acquireNextImage(),mFile));
        }
    };

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener =new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width,height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width,height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private final CameraDevice.StateCallback mStateCallback =new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            mCameraDevice =camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallBack = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result){
            switch (mState){
                case STATE_PREVIEW:{
                    if(isNeedHIdeProgressbar){
                        mListener.onAfterPreviewBack();
                        isNeedHIdeProgressbar=false;
                    }
                    break;
                }
                case STATE_WAITING_LOCK:
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                     if(afState==null){
                         captureStillPicture();
                     }else if(CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED==afState||CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED==afState){
                         Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                         if(aeState == null || aeState== CaptureResult.CONTROL_AE_STATE_CONVERGED){
                             mState = STATE_PICTURE_TAKEN;
                             captureStillPicture();
                         }else{
                             runPrecaptureSequence();
                         }

                     }
                     break;
                case STATE_WAITING_PRECAPTURE:
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if(aeState == null || aeState !=CaptureResult.CONTROL_AE_STATE_PRECAPTURE){
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                 default:
                     break;
            }
        }


        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);

            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            process(result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
        }
    };


    private volatile static Camera2Helper singleton;

    public Camera2Helper(Activity act, AutoFitTextureView view ) {

    }
    public Camera2Helper(Activity act, AutoFitTextureView view ,File file) {
        mActivity =act;
        mTextureView = view;
        mFile = file;
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
    }

    public static Camera2Helper getSingleton(Activity act,AutoFitTextureView view,File file){
        if(singleton == null){
            synchronized (Camera2Helper.class){
                singleton = new Camera2Helper(act,view,file);
            }
        }
        return singleton;
    }


    public void startCameraPreView(){
            startBackgroundThread();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mTextureView != null){
                        if(mTextureView.isAvailable()){
                            openCamera(mTextureView.getWidth(),mTextureView.getHeight());
                        }else{
                            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
                        }
                    }
                }
            },300);


    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread(){
        if(mBackgroundThread==null){
            return;
        }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    public void takePicture(){
        lockFocus();
    }

    private void captureStillPicture(){
        try {
            if(mCameraDevice == null){
                return;
            }
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);
            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,rotation);

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback(){
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    unlockFocus();
                    mListener.onAfterTackPicture();
                    showToast("Saved: " + mFile);
                }
            };
            mCameraCaptureSession.stopRepeating();
            //mCameraCaptureSession.abortCaptures();
            mCameraCaptureSession.capture(captureBuilder.build(),CaptureCallback,null);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void lockFocus(){
        try {
            if(mCameraCaptureSession == null){
                return;
            }
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_START);
            mState =  STATE_WAITING_LOCK;
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(),mCaptureCallBack,mBackgroundHandler);

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void unlockFocus(){
        try{
            if(mCameraCaptureSession != null){
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                setAutoFlash(mPreviewRequestBuilder);
                mCameraCaptureSession.capture(mPreviewRequestBuilder.build(),mCaptureCallBack,mBackgroundHandler);
                mState = STATE_PREVIEW;
                mCameraCaptureSession.setRepeatingRequest(mPreviewRequest,mCaptureCallBack,mBackgroundHandler);
            }
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder){
        if(mFlashSupported){
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    private int getOrientation(int rotation){
        return (ORIENTATIONS.get(rotation)+mSensorOrientation+270)%360;
    }

    private void runPrecaptureSequence(){
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mState = STATE_WAITING_PRECAPTURE;
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallBack, mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession(){
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture!=null;

            texture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());;

            Surface surface = new Surface(texture);
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                        if(mCameraDevice == null){
                            return;
                        }

                        mCameraCaptureSession = session;
                        try {

                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            setAutoFlash(mPreviewRequestBuilder);
                            mPreviewRequest = mPreviewRequestBuilder.build();
                            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest,mCaptureCallBack,mBackgroundHandler);
                        }catch (CameraAccessException e){
                            e.printStackTrace();
                        }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            },null);

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs(int width , int height){
        try{

            for(String cameraid:mCameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraid);
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),new Camera2Helper.CompareSizesByArea());
                    mImageReader = ImageReader.newInstance(largest.getWidth(),largest.getHeight(),ImageFormat.JPEG,2);
                    mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);
                int displayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                mSensorOrientation =  cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimemsions = false;
                switch (displayRotation){
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                            if(mSensorOrientation == 90||mSensorOrientation==270){
                                swappedDimemsions =true;
                            }
                          break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimemsions = true;
                        }
                        break;
                }

                Point displaySize = new Point();
                mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreViewWidth = width;
                int rotatedPreviewHeight =  height;
                int maxPreViewWidht = displaySize.x;
                int maxPreViewHeight = displaySize.y;
                if(swappedDimemsions){
                    rotatedPreViewWidth = height;
                    rotatedPreviewHeight =width;
                    maxPreViewWidht = displaySize.y;
                    maxPreViewHeight = displaySize.x;
                }
                if(maxPreViewWidht > MAX_PREVIEW_WIDTH){
                    maxPreViewWidht = MAX_PREVIEW_WIDTH;
                }
                if(maxPreViewHeight > MAX_PREVIEW_HEIGHT){
                    maxPreViewHeight = MAX_PREVIEW_HEIGHT;
                }

                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedPreViewWidth,rotatedPreviewHeight,maxPreViewWidht,maxPreViewHeight,largest);

                int orientation = mActivity.getResources().getConfiguration().orientation;
                    if(orientation == Configuration.ORIENTATION_LANDSCAPE){
                        mTextureView.setAspectRatio(mPreviewSize.getWidth(),mPreviewSize.getHeight());
                    }else{
                        mTextureView.setAspectRatio(mPreviewSize.getHeight(),mPreviewSize.getWidth());
                    }

                    Boolean available = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    mFlashSupported =  available == null ? false : available;
                    mCameraId = cameraid;
                    return;

            }

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }


    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth , int textureViewHeight, int maxWidth ,int maxHeight , Size aspectRatio){

            List<Size> bigEnough =  new ArrayList<>();
            List<Size> notBigEnough = new ArrayList<>();

            int w = aspectRatio.getWidth();
            int h = aspectRatio.getHeight();
            for (Size option : choices){

                if(option.getWidth() <= maxWidth && option.getHeight() <= maxHeight){
                    if(option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight){
                        bigEnough.add(option);
                    }else{
                        notBigEnough.add(option);
                    }
                }
            }

            if(bigEnough.size() > 0) {
                return Collections.min(bigEnough, new Camera2Helper.CompareSizesByArea());
            }else if (notBigEnough.size() > 0){
                return Collections.max(notBigEnough,new Camera2Helper.CompareSizesByArea());
            }else{
                return choices[0];
            }

    }

    private void openCamera(int width , int height){
        if(ActivityCompat.checkSelfPermission(mActivity,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){

            return;
        }
        setUpCameraOutputs(width,height);
        configureTransform(width,height);

        try {
            if(!mCameraOpenCloseLock.tryAcquire(2500,TimeUnit.MILLISECONDS)){
                throw new RuntimeException("Time out waiting to lock camera opening");
            }

            mCameraManager.openCamera(mCameraId,mStateCallback,mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }catch (InterruptedException e){
            throw new RuntimeException("Interrupted while trying to lock camera opening",e);
        }


    }

    private void closeCamera(){
        try {
            mCameraOpenCloseLock.acquire();
            if(mCameraCaptureSession !=null){
                mCameraCaptureSession.close();
                mCameraCaptureSession = null;
            }
            if(mCameraDevice !=null){
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if(mImageReader != null){
                mImageReader.close();
                mImageReader = null;
            }


        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void configureTransform(int width, int height){

        if(mTextureView ==null || mPreviewSize == null){
            return;
        }

        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, width, height);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if(Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation){
            bufferRect.offset(centerX - bufferRect.centerX(),centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect,bufferRect,Matrix.ScaleToFit.FILL);
            float scale = Math.max((float)height/ mPreviewSize.getWidth(),(float)width/mPreviewSize.getHeight());
            matrix.postScale(scale,scale,centerX,centerY);
            matrix.postRotate(90*(rotation - 2),centerX,centerY);

        }else if(Surface.ROTATION_180 == rotation){
            matrix.postRotate(180,centerX,centerY);
        }

        mTextureView.setTransform(matrix);

    }

    public void onDestroyHelper(){
        stopBackgroundThread();
        closeCamera();
        mActivity = null;
        mTextureView = null;
        mListener = null;

    }


 private    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

     static class ImageSaver implements Runnable{


        private final Image mImage;
        private final File mFile;

        public ImageSaver(Image image, File file){
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {

            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    public interface AfterDoListener{
        void onAfterPreviewBack();
        void onAfterTackPicture();
    }
    public void setAfterDoListener (AfterDoListener listener){
        mListener =listener;
    }

    private void showToast(final String text) {

        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }







}
