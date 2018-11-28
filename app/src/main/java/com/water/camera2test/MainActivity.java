package com.water.camera2test;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity implements Camera2Helper.AfterDoListener {

    private Camera2Helper mCameraHelp;
    private File mFile;
    private AutoFitTextureView mTextView;
    private ImageView mImageView;
    private Button mButton;
    private ProgressBar mProgressBar;
    public static final String PHOTO_PATH =Environment.getExternalStorageDirectory().getPath();
    public static final String PHOTO_NAME = "camera";
    private boolean mHasCriticalPermissions;

    public static final int CODE_FOR_WRITE_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
        if(mHasCriticalPermissions){
            init();
        }else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, CODE_FOR_WRITE_PERMISSION);
        }
    }




    @Override
    protected void onResume() {
        super.onResume();
        if(mCameraHelp!=null){
            mCameraHelp.startCameraPreView();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mCameraHelp!=null){
            mCameraHelp.onDestroyHelper();
        }
    }

    private void checkPermissions() {


        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            mHasCriticalPermissions = true;
        } else {
            mHasCriticalPermissions = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CODE_FOR_WRITE_PERMISSION){
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                init();
            }else{
                finish();
            }
        }
    }

    private void init(){

        mTextView = (AutoFitTextureView) findViewById(R.id.texture);
        //mImageView = (ImageView) findViewById(R.id.img_photo);
        mButton = (Button) findViewById(R.id.btn_take_photo);
        //mProgressBar = (ProgressBar)findViewById(R.id.progressbar_loading);
        mFile = new File(PHOTO_PATH,PHOTO_NAME+".jpg");
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraHelp.takePicture();
            }
        });

        mCameraHelp = Camera2Helper.getSingleton(this,mTextView,mFile);
        mCameraHelp.setAfterDoListener(this);
    }


    @Override
    public void onAfterPreviewBack() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // mProgressBar.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onAfterTackPicture() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                InputStream imput = null;
                try{
                    imput = new FileInputStream(mFile);
                    byte[] byt = new byte[imput.available()];
                    imput.read(byt);
                    //mImageView.setImageBitmap();
                }catch (FileNotFoundException e){
                    e.printStackTrace();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        });
    }
}
