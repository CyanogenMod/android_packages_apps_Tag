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

import com.android.apps.tag.R;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import android.app.Activity;
import android.nfc.NdefRecord;
import android.pim.vcard.VCardConfig;
import android.pim.vcard.VCardEntry;
import android.pim.vcard.VCardEntryConstructor;
import android.pim.vcard.VCardEntryHandler;
import android.pim.vcard.VCardParser;
import android.pim.vcard.VCardParser_V30;
import android.pim.vcard.exception.VCardException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * VCard Ndef Record object
 */
public class VCardRecord implements ParsedNdefRecord {

    private final VCardEntry mEntry;

    private VCardRecord(VCardEntry entry) {
        this.mEntry = Preconditions.checkNotNull(entry);
    }

    @VisibleForTesting
    public VCardEntry getEntry() {
        return mEntry;
    }

    @Override
    public View getView(Activity activity, LayoutInflater inflater, ViewGroup parent) {
        TextView text = (TextView) inflater.inflate(R.layout.tag_text, parent, false);
        text.setText(mEntry.getDisplayName());
        return text;
    }

    public static VCardRecord parse(NdefRecord record) {
        MimeRecord underlyingRecord = MimeRecord.parse(record);

        // TODO: Add support for other vcard mime types.
        Preconditions.checkArgument("text/x-vCard".equals(underlyingRecord.getMimeType()));

        try {
            byte[] vcard = underlyingRecord.getContent();
            final InputStream is = new ByteArrayInputStream(vcard);

            // Assume vCard version 3.0 with UTF-8.
            final int vCardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
            final VCardEntryConstructor constructor = new VCardEntryConstructor(vCardType);
            CustomHandler handler = new CustomHandler();
            constructor.addEntryHandler(handler);
            final VCardParser parser = new VCardParser_V30(vCardType);
            parser.parse(is, constructor);
            if (handler.mEntry == null) {
                throw new IllegalArgumentException("no vcard entry found");
            }

            return new VCardRecord(handler.mEntry);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } catch (VCardException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static class CustomHandler implements VCardEntryHandler {
        private VCardEntry mEntry = null;
        @Override public void onEnd() { }

        @Override public void onStart() { }

        @Override
        public void onEntryCreated(VCardEntry entry) {
            Preconditions.checkState(mEntry == null);
            mEntry = Preconditions.checkNotNull(entry);
        }
    }

    public static boolean isVCard(NdefRecord record) {
        try {
            parse(record);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
