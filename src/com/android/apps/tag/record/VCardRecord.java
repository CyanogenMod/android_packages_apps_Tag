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
import com.android.apps.tag.record.RecordUtils.ClickInfo;
import com.google.common.base.Preconditions;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NdefRecord;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * VCard Ndef Record object
 */
public class VCardRecord implements ParsedNdefRecord, OnClickListener {

    public static final String RECORD_TYPE = "vcard";

    private final byte[] mVCard;

    private VCardRecord(byte[] content) {
        mVCard = content;
    }

    @Override
    public View getView(Activity activity, LayoutInflater inflater, ViewGroup parent) {
        // TODO hookup a way to read the VCARD data from the content provider.
        Intent intent = new Intent();
//        intent.setType("text/x-vcard");
        return RecordUtils.getViewsForIntent(activity, inflater, parent, this, intent,
                activity.getString(R.string.import_vcard));
    }

    /**
     * Returns a view in a list of record types for adding new records to a message.
     */
    public static View getAddView(Context context, LayoutInflater inflater, ViewGroup parent) {
        ViewGroup root = (ViewGroup) inflater.inflate(
                R.layout.tag_add_record_list_item, parent, false);

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.Contacts.CONTENT_TYPE);

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        if (activities.isEmpty()) {
            return null;
        }

        ResolveInfo info = activities.get(0);
        ((ImageView) root.findViewById(R.id.image)).setImageDrawable(info.loadIcon(pm));
        ((TextView) root.findViewById(R.id.text)).setText(context.getString(R.string.contact));

        root.setTag(new VCardRecordEditInfo(intent));
        return root;
    }

    public static VCardRecord parse(NdefRecord record) {
        MimeRecord underlyingRecord = MimeRecord.parse(record);

        // TODO: Add support for other vcard mime types.
        Preconditions.checkArgument("text/x-vCard".equals(underlyingRecord.getMimeType()));
        return new VCardRecord(underlyingRecord.getContent());
    }

    public static NdefRecord newVCardRecord(byte[] data) {
        return MimeRecord.newMimeRecord("text/x-vCard", data);
    }

    @Override
    public void onClick(View view) {
        ClickInfo info = (ClickInfo) view.getTag();
        info.activity.startActivity(info.intent);
        info.activity.finish();
    }

    public static boolean isVCard(NdefRecord record) {
        try {
            parse(record);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static class VCardRecordEditInfo extends RecordEditInfo {
        private final Intent mIntent;
        private Uri mLookupUri;

        private String mCachedName = null;
        private Drawable mCachedPhoto = null;
        private byte[] mCachedValue = null;

        public VCardRecordEditInfo(Intent intent) {
            super(RECORD_TYPE);
            mIntent = intent;
        }

        protected VCardRecordEditInfo(Parcel parcel) {
            super(parcel);
            mIntent = parcel.readParcelable(null);
            mLookupUri = parcel.readParcelable(null);
        }

        @Override
        public Intent getPickIntent() {
            return mIntent;
        }

        private void buildInternalValues(Context context) {
            if (mCachedValue != null) {
                return;
            }
            // TODO: do all this work asynchronously?

            Cursor cursor = null;
            long id;
            String lookupKey = null;
            try {
                String[] projection = {
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.LOOKUP_KEY,
                        ContactsContract.Contacts.DISPLAY_NAME
                        };
                cursor = context.getContentResolver().query(
                        mLookupUri, projection, null, null, null);
                cursor.moveToFirst();
                id = cursor.getLong(0);
                lookupKey = cursor.getString(1);
                mCachedName = cursor.getString(2);

            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }

            if (lookupKey == null) {
                // TODO: handle errors.
                return;
            }

            // Note: the lookup key should already encoded.
            Uri vcardUri = Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_VCARD_URI,
                    lookupKey);

            AssetFileDescriptor descriptor;
            FileInputStream in = null;
            try {
                descriptor =  context.getContentResolver().openAssetFileDescriptor(vcardUri, "r");
                mCachedValue = new byte[(int) descriptor.getLength()];

                in = descriptor.createInputStream();
                in.read(mCachedValue);
                in.close();
            } catch (FileNotFoundException e) {
                mCachedValue = null;
            } catch (IOException e) {
                mCachedValue = null;
            }

            Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
            InputStream photoIn = ContactsContract.Contacts.openContactPhotoInputStream(
                    context.getContentResolver(), contactUri);
            if (photoIn != null) {
                mCachedPhoto = Drawable.createFromStream(photoIn, contactUri.toString());
            }
        }

        @Override
        public NdefRecord getValue() {
            return (mCachedValue == null) ? null : VCardRecord.newVCardRecord(mCachedValue);
        }

        @Override
        public void handlePickResult(Context context, Intent data) {
            mLookupUri = data.getData();
            mCachedValue = null;
            mCachedName = null;
            mCachedPhoto = null;
        }

        @Override
        public View getEditView(Activity activity, LayoutInflater inflater, ViewGroup parent) {
            buildInternalValues(activity);

            View result = inflater.inflate(R.layout.tag_edit_vcard, parent, false);
            if (mCachedPhoto != null) {
                ((ImageView) result.findViewById(R.id.photo)).setImageDrawable(mCachedPhoto);
            } else {
                // TODO: get placeholder image.
            }

            if (mCachedName != null) {
                ((TextView) result.findViewById(R.id.display_name)).setText(mCachedName);
            }
            result.setTag(this);
            return result;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeParcelable(mIntent, flags);
            out.writeParcelable(mLookupUri, flags);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<VCardRecordEditInfo> CREATOR =
                new Parcelable.Creator<VCardRecordEditInfo>() {
            public VCardRecordEditInfo createFromParcel(Parcel in) {
                return new VCardRecordEditInfo(in);
            }

            public VCardRecordEditInfo[] newArray(int size) {
                return new VCardRecordEditInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
