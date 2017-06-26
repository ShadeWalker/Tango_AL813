package com.mediatek.contacts.group;

import java.util.HashMap;
import java.util.List;

import com.android.contacts.group.SuggestedMemberListAdapter.SuggestedMember;
import com.mediatek.contacts.simcontact.SimCardUtils;

import android.database.Cursor;

public class SuggestedMemberUtils {

    public static void setSimInfo(Cursor cursor, SuggestedMember member,
            int simIdColumnIndex, int sdnColumnIndex) {
        int simId = cursor.getInt(simIdColumnIndex);
        member.setSimId(simId);
        if (simId > 0) {
            member.setSimType(SimCardUtils.getSimTypeBySubId(simId));
        }
        member.setIsSdnContact(cursor.getInt(sdnColumnIndex));
    }

    /**
     * Bug fix ALPS00280807, process the joint contacts.
     */
    public static void processJointContacts(SuggestedMember member, long rawContactId,
            HashMap<Long, SuggestedMember> jointContactsMap, List<SuggestedMember> suggestionsList) {
        if (member.getRawContactId() < 0) {
            member.setRawContactId(rawContactId);
        } else {
            if (member.getRawContactId() != rawContactId) {
                SuggestedMember tempMember = jointContactsMap.get(rawContactId);
                if (tempMember == null) {
                    tempMember = new SuggestedMember(member);
                    tempMember.setRawContactId(rawContactId);
                    int index = suggestionsList.indexOf(member);
                    if (index >= 0 && index <= suggestionsList.size()) {
                        suggestionsList.add(index, tempMember);
                    } else {
                        suggestionsList.add(tempMember);
                    }
                    jointContactsMap.put(rawContactId, tempMember);
                }
                member = tempMember;
            }
        }
    }

    /**
     * Bug fix ALPS00280807, set suggested member's fix extras info.
     */
    public static void setFixExtrasInfo(SuggestedMember member, int dataColumnIndex,
            Cursor memberDataCursor, String searchFilter) {
        String info = memberDataCursor.getString(dataColumnIndex);
        if (!member.hasExtraInfo()) {
            if (info.indexOf(searchFilter) > -1) {
                member.setFixExtrasInfo(true);
            }
        } else {
            if (!member.hasFixedExtrasInfo()) {
                if (info.indexOf(searchFilter) > -1) {
                    member.setExtraInfo(info);
                    member.setFixExtrasInfo(true);
                }
            }
        }
    }
}
