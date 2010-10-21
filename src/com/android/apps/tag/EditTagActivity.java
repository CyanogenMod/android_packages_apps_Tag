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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Set;

/**
 * A base {@link Activity} class for an editor of an {@link NdefMessage} tag.
 *
 * The core of the editing is done by various child {@link View}s that differ based on
 * {@link ParsedNdefRecord} types. Each type of {@link ParsedNdefRecord} can build views to
 * pick/select a new piece of content, or edit an existing content for the {@link NdefMessage}.
 */
public abstract class EditTagActivity extends Activity {

    private static final String BUNDLE_KEY_OUTSTANDING_PICK = "outstanding-pick";
    protected static final int DIALOG_ID_SELECT_CONTENT = 0;

    private static final Set<String> SUPPORTED_RECORD_TYPES = ImmutableSet.of(
        ImageRecord.RECORD_TYPE
    );

    /**
     * Records contained in the current message being edited.
     */
    private final ArrayList<ParsedNdefRecord> mRecords = Lists.newArrayList();

    /**
     * The container where the subviews for each record are housed.
     */
    private ViewGroup mContentRoot;

    /**
     * Info about an outstanding picking activity to add a new record.
     */
    private RecordEditInfo mRecordWithOutstandingPick;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            mRecordWithOutstandingPick = savedState.getParcelable(BUNDLE_KEY_OUTSTANDING_PICK);
        }
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
            return ImageRecord.getAddView(this, LayoutInflater.from(this), parent);
        }
        throw new IllegalArgumentException("Not a supported view type");
    }

    /**
     * Builds a {@link View} used as an item in a list when editing content for a tag.
     */
    public void addRecord(ParsedNdefRecord record) {
        mRecords.add(Preconditions.checkNotNull(record));
        getContentRoot().addView(record.getView(this, LayoutInflater.from(this), mContentRoot));
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (id == DIALOG_ID_SELECT_CONTENT) {
            return new TagContentSelector(this);
        }
        return super.onCreateDialog(id, args);
    }

    /**
     * Displays a {@link Dialog} to select a new content type to add to the Tag.
     */
    protected void showSelectContentDialog() {
        showDialog(DIALOG_ID_SELECT_CONTENT);
    }

    /**
     * Handles a click to select and add a new content type.
     */
    public void onAddContentClick(View target) {
        RecordEditInfo info = (RecordEditInfo) target.getTag();
        Intent pickIntent = info.getPickIntent();
        if (pickIntent != null) {
            mRecordWithOutstandingPick = info;
            startActivityForResult(pickIntent, 0);
        }

        // TODO: handle content types that don't require external activities to pick content.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((resultCode != RESULT_OK) || (data == null) || (mRecordWithOutstandingPick == null)) {
            return;
        }
        // Handles results from another Activity that picked content to write to a tag.
        RecordEditInfo recordInfo = mRecordWithOutstandingPick;
        ParsedNdefRecord record = recordInfo.handlePickResult(this, data);
        if (record != null) {
            addRecord(record);
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
}
