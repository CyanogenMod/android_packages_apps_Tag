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
import android.content.Intent;
import android.nfc.NdefRecord;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

/**
 * VCard Ndef Record object
 */
public class VCardRecord implements ParsedNdefRecord, OnClickListener {

    private byte[] mVCard;

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

    public static VCardRecord parse(NdefRecord record) {
        MimeRecord underlyingRecord = MimeRecord.parse(record);

        // TODO: Add support for other vcard mime types.
        Preconditions.checkArgument("text/x-vCard".equals(underlyingRecord.getMimeType()));
        return new VCardRecord(underlyingRecord.getContent());
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
}
