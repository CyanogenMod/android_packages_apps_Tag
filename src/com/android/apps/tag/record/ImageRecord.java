/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.apps.tag.record;

import com.android.apps.tag.R;
import com.google.common.base.Preconditions;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.nfc.NdefRecord;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * A NdefRecord corresponding to an image type.
 */
public class ImageRecord extends ParsedNdefRecord {

    public static final String RECORD_TYPE = "ImageRecord";

    private final Bitmap mBitmap;

    private ImageRecord(Bitmap bitmap) {
        mBitmap = Preconditions.checkNotNull(bitmap);
    }

    @Override
    public View getView(Activity activity, LayoutInflater inflater, ViewGroup parent, int offset) {
        ImageView image = (ImageView) inflater.inflate(R.layout.tag_image, parent, false);
        image.setImageBitmap(mBitmap);
        return image;
    }

    @Override
    public RecordEditInfo getEditInfo(Activity host) {
        return new ImageRecordEditInfo(mBitmap);
    }

    private static Intent getPickImageIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        return intent;
    }

    /**
     * Returns a view in a list of record types for adding new records to a message.
     */
    public static View getAddView(Context context, LayoutInflater inflater, ViewGroup parent) {
        ViewGroup root = (ViewGroup) inflater.inflate(
                R.layout.tag_add_record_list_item, parent, false);

        // Determine which Activity can retrieve images.
        Intent intent = getPickImageIntent();
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        if (activities.isEmpty()) {
            return null;
        }

        ResolveInfo info = activities.get(0);
        ((ImageView) root.findViewById(R.id.image)).setImageDrawable(info.loadIcon(pm));
        ((TextView) root.findViewById(R.id.text)).setText(context.getString(R.string.photo));

        root.setTag(new ImageRecordEditInfo());
        return root;
    }

    public static ImageRecord parse(NdefRecord record) {
        MimeRecord underlyingRecord = MimeRecord.parse(record);
        Preconditions.checkArgument(underlyingRecord.getMimeType().startsWith("image/"));

        // Try to ensure it's a legal, valid image
        byte[] content = underlyingRecord.getContent();
        Bitmap bitmap = BitmapFactory.decodeByteArray(content, 0, content.length);
        if (bitmap == null) {
            throw new IllegalArgumentException("not a valid image file");
        }
        return new ImageRecord(bitmap);
    }

    public static boolean isImage(NdefRecord record) {
        try {
            parse(record);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static NdefRecord newImageRecord(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        byte[] content = out.toByteArray();
        return MimeRecord.newMimeRecord("image/jpeg", content);
    }

    private static class ImageRecordEditInfo extends RecordEditInfo {
        /**
         * The path on the device where we can load the image. If this is set, the value will be
         * lazily loaded from the path.
         */
        private String mCurrentPath;

        /**
         * The actual current value of the image for the record.
         */
        private Bitmap mCachedValue;

        /**
         * Pixel size to crop loaded images to.
         */
        public static final int MAX_IMAGE_SIZE = 128;

        public ImageRecordEditInfo() {
            super(RECORD_TYPE);
            mCurrentPath = "";
            mCachedValue = null;
        }

        public ImageRecordEditInfo(String path) {
            super(RECORD_TYPE);
            mCurrentPath = path;
            mCachedValue = null;
        }

        public ImageRecordEditInfo(Bitmap value) {
            super(RECORD_TYPE);
            mCurrentPath = "";
            mCachedValue = value;
        }

        protected ImageRecordEditInfo(Parcel parcel) {
            super(parcel);
            mCurrentPath = parcel.readString();
            mCachedValue = parcel.readParcelable(null);
        }

        @Override
        public Intent getPickIntent() {
            return getPickImageIntent();
        }

        @Override
        public NdefRecord getValue() {
            return ImageRecord.newImageRecord(getValueInternal());
        }

        private Bitmap getValueInternal() {
            if (mCachedValue == null) {
                Bitmap original = BitmapFactory.decodeFile(mCurrentPath);
                int width = original.getWidth();
                int height = original.getHeight();
                int major = (width > height) ? width : height;
                if (major > MAX_IMAGE_SIZE) {
                    double scale = 1.0 * MAX_IMAGE_SIZE / major;
                    width *= scale;
                    height *= scale;
                }
                mCachedValue = ThumbnailUtils.extractThumbnail(original, width, height);
            }
            return mCachedValue;
        }

        @Override
        public void handlePickResult(Context context, Intent data) {
            Cursor cursor = null;
            mCachedValue = null;

            try {
                String[] projection = { MediaStore.Images.Media.DATA, OpenableColumns.SIZE };
                cursor = context.getContentResolver().query(
                        data.getData(), projection, null, null, null);

                if (cursor == null) {
                    Toast.makeText(
                            context,
                            context.getResources().getString(R.string.bad_photo),
                            Toast.LENGTH_LONG).show();
                    throw new IllegalArgumentException("Selected image could not be loaded");
                }

                cursor.moveToFirst();
                int size = cursor.getInt(1);
                mCurrentPath = cursor.getString(0);

                // TODO: enforce a size limit. May be tricky.

            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        @Override
        public View getEditView(
                Activity activity, LayoutInflater inflater,
                ViewGroup parent, EditCallbacks callbacks) {
            View result = buildEditView(
                    activity, inflater, R.layout.tag_edit_image, parent, callbacks);
            ((ImageView) result.findViewById(R.id.image)).setImageBitmap(getValueInternal());
            result.setOnClickListener(this);
            return result;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeString(mCurrentPath);
            out.writeParcelable(mCachedValue, flags);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<ImageRecordEditInfo> CREATOR =
                new Parcelable.Creator<ImageRecordEditInfo>() {
            @Override
            public ImageRecordEditInfo createFromParcel(Parcel in) {
                return new ImageRecordEditInfo(in);
            }

            @Override
            public ImageRecordEditInfo[] newArray(int size) {
                return new ImageRecordEditInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onClick(View target) {
            if (this == target.getTag()) {
                mCallbacks.startPickForRecord(this, getPickIntent());
            } else {
                super.onClick(target);
            }
        }
    }
}
