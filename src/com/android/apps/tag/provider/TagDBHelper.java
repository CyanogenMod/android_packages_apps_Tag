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

package com.android.apps.tag.provider;

import com.android.apps.tag.provider.TagContract.NdefMessages;
import com.google.common.annotations.VisibleForTesting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Database utilities for the saved tags.
 */
public class TagDBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tags.db";
    private static final int DATABASE_VERSION = 14;

    public static final String TABLE_NAME_NDEF_MESSAGES = "ndef_msgs";

    TagDBHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    @VisibleForTesting
    TagDBHelper(Context context, String dbFile) {
        super(context, dbFile, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE " + TABLE_NAME_NDEF_MESSAGES + " (" +
                NdefMessages._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                NdefMessages.DATE + " INTEGER NOT NULL, " +
                NdefMessages.TITLE + " TEXT NOT NULL DEFAULT ''," +
                NdefMessages.BYTES + " BLOB NOT NULL, " +
                NdefMessages.STARRED + " INTEGER NOT NULL DEFAULT 0" +  // boolean
                ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop everything and recreate it for now
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_NDEF_MESSAGES);
        onCreate(db);
    }
}
