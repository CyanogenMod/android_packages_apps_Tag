// Copyright 2010 Google Inc. All Rights Reserved.

package com.android.apps.tag.record;

import android.app.Activity;
import android.content.Intent;

/**
 * A simple holder for information required for editing a {@code ParsedNdefRecord}.
 */
public abstract class RecordEditInfo {
    /**
     * The record type being edited.
     */
    private final String mType;

    /**
     * The current value being edited.
     * Can be null if not yet set.
     */
    protected ParsedNdefRecord mRecord;

    public RecordEditInfo(String type) {
        mType = type;
    }

    public String getType() {
        return mType;
    }

    /**
     * An {@link Intent} which can be fired to retrieve content for the {@code ParsedNdefRecord}.
     * Can be null, if no external {@link Activity} is required.
     */
    public abstract Intent getPickIntent();

    /**
     * Handles a pick {@link Intent}. Must be fully implemented if {@link #getPickIntent} returns
     * a non-null value.
     */
    public abstract ParsedNdefRecord handlePickResult(Intent data);
}
