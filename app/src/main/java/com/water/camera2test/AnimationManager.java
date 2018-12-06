/*
 * Copyright Statement:
 *
 *   This software/firmware and related documentation ("MediaTek Software") are
 *   protected under relevant copyright laws. The information contained herein is
 *   confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 *   the prior written permission of MediaTek inc. and/or its licensors, any
 *   reproduction, modification, use or disclosure of MediaTek Software, and
 *   information contained herein, in whole or in part, shall be strictly
 *   prohibited.
 *
 *   MediaTek Inc. (C) 2016. All rights reserved.
 *
 *   BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 *   THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 *   RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 *   ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 *   WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 *   WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 *   NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 *   RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 *   INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 *   TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 *   RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 *   OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 *   SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 *   RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 *   STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 *   ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 *   RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 *   MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 *   CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 *   The following software/firmware and/or related documentation ("MediaTek
 *   Software") have been modified by MediaTek Inc. All revisions are subject to
 *   any receiver's applicable license agreements with MediaTek Inc.
 */
package com.water.camera2test;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;

import android.graphics.Rect;
import android.os.AsyncTask;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.support.v8.renderscript.ScriptIntrinsicYuvToRGB;
import android.support.v8.renderscript.Type;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;


/**
 * View Animation manager.
 */
class AnimationManager {
    private static final String TAG = AnimationManager.class.getSimpleName();

    private final ImageView mAnimationView;
    private final ViewGroup mAnimationRoot;
    private final CoverView mCoverView;
    private Activity mActivity;
    private AnimationTask mAnimationTask;
    private AnimatorSet mFlipAnimation;
    private AnimatorSet mSwitchCameraAnimator;



    enum AnimationType {
        TYPE_SWITCH_CAMERA,
        TYPE_CAPTURE,
        TYPE_SWITCH_MODE
    }

    private static final class AsyncData {
        public AnimationType mType;
        public Bitmap mBitmap;
        public int mNextId;
    }


    public AnimationManager(Activity activity) {
        mActivity = activity;
        mAnimationRoot = (ViewGroup) activity.findViewById(R.id.animation_root);
        mAnimationView = (ImageView) activity.findViewById(R.id.animation_view);


        mCoverView =(CoverView) activity.findViewById(R.id.camera_cover);
    }

    public void animationStart(AnimationType type, Bitmap bitmap,int cameraId) {

        switch (type) {
            case TYPE_SWITCH_CAMERA:
                flipAnimationStart(type, bitmap,cameraId);
                break;
            case TYPE_CAPTURE:
                mCoverView.setVisibility(View.VISIBLE);
                //playCaptureAnimation();
                break;
            case TYPE_SWITCH_MODE:
                //slideAnimationStart(type, bitmap);
                break;
            default:
                break;
        }
    }

    private void flipAnimationStart(AnimationType type, Bitmap data,int cameraId) {

        if (data != null) {

            AsyncData asyncData = new AsyncData();
            asyncData.mType = type;
            asyncData.mBitmap = data;
            asyncData.mNextId = cameraId;
            mAnimationTask = new AnimationTask();
            mAnimationTask.execute(asyncData);
        } else {
            Log.e(TAG, "The animation data is null, cannot do the animation!");
        }

        Log.d(TAG, "flipAnimationStart -");
    }



    private class AnimationTask extends AsyncTask<AsyncData, Void, Bitmap> {
        private AsyncData mData;
        private Size mPreSize;
        private Size mNextSize;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            TextureView textureView;
            textureView = (TextureView) mActivity.findViewById(R.id.texture);
            int previewWidth = Math.max(textureView.getWidth(), textureView.getHeight());
            int previewHeight = Math.min(textureView.getWidth(), textureView.getHeight());
            Log.d(TAG, "onPreExecute width " + previewWidth + " height " + previewHeight);
            ViewGroup.LayoutParams params = mAnimationView.getLayoutParams();
            params.width = previewHeight;
            params.height = previewWidth;
            mPreSize =new Size(params.width,params.height);
            mAnimationView.setLayoutParams(params);

        }

        @Override
        protected Bitmap doInBackground(AsyncData... asyncData) {
            mData = asyncData[0];
            mPreSize = CameraUtil.getPreviewSize(mActivity,mData.mNextId==0?1:0);
            mNextSize = CameraUtil.getPreviewSize(mActivity,mData.mNextId);
            Bitmap  result =Bitmap.createScaledBitmap(mData.mBitmap,mData.mBitmap.getWidth()/4,mData.mBitmap.getHeight()/4,true);
             result = blurBitmap(result);
            return result;
        }

        @Override
        protected void onPostExecute(final Bitmap result) {
            super.onPostExecute(result);
            if (result == null) {
                Log.e(TAG, "The result bitmap is null!");
                return;
            }
            switch(mData.mType) {
                case TYPE_SWITCH_CAMERA:
//                    mAnimationRoot.setVisibility(View.VISIBLE);
//                    mAnimationView.setImageBitmap(result);
//                    mAnimationView.setVisibility(View.VISIBLE);
                    mCoverView.setBitmap(result,mPreSize,mNextSize);
                    playSwitchCameraAnimation(mPreSize.getHeight()!=mNextSize.getHeight());
                    break;
                case TYPE_SWITCH_MODE:
                    mAnimationRoot.setVisibility(View.VISIBLE);
                    mAnimationView.setImageBitmap(result);
                    mAnimationView.setVisibility(View.VISIBLE);
                    //playSlideAnimation();
                    break;
                default:
                    break;
            }
            mAnimationTask = null;
        }
    }



    private Bitmap blurBitmap(Bitmap bitmap) {
        Log.d(TAG, "blurBitmap +");
        //Let's create an empty bitmap with the same size of the bitmap we want to blur
        Bitmap outBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);

        //Instantiate a new Renderscript
        RenderScript rs = RenderScript.create(mActivity.getApplicationContext());

        //Create an Intrinsic Blur Script using the Renderscript
        ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));

        //Create the Allocations (in/out) with the Renderscript and the in/out bitmaps
        Allocation allIn = Allocation.createFromBitmap(rs, bitmap);
        Allocation allOut = Allocation.createFromBitmap(rs, outBitmap);

        //Set the radius of the blur
        blurScript.setRadius(25.f);

        //Perform the Renderscript
        blurScript.setInput(allIn);
        blurScript.forEach(allOut);

        //Copy the final bitmap created by the out Allocation to the outBitmap
        allOut.copyTo(outBitmap);

        //recycle the original bitmap
        bitmap.recycle();

        //After finishing everything, we destroy the Renderscript.
        rs.destroy();
        bitmap.recycle();
        Log.d(TAG, "blurBitmap -");
        return outBitmap;
    }

    private void playSwitchCameraAnimation(boolean isClip) {
        mCoverView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mCoverView.setVisibility(View.VISIBLE);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mCoverView, "alpha", 0.4f, 1.0f);
        fadeIn.setInterpolator(new AccelerateInterpolator());
        fadeIn.setDuration(200);
        fadeIn.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                mCoverView.setAinmaType(CoverView.AnimaType.NORMAL);
            }

            @Override
            public void onAnimationEnd(Animator animator) {

            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        ValueAnimator flip =  ValueAnimator.ofFloat(0,1f);
        flip.setDuration(350);
        flip.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float)animation.getAnimatedValue();
                mCoverView.setProgress(value);
            }
        });
        flip.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator animation, boolean isReverse) {
                mCoverView.setAinmaType(CoverView.AnimaType.ROTATE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
            }
        });

        ValueAnimator clip =  ValueAnimator.ofFloat(0,1f);
        clip.setDuration(150);
        clip.setInterpolator(new AccelerateDecelerateInterpolator());
        clip.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float)animation.getAnimatedValue();
                 mCoverView.setProgress(value);
            }
        });
        clip.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator animation, boolean isReverse) {
                mCoverView.setAinmaType(CoverView.AnimaType.CLIP);
            }
            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
            }
        });

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(mCoverView, "alpha", 1.0f, 0.0f);
        fadeOut.setDuration(150);
        fadeOut.setInterpolator(new AccelerateInterpolator());
        fadeOut.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        mSwitchCameraAnimator = new AnimatorSet();
        if(isClip){
            mSwitchCameraAnimator.playSequentially(fadeIn,flip,clip, fadeOut);
        }else{
            mSwitchCameraAnimator.playSequentially(fadeIn,flip,fadeOut);
        }
        mSwitchCameraAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mCoverView.setVisibility(View.GONE);
                mCoverView.setLayerType(View.LAYER_TYPE_NONE, null);

            }

            @Override
            public void onAnimationCancel(Animator animator) {
                mCoverView.setVisibility(View.GONE);
                mCoverView.setLayerType(View.LAYER_TYPE_NONE, null);
            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        mSwitchCameraAnimator.start();
    }

































}
