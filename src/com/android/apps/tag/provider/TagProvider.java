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

import com.android.apps.tag.provider.TagContract.NdefMessages;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import java.util.HashMap;

/**
 * Stores NFC tags in a database. The contract is defined in {@link TagContract}.
 */
public class TagProvider extends SQLiteContentProvider {

    private static final int NDEF_MESSAGES = 1000;
    private static final int NDEF_MESSAGES_ID = 1001;
    private static final UriMatcher sMatcher;

    private static final HashMap<String, String> sNdefMessagesProjectionMap;

    static {
        sMatcher = new UriMatcher(0);
        String auth = TagContract.AUTHORITY;
        sMatcher.addURI(auth, "ndef", NDEF_MESSAGES);
        sMatcher.addURI(auth, "ndef/#", NDEF_MESSAGES_ID);

        HashMap<String, String> map = new HashMap<String, String>();
        map.put(NdefMessages._ID, NdefMessages._ID);
        map.put(NdefMessages.TITLE, NdefMessages.TITLE);
        map.put(NdefMessages.BYTES, NdefMessages.BYTES);
        map.put(NdefMessages.DATE, NdefMessages.DATE);
        map.put(NdefMessages.STARRED, NdefMessages.STARRED);
        sNdefMessagesProjectionMap = map;
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

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteDatabase db = getDatabaseHelper().getReadableDatabase();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        int match = sMatcher.match(uri);
        switch (match) {
            case NDEF_MESSAGES_ID: {
                selection = DatabaseUtils.concatenateWhere(selection,
                        TagDBHelper.TABLE_NAME_NDEF_MESSAGES + "._id=?");
                selectionArgs = appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case NDEF_MESSAGES: {
                qb.setTables(TagDBHelper.TABLE_NAME_NDEF_MESSAGES);
                qb.setProjectionMap(sNdefMessagesProjectionMap);
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
        int match = sMatcher.match(uri);
        long id = -1;
        switch (match) {
            case NDEF_MESSAGES: {
                id = db.insert(TagDBHelper.TABLE_NAME_NDEF_MESSAGES, NdefMessages.TITLE, values);
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
        int match = sMatcher.match(uri);
        int count = 0;
        switch (match) {
            case NDEF_MESSAGES_ID: {
                selection = DatabaseUtils.concatenateWhere(selection,
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
        int match = sMatcher.match(uri);
        int count = 0;
        switch (match) {
            case NDEF_MESSAGES_ID: {
                selection = DatabaseUtils.concatenateWhere(selection,
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
        int match = sMatcher.match(uri);
        switch (match) {
            case NDEF_MESSAGES_ID: {
                return NdefMessages.CONTENT_ITEM_TYPE;
            }
            case NDEF_MESSAGES: {
                return NdefMessages.CONTENT_TYPE;
            }
        }
        return null;
    }

    @Override
    protected void notifyChange() {
        getContext().getContentResolver().notifyChange(TagContract.AUTHORITY_URI, null, false);
    }
}
