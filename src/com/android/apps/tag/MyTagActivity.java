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
import com.android.apps.tag.record.TextRecord;
import com.google.common.collect.Lists;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor {@link Activity} for the tag that can be programmed into the device.
 */
public class MyTagActivity extends EditTagActivity implements OnClickListener {

    private static final String LOG_TAG = "TagEditor";

    private EditText mTitleView;
    private EditText mTextView;
    private CheckBox mEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.my_tag_activity);

        findViewById(R.id.toggle_enabled_target).setOnClickListener(this);
        findViewById(R.id.add_content_target).setOnClickListener(this);

        mTitleView = (EditText) findViewById(R.id.input_tag_title);
        mTextView = (EditText) findViewById(R.id.input_tag_text);
        mEnabled = (CheckBox) findViewById(R.id.toggle_enabled_checkbox);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Populate tag editor from stored tag info.
        NdefMessage localMessage = NfcAdapter.getDefaultAdapter().getLocalNdefMessage();
        if (localMessage == null) {
            mEnabled.setChecked(false);
            return;
        }

        mEnabled.setChecked(true);
        ParsedNdefMessage parsed = NdefMessageParser.parse(localMessage);

        // There is always a "Title" and a "Text" record for the local message, if it exists.
        List<ParsedNdefRecord> records = parsed.getRecords();
        if (records.size() < 2) {
            Log.w(LOG_TAG, "Local record not in expected format");
            return;
        }
        TextRecord titleRecord = (TextRecord) records.get(0);
        TextRecord textRecord = (TextRecord) records.get(1);

        mTitleView.setText(titleRecord.getText());
        mTextView.setText(textRecord.getText());
    }

    /**
     * Persists content to store.
     */
    private void onSave() {
        String title = mTitleView.getText().toString();
        String text = mTextView.getText().toString();
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter();

        if ((title.isEmpty() && text.isEmpty()) || !mEnabled.isChecked()) {
            nfc.setLocalNdefMessage(null);
            return;
        }

        Locale locale = getResources().getConfiguration().locale;
        ArrayList<NdefRecord> values = Lists.newArrayList(
                TextRecord.newTextRecord(title, locale),
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

}
