// Copyright 2010 Google Inc. All Rights Reserved.

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

    public TagContentSelector(EditTagActivity activity) {
        super(activity);
        mActivity = activity;

        setTitle(activity.getResources().getString(R.string.select_type));

        LayoutInflater inflater = LayoutInflater.from(activity);
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.tag_content_selector, null);
        ViewGroup list = (ViewGroup) root.findViewById(R.id.list);

        for (String type : mActivity.getSupportedTypes()) {
            View selectItemView = mActivity.getAddView(list, type);
            if (selectItemView != null) {
                selectItemView.setOnClickListener(this);
                list.addView(selectItemView);
            }
        }

        setView(root);
        setIcon(0);
        setButton(
                DialogInterface.BUTTON_POSITIVE,
                activity.getString(android.R.string.cancel),
                this);
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