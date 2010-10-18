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
 * limitations under the License
 */

package com.android.apps.tag;

import com.android.apps.tag.provider.TagContract;
import com.android.apps.tag.provider.TagContract.NdefMessages;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.nfc.NdefMessage;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;

public class TagService extends IntentService {
    private static final String TAG = "TagService";

    public static final String EXTRA_SAVE_MSGS = "msgs";
    public static final String EXTRA_DELETE_ID = "delete";

    public TagService() {
        super("SaveTagService");
    }

    @Override
    public void onHandleIntent(Intent intent) {
        if (intent.hasExtra(EXTRA_SAVE_MSGS)) {
            Parcelable[] parcels = intent.getParcelableArrayExtra(EXTRA_SAVE_MSGS);
            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
            for (Parcelable parcel : parcels) {
                ContentValues values = NdefMessages.ndefMessageToValues(this, (NdefMessage) parcel
                        , false);
                ops.add(ContentProviderOperation.newInsert(NdefMessages.CONTENT_URI)
                        .withValues(values).build());
            }
            try {
                getContentResolver().applyBatch(TagContract.AUTHORITY, ops);
            } catch (OperationApplicationException e) {
                Log.e(TAG, "Failed to save messages", e);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to save messages", e);
            }
            return;
        } else if (intent.hasExtra(EXTRA_DELETE_ID)) {
            long id = intent.getLongExtra(EXTRA_DELETE_ID, 0);
            getContentResolver().delete(ContentUris.withAppendedId(NdefMessages.CONTENT_URI, id),
                    null, null);
            return;
        }
    }
}
