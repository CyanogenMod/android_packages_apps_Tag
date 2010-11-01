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

import com.android.apps.tag.message.NdefMessageParser;
import com.android.apps.tag.message.ParsedNdefMessage;
import com.android.apps.tag.record.ParsedNdefRecord;
import com.android.apps.tag.record.RecordEditInfo;
import com.android.apps.tag.record.TextRecord;
import com.android.apps.tag.record.UriRecord;
import com.google.common.collect.Lists;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor {@link Activity} for the tag that can be programmed into the device.
 */
public class MyTagActivity extends EditTagActivity implements OnClickListener {

    private static final String LOG_TAG = "TagEditor";

    private EditText mTextView;
    private CheckBox mEnabled;

    /**
     * Whether or not data was already parsed from an {@link Intent}. This happens when the user
     * shares data via the My tag feature.
     */
    private boolean mParsedIntent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.my_tag_activity);

        findViewById(R.id.toggle_enabled_target).setOnClickListener(this);
        findViewById(R.id.add_content_target).setOnClickListener(this);

        mTextView = (EditText) findViewById(R.id.input_tag_text);
        mEnabled = (CheckBox) findViewById(R.id.toggle_enabled_checkbox);

        populateEditor();
    }

    private void populateEditor() {
        NdefMessage localMessage = NfcAdapter.getDefaultAdapter().getLocalNdefMessage();

        if (Intent.ACTION_SEND.equals(getIntent().getAction()) && !mParsedIntent) {
            if (localMessage != null) {
                // TODO: prompt user for confirmation about wiping their old tag.
            }

            if (buildFromIntent(getIntent())) {
                return;
            }

            mParsedIntent = true;

        } else if (localMessage == null) {
            mEnabled.setChecked(false);
            return;

        } else {
            // Locally stored message.
            ParsedNdefMessage parsed = NdefMessageParser.parse(localMessage);
            List<ParsedNdefRecord> records = parsed.getRecords();

            // There is always a "Text" record for a My Tag.
            if (records.size() < 1) {
                Log.w(LOG_TAG, "Local record not in expected format");
                return;
            }
            mEnabled.setChecked(true);
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
    }

    /**
     * Populates the editor from extras in a given {@link Intent}
     * @param intent the {@link Intent} to parse.
     * @return whether or not the {@link Intent} could be handled.
     */
    private boolean buildFromIntent(final Intent intent) {
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

            } catch (MalformedURLException ex) {
                // Ignore. Just treat as plain text.
                mTextView.setText((text == null) ? "" : text);
            }

            mEnabled.setChecked(true);
            onSave();
            return true;
        }
        // TODO: handle vcards and images.
        return false;
    }

    /**
     * Persists content to store.
     */
    private void onSave() {
        String text = mTextView.getText().toString();
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter();

        if (!mEnabled.isChecked()) {
            nfc.setLocalNdefMessage(null);
            return;
        }

        Locale locale = getResources().getConfiguration().locale;
        ArrayList<NdefRecord> values = Lists.newArrayList(
                TextRecord.newTextRecord(text, locale)
        );

        values.addAll(getValues());

        Log.d(LOG_TAG, "Writing local NdefMessage from tag app....");
        nfc.setLocalNdefMessage(new NdefMessage(values.toArray(new NdefRecord[values.size()])));
    }

    @Override
    public void onPause() {
        super.onPause();
        onSave();
    }

    @Override
    public void onClick(View target) {
        switch (target.getId()) {
            case R.id.toggle_enabled_target:
                boolean enabled = !mEnabled.isChecked();
                mEnabled.setChecked(enabled);

                // TODO: Persist to some store.
                if (enabled) {
                    onSave();
                } else {
                    NfcAdapter.getDefaultAdapter().setLocalNdefMessage(null);
                }
                break;

            case R.id.add_content_target:
                showAddContentDialog();
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
