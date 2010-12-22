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

package com.android.apps.tag;

import com.android.apps.tag.provider.TagContract.NdefMessages;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Displays the list of tags that can be set as "My tag", and allows the user to select the
 * active tag that the device shares.
 */
public class MyTagList extends Activity implements OnItemClickListener, View.OnClickListener {

    static final String TAG = "TagList";

    private static final int REQUEST_EDIT = 0;
    private static final int DIALOG_ID_SELECT_ACTIVE_TAG = 0;

    private static final String BUNDLE_KEY_TAG_ID_IN_EDIT = "tag-edit";
    private static final String PREF_KEY_ACTIVE_TAG = "active-my-tag";
    static final String PREF_KEY_TAG_TO_WRITE = "tag-to-write";

    private View mSelectActiveTagAnchor;
    private View mActiveTagDetails;
    private CheckBox mEnabled;
    private ListView mList;

    private TagAdapter mAdapter;
    private long mActiveTagId;
    private NdefMessage mActiveTag;
    private boolean mInitialLoadComplete = false;

    private WeakReference<SelectActiveTagDialog> mSelectActiveTagDialog;
    private long mTagIdInEdit = -1;

    private boolean mWriteSupport = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.my_tag_activity);

        if (savedInstanceState != null) {
            mTagIdInEdit = savedInstanceState.getLong(BUNDLE_KEY_TAG_ID_IN_EDIT, -1);
        }

        // Set up the check box to toggle My tag sharing.
        mEnabled = (CheckBox) findViewById(R.id.toggle_enabled_checkbox);
        mEnabled.setChecked(false);  // Set after initial data load completes.
        findViewById(R.id.toggle_enabled_target).setOnClickListener(this);

        // Setup the active tag selector.
        mActiveTagDetails = findViewById(R.id.active_tag_details);
        mSelectActiveTagAnchor = findViewById(R.id.choose_my_tag);
        findViewById(R.id.active_tag).setOnClickListener(this);
        updateActiveTagView(null);  // Filled in after initial data load.

        mActiveTagId = getPreferences(Context.MODE_PRIVATE).getLong(PREF_KEY_ACTIVE_TAG, -1);

        // Setup the list
        mAdapter = new TagAdapter(this);
        mList = (ListView) findViewById(android.R.id.list);
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener(this);
        findViewById(R.id.add_tag).setOnClickListener(this);

        // Kick off an async task to load the tags.
        new TagLoaderTask().execute((Void[]) null);

        // If we're not on a user build offer a back door for writing tags.
        // The UX is horrible so we don't want to ship it but need it for testing.
        if (!Build.TYPE.equalsIgnoreCase("user")) {
            mWriteSupport = true;
            registerForContextMenu(mList);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mTagIdInEdit = -1;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_TAG_ID_IN_EDIT, mTagIdInEdit);
    }

    @Override
    protected void onDestroy() {
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        super.onDestroy();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (mWriteSupport) {
            menu.add(0, 1, 0, "Write to next tag scanned");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        SharedPreferences prefs = getSharedPreferences("tags.pref", Context.MODE_PRIVATE);
        prefs.edit().putLong(PREF_KEY_TAG_TO_WRITE, info.id).apply();
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // TODO: use implicit Intent?
        Intent intent = new Intent(this, EditTagActivity.class);
        intent.setData(ContentUris.withAppendedId(NdefMessages.CONTENT_URI, id));
        mTagIdInEdit = id;
        startActivityForResult(intent, REQUEST_EDIT);
    }

    public void setEmptyView() {
        // TODO: set empty view.
    }

    public interface TagQuery {
        static final String[] PROJECTION = new String[] {
                NdefMessages._ID, // 0
                NdefMessages.DATE, // 1
                NdefMessages.TITLE, // 2
                NdefMessages.BYTES, // 3
        };

        static final int COLUMN_ID = 0;
        static final int COLUMN_DATE = 1;
        static final int COLUMN_TITLE = 2;
        static final int COLUMN_BYTES = 3;
    }

    /**
     * Asynchronously loads the tags info from the database.
     */
    final class TagLoaderTask extends AsyncTask<Void, Void, Cursor> {
        @Override
        public Cursor doInBackground(Void... args) {
            // Don't setup the empty view until after the first load
            // so the empty text doesn't flash when first loading the
            // activity.
            mList.setEmptyView(null);
            Cursor cursor = getContentResolver().query(
                    NdefMessages.CONTENT_URI,
                    TagQuery.PROJECTION,
                    NdefMessages.IS_MY_TAG + "=1",
                    null, NdefMessages.DATE + " DESC");

            // Ensure the cursor executes and fills its window
            if (cursor != null) cursor.getCount();
            return cursor;
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            boolean firstLoad = !mInitialLoadComplete;
            if (!mInitialLoadComplete) {
                mInitialLoadComplete = true;
            }

            if (cursor == null || cursor.getCount() == 0) {
                setEmptyView();
            } else if (mActiveTagId != -1) {
                mAdapter.changeCursor(cursor);

                // Find the active tag.
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    if (mActiveTagId == cursor.getLong(TagQuery.COLUMN_ID)) {
                        selectActiveTag(cursor.getPosition());

                        // If there was an existing shared tag, we update the contents, since
                        // the active tag contents may have been changed. This also forces the
                        // active tag to be in sync with what the NfcAdapter.
                        if (NfcAdapter.getDefaultAdapter().getLocalNdefMessage() != null) {
                            enableSharing();
                        }
                        break;
                    }
                }
            }


            SelectActiveTagDialog dialog = (mSelectActiveTagDialog == null)
                    ? null : mSelectActiveTagDialog.get();
            if (dialog != null) {
                dialog.setData(cursor);
            }
        }
    }

    /**
     * Struct to hold pointers to views in the list items to save time at view binding time.
     */
    static final class ViewHolder {
        public CharArrayBuffer titleBuffer;
        public TextView mainLine;
    }

    /**
     * Adapter to display the the My tag entries.
     */
    public class TagAdapter extends CursorAdapter {
        private final LayoutInflater mInflater;

        public TagAdapter(Context context) {
            super(context, null, false);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder holder = (ViewHolder) view.getTag();

            CharArrayBuffer buf = holder.titleBuffer;
            cursor.copyStringToBuffer(TagQuery.COLUMN_TITLE, buf);
            holder.mainLine.setText(buf.data, 0, buf.sizeCopied);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mInflater.inflate(R.layout.tag_list_item, null);

            // Cache items for the view
            ViewHolder holder = new ViewHolder();
            holder.titleBuffer = new CharArrayBuffer(64);
            holder.mainLine = (TextView) view.findViewById(R.id.title);
            view.findViewById(R.id.date).setVisibility(View.GONE);
            view.setTag(holder);

            return view;
        }

        @Override
        public void onContentChanged() {
            // Kick off an async query to refresh the list
            new TagLoaderTask().execute((Void[]) null);
        }
    }

    @Override
    public void onClick(View target) {
        switch (target.getId()) {
            case R.id.toggle_enabled_target:
                boolean enabled = !mEnabled.isChecked();
                if (enabled) {
                    if (mActiveTag != null) {
                        enableSharing();
                        return;
                    }
                    // TODO: just disable the checkbox when no tag is set
                    Toast.makeText(
                            this,
                            "You must select a tag to share first.",
                            Toast.LENGTH_SHORT).show();
                }

                disableSharing();
                break;

            case R.id.add_tag:
                // TODO: use implicit intents.
                Intent intent = new Intent(this, EditTagActivity.class);
                startActivityForResult(intent, REQUEST_EDIT);
                break;

            case R.id.active_tag:
                showDialog(DIALOG_ID_SELECT_ACTIVE_TAG);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK) {
            NdefMessage msg = (NdefMessage) Preconditions.checkNotNull(
                    data.getParcelableExtra(EditTagActivity.EXTRA_RESULT_MSG));

            if (mTagIdInEdit != -1) {
                TagService.updateMyMessage(this, mTagIdInEdit, msg);
            } else {
                TagService.saveMyMessages(this, new NdefMessage[] { msg });
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (id == DIALOG_ID_SELECT_ACTIVE_TAG) {
            mSelectActiveTagDialog = new WeakReference<SelectActiveTagDialog>(
                    new SelectActiveTagDialog(this, mAdapter.getCursor()));
            return mSelectActiveTagDialog.get();
        }
        return super.onCreateDialog(id, args);
    }

    /**
     * Selects the tag to be used as the "My tag" shared tag.
     *
     * This does not necessarily persist the selection to the {@code NfcAdapter}. That must be done
     * via {@link #enableSharing}. However, it will call {@link #disableSharing} if the tag
     * is invalid.
     */
    private void selectActiveTag(int position) {
        Cursor cursor = mAdapter.getCursor();
        if (cursor != null && cursor.moveToPosition(position)) {
            mActiveTagId = cursor.getLong(TagQuery.COLUMN_ID);

            try {
                mActiveTag = new NdefMessage(cursor.getBlob(TagQuery.COLUMN_BYTES));

                // Persist active tag info to preferences.
                getPreferences(Context.MODE_PRIVATE)
                        .edit()
                        .putLong(PREF_KEY_ACTIVE_TAG, mActiveTagId)
                        .apply();

                // Notify NFC adapter of the My tag contents.
                updateActiveTagView(cursor.getString(TagQuery.COLUMN_TITLE));

            } catch (FormatException e) {
                // TODO: handle.
                disableSharing();
            }
        } else {
            updateActiveTagView(null);
            disableSharing();
        }
    }

    private void enableSharing() {
        mEnabled.setChecked(true);
        NfcAdapter.getDefaultAdapter().setLocalNdefMessage(Preconditions.checkNotNull(mActiveTag));
    }

    private void disableSharing() {
        mEnabled.setChecked(false);
        NfcAdapter.getDefaultAdapter().setLocalNdefMessage(null);
    }

    private void updateActiveTagView(String title) {
        if (title == null) {
            mActiveTagDetails.setVisibility(View.GONE);
            mSelectActiveTagAnchor.setVisibility(View.VISIBLE);
        } else {
            mActiveTagDetails.setVisibility(View.VISIBLE);
            ((TextView) mActiveTagDetails.findViewById(R.id.active_tag_title)).setText(title);
            mSelectActiveTagAnchor.setVisibility(View.GONE);
        }
    }

    class SelectActiveTagDialog extends AlertDialog
            implements DialogInterface.OnClickListener, OnItemClickListener {

        private final ArrayList<HashMap<String, String>> mData;
        private final SimpleAdapter mSelectAdapter;

        protected SelectActiveTagDialog(Context context, Cursor cursor) {
            super(context);

            setTitle(context.getResources().getString(R.string.choose_my_tag));
            LayoutInflater inflater = LayoutInflater.from(context);
            ListView list = new ListView(MyTagList.this);

            mData = Lists.newArrayList();
            mSelectAdapter = new SimpleAdapter(
                    context,
                    mData,
                    android.R.layout.simple_list_item_1,
                    new String[] { "title" },
                    new int[] { android.R.id.text1 });

            list.setAdapter(mSelectAdapter);
            list.setOnItemClickListener(this);
            setView(list);
            setIcon(0);
            setButton(
                    DialogInterface.BUTTON_POSITIVE,
                    context.getString(android.R.string.cancel),
                    this);

            setData(cursor);
        }

        public void setData(final Cursor cursor) {
            if ((cursor == null) || (cursor.getCount() == 0)) {
                cancel();
                return;
            }
            mData.clear();

            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                mData.add(new HashMap<String, String>() {{
                    put("title", cursor.getString(MyTagList.TagQuery.COLUMN_TITLE));
                }});
            }

            mSelectAdapter.notifyDataSetChanged();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            cancel();
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectActiveTag(position);
            enableSharing();
            cancel();
        }
    }
}
