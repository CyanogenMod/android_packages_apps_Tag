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

package com.android.apps.tag.provider;

import com.android.apps.tag.R;
import com.android.apps.tag.provider.TagContract.NdefMessages;
import com.android.apps.tag.provider.TagContract.NdefRecords;
import com.android.apps.tag.provider.TagContract.NdefTags;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Stores NFC tags in a database. The contract is defined in {@link TagContract}.
 */
public class TagProvider extends SQLiteContentProvider implements TagProviderPipeDataWriter {
    private static final String TAG = "TagProvider";

    private static final int NDEF_MESSAGES = 1000;
    private static final int NDEF_MESSAGES_ID = 1001;

    private static final int NDEF_RECORDS = 2000;
    private static final int NDEF_RECORDS_ID = 2001;
    private static final int NDEF_RECORDS_ID_MIME = 2002;

    private static final int NDEF_TAGS = 3000;
    private static final int NDEF_TAGS_ID = 3001;

    private static final UriMatcher MATCHER;

    private static final Map<String, String> NDEF_TAGS_PROJECTION_MAP =
            ImmutableMap.<String, String>builder()
                .put(NdefTags._ID, NdefTags._ID)
                .put(NdefTags.DATE, NdefTags.DATE)
                .build();

    private static final Map<String, String> NDEF_MESSAGES_PROJECTION_MAP =
            ImmutableMap.<String, String>builder()
                .put(NdefMessages._ID, NdefMessages._ID)
                .put(NdefMessages.TAG_ID, NdefMessages.TAG_ID)
                .put(NdefMessages.TITLE, NdefMessages.TITLE)
                .put(NdefMessages.BYTES, NdefMessages.BYTES)
                .put(NdefMessages.DATE, NdefMessages.DATE)
                .put(NdefMessages.STARRED, NdefMessages.STARRED)
                .put(NdefMessages.ORDINAL, NdefMessages.ORDINAL)
                .build();

    private static final Map<String, String> NDEF_RECORDS_PROJECTION_MAP =
            ImmutableMap.<String, String>builder()
                .put(NdefRecords._ID, NdefRecords._ID)
                .put(NdefRecords.MESSAGE_ID, NdefRecords.MESSAGE_ID)
                .put(NdefRecords.TNF, NdefRecords.TNF)
                .put(NdefRecords.TYPE, NdefRecords.TYPE)
                .put(NdefRecords.BYTES, NdefRecords.BYTES)
                .put(NdefRecords.ORDINAL, NdefRecords.ORDINAL)
                .put(NdefRecords.TAG_ID, NdefRecords.TAG_ID)
                .put(NdefRecords.POSTER_ID, NdefRecords.POSTER_ID)
                .build();

    private Map<String, String> mNdefRecordsMimeProjectionMap;

    static {
        MATCHER = new UriMatcher(0);
        String auth = TagContract.AUTHORITY;

        MATCHER.addURI(auth, "ndef_msgs", NDEF_MESSAGES);
        MATCHER.addURI(auth, "ndef_msgs/#", NDEF_MESSAGES_ID);

        MATCHER.addURI(auth, "ndef_records", NDEF_RECORDS);
        MATCHER.addURI(auth, "ndef_records/#", NDEF_RECORDS_ID);
        MATCHER.addURI(auth, "ndef_records/#/mime", NDEF_RECORDS_ID_MIME);

        MATCHER.addURI(auth, "ndef_tags", NDEF_TAGS);
        MATCHER.addURI(auth, "ndef_tags/#", NDEF_TAGS_ID);
    }

    @Override
    public boolean onCreate() {
        boolean result = super.onCreate();

        // Build the projection map for the MIME records using a localized display name
        mNdefRecordsMimeProjectionMap = ImmutableMap.<String, String>builder()
                .put(NdefRecords.MIME._ID, NdefRecords.MIME._ID)
                .put(NdefRecords.MIME.SIZE,
                        "LEN(" + NdefRecords.BYTES + ") AS " + NdefRecords.MIME.SIZE)
                .put(NdefRecords.MIME.DISPLAY_NAME,
                        "'" + getContext().getString(R.string.mime_display_name) + "' AS "
                        + NdefRecords.MIME.DISPLAY_NAME)
                .build();

        return result;
    }

    @Override
    protected SQLiteOpenHelper getDatabaseHelper(Context context) {
        return new TagDBHelper(context);
    }

    /**
     * Appends one set of selection args to another. This is useful when adding a selection
     * argument to a user provided set.
     */
    public static String[] appendSelectionArgs(String[] originalValues, String[] newValues) {
        if (originalValues == null || originalValues.length == 0) {
            return newValues;
        }
        String[] result = new String[originalValues.length + newValues.length ];
        System.arraycopy(originalValues, 0, result, 0, originalValues.length);
        System.arraycopy(newValues, 0, result, originalValues.length, newValues.length);
        return result;
    }

    /**
     * Concatenates two SQL WHERE clauses, handling empty or null values.
     */
    public static String concatenateWhere(String a, String b) {
        if (TextUtils.isEmpty(a)) {
            return b;
        }
        if (TextUtils.isEmpty(b)) {
            return a;
        }

        return "(" + a + ") AND (" + b + ")";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteDatabase db = getDatabaseHelper().getReadableDatabase();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        int match = MATCHER.match(uri);
        switch (match) {
            case NDEF_TAGS_ID: {
                selection = concatenateWhere(selection,
                        TagDBHelper.TABLE_NAME_NDEF_TAGS + "._id=?");
                selectionArgs = appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case NDEF_TAGS: {
                qb.setTables(TagDBHelper.TABLE_NAME_NDEF_TAGS);
                qb.setProjectionMap(NDEF_TAGS_PROJECTION_MAP);
                break;
            }

            case NDEF_MESSAGES_ID: {
                selection = concatenateWhere(selection,
                        TagDBHelper.TABLE_NAME_NDEF_MESSAGES + "._id=?");
                selectionArgs = appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case NDEF_MESSAGES: {
                qb.setTables(TagDBHelper.TABLE_NAME_NDEF_MESSAGES);
                qb.setProjectionMap(NDEF_MESSAGES_PROJECTION_MAP);
                break;
            }

            case NDEF_RECORDS_ID: {
                selection = concatenateWhere(selection,
                        TagDBHelper.TABLE_NAME_NDEF_RECORDS + "._id=?");
                selectionArgs = appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case NDEF_RECORDS: {
                qb.setTables(TagDBHelper.TABLE_NAME_NDEF_RECORDS);
                qb.setProjectionMap(NDEF_RECORDS_PROJECTION_MAP);
                break;
            }

            case NDEF_RECORDS_ID_MIME: {
                selection = concatenateWhere(selection,
                        TagDBHelper.TABLE_NAME_NDEF_RECORDS + "._id=?");
                selectionArgs = appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                qb.setTables(TagDBHelper.TABLE_NAME_NDEF_RECORDS);
                qb.setProjectionMap(mNdefRecordsMimeProjectionMap);
                break;
            }

            default: {
                throw new IllegalArgumentException("unkown uri " + uri);
            }
        }

        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (cursor != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), TagContract.AUTHORITY_URI);
        }
        return cursor;
    }

    @Override
    protected Uri insertInTransaction(Uri uri, ContentValues values) {
        SQLiteDatabase db = getDatabaseHelper().getWritableDatabase();
        int match = MATCHER.match(uri);
        long id = -1;
        switch (match) {
            case NDEF_MESSAGES: {
                id = db.insert(TagDBHelper.TABLE_NAME_NDEF_MESSAGES, NdefMessages.TITLE, values);
                break;
            }

            case NDEF_RECORDS: {
                id = db.insert(TagDBHelper.TABLE_NAME_NDEF_RECORDS, "", values);
                break;
            }

            case NDEF_TAGS: {
                id = db.insert(TagDBHelper.TABLE_NAME_NDEF_TAGS, "", values);
                break;
            }

            default: {
                throw new IllegalArgumentException("unkown uri " + uri);
            }
        }

        if (id >= 0) {
            return ContentUris.withAppendedId(uri, id);
        }
        return null;
    }

    @Override
    protected int updateInTransaction(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        SQLiteDatabase db = getDatabaseHelper().getWritableDatabase();
        int match = MATCHER.match(uri);
        int count = 0;
        switch (match) {
            case NDEF_MESSAGES_ID: {
                selection = concatenateWhere(selection,
                        TagDBHelper.TABLE_NAME_NDEF_MESSAGES + "._id=?");
                selectionArgs = appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case NDEF_MESSAGES: {
                count = db.update(TagDBHelper.TABLE_NAME_NDEF_MESSAGES, values, selection,
                        selectionArgs);
                break;
            }

            default: {
                throw new IllegalArgumentException("unkown uri " + uri);
            }
        }

        return count;
    }

    @Override
    protected int deleteInTransaction(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = getDatabaseHelper().getWritableDatabase();
        int match = MATCHER.match(uri);
        int count = 0;
        switch (match) {
            case NDEF_MESSAGES_ID: {
                selection = concatenateWhere(selection,
                        TagDBHelper.TABLE_NAME_NDEF_MESSAGES + "._id=?");
                selectionArgs = appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case NDEF_MESSAGES: {
                count = db.delete(TagDBHelper.TABLE_NAME_NDEF_MESSAGES, selection, selectionArgs);
                break;
            }

            default: {
                throw new IllegalArgumentException("unkown uri " + uri);
            }
        }

        return count;
    }

    @Override
    public String getType(Uri uri) {
        int match = MATCHER.match(uri);
        switch (match) {
            case NDEF_TAGS_ID: {
                return NdefTags.CONTENT_ITEM_TYPE;
            }
            case NDEF_TAGS: {
                return NdefTags.CONTENT_TYPE;
            }

            case NDEF_MESSAGES_ID: {
                return NdefMessages.CONTENT_ITEM_TYPE;
            }
            case NDEF_MESSAGES: {
                return NdefMessages.CONTENT_TYPE;
            }

            case NDEF_RECORDS_ID: {
                return NdefRecords.CONTENT_ITEM_TYPE;
            }
            case NDEF_RECORDS: {
                return NdefRecords.CONTENT_TYPE;
            }

            case NDEF_RECORDS_ID_MIME: {
                Cursor cursor = null;
                try {
                    SQLiteDatabase db = getDatabaseHelper().getReadableDatabase();
                    cursor = db.query(TagDBHelper.TABLE_NAME_NDEF_RECORDS,
                            new String[] { NdefRecords.TYPE }, "_id=?",
                            new String[] { uri.getPathSegments().get(1) }, null, null, null, null);
                    if (cursor.moveToFirst()) {
                        return new String(cursor.getBlob(0), Charsets.US_ASCII);
                    }
                } finally {
                    if (cursor != null) cursor.close();
                }
                return null;
            }

            default: {
                throw new IllegalArgumentException("unknown uri " + uri);
            }
        }
    }

    @Override
    protected void notifyChange() {
        getContext().getContentResolver().notifyChange(TagContract.AUTHORITY_URI, null, false);
    }

    @Override
    public void writeMimeDataToPipe(ParcelFileDescriptor output, Uri uri) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getDatabaseHelper().getReadableDatabase();
            cursor = db.query(TagDBHelper.TABLE_NAME_NDEF_RECORDS,
                    new String[] { NdefRecords.BYTES }, "_id=?",
                    new String[] { uri.getPathSegments().get(1) }, null, null, null, null);
            if (cursor.moveToFirst()) {
                byte[] data = cursor.getBlob(0);
                FileOutputStream os = new FileOutputStream(output.getFileDescriptor());
                os.write(data);
                os.flush();
                // openMimePipe() will close output for us, don't close it here.
            }
        } catch (IOException e) {
            Log.e(TAG, "failed to write MIME data to " + uri, e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * A helper function for implementing {@link #openFile}, for
     * creating a data pipe and background thread allowing you to stream
     * generated data back to the client.  This function returns a new
     * ParcelFileDescriptor that should be returned to the caller (the caller
     * is responsible for closing it).
     *
     * @param uri The URI whose data is to be written.
     * @param func Interface implementing the function that will actually
     * stream the data.
     * @return Returns a new ParcelFileDescriptor holding the read side of
     * the pipe.  This should be returned to the caller for reading; the caller
     * is responsible for closing it when done.
     */
    public ParcelFileDescriptor openMimePipe(final Uri uri,
            final TagProviderPipeDataWriter func) throws FileNotFoundException {
        try {
            final ParcelFileDescriptor[] fds = ParcelFileDescriptor.createPipe();

            AsyncTask<Object, Object, Object> task = new AsyncTask<Object, Object, Object>() {
                @Override
                protected Object doInBackground(Object... params) {
                    func.writeMimeDataToPipe(fds[1], uri);
                    try {
                        fds[1].close();
                    } catch (IOException e) {
                        Log.w(TAG, "Failure closing pipe", e);
                    }
                    return null;
                }
            };
            task.execute((Object[])null);

            return fds[0];
        } catch (IOException e) {
            throw new FileNotFoundException("failure making pipe");
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Preconditions.checkArgument("r".equals(mode));
        Preconditions.checkArgument(MATCHER.match(uri) == NDEF_RECORDS_ID_MIME);
        return openMimePipe(uri, this);
    }
}
