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

import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.ImageFormat;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.support.annotation.NonNull;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


/**
 * the class for saving file after capturing a picture or video, need new it in
 * camera context.
 */
public class MediaSaver {

    private static final String TEMP_SUFFIX = ".tmp";
    public static final String PHOTO_PATH =Environment.getExternalStorageDirectory().getPath()+"/Camera2_Photo";
    private final ContentResolver mContentResolver;
    private final List<Request> mSaveQueue = new LinkedList<>();
    private List<MediaSaverListener> mMediaSaverListeners = new ArrayList<>();
    private SaveTask mSaveTask;

    /**
     * the interface notify others when save completed.
     */
    public interface MediaSaverListener {
        /**
         * notified others when save completed.
         * @param uri The uri of saved file.
         */
        void onFileSaved(String path);
    }

    /**
     * add media saver listener for those who want know new file is saved.
     * @param listener the use listener.
     */
    public void addMediaSaverListener(MediaSaverListener listener) {
        mMediaSaverListeners.add(listener);
    }
    /**
     * the constructor of mediaSaver.
     * @param activity The camera activity
     */
    public MediaSaver(Activity activity) {

        mContentResolver = activity.getContentResolver();
        File file =new File(PHOTO_PATH);
        if((!file.exists())||(!file.isDirectory())){
            file.mkdir();
            scanFileAsync(activity,file);
        }

    }

    public void scanFileAsync(Activity activity, File file) {
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setData(Uri.fromFile(file));
        activity.sendBroadcast(scanIntent);
    }

    /**
     * Add save request to mediaSaver for only write data base after capturing,
     * most used in case that not need mediaSaver to write fileSystem, such as video.
     * @param contentValues The contentValues to insert into data base, can not be null.
     * @param filePath      The file path where video should save.
     * @param listener      MediaSaverListener notified.
     */
    public void addSaveRequest(@NonNull byte[] pictureData, ContentValues contentValues,
                               @NonNull String filePath, MediaSaverListener listener) {
        addSaveRequest(pictureData, contentValues, filePath, listener, ImageFormat.JPEG);
    }

    /**
     * Add save request to mediaSaver for write fileSystem and write data base
     * after capturing a picture.
     * @param pictureData   The picture data to save, can not be null.
     * @param contentValues The contentValues to insert into data base, can be null when
     *                      no need insert data base.
     * @param filePath      The file path where picture/video should save.
     *                      can be null if ContentValues has file path.
     * @param type          image format
     * @param listener      MediaSaverListener notified.
     */
    public void addSaveRequest(@NonNull byte[] pictureData, ContentValues contentValues,
                               @NonNull String filePath, MediaSaverListener listener, int type) {
        if (pictureData == null) {
            return;
        }
        Request request = new Request(pictureData, contentValues, filePath, listener, null, type);
        addRequest(request);
    }


    /**
     * Add save request to mediaSaver for only write data base after capturing,
     * most used in case that not need mediaSaver to write fileSystem, such as video.
     * @param contentValues The contentValues to insert into data base, can not be null.
     * @param filePath      The file path where video should save.
     * @param listener      MediaSaverListener notified.
     */
    public void addSaveRequest(@NonNull ContentValues contentValues, String filePath,
                               MediaSaverListener listener) {
        if (contentValues == null) {
            return;
        }
        Request request = new Request(null, contentValues, filePath, listener, null, 0);
        addRequest(request);
    }

    /**
     * update save request to mediaSaver for data base need to update data,
     * most used in case that update data base data according to uri.
     * @param pictureData the jpeg data, can not be null.
     * @param contentValues The contentValues to update into data base, can not be null.
     * @param filePath      The file path where the data should save.
     * @param uri      the uri of saved data.
     */
    public void updateSaveRequest(@NonNull byte[] pictureData,
                                  @NonNull ContentValues contentValues, String filePath,
                                  Uri uri) {
        if (contentValues == null) {
            return;
        }
        Request request = new Request(pictureData, contentValues, filePath, null, uri, 0);
        addRequest(request);
    }

    /**
     * get the total data bytes waiting in the save task.
     * @return The data size bytes waiting to save.
     */
    public long getBytesWaitingToSave() {
        long totalToWrite = 0;
        synchronized (mSaveQueue) {
            for (Request r : mSaveQueue) {
                totalToWrite += r.getDataSize();
            }
        }
        return totalToWrite;
    }

    /**
     * get the number in the save queue.
     * @return The number in the save queue.
     */
    public int getPendingRequestNumber() {
        synchronized (mSaveQueue) {
            return mSaveQueue.size();
        }
    }

    private void saveDataToStorage(Request request) {
        if (request.mData == null) {
            return;
        }
        if (request.mFilePath == null && request.mValues != null) {
            request.mFilePath = request.mValues.getAsString(ImageColumns.DATA);
        }
        if (request.mFilePath == null) {
            return;
        }

        String tempFilePath = request.mFilePath + TEMP_SUFFIX;

        FileOutputStream out = null;
        try {
            // Write to a temporary file and rename it to the final name.
            // This
            // avoids other apps reading incomplete data.
            out = new FileOutputStream(tempFilePath);
            out.write(request.mData);
            out.close();
            new File(tempFilePath).renameTo(new File(request.mFilePath));
        } catch (IOException e) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void insertDb(Request request) {
        if (request.mValues == null) {
            return;
        }
        if (request.mData != null) {
            try {
                // because get the exif from inner is error. so use the SDK api.
                updateContentValues(request);

                request.mUri = mContentResolver.insert(
                        Images.Media.EXTERNAL_CONTENT_URI, request.mValues);
            } catch (IllegalArgumentException e) {
                // failed to insert into the database. This can happen if
                // the SD card is unmounted.
            } catch (UnsupportedOperationException e) {
                // failed to insert into the database. This can happen if
                // the SD card is unmounted.

            }  catch (SQLiteConstraintException e) {
                // failed to insert into the database. unique constraint failed.

            } finally {

            }
        } else {
            if (request.mFilePath == null) {
                return;
            }
            String filePath = request.mValues.getAsString(Video.Media.DATA);
            File temp = new File(request.mFilePath);
            File file = new File(filePath);
            temp.renameTo(file);
            try {
                request.mUri = mContentResolver.insert(
                        Video.Media.EXTERNAL_CONTENT_URI, request.mValues);
            } catch (IllegalArgumentException e) {
                // failed to insert into the database. This can happen if
                // the SD card is unmounted.

            } catch (UnsupportedOperationException e) {
                // failed to insert into the database. This can happen if
                // the SD card is unmounted.

            } catch (SQLiteConstraintException e) {
                // failed to insert into the database. unique constraint failed.

            } finally {

            }
        }
    }

    private void updateDbAccordingUri(Request request) {

        if (request.mValues == null) {

            return;
        }
        if (request.mData != null) {
            try {
                // because get the exif from inner is error. so use the SDK api.
                updateContentValues(request);

                mContentResolver.update(
                        request.mUri, request.mValues, null, null);
            } catch (IllegalArgumentException e) {
                // failed to insert into the database. This can happen if
                // the SD card is unmounted.

            } catch (UnsupportedOperationException e) {
                // failed to insert into the database. This can happen if
                // the SD card is unmounted.

            } catch (SQLiteConstraintException e) {
                // failed to insert into the database. unique constraint failed.

            } finally {

            }
        }
    }

    private void addRequest(Request request) {

        synchronized (mSaveQueue) {
            mSaveQueue.add(request);
        }
        if (mSaveTask == null) {
            mSaveTask = new SaveTask();
            mSaveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        }

    }

    private void updateContentValues(Request request) {
//        if (request.mFilePath != null) {
//            Integer width = request.mValues.getAsInteger(ImageColumns.WIDTH);
//            Integer height = request.mValues.getAsInteger(ImageColumns.HEIGHT);
//
//            if (width != null && height != null &&
//                    (width.intValue() == 0 || height.intValue() == 0)) {
//                //change the mValues;
//                Size pictureSize = CameraUtil.getSizeFromSdkExif(request.mFilePath);
//                request.mValues.put(ImageColumns.WIDTH, pictureSize.getWidth());
//                request.mValues.put(ImageColumns.HEIGHT, pictureSize.getHeight());
//
//            }
//        }
    }
    /**
     * inner class for mediaSaver use.
     */
    private class Request {
        private byte[] mData;
        private ContentValues mValues;
        private String mFilePath;
        private MediaSaverListener mMediaSaverListener;
        private Uri mUri;
        private int mType;

        public Request(byte[] data, ContentValues values, String filePath,
                       MediaSaverListener listener, Uri uri, int type) {
            this.mData = data;
            this.mValues = values;
            this.mFilePath = filePath;
            this.mMediaSaverListener = listener;
            this.mUri = uri;
            this.mType = type;
        }

        private int getDataSize() {
            if (mData == null) {
                return 0;
            } else {
                return mData.length;
            }
        }

        private void saveRequest() {
            saveDataToStorage(this);
            if (this.mUri == null) {
                insertDb(this);
            } else {
                //Update data base according to uri.
                updateDbAccordingUri(this);
            }
        }
    }

    /**
     * the AsyncTask to handle all request to save files.
     */
    private class SaveTask extends AsyncTask<Void, Void, Void> {
        Request mRequest;

        public SaveTask() {
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Void doInBackground(Void... v) {

            while (!mSaveQueue.isEmpty()) {
                synchronized (mSaveQueue) {
                    if (!mSaveQueue.isEmpty()) {
                        mRequest = mSaveQueue.get(0);
                        mSaveQueue.remove(0);
                    } else {
                        break;
                    }
                }
                mRequest.saveRequest();
                if (mRequest.mMediaSaverListener != null) {
                    mRequest.mMediaSaverListener.onFileSaved(mRequest.mFilePath);
                    for (MediaSaverListener listener : mMediaSaverListeners) {
                        listener.onFileSaved(mRequest.mFilePath);
                    }
                }
            }
            mSaveTask = null;

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
        }
    }

    private static final String IMAGE_FORMAT = "'IMG'_yyyyMMdd_HHmmss_S";
    public String generateTitle(long dateTaken) {
        SimpleDateFormat  simpleDateFormat = new SimpleDateFormat(IMAGE_FORMAT);
        Date date = new Date(dateTaken);
        String result = simpleDateFormat.format(date);
        return result;
    }
    public ContentValues createContentValues(byte[] data, int pictureWidth, int pictureHeight) {

        ContentValues values = new ContentValues();
        long dateTaken = System.currentTimeMillis();
        String title = generateTitle(dateTaken);
        String fileName = title + ".jpg";
        int orientation = 0;// CameraUtil.getOrientationFromExif(data);

        String mime = "image/jpeg";
        String path = PHOTO_PATH + '/' + fileName;

        values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, dateTaken);
        values.put(MediaStore.Images.ImageColumns.TITLE, title);
        values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.ImageColumns.MIME_TYPE, mime);
        values.put(MediaStore.Images.ImageColumns.DATA, path);
        values.put(MediaStore.Images.ImageColumns.ORIENTATION, orientation);
        if(pictureWidth!=0){
            values.put(MediaStore.Images.ImageColumns.WIDTH, pictureWidth);
        }
        if(pictureHeight!=0){
            values.put(MediaStore.Images.ImageColumns.HEIGHT, pictureHeight);
        }






        return values;
    }
}
