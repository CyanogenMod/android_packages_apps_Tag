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

import com.android.apps.tag.record.RecordEditInfo;
import com.android.apps.tag.record.UriRecord;
import com.android.apps.tag.record.VCardRecord;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.nfc.NdefRecord;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Set;

/**
 * A {@link Dialog} that presents options to select data which can be written into a
 * {@link NdefRecord} for an NFC tag.
 */
public class TagContentSelector extends AlertDialog
        implements DialogInterface.OnClickListener, android.view.View.OnClickListener {

    private final ViewGroup mListRoot;
    private final LayoutInflater mInflater;
    private final SelectContentCallbacks mCallbacks;

    public interface SelectContentCallbacks {
        /**
         * Determines which data types should be displayed in this selector.
         * Keys correspond to types in {@link RecordEditInfo}.
         */
        Set<String> getSupportedTypes();

        /**
         * Handle a selection of new data for an {@link NdefRecord}.
         */
        void onSelectContent(RecordEditInfo info);
    }

    public TagContentSelector(Activity activity, SelectContentCallbacks callbacks) {
        super(activity);
        mCallbacks = callbacks;

        setTitle(activity.getResources().getString(R.string.select_type));

        mInflater = LayoutInflater.from(activity);
        ViewGroup root = (ViewGroup) mInflater.inflate(R.layout.tag_content_selector, null);
        mListRoot = (ViewGroup) root.findViewById(R.id.list);

        rebuildViews();

        setView(root);
        setIcon(0);
        setButton(
                DialogInterface.BUTTON_POSITIVE,
                activity.getString(android.R.string.cancel),
                this);
    }

    /**
     * Builds a {@link View} used as an item in a list when picking a new piece of content to add
     * to the tag.
     */
    public View getAddView(ViewGroup parent, String type) {
        if (UriRecord.RECORD_TYPE.equals(type)) {
            return UriRecord.getAddView(getContext(), mInflater, parent);
        } else if (VCardRecord.RECORD_TYPE.equals(type)) {
            return VCardRecord.getAddView(getContext(), mInflater, parent);
        }
        throw new IllegalArgumentException("Not a supported view type");
    }


    public void rebuildViews() {
        mListRoot.removeAllViews();
        for (String type : mCallbacks.getSupportedTypes()) {
            View selectItemView = getAddView(mListRoot, type);
            if (selectItemView != null) {
                selectItemView.setOnClickListener(this);
                mListRoot.addView(selectItemView);
            }
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        dismiss();
    }

    @Override
    public void onClick(View target) {
        Object tag = target.getTag();
        if ((tag == null) || !(tag instanceof RecordEditInfo)) {
            return;
        }
        mCallbacks.onSelectContent((RecordEditInfo) tag);
        dismiss();
    }
}
