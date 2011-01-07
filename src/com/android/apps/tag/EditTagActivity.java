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

package com.android.apps.tag;

import com.android.apps.tag.record.ImageRecord;
import com.android.apps.tag.record.ParsedNdefRecord;
import com.android.apps.tag.record.RecordEditInfo;
import com.android.apps.tag.record.RecordEditInfo.EditCallbacks;
import com.android.apps.tag.record.UriRecord;
import com.android.apps.tag.record.VCardRecord;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;

/**
 * A base {@link Activity} class for an editor of an {@link NdefMessage} tag.
 *
 * The core of the editing is done by various child {@link View}s that differ based on
 * {@link ParsedNdefRecord} types. Each type of {@link ParsedNdefRecord} can build views to
 * pick/select a new piece of content, or edit an existing content for the {@link NdefMessage}.
 */
public abstract class EditTagActivity extends Activity implements OnClickListener, EditCallbacks {

    private static final String BUNDLE_KEY_OUTSTANDING_PICK = "outstanding-pick";
    protected static final int DIALOG_ID_ADD_CONTENT = 0;

    private static final Set<String> SUPPORTED_RECORD_TYPES = ImmutableSet.of(
        ImageRecord.RECORD_TYPE,
        UriRecord.RECORD_TYPE,
        VCardRecord.RECORD_TYPE
    );

    /**
     * Records contained in the current message being edited.
     */
    protected final ArrayList<RecordEditInfo> mRecords = Lists.newArrayList();

    /**
     * The container where the subviews for each record are housed.
     */
    private ViewGroup mContentRoot;

    /**
     * Info about an outstanding picking activity to add a new record.
     */
    private RecordEditInfo mRecordWithOutstandingPick;

    private LayoutInflater mInflater;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            mRecordWithOutstandingPick = savedState.getParcelable(BUNDLE_KEY_OUTSTANDING_PICK);
        }
        mInflater = LayoutInflater.from(this);
    }

    protected ViewGroup getContentRoot() {
        if (mContentRoot == null) {
            mContentRoot = (ViewGroup) findViewById(R.id.content_parent);
        }
        return mContentRoot;
    }

    /**
     * @return The list of {@link ParsedNdefRecord} types that this editor supports. Subclasses
     *     may override to filter out specific types.
     */
    public Set<String> getSupportedTypes() {
        return SUPPORTED_RECORD_TYPES;
    }

    /**
     * Builds a {@link View} used as an item in a list when picking a new piece of content to add
     * to the tag.
     */
    public View getAddView(ViewGroup parent, String type) {
        if (ImageRecord.RECORD_TYPE.equals(type)) {
            return ImageRecord.getAddView(this, mInflater, parent);
        } else if (UriRecord.RECORD_TYPE.equals(type)) {
            return UriRecord.getAddView(this, mInflater, parent);
        } else if (VCardRecord.RECORD_TYPE.equals(type)) {
            return VCardRecord.getAddView(this, mInflater, parent);
        }
        throw new IllegalArgumentException("Not a supported view type");
    }

    /**
     * Builds a snapshot of current values as held in the internal state of this editor.
     */
    public ArrayList<NdefRecord> getValues() {
        ArrayList<NdefRecord> result = new ArrayList<NdefRecord>(mRecords.size());
        for (RecordEditInfo editInfo : mRecords) {
            result.add(editInfo.getValue());
        }
        return result;
    }

    /**
     * Builds a {@link View} used as an item in a list when editing content for a tag.
     */
    public void addRecord(RecordEditInfo editInfo) {
        mRecords.add(Preconditions.checkNotNull(editInfo));
        addViewForRecord(editInfo);
    }

    /**
     * Adds a child editor view for a record.
     */
    public void addViewForRecord(RecordEditInfo editInfo) {
        ViewGroup root = getContentRoot();
        View editView = editInfo.getEditView(this, mInflater, root, this);
        root.addView(mInflater.inflate(R.layout.tag_divider, root, false));
        root.addView(editView);
    }

    protected void rebuildChildViews() {
        ViewGroup root = getContentRoot();
        root.removeAllViews();
        for (RecordEditInfo editInfo : mRecords) {
            addViewForRecord(editInfo);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (id == DIALOG_ID_ADD_CONTENT) {
            return new TagContentSelector(this);
        }
        return super.onCreateDialog(id, args);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        super.onPrepareDialog(id, dialog, args);
        if (dialog instanceof TagContentSelector) {
            ((TagContentSelector) dialog).rebuildViews();
        }
    }

    /**
     * Displays a {@link Dialog} to select a new content type to add to the Tag.
     */
    protected void showAddContentDialog() {
        showDialog(DIALOG_ID_ADD_CONTENT);
    }

    @Override
    public void startPickForRecord(RecordEditInfo editInfo, Intent intent) {
        mRecordWithOutstandingPick = editInfo;
        startActivityForResult(intent, 0);
    }

    /**
     * Handles a click to select and add a new content type.
     */
    public void onAddContentClick(View target) {
        Object tag = target.getTag();
        if ((tag == null) || !(tag instanceof RecordEditInfo)) {
            return;
        }

        RecordEditInfo info = (RecordEditInfo) tag;
        Intent pickIntent = info.getPickIntent();
        if (pickIntent != null) {
            startPickForRecord(info, pickIntent);
        } else {
            // Does not require an external Activity. Add the edit view directly.
            addRecord(info);
        }
    }

    @Override
    public void deleteRecord(RecordEditInfo editInfo) {
        mRecords.remove(editInfo);
        rebuildChildViews();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((resultCode != RESULT_OK) || (data == null)) {
            mRecordWithOutstandingPick = null;
            return;
        }
        if (mRecordWithOutstandingPick == null) {
            return;
        }

        // Handles results from another Activity that picked content to write to a tag.
        RecordEditInfo recordInfo = mRecordWithOutstandingPick;
        try {
            recordInfo.handlePickResult(this, data);
        } catch (IllegalArgumentException ex) {
            if (mRecords.contains(recordInfo)) {
                deleteRecord(recordInfo);
            }
            return;
        }

        if (mRecords.contains(recordInfo)) {
            // Editing an existing record. Just rebuild everything.
            rebuildChildViews();

        } else {
            // Adding a new record.
            addRecord(recordInfo);
        }
        // TODO: handle errors in picking (e.g. the image is too big, etc).

        mRecordWithOutstandingPick = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mRecordWithOutstandingPick != null) {
            outState.putParcelable(BUNDLE_KEY_OUTSTANDING_PICK, mRecordWithOutstandingPick);
        }
    }

    interface GetTagQuery {
        final static String[] PROJECTION = new String[] {
                NdefMessages.BYTES
        };

        static final int COLUMN_BYTES = 0;
    }

    /**
     * Loads a tag from the database, parses it, and builds the views.
     */
    final class LoadTagTask extends AsyncTask<Uri, Void, Cursor> {
        @Override
        public Cursor doInBackground(Uri... args) {
            Cursor cursor = getContentResolver().query(args[0], GetTagQuery.PROJECTION,
                    null, null, null);

            // Ensure the cursor loads its window.
            if (cursor != null) {
                cursor.getCount();
            }
            return cursor;
        }

        @Override
        public void onPostExecute(Cursor cursor) {
            NdefMessage msg = null;
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    msg = new NdefMessage(cursor.getBlob(GetTagQuery.COLUMN_BYTES));
                    if (msg != null) {
                        populateFromMessage(msg);
                    } else {
                        // TODO: do something more graceful.
                        finish();
                    }
                }
            } catch (FormatException e) {
                Log.e(LOG_TAG, "Unable to parse tag for editing.", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    private void resolveIntent() {
        Intent intent = getIntent();

        if (Intent.ACTION_SEND.equals(intent.getAction()) && !mParsedIntent) {
            if (buildFromSendIntent(intent)) {
                return;
            }

            mParsedIntent = true;
            return;
        }

        Uri uri = intent.getData();
        if (uri != null) {
            // Edit existing tag.
            new LoadTagTask().execute(uri);
        }
        // else, new tag - do nothing.
    }

    private void populateFromMessage(NdefMessage refMessage) {
        // Locally stored message.
        ParsedNdefMessage parsed = NdefMessageParser.parse(refMessage);
        List<ParsedNdefRecord> records = parsed.getRecords();

        // TODO: loosen this restriction. Just check the type of the first record.
        // There is always a "Text" record for a My Tag.
        if (records.size() < 1) {
            Log.w(LOG_TAG, "Message not in expected format");
            return;
        }
        mTextView.setText(((TextRecord) records.get(0)).getText());

        mRecords.clear();
        for (int i = 1, len = records.size(); i < len; i++) {
            RecordEditInfo editInfo = records.get(i).getEditInfo(this);
            if (editInfo != null) {
                addRecord(editInfo);
            }
        }
        rebuildChildViews();
    }

    /**
     * Populates the editor from extras in a given {@link Intent}
     * @param intent the {@link Intent} to parse.
     * @return whether or not the {@link Intent} could be handled.
     */
    private boolean buildFromSendIntent(final Intent intent) {
        String type = intent.getType();

        if ("text/plain".equals(type)) {
            String title = getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
            mTextView.setText((title == null) ? "" : title);

            String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            try {
                URL parsed = new URL(text);

                // Valid URL.
                mTextView.setText("");
                mRecords.add(new UriRecord.UriRecordEditInfo(text));
                rebuildChildViews();
                return true;

            } catch (MalformedURLException ex) {
                // Ignore. Just treat as plain text.
                mTextView.setText((text == null) ? "" : text);
            }

        } else if ("text/x-vcard".equals(type)) {
            Uri stream = (Uri) getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null) {
                RecordEditInfo editInfo = VCardRecord.editInfoForUri(stream);
                if (editInfo != null) {
                    mRecords.add(editInfo);
                    rebuildChildViews();
                    return true;
                }
            }
        }

        // TODO: handle images.

        return false;
    }

    /**
     * Saves the content of the tag.
     */
    private void saveAndFinish() {
        String text = mTextView.getText().toString();
        Locale locale = getResources().getConfiguration().locale;
        ArrayList<NdefRecord> values = Lists.newArrayList(
                TextRecord.newTextRecord(text, locale)
        );

        values.addAll(getValues());
        NdefMessage msg = new NdefMessage(values.toArray(new NdefRecord[values.size()]));

        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            // If opening directly from a different application via ACTION_SEND, save the tag and
            // open the MyTagList so they can enable it.
            TagService.saveMyMessages(this, new NdefMessage[] { msg });

            Intent openMyTags = new Intent(this, MyTagList.class);
            startActivity(openMyTags);
            finish();

        } else {
            Intent result = new Intent();
            result.putExtra(EXTRA_RESULT_MSG, msg);
            setResult(RESULT_OK, result);
            finish();
        }
    }

    @Override
    public void onClick(View target) {
        switch (target.getId()) {
            case R.id.add_content_target:
                showAddContentDialog();
                break;
            case R.id.save:
                saveAndFinish();
                break;
            case R.id.cancel:
                finish();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.help:
                HelpUtils.openHelp(this);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
