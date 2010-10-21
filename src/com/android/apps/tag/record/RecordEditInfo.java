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

package com.android.apps.tag.record;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A simple holder for information required for editing a {@code ParsedNdefRecord}.
 */
public abstract class RecordEditInfo implements Parcelable {

    /**
     * The record type being edited.
     */
    private final String mType;

    public RecordEditInfo(String type) {
        mType = type;
    }

    protected RecordEditInfo(Parcel parcel) {
        mType = parcel.readString();
    }

    public String getType() {
        return mType;
    }

    /**
     * Returns the current value of the record in edit. Can be {@code null} if not fully inputted
     * by user, or the value is invalid for any reason.
     */
    public abstract ParsedNdefRecord getValue();

    /**
     * An {@link Intent} which can be fired to retrieve content for the {@code ParsedNdefRecord}.
     * Can be null, if no external {@link Activity} is required.
     */
    public abstract Intent getPickIntent();

    /**
     * Handles a pick {@link Intent}. Must be fully implemented if {@link #getPickIntent} returns
     * a non-null value.
     */
    public abstract void handlePickResult(Context context, Intent data);

    /**
     * Builds a {@link View} that can edit an underlying record, or launch a picker to change
     * the value of the record.
     */
    public abstract View getEditView(Activity activity, LayoutInflater inflater, ViewGroup parent);

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mType);
    }
}
