package com.water.camera2test;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity implements MediaSaver.MediaSaverListener {

    private Camera2Helper mCameraHelp;
    private AutoFitTextureView mTextView;
    private ImageView mShutterButton;
    private ImageView mSwitchButton;
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
        mShutterButton = (ImageView) findViewById(R.id.shutter_button_photo);
        mSwitchButton = (ImageView) findViewById(R.id.switch_button);
        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraHelp.takePicture();
            }
        });
        mSwitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraHelp.switchCamera();
            }
        });
        mCameraHelp = new Camera2Helper(this,mTextView);
    }

    @Override
    public void onFileSaved(final String path) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, path, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
