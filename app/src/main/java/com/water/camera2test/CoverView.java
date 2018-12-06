package com.water.camera2test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.View;

public class CoverView extends View {


    private float mRotate;
    private float mAlpha;
    private  int mWidth;
    private  int mHeight;
    private  float mMaxDepth;
    private float mProgress;
    private AnimaType mAnimaType;
    private Size mPreSize;
    private Size mNextSize;

    private Bitmap mBitmap;

    public enum AnimaType {
        NORMAL,
        ROTATE,
        CLIP,
        BLUR
    }


    private Camera mCamera;
    private Matrix mMatrix;
    private Paint mPaint;

    public CoverView(Context context) {
        super(context);
    }
    public CoverView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CoverView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CoverView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

    }



    public  void setBitmap(Bitmap bitmap,Size presize,Size nextSize){
        mPreSize = presize;
        mNextSize = nextSize;
        mBitmap = Bitmap.createScaledBitmap(bitmap,mPreSize.getWidth(),mPreSize.getHeight(),true);
        mMaxDepth =mBitmap.getWidth();
        mCamera = new Camera();
        mMatrix = new Matrix();
        mPaint =new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.FILL);
        invalidate();

    }
    public void setAinmaType(AnimaType type){
        mAnimaType = type;
    }


    public  void setProgress(float progress){
        mProgress = progress;
        invalidate();
    }



    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(mAnimaType == AnimaType.NORMAL){
            float top =(float)(mHeight-mBitmap.getHeight())/2f;
            float left =(float)(mWidth- mBitmap.getWidth())/2f;
            RectF dstF = new RectF(left,top,left+mBitmap.getWidth(),top+mBitmap.getHeight());
            Rect src = new Rect(0, 0,mBitmap.getWidth(),mBitmap.getHeight());
            canvas.drawBitmap(mBitmap,src,dstF,mPaint);
        }else if(mAnimaType == AnimaType.ROTATE){
            drawRotateBottom(canvas);
        }else if(mAnimaType == AnimaType.CLIP){
            drawClip(canvas);
        }


    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mWidth = MeasureSpec.getSize(widthMeasureSpec);
        mHeight =  MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(mWidth, mHeight);
    }

    private void drawRotatetop(Canvas canvas){

        if(mBitmap != null){
            mCamera.save();
            mMatrix.reset();
            float depthz = 0f;
            if(mProgress<=0.5){
                mRotate = 90/0.5f*mProgress;
                depthz =  mMaxDepth * mProgress;
            }else{
                mRotate = -90/0.5f*(1-mProgress);
                depthz =  mMaxDepth * (1-mProgress)*2f;
            }
            Log.e("@water","mRotate == "+mRotate+"  depthz == "+depthz);
            mCamera.translate(0.0f, 0.0f, depthz);
            mCamera.rotateX(mRotate);
            mCamera.getMatrix(mMatrix);
            mCamera.restore();

            mMatrix.preTranslate(-mWidth/2, -mHeight/2);
            mMatrix.postTranslate(mWidth/2, mHeight/2);

            canvas.translate((float)(mWidth- mBitmap.getWidth())/2f,(float)(mHeight-mBitmap.getHeight())/2f);
            canvas.drawBitmap(mBitmap,mMatrix,mPaint);

        }
    }

    private void drawRotateBottom(Canvas canvas){

        if(mBitmap != null){
            mCamera.save();
            mMatrix.reset();
            float depthz = 0f;
            if(mProgress<=0.5){
                mRotate = -90/0.5f*mProgress;
                depthz =  mMaxDepth*2f * mProgress;
            }else{
                mRotate = 90/0.5f*(1-mProgress);
                depthz =  mMaxDepth * (1-mProgress);
            }
            Log.e("@water","mRotate == "+mRotate+"  depthz == "+depthz);
            mCamera.translate(0.0f, 0.0f, depthz);
            mCamera.rotateX(mRotate);
            mCamera.getMatrix(mMatrix);
            mCamera.restore();

            mMatrix.preTranslate(-mWidth/2, -mHeight/2);
            mMatrix.postTranslate(mWidth/2, mHeight/2);

            canvas.translate((float)(mWidth- mBitmap.getWidth())/2f,(float)(mHeight-mBitmap.getHeight())/2f);
            canvas.drawBitmap(mBitmap,mMatrix,mPaint);

        }
    }

    private void drawClip(Canvas canvas){

        if(mBitmap != null){
            float offestx = (mWidth-mPreSize.getWidth())/2f;
            float offesty = (mHeight-mPreSize.getHeight())/2f;
            float dw =(mPreSize.getWidth() - mNextSize.getWidth())/2*mProgress;
            float dh =(mPreSize.getHeight() - mNextSize.getHeight())/2*mProgress;
            RectF dstF = new RectF(offestx+dw,offesty+dh,mPreSize.getWidth()+offestx-dw,mPreSize.getHeight()+offesty-dh);

            Rect src;
            if(mPreSize.getHeight() < mNextSize.getHeight()){
                src = new Rect(0, 0,mBitmap.getWidth(),mBitmap.getHeight());
            }else{
                src = new Rect((int)dw,(int)dh,(int)(mBitmap.getWidth()-dw),(int)(mBitmap.getHeight()-dh));
            }


            canvas.drawBitmap(mBitmap,src,dstF,mPaint);

        }
    }



}
