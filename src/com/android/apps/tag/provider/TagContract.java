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

import com.android.apps.tag.message.NdefMessageParser;
import com.android.apps.tag.message.ParsedNdefMessage;
import com.google.common.collect.Lists;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NdefTag;
import android.provider.OpenableColumns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class TagContract {
    public static final String AUTHORITY = "com.android.apps.tag";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    public static final class NdefMessages {
        /**
         * Utility class, cannot be instantiated.
         */
        private NdefMessages() {}

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
                AUTHORITY_URI.buildUpon().appendPath("ndef_msgs").build();

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of
         * NDEF messages.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/ndef_msg";

        /**
         * The MIME type of a {@link #CONTENT_URI} subdirectory of a single
         * NDEF message.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/ndef_msg";

        // columns
        public static final String _ID = "_id";
        public static final String TAG_ID = "tag_id";
        public static final String TITLE = "title";
        public static final String BYTES = "bytes";
        public static final String DATE = "date";
        public static final String STARRED = "starred";
        public static final String ORDINAL = "ordinal";

        /**
         * Converts an NdefMessage to ContentValues that can be insrted into this table.
         */
        static ContentValues toValues(Context context, NdefMessage msg,
                boolean isStarred, long date, int ordinal) {
            ParsedNdefMessage parsedMsg = NdefMessageParser.parse(msg);
            ContentValues values = new ContentValues();
            values.put(BYTES, msg.toByteArray());
            values.put(DATE, date);
            values.put(STARRED, isStarred ? 1 : 0);
            values.put(TITLE, parsedMsg.getSnippet(context, Locale.getDefault()));
            values.put(ORDINAL, ordinal);
            return values;
        }
    }

    public static final class NdefRecords {
        /**
         * Utility class, cannot be instantiated.
         */
        private NdefRecords() {}

        public static final Uri CONTENT_URI =
                AUTHORITY_URI.buildUpon().appendPath("ndef_records").build();

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of
         * NDEF messages.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/ndef_record";

        /**
         * The MIME type of a {@link #CONTENT_URI} subdirectory of a single
         * NDEF message.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/ndef_record";

        // columns
        public static final String _ID = "_id";
        public static final String MESSAGE_ID = "msg_id";
        public static final String TNF = "tnf";
        public static final String TYPE = "type";
        public static final String BYTES = "bytes";
        public static final String ORDINAL = "ordinal";
        public static final String TAG_ID = "tag_id";
        public static final String POSTER_ID = "poster_id";

        static ContentValues toValues(Context context, NdefRecord record, int ordinal) {
            ContentValues values = new ContentValues();
            values.put(TNF, record.getTnf());
            values.put(TYPE, record.getTnf());
            values.put(BYTES, record.getPayload());
            values.put(ORDINAL, ordinal);
            values.put(ORDINAL, ordinal);
            return values;
        }

        static final class MIME implements OpenableColumns {
            /**
             * A sub directory of a single entry in this table that treats the entry as raw
             * MIME data.
             */
            public static final String CONTENT_DIRECTORY_MIME = "mime";

            public static final String _ID = "_id";
        }
    }

    public static final class NdefTags {
        /**
         * Utility class, cannot be instantiated.
         */
        private NdefTags() {}

        public static final Uri CONTENT_URI =
                AUTHORITY_URI.buildUpon().appendPath("ndef_tags").build();

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of
         * NDEF messages.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/ndef_tag";

        /**
         * The MIME type of a {@link #CONTENT_URI} subdirectory of a single
         * NDEF message.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/ndef_tag";

        public static final String _ID = "_id";
        public static final String DATE = "date";

        public static ArrayList<ContentProviderOperation> toContentProviderOperations(
                Context context, NdefTag tag, boolean starred) {
            ArrayList<ContentProviderOperation> ops = Lists.newArrayList();
            long now = System.currentTimeMillis();

            // Create the tag entry
            ContentProviderOperation op = ContentProviderOperation.newInsert(CONTENT_URI)
                    .withValue(DATE, now)
                    .build();

            ops.add(op);

            // Create the message entries
            NdefMessage[] msgs = tag.getNdefMessages();

            int msgOrdinal = 0;
            int recordOrdinal = 0;

            for (NdefMessage msg : msgs) {
                int messageOffset = ops.size();
                ContentValues values = NdefMessages.toValues(context, msg, false, now, msgOrdinal);
                op = ContentProviderOperation.newInsert(NdefMessages.CONTENT_URI)
                        .withValues(values)
                        .withValue(NdefMessages.STARRED, starred ? 1 : 0)
                        .withValueBackReference(NdefMessages.TAG_ID, 0)
                        .build();
                ops.add(op);

                for (NdefRecord record : msg.getRecords()) {
                    values = NdefRecords.toValues(context, record, recordOrdinal);

                    op = ContentProviderOperation.newInsert(NdefRecords.CONTENT_URI)
                            .withValues(values)
                            .withValueBackReference(NdefRecords.MESSAGE_ID, messageOffset)
                            .withValueBackReference(NdefRecords.TAG_ID, 0)
                            .build();

                    ops.add(op);

                    recordOrdinal++;

                    if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN &&
                            Arrays.equals(record.getType(), NdefRecord.RTD_SMART_POSTER)) {

                        // This is a poster. Store all of its records as well.
                        int posterOffset = ops.size() - 1;

                        try {
                            NdefMessage subRecords = new NdefMessage(record.getPayload());

                            for (NdefRecord subRecord : subRecords.getRecords()) {
                                values = NdefRecords.toValues(context, subRecord, recordOrdinal);

                                op = ContentProviderOperation.newInsert(NdefRecords.CONTENT_URI)
                                        .withValues(values)
                                        .withValueBackReference(NdefRecords.POSTER_ID,
                                                posterOffset)
                                        .withValueBackReference(NdefRecords.MESSAGE_ID,
                                                messageOffset)
                                        .withValueBackReference(NdefRecords.TAG_ID, 0)
                                        .build();

                                ops.add(op);

                                recordOrdinal++;
                            }

                        } catch (FormatException e) {
                            // ignore
                        }
                    }
                }

                msgOrdinal++;
            }

           return ops;
        }
    }
}
