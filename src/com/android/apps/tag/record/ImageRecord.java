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
import android.nfc.NdefRecord;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * A NdefRecord corresponding to an image type.
 */
public class ImageRecord implements ParsedNdefRecord {

    public static final String RECORD_TYPE = "ImageRecord";

    private final Bitmap mBitmap;

    private ImageRecord(Bitmap bitmap) {
        mBitmap = Preconditions.checkNotNull(bitmap);
    }

    @Override
    public View getView(Activity activity, LayoutInflater inflater, ViewGroup parent) {
        ImageView image = (ImageView) inflater.inflate(R.layout.tag_image, parent, false);
        image.setImageBitmap(mBitmap);
        return image;
    }

    /**
     * Returns a view in a list of record types for adding new records to a message.
     */
    public static View getAddView(Context context, LayoutInflater inflater, ViewGroup parent) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.tag_image_picker, parent, false);

        // Determine which Activity can retrieve images.
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        if (activities.isEmpty()) {
            return null;
        }

        ResolveInfo info = activities.get(0);
        ((ImageView) root.findViewById(R.id.image)).setImageDrawable(info.loadIcon(pm));
        ((TextView) root.findViewById(R.id.text)).setText(context.getString(R.string.photo));

        root.setTag(new ImageRecordEditInfo(context, intent));
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

    private static class ImageRecordEditInfo extends RecordEditInfo {
        private final Context mContext;
        private final Intent mIntent;

        public ImageRecordEditInfo(Context context, Intent intent) {
            super(RECORD_TYPE);
            mContext = context;
            mIntent = intent;
        }

        @Override
        public Intent getPickIntent() {
            return mIntent;
        }

        @Override
        public ParsedNdefRecord handlePickResult(Intent data) {
            Cursor cursor = null;
            try {
                String[] projection = {MediaStore.Images.Media.DATA};
                cursor = mContext.getContentResolver().query(
                        data.getData(), projection, null, null, null);
                int index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                String path = cursor.getString(index);

                // TODO: verify size limits.
                return new ImageRecord(BitmapFactory.decodeFile(path));

            } catch (IllegalArgumentException ex) {
                return null;

            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }
}
