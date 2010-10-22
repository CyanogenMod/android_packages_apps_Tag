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
import com.android.apps.tag.provider.TagContract.NdefTags;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.nfc.NdefTag;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;

public class TagService extends IntentService {
    private static final String TAG = "TagService";

    private static final String EXTRA_SAVE_TAG = "tag";
    private static final String EXTRA_DELETE_URI = "delete";
    private static final String EXTRA_STAR_URI = "set_star";
    private static final String EXTRA_UNSTAR_URI = "remove_star";
    private static final String EXTRA_STARRED = "starred";

    public TagService() {
        super("SaveTagService");
    }

    @Override
    public void onHandleIntent(Intent intent) {
        if (intent.hasExtra(EXTRA_SAVE_TAG)) {
            NdefTag tag = (NdefTag) intent.getParcelableExtra(EXTRA_SAVE_TAG);
            boolean starred = intent.getBooleanExtra(EXTRA_STARRED, false);
            ArrayList<ContentProviderOperation> ops = NdefTags.toContentProviderOperations(this,
                    tag, starred);
            try {
                getContentResolver().applyBatch(TagContract.AUTHORITY, ops);
            } catch (OperationApplicationException e) {
                Log.e(TAG, "Failed to save tag", e);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to save tag", e);
            }
            return;
        }

        if (intent.hasExtra(EXTRA_DELETE_URI)) {
            Uri uri = (Uri) intent.getParcelableExtra(EXTRA_DELETE_URI);
            getContentResolver().delete(uri, null, null);
            return;
        }

        if (intent.hasExtra(EXTRA_STAR_URI)) {
            Uri uri = (Uri) intent.getParcelableExtra(EXTRA_STAR_URI);
            ContentValues values = new ContentValues();
            values.put(NdefMessages.STARRED, 1);
            getContentResolver().update(uri, values, null, null);
        }

        if (intent.hasExtra(EXTRA_UNSTAR_URI)) {
            Uri uri = (Uri) intent.getParcelableExtra(EXTRA_UNSTAR_URI);
            ContentValues values = new ContentValues();
            values.put(NdefMessages.STARRED, 0);
            getContentResolver().update(uri, values, null, null);
        }
    }

    public static void saveTag(Context context, NdefTag tag, boolean starred) {
        Intent intent = new Intent(context, TagService.class);
        intent.putExtra(TagService.EXTRA_SAVE_TAG, tag);
        intent.putExtra(TagService.EXTRA_STARRED, starred);
        context.startService(intent);
    }

    public static void delete(Context context, Uri uri) {
        Intent intent = new Intent(context, TagService.class);
        intent.putExtra(TagService.EXTRA_DELETE_URI, uri);
        context.startService(intent);
    }

    public static void setStar(Context context, Uri message, boolean star) {
        Intent intent = new Intent(context, TagService.class);
        if (star) {
            intent.putExtra(EXTRA_STAR_URI, message);
        } else {
            intent.putExtra(EXTRA_UNSTAR_URI, message);
        }
        context.startService(intent);
    }
}
