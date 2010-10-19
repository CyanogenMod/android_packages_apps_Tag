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

import com.android.apps.tag.MockNdefMessages;
import com.google.common.collect.Iterables;

import android.nfc.NdefMessage;
import android.pim.vcard.VCardEntry;
import android.test.AndroidTestCase;

import java.util.List;

/**
 * Tests for {@link VCardRecord}.
 */
public class VCardRecordTest extends AndroidTestCase {
    public void testVCardMimeEntry() throws Exception {
        NdefMessage msg = new NdefMessage(MockNdefMessages.VCARD);
        VCardRecord record = VCardRecord.parse(msg.getRecords()[0]);
        VCardEntry entry = record.getEntry();
        assertEquals("Joe Google Employee", entry.getFullName());

        List<VCardEntry.EmailData> emails = entry.getEmailList();
        VCardEntry.EmailData email = Iterables.getOnlyElement(emails);
        assertEquals("support@google.com", email.data);

        List<VCardEntry.PhoneData> phones = entry.getPhoneList();
        VCardEntry.PhoneData phone = Iterables.getOnlyElement(phones);
        assertEquals("650-253-0000", phone.data);
    }
}
