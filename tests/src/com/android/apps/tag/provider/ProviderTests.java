package com.android.apps.tag.provider;

import com.android.apps.tag.provider.TagContract.NdefMessages;
import com.android.apps.tag.provider.TagContract.NdefRecords;
import com.android.apps.tag.provider.TagContract.NdefTags;
import com.android.apps.tag.record.TextRecord;
import com.google.common.collect.Lists;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NdefTag;
import android.nfc.Tag;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;

public class ProviderTests extends AndroidTestCase {

    public static class ContentValuesCursor extends AbstractCursor {

        private String[] mColumnNames;
        private ArrayList<ContentValues> mValues;

        public ContentValuesCursor(String[] columnNames, ArrayList<ContentValues> values) {
            mColumnNames = columnNames;
            mValues = values;
        }

        @Override
        public int getCount() {
            return mValues.size();
        }

        @Override
        public String[] getColumnNames() {
            return mColumnNames;
        }

        @Override
        public String getString(int column) {
            return mValues.get(mPos).getAsString(mColumnNames[column]);
        }

        @Override
        public short getShort(int column) {
            return mValues.get(mPos).getAsShort(mColumnNames[column]);
        }

        @Override
        public int getInt(int column) {
            return mValues.get(mPos).getAsInteger(mColumnNames[column]);
        }

        @Override
        public long getLong(int column) {
            return mValues.get(mPos).getAsLong(mColumnNames[column]);
        }

        @Override
        public float getFloat(int column) {
            return mValues.get(mPos).getAsFloat(mColumnNames[column]);
        }

        @Override
        public double getDouble(int column) {
            return mValues.get(mPos).getAsDouble(mColumnNames[column]);
        }

        @Override
        public boolean isNull(int column) {
            return mValues.get(mPos).containsKey(mColumnNames[column]) == false;
        }

        @Override
        public byte[] getBlob(int column) {
            return mValues.get(mPos).getAsByteArray(mColumnNames[column]);
        }
    }

    public static class TestProvider extends MockContentProvider {

        private static final int NDEF_MESSAGES = 1000;
        private static final int NDEF_RECORDS = 1002;
        private static final int NDEF_TAGS = 1004;

        private static final UriMatcher MATCHER;

        static {
            MATCHER = new UriMatcher(0);
            String auth = TagContract.AUTHORITY;

            MATCHER.addURI(auth, "ndef_msgs", NDEF_MESSAGES);
            MATCHER.addURI(auth, "ndef_records", NDEF_RECORDS);
            MATCHER.addURI(auth, "ndef_tags", NDEF_TAGS);
        }

        private int nextId = 0;
        private ArrayList<ContentValues> messages = Lists.newArrayList();
        private ArrayList<ContentValues> records = Lists.newArrayList();
        private ArrayList<ContentValues> tags = Lists.newArrayList();

        @Override
        public boolean onCreate() {
            return false;
        }

        @Override
        public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            try {
                for (int i = 0; i < numOperations; i++) {
                    final ContentProviderOperation operation = operations.get(i);
                    results[i] = operation.apply(this, results, i);
                }
            } catch (OperationApplicationException e) {
                fail(e.toString());
            }

            return results;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            int match = MATCHER.match(uri);
            switch (match) {
            case NDEF_MESSAGES: {
                messages.add(values);
                break;
            }

            case NDEF_RECORDS: {
                records.add(values);
                break;
            }

            case NDEF_TAGS: {
                tags.add(values);
                break;
            }
            }

            return ContentUris.withAppendedId(uri, nextId++);
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            int match = MATCHER.match(uri);
            switch (match) {
            case NDEF_MESSAGES: {
                return toCursor(messages);
            }

            case NDEF_RECORDS: {
                return toCursor(records);
            }

            case NDEF_TAGS: {
                return toCursor(tags);
            }
            }

            return null;
        }

        private String[] getColumnNames(ArrayList<ContentValues> values) {
            if (values.size() < 0) {
                return null;
            }

            ContentValues cv = values.get(0);

            final int size = cv.size();
            String[] columns = new String[size];

            Set<Entry<String, Object>> valueSet = cv.valueSet();

            int index = 0;
            for (Entry<String, Object> entry : valueSet) {
                columns[index++] = entry.getKey();
            }

            return columns;
        }

        private Cursor toCursor(ArrayList<ContentValues> values) {
            String[] columnNames = getColumnNames(values);
            assertNotNull(columnNames);
            return new ContentValuesCursor(columnNames, values);
        }
    }

    public void testOrdinals() throws Exception {
        // This test creates a NdefTag with three NdefMessage each containing a single
        // record and ensures the ordinal for the records and messages

        NdefMessage[] msgs = new NdefMessage[3];
        msgs[0] = new NdefMessage(new NdefRecord[] { TextRecord.newTextRecord("0", Locale.US) });
        msgs[1] = new NdefMessage(new NdefRecord[] { TextRecord.newTextRecord("1", Locale.US) });
        msgs[2] = new NdefMessage(new NdefRecord[] { TextRecord.newTextRecord("2", Locale.US) });

        NdefTag tag = NdefTag.createMockNdefTag(new byte[] { },
                new String[] { Tag.TARGET_ISO_14443_4 },
                null, null, new String[] { NdefTag.TARGET_TYPE_4 },
                new NdefMessage[][] { msgs });

        Context context = getContext();

        ArrayList<ContentProviderOperation> ops = NdefTags.toContentProviderOperations(context, tag,
                false);

        // There should be seven operations. tag insert, three msg inserts, and three record inserts
        assertEquals(7, ops.size());

        // Write the operation out to the database

        TestProvider provider = new TestProvider();
        provider.applyBatch(ops);

        // Now verify the ordinals of the messages and records

        Cursor cursor = provider.query(NdefMessages.CONTENT_URI, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(3, cursor.getCount());

        int ordinalIndex = cursor.getColumnIndex(NdefMessages.ORDINAL);
        assertTrue(ordinalIndex != -1);

        int expectedOrdinal = 0;
        while (cursor.moveToNext()) {
            int ordinal = cursor.getInt(ordinalIndex);
            assertEquals(expectedOrdinal++, ordinal);
        }

        // Test the records
        cursor = provider.query(NdefRecords.CONTENT_URI, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(3, cursor.getCount());

        ordinalIndex = cursor.getColumnIndex(NdefRecords.ORDINAL);
        assertTrue(ordinalIndex != -1);

        int bytesIndex = cursor.getColumnIndex(NdefRecords.BYTES);
        assertTrue(bytesIndex != -1);

        int index = 0;
        while (cursor.moveToNext()) {
            assertEquals(index, cursor.getInt(ordinalIndex));
            assertTrue(Arrays.equals(msgs[index].getRecords()[0].getPayload(), cursor.getBlob(bytesIndex)));
            index++;
        }
    }

    public void testSmartPoster() throws Exception {

        NdefMessage posterMessage = new NdefMessage(new NdefRecord[] {
                TextRecord.newTextRecord("4", Locale.US),
                TextRecord.newTextRecord("5", Locale.US) });

        NdefRecord poster = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_SMART_POSTER,
                new byte[] { }, posterMessage.toByteArray());


        NdefMessage[] msgs = new NdefMessage[4];
        msgs[0] = new NdefMessage(new NdefRecord[] {
                TextRecord.newTextRecord("0", Locale.US),
                TextRecord.newTextRecord("1", Locale.US)
                });
        msgs[1] = new NdefMessage(new NdefRecord[] { TextRecord.newTextRecord("2", Locale.US) });
        msgs[2] = new NdefMessage(new NdefRecord[] { poster });
        msgs[3] = new NdefMessage(new NdefRecord[] { TextRecord.newTextRecord("6", Locale.US) });

        NdefTag tag = NdefTag.createMockNdefTag(new byte[] { },
                new String[] { Tag.TARGET_ISO_14443_4 },
                null, null, new String[] { NdefTag.TARGET_TYPE_4 },
                new NdefMessage[][] { msgs });

        Context context = getContext();

        ArrayList<ContentProviderOperation> ops = NdefTags.toContentProviderOperations(context, tag,
                false);

        // There should be seven operations. tag insert, three msg inserts, and three record inserts
        assertEquals(12, ops.size());

        // Write the operation out to the database

        TestProvider provider = new TestProvider();
        provider.applyBatch(ops);

        Cursor cursor = provider.query(NdefMessages.CONTENT_URI, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(4, cursor.getCount());

        int ordinalIndex = cursor.getColumnIndex(NdefMessages.ORDINAL);
        assertTrue(ordinalIndex != -1);

        int expectedOrdinal = 0;
        while (cursor.moveToNext()) {
            int ordinal = cursor.getInt(ordinalIndex);
            assertEquals(expectedOrdinal++, ordinal);
        }

        // Test the records
        cursor = provider.query(NdefRecords.CONTENT_URI, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(7, cursor.getCount());

        ordinalIndex = cursor.getColumnIndex(NdefRecords.ORDINAL);
        assertTrue(ordinalIndex != -1);

        int bytesIndex = cursor.getColumnIndex(NdefRecords.BYTES);
        assertTrue(bytesIndex != -1);

        int index = 0;

        cursor.moveToNext();
        assertEquals(0, cursor.getInt(ordinalIndex));
        assertTrue(Arrays.equals(msgs[index].getRecords()[0].getPayload(), cursor.getBlob(bytesIndex)));

        cursor.moveToNext();
        assertEquals(1, cursor.getInt(ordinalIndex));
        assertTrue(Arrays.equals(msgs[index].getRecords()[1].getPayload(), cursor.getBlob(bytesIndex)));

        index++;

        cursor.moveToNext();
        assertEquals(2, cursor.getInt(ordinalIndex));
        assertTrue(Arrays.equals(msgs[index++].getRecords()[0].getPayload(), cursor.getBlob(bytesIndex)));

        cursor.moveToNext();
        assertEquals(3, cursor.getInt(ordinalIndex));
        assertTrue(Arrays.equals(msgs[index++].getRecords()[0].getPayload(), cursor.getBlob(bytesIndex)));

        cursor.moveToNext();
        assertEquals(4, cursor.getInt(ordinalIndex));
        assertTrue(Arrays.equals(posterMessage.getRecords()[0].getPayload(), cursor.getBlob(bytesIndex)));

        cursor.moveToNext();
        assertEquals(5, cursor.getInt(ordinalIndex));
        assertTrue(Arrays.equals(posterMessage.getRecords()[1].getPayload(), cursor.getBlob(bytesIndex)));

        cursor.moveToNext();
        assertEquals(6, cursor.getInt(ordinalIndex));
        assertTrue(Arrays.equals(msgs[index++].getRecords()[0].getPayload(), cursor.getBlob(bytesIndex)));
    }
}
