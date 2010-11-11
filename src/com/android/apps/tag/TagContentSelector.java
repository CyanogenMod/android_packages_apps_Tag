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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.nfc.NdefRecord;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A {@link Dialog} that presents options to select data which can be written into a
 * {@link NdefRecord} for an NFC tag.
 */
public class TagContentSelector extends AlertDialog
        implements DialogInterface.OnClickListener, android.view.View.OnClickListener {

    private final EditTagActivity mActivity;
    private final ViewGroup mListRoot;

    public TagContentSelector(EditTagActivity activity) {
        super(activity);
        mActivity = activity;

        setTitle(activity.getResources().getString(R.string.select_type));

        LayoutInflater inflater = LayoutInflater.from(activity);
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.tag_content_selector, null);
        mListRoot = (ViewGroup) root.findViewById(R.id.list);

        rebuildViews();

        setView(root);
        setIcon(0);
        setButton(
                DialogInterface.BUTTON_POSITIVE,
                activity.getString(android.R.string.cancel),
                this);
    }

    public void rebuildViews() {
        mListRoot.removeAllViews();
        for (String type : mActivity.getSupportedTypes()) {
            View selectItemView = mActivity.getAddView(mListRoot, type);
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
        mActivity.onAddContentClick(target);
        dismiss();
    }
}
