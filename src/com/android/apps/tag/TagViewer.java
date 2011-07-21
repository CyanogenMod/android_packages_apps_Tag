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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.List;

/**
 * An {@link Activity} which handles a broadcast of a new tag that the device just discovered.
 */
public class TagViewer extends Activity implements OnClickListener {
    static final String TAG = "TagViewer";

    LinearLayout mTagContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tag_viewer);

        mTagContent = (LinearLayout) findViewById(R.id.list);
        findViewById(R.id.button_done).setOnClickListener(this);

        resolveIntent(getIntent());
    }

    void resolveIntent(Intent intent) {
        // Parse the intent
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            // When a tag is discovered we send it to the service to be save. We
            // include a PendingIntent for the service to call back onto. This
            // will cause this activity to be restarted with onNewIntent(). At
            // that time we read it from the database and view it.
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs;
            if (rawMsgs != null && rawMsgs.length > 0) {
                // stupid java, need to cast one-by-one
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            } else {
                // Unknown tag type
                byte[] empty = new byte[] {};
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty);
                NdefMessage msg = new NdefMessage(new NdefRecord[] { record });
                msgs = new NdefMessage[] { msg };
            }
        } else {
            Log.e(TAG, "Unknown intent " + intent);
            finish();
            return;
        }
    }

    void buildTagViews(NdefMessage msg) {
        if (msg == null) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout content = mTagContent;

        // Clear out any old views in the content area, for example if you scan two tags in a row.
        content.removeAllViews();

        // Parse the first message in the list
        //TODO figure out what to do when/if we support multiple messages per tag
        ParsedNdefMessage parsedMsg = NdefMessageParser.parse(msg);

        // Build views for all of the sub records
        List<ParsedNdefRecord> records = parsedMsg.getRecords();
        final int size = records.size();

        for (int i = 0; i < size; i++) {
            ParsedNdefRecord record = records.get(i);
            content.addView(record.getView(this, inflater, content, i));
            inflater.inflate(R.layout.tag_divider, content, true);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }

    @Override
    public void onClick(View view) {
        finish();
    }
}
