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
import com.android.apps.tag.provider.TagContract.NdefMessages;
import com.android.apps.tag.record.ParsedNdefRecord;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NdefTag;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;

/**
 * An {@link Activity} which handles a broadcast of a new tag that the device just discovered.
 */
public class TagViewer extends Activity implements OnClickListener {
    static final String TAG = "SaveTag";
    static final String EXTRA_TAG_DB_ID = "db_id";
    static final String EXTRA_MESSAGE = "msg";

    /** This activity will finish itself in this amount of time if the user doesn't do anything. */
    static final int ACTIVITY_TIMEOUT_MS = 5 * 1000;

    Uri mTagUri;
    ImageView mIcon;
    TextView mTitle;
    TextView mDate;
    CheckBox mStar;
    Button mDeleteButton;
    Button mDoneButton;
    NdefTag mTag = null;
    LinearLayout mTagContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        );

        setContentView(R.layout.tag_viewer);

        mTagContent = (LinearLayout) findViewById(R.id.list);
        mTitle = (TextView) findViewById(R.id.title);
        mDate = (TextView) findViewById(R.id.date);
        mIcon = (ImageView) findViewById(R.id.icon);
        mStar = (CheckBox) findViewById(R.id.star);
        mDeleteButton = (Button) findViewById(R.id.button_delete);
        mDoneButton = (Button) findViewById(R.id.button_done);

        mDeleteButton.setOnClickListener(this);
        mDoneButton.setOnClickListener(this);
        mStar.setOnClickListener(this);
        mIcon.setImageResource(R.drawable.ic_launcher_nfc);

        resolveIntent(getIntent());
    }

    @Override
    public void onRestart() {
        super.onRestart();
        if (mTagUri == null) {
            // Someone how the user was fast enough to navigate away from the activity
            // before the service was able to save the tag and call back onto this
            // activity with the pending intent. Since we don't know what do display here
            // just finish the activity.
            finish();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        PendingIntent pending = getPendingIntent();
        pending.cancel();
    }

    private PendingIntent getPendingIntent() {
        Intent callback = new Intent();
        callback.setClass(this, TagViewer.class);
        callback.setAction(Intent.ACTION_VIEW);
        callback.setFlags(Intent. FLAG_ACTIVITY_CLEAR_TOP);

        return PendingIntent.getActivity(this, 0, callback, PendingIntent.FLAG_CANCEL_CURRENT);
    }


    void resolveIntent(Intent intent) {
        // Parse the intent
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            // When a tag is discovered we send it to the service to be save. We
            // include a PendingIntent for the service to call back onto. This
            // will cause this activity to be restarted with onNewIntent(). At
            // that time we read it from the database and view it.
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs;
            if (rawMsgs != null) {
                // stupid java, need to cast one-by-one
                msgs = new NdefMessage[rawMsgs.length];
                for (int i=0; i<rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            } else {
                // Unknown tag type
                byte[] empty = new byte[] {};
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty);
                NdefMessage msg = new NdefMessage(new NdefRecord[] { record });
                msgs = new NdefMessage[] { msg };
            }
            TagService.saveMessages(this, msgs, false, getPendingIntent());

            // Setup the views
            setTitle(R.string.title_scanned_tag);
            mDate.setVisibility(View.GONE);
            mStar.setChecked(false);
            mStar.setEnabled(true);

            // Play notification.
            try {
                MediaPlayer player = new MediaPlayer();
                AssetFileDescriptor file = getResources().openRawResourceFd(
                        R.raw.discovered_tag_notification);
                player.setDataSource(
                        file.getFileDescriptor(),
                        file.getStartOffset(),
                        file.getLength());
                file.close();
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.prepare();
                player.start();
            } catch (IOException ex) {
                Log.w(TAG, "Sound creation failed for tag discovery");
            }

        } else if (Intent.ACTION_VIEW.equals(action)) {
            // Setup the views
            setTitle(R.string.title_existing_tag);
            mStar.setVisibility(View.VISIBLE);
            mStar.setEnabled(false); // it's reenabled when the async load completes
            mDate.setVisibility(View.VISIBLE);

            // Read the tag from the database asynchronously
            mTagUri = intent.getData();
            new LoadTagTask().execute(mTagUri);
        } else {
            Log.e(TAG, "Unknown intent " + intent);
            finish();
            return;
        }
    }

    void buildTagViews(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout content = mTagContent;

        // Clear out any old views in the content area, for example if you scan two tags in a row.
        content.removeAllViews();

        // Parse the first message in the list
        //TODO figure out what to do when/if we support multiple messages per tag
        ParsedNdefMessage parsedMsg = NdefMessageParser.parse(msgs[0]);

        // Build views for all of the sub records
        List<ParsedNdefRecord> records = parsedMsg.getRecords();
        final int size = records.size();

        for (int i = 0 ; i < size ; i++) {
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
    public void setTitle(CharSequence title) {
        mTitle.setText(title);
    }

    @Override
    public void onClick(View view) {
        if (view == mDeleteButton) {
            if (mTagUri == null) {
                // The tag hasn't been saved yet, so indicate it shouldn't be saved
                mTag = null;
                finish();
            } else {
                // The tag came from the database, start a service to delete it
                TagService.delete(this, mTagUri);
                finish();
            }
        } else if (view == mDoneButton) {
            finish();
        } else if (view == mStar) {
            if (mTagUri != null) {
                TagService.setStar(this, mTagUri, mStar.isChecked());
            }
        }
    }

    interface ViewTagQuery {
        final static String[] PROJECTION = new String[] {
                NdefMessages.BYTES, // 0
                NdefMessages.STARRED, // 1
                NdefMessages.DATE, // 2
        };

        static final int COLUMN_BYTES = 0;
        static final int COLUMN_STARRED = 1;
        static final int COLUMN_DATE = 2;
    }

    /**
     * Loads a tag from the database, parses it, and builds the views
     */
    final class LoadTagTask extends AsyncTask<Uri, Void, Cursor> {
        @Override
        public Cursor doInBackground(Uri... args) {
            Cursor cursor = getContentResolver().query(args[0], ViewTagQuery.PROJECTION,
                    null, null, null);

            // Ensure the cursor loads its window
            if (cursor != null) cursor.getCount();
            return cursor;
        }

        @Override
        public void onPostExecute(Cursor cursor) {
            NdefMessage msg = null;
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    msg = new NdefMessage(cursor.getBlob(ViewTagQuery.COLUMN_BYTES));
                    if (msg != null) {
                        mDate.setText(DateUtils.getRelativeTimeSpanString(TagViewer.this,
                                cursor.getLong(ViewTagQuery.COLUMN_DATE)));
                        mStar.setChecked(cursor.getInt(ViewTagQuery.COLUMN_STARRED) != 0);
                        mStar.setEnabled(true);
                        buildTagViews(new NdefMessage[] { msg });
                    }
                }
            } catch (FormatException e) {
                Log.e(TAG, "invalid tag format", e);
            } finally {
                if (cursor != null) cursor.close();
            }
        }
    }
}
