/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.model;

import java.util.HashSet;

/**
 * Class used to  huawei
 */
public class SameNameList {

	private HashSet<Long> mContactIds;
	private String mDisplayName;

	public SameNameList(String name) {
		mDisplayName = name;
		mContactIds = new HashSet<Long>();
	}

	public String getDisplayName() {
		return mDisplayName;
	}

	public void addContact(long id) {
		mContactIds.add(id);
	}

	public boolean isValid() {
		return mContactIds.size() > 1;
	}

	public HashSet<Long> getContactIds() {
		return mContactIds;
	}

	@Override
	public boolean equals(Object o) {
        // / Added by guofeiyao
        if ( o instanceof SameNameList ) {
        // / End
	
		SameNameList nameList = (SameNameList) o;
		if (!mDisplayName.equals(nameList.getDisplayName()))
			return false;
		if (!mContactIds.equals(getContactIds()))
			return false;
		return true;

		// / Added by guofeiyao
        }
		return false;
		// / End
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (long id : mContactIds) {
			sb.append(id + ", ");
		}
		return mDisplayName + ": " + sb.toString();
	}
}