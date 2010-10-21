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
import com.android.apps.tag.provider.TagContract.NdefTags;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.nfc.NdefTag;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;

public class TagService extends IntentService {
    private static final String TAG = "TagService";

    public static final String EXTRA_SAVE_TAG = "tag";
    public static final String EXTRA_DELETE_URI = "delete";

    public TagService() {
        super("SaveTagService");
    }

    @Override
    public void onHandleIntent(Intent intent) {
        if (intent.hasExtra(EXTRA_SAVE_TAG)) {
            NdefTag tag = (NdefTag) intent.getParcelableExtra(EXTRA_SAVE_TAG);
            ArrayList<ContentProviderOperation> ops = NdefTags.toContentProviderOperations(this, tag);
            try {
                getContentResolver().applyBatch(TagContract.AUTHORITY, ops);
            } catch (OperationApplicationException e) {
                Log.e(TAG, "Failed to save tag", e);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to save tag", e);
            }
            return;
        } else if (intent.hasExtra(EXTRA_DELETE_URI)) {
            Uri uri = (Uri) intent.getParcelableExtra(EXTRA_DELETE_URI);
            getContentResolver().delete(uri, null, null);
            return;
        }
    }
}
