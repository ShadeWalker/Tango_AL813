/*
* This Software is the property of VIA Telecom, Inc. and may only be used pursuant to a 
license from VIA Telecom, Inc.
* Any unauthorized use inconsistent with the terms of such license is strictly prohibited.
* Copyright (c) 2013 -2015 VIA Telecom, Inc. All rights reserved.
*/

/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony.cdma.utk;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;

import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;

import java.util.Iterator;
import java.util.List;

/**
 * Factory class, used for decoding raw byte arrays, received from baseband,
 * into a CommandParams object.
 *
 */
class CommandParamsFactory extends Handler {
    private static CommandParamsFactory sInstance = null;
    private IconLoader mIconLoader;
    private CommandParams mCmdParams = null;
    private int mIconLoadState = LOAD_NO_ICON;
    private RilMessageDecoder mCaller = null;
    private Context mContext;

    // constants
    static final int MSG_ID_LOAD_ICON_DONE = 1;

    // loading icons state parameters.
    static final int LOAD_NO_ICON           = 0;
    static final int LOAD_SINGLE_ICON       = 1;
    static final int LOAD_MULTI_ICONS       = 2;

    // Command Qualifier values for refresh command
    static final int REFRESH_NAA_INIT_AND_FULL_FILE_CHANGE  = 0x00;
    static final int REFRESH_NAA_FILE_CHANGE                = 0x01;
    static final int REFRESH_NAA_INIT_AND_FILE_CHANGE       = 0x02;
    static final int REFRESH_NAA_INIT                       = 0x03;
    static final int REFRESH_UICC_RESET                     = 0x04;

    static final int UIM_INPUT_MAX_UNICODE_LEN = (255 - 16)/2;
    static synchronized CommandParamsFactory getInstance(RilMessageDecoder caller,
            IccFileHandler fh) {
        if (sInstance != null) {
            return sInstance;
        }
        if (fh != null) {
            return new CommandParamsFactory(caller, fh);
        }
        return null;
    }
    
    static synchronized CommandParamsFactory getInstance(RilMessageDecoder caller,
            IccFileHandler fh, Context context) {
        if (sInstance != null) {
            return sInstance;
        }
        if (fh != null && context != null) {
            UtkLog.d("CommandParamsFactory", "Create CommandParamsFactory instance"
                                                                            + caller.getPhoneId());
            return new CommandParamsFactory(caller, fh, context);
        }
        return null;
    }

    private CommandParamsFactory(RilMessageDecoder caller, IccFileHandler fh) {
        mCaller = caller;
        mIconLoader = IconLoader.getInstance(this, fh, mCaller.getPhoneId());
    }
    
    private CommandParamsFactory(RilMessageDecoder caller, IccFileHandler fh, Context context) {
        mCaller = caller;
        mIconLoader = IconLoader.getInstance(this, fh, mCaller.getPhoneId());
        mContext = context;
    }

    private CommandDetails processCommandDetails(List<ComprehensionTlv> ctlvs) {
        CommandDetails cmdDet = null;

        if (ctlvs != null) {
            // Search for the Command Details object.
            ComprehensionTlv ctlvCmdDet = searchForTag(
                    ComprehensionTlvTag.COMMAND_DETAILS, ctlvs);
            if (ctlvCmdDet != null) {
                try {
                    cmdDet = ValueParser.retrieveCommandDetails(ctlvCmdDet);
                } catch (ResultException e) {
                    UtkLog.d(this,
                            "processCommandDetails: Failed to procees command details e=" + e);
                }
            }
        }
        return cmdDet;
    }

    void make(BerTlv berTlv) {
        if (berTlv == null) {
            return;
        }
        // reset global state parameters.
        mCmdParams = null;
        mIconLoadState = LOAD_NO_ICON;
        // only proactive command messages are processed.
        if (berTlv.getTag() != BerTlv.BER_PROACTIVE_COMMAND_TAG) {
            UtkLog.d(this, "berTlv.getTag()=" + berTlv.getTag());
            sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
            return;
        }
        boolean cmdPending = false;
        List<ComprehensionTlv> ctlvs = berTlv.getComprehensionTlvs();
        // process command dtails from the tlv list.
        CommandDetails cmdDet = processCommandDetails(ctlvs);
        if (cmdDet == null) {
            UtkLog.d(this, "processCommandDetails cmdDet is null");
            sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
            return;
        }

        // extract command type enumeration from the raw value stored inside
        // the Command Details object.
        AppInterface.CommandType cmdType = AppInterface.CommandType
                .fromInt(cmdDet.typeOfCommand);
        if (cmdType == null) {
            UtkLog.d(this, "AppInterface.CommandType.fromInt=" + cmdDet.typeOfCommand +
                           " to null cmd type");
            sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
            return;
        }

        UtkLog.d(this, "make cmdType " + cmdType);
        try {
            switch (cmdType) {
            case SET_UP_MENU:
                cmdPending = processSelectItem(cmdDet, ctlvs);
                break;
            case SELECT_ITEM:
                cmdPending = processSelectItem(cmdDet, ctlvs);
                break;
            case DISPLAY_TEXT:
                cmdPending = processDisplayText(cmdDet, ctlvs);
                break;
             case SET_UP_IDLE_MODE_TEXT:
                 cmdPending = processSetUpIdleModeText(cmdDet, ctlvs);
                 break;
             case GET_INKEY:
                cmdPending = processGetInkey(cmdDet, ctlvs);
                break;
             case GET_INPUT:
                 cmdPending = processGetInput(cmdDet, ctlvs);
                 break;
             case SEND_DTMF:
             case SEND_SS:
             case SEND_USSD:
                 cmdPending = processEventNotify(cmdDet, ctlvs);
                 break;
             case SEND_SMS:
                 cmdPending = processSendSms(cmdDet, ctlvs);
                 break;
             case SET_UP_CALL:
                 cmdPending = processSetupCall(cmdDet, ctlvs);
                 break;
             case REFRESH:
                processRefresh(cmdDet, ctlvs);
                cmdPending = false;
                break;
             //case LAUNCH_BROWSER:
             //    cmdPending = processLaunchBrowser(cmdDet, ctlvs);
             //    break;
             case PLAY_TONE:
                cmdPending = processPlayTone(cmdDet, ctlvs);
                break;
             case MORE_TIME:
                cmdPending = processMoreTime(cmdDet, ctlvs);
                break;
             case LOCAL_INFO:
                cmdPending = processLocalInformation(cmdDet, ctlvs);
                break;

             //bip start
             case POLL_INTERVAL:
                  cmdPending = processPollInterval(cmdDet, ctlvs);
                  break;
             case TIMER_MANAGEMENT:
                  cmdPending = processTimerManagement(cmdDet, ctlvs);
                  break;
             case SET_UP_EVENT_LIST:
                  cmdPending = processSetUpEventList(cmdDet, ctlvs);
                  break;
              case OPEN_CHANNEL:
                  cmdPending = processOpenChannel(cmdDet, ctlvs);
                  break;
              case CLOSE_CHANNEL:
                  cmdPending = processCloseChannel(cmdDet, ctlvs);
                  break;
              case SEND_DATA:
                  cmdPending = processSendData(cmdDet, ctlvs);
                  break;
              case RECEIVE_DATA:
                  cmdPending = processReceiveData(cmdDet, ctlvs);
                  break;
              case GET_CHANNEL_STATUS:
                  cmdPending = processGetChannelStatus(cmdDet, ctlvs);
                  break;
                //bip end

              default:
                // unsupported proactive commands
                mCmdParams = new CommandParams(cmdDet);
                sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
                return;
            }
        } catch (ResultException e) {
            UtkLog.d(this, "make: caught ResultException e=" + e);
            mCmdParams = new CommandParams(cmdDet);
            sendCmdParams(e.result());
            return;
        }
        if (!cmdPending) {
            sendCmdParams(ResultCode.OK);
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case MSG_ID_LOAD_ICON_DONE:
            sendCmdParams(setIcons(msg.obj));
            break;
        }
    }

    private ResultCode setIcons(Object data) {
        Bitmap[] icons = null;
        int iconIndex = 0;

        if (data == null) {
            return ResultCode.PRFRMD_ICON_NOT_DISPLAYED;
        }
        switch(mIconLoadState) {
        case LOAD_SINGLE_ICON:
            mCmdParams.setIcon((Bitmap) data);
            break;
        case LOAD_MULTI_ICONS:
            icons = (Bitmap[]) data;
            // set each item icon.
            for (Bitmap icon : icons) {
                mCmdParams.setIcon(icon);
            }
            break;
        }
        return ResultCode.OK;
    }

    private void sendCmdParams(ResultCode resCode) {
        mCaller.sendMsgParamsDecoded(resCode, mCmdParams);
    }

    /**
     * Search for a COMPREHENSION-TLV object with the given tag from a list
     *
     * @param tag A tag to search for
     * @param ctlvs List of ComprehensionTlv objects used to search in
     *
     * @return A ComprehensionTlv object that has the tag value of {@code tag}.
     *         If no object is found with the tag, null is returned.
     */
    private ComprehensionTlv searchForTag(ComprehensionTlvTag tag,
            List<ComprehensionTlv> ctlvs) {
        Iterator<ComprehensionTlv> iter = ctlvs.iterator();
        return searchForNextTag(tag, iter);
    }

    /**
     * Search for the next COMPREHENSION-TLV object with the given tag from a
     * list iterated by {@code iter}. {@code iter} points to the object next to
     * the found object when this method returns. Used for searching the same
     * list for similar tags, usually item id.
     *
     * @param tag A tag to search for
     * @param iter Iterator for ComprehensionTlv objects used for search
     *
     * @return A ComprehensionTlv object that has the tag value of {@code tag}.
     *         If no object is found with the tag, null is returned.
     */
    private ComprehensionTlv searchForNextTag(ComprehensionTlvTag tag,
            Iterator<ComprehensionTlv> iter) {
        int tagValue = tag.value();
        while (iter.hasNext()) {
            ComprehensionTlv ctlv = iter.next();
            if (ctlv.getTag() == tagValue) {
                return ctlv;
            }
        }
        return null;
    }

    /**
     * Processes DISPLAY_TEXT proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processDisplayText(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs)
            throws ResultException {

        UtkLog.d(this, "process DisplayText");

        TextMessage textMsg = new TextMessage();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveTextString(ctlv);
        }
        // If the tlv object doesn't exist or the it is a null object reply
        // with command not understood.
        if (textMsg.text == null) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }

        ctlv = searchForTag(ComprehensionTlvTag.IMMEDIATE_RESPONSE, ctlvs);
        if (ctlv != null) {
            textMsg.responseNeeded = false;
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }
        // parse tone duration
        ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
        if (ctlv != null) {
            textMsg.duration = ValueParser.retrieveDuration(ctlv);
        }

        // Parse command qualifier parameters.
        textMsg.isHighPriority = (cmdDet.commandQualifier & 0x01) != 0;
        textMsg.userClear = (cmdDet.commandQualifier & 0x80) != 0;

        mCmdParams = new DisplayTextParams(cmdDet, textMsg);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes SET_UP_IDLE_MODE_TEXT proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processSetUpIdleModeText(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "process SetUpIdleModeText");

        TextMessage textMsg = new TextMessage();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveTextString(ctlv);
        }
        // load icons only when text exist.
        if (textMsg.text != null) {
            ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
            if (ctlv != null) {
                iconId = ValueParser.retrieveIconId(ctlv);
                textMsg.iconSelfExplanatory = iconId.selfExplanatory;
            }
        }

        mCmdParams = new DisplayTextParams(cmdDet, textMsg);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes GET_INKEY proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processGetInkey(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "process GetInkey");

        Input input = new Input();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING,
                ctlvs);
        if (ctlv != null) {
            input.text = ValueParser.retrieveTextString(ctlv);
        } else {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
        }

        // parse duration
        ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
        if (ctlv != null) {
            input.duration = ValueParser.retrieveDuration(ctlv);
        }

        input.minLen = 1;
        input.maxLen = 1;

        input.digitOnly = (cmdDet.commandQualifier & 0x01) == 0;
        input.ucs2 = (cmdDet.commandQualifier & 0x02) != 0;
        input.yesNo = (cmdDet.commandQualifier & 0x04) != 0;
        input.helpAvailable = (cmdDet.commandQualifier & 0x80) != 0;
        input.echo = true;

        mCmdParams = new GetInputParams(cmdDet, input);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes GET_INPUT proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processGetInput(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "process GetInput cmdDet.commandQualifier = "+ cmdDet.commandQualifier);

        Input input = new Input();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING,
                ctlvs);
        if (ctlv != null) {
            input.text = ValueParser.retrieveTextString(ctlv);
        } else {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }

        ctlv = searchForTag(ComprehensionTlvTag.RESPONSE_LENGTH, ctlvs);
        if (ctlv != null) {
            try {
                byte[] rawValue = ctlv.getRawValue();
                int valueIndex = ctlv.getValueIndex();
                input.minLen = rawValue[valueIndex] & 0xff;
                input.maxLen = rawValue[valueIndex + 1] & 0xff;
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        } else {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }

        ctlv = searchForTag(ComprehensionTlvTag.DEFAULT_TEXT, ctlvs);
        if (ctlv != null) {
            input.defaultText = ValueParser.retrieveTextString(ctlv);
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
        }

        if ((cmdDet.commandQualifier & 0x01)!=0)
        {
            if ((cmdDet.commandQualifier & 0x02)!=0)
            {
                input.ucs2 = true;
            }
        }
        else
        {
            input.digitOnly = true;
        }

        if (input.ucs2)
        {
            if(input.maxLen /2 > UIM_INPUT_MAX_UNICODE_LEN)
            {
               input.maxLen = UIM_INPUT_MAX_UNICODE_LEN;
            }
            else
            {
                input.maxLen = input.maxLen /2;
            }
        }
        UtkLog.d(this, "maxLen = " + input.maxLen);
        input.echo = (cmdDet.commandQualifier & 0x04) == 0;
        input.packed = (cmdDet.commandQualifier & 0x08) != 0;
        input.helpAvailable = (cmdDet.commandQualifier & 0x80) != 0;

        mCmdParams = new GetInputParams(cmdDet, input);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes REFRESH proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     */
    private boolean processRefresh(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) {

        UtkLog.d(this, "process Refresh: " + cmdDet.commandQualifier);

        // REFRESH proactive command is rerouted by the baseband and handled by
        // the telephony layer. IDLE TEXT should be removed for a REFRESH command
        // with "initialization" or "reset"
        switch (cmdDet.commandQualifier) {
        case REFRESH_NAA_INIT_AND_FULL_FILE_CHANGE:
        case REFRESH_NAA_FILE_CHANGE:
        case REFRESH_NAA_INIT_AND_FILE_CHANGE:
        case REFRESH_NAA_INIT:
        case REFRESH_UICC_RESET:
            mCmdParams = new DisplayTextParams(cmdDet, null);
            break;
        }
        return false;
    }

    /**
     * Processes SELECT_ITEM proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
    private boolean processSelectItem(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "process SelectItem");

        Menu menu = new Menu();
        IconId titleIconId = null;
        ItemsIconId itemsIconId = null;
        Iterator<ComprehensionTlv> iter = ctlvs.iterator();

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID,
                ctlvs);
        if (ctlv != null) {
            menu.title = ValueParser.retrieveAlphaId(ctlv);
        }

        while (true) {
            ctlv = searchForNextTag(ComprehensionTlvTag.ITEM, iter);
            if (ctlv != null) {
                menu.items.add(ValueParser.retrieveItem(ctlv));
            } else {
                break;
            }
        }

        // We must have at least one menu item.
        if (menu.items.size() == 0) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ITEM_ID, ctlvs);
        if (ctlv != null) {
            // UTK items are listed 1...n while list start at 0, need to
            // subtract one.
            menu.defaultItem = ValueParser.retrieveItemId(ctlv) - 1;
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            titleIconId = ValueParser.retrieveIconId(ctlv);
            menu.titleIconSelfExplanatory = titleIconId.selfExplanatory;
        }

        ctlv = searchForTag(ComprehensionTlvTag.ITEM_ICON_ID_LIST, ctlvs);
        if (ctlv != null) {
            mIconLoadState = LOAD_MULTI_ICONS;
            itemsIconId = ValueParser.retrieveItemsIconId(ctlv);
            menu.itemsIconSelfExplanatory = itemsIconId.selfExplanatory;
        }

        boolean presentTypeSpecified = (cmdDet.commandQualifier & 0x01) != 0;
        if (presentTypeSpecified) {
            if ((cmdDet.commandQualifier & 0x02) == 0) {
                menu.presentationType = PresentationType.DATA_VALUES;
            } else {
                menu.presentationType = PresentationType.NAVIGATION_OPTIONS;
            }
        }
        menu.softKeyPreferred = (cmdDet.commandQualifier & 0x04) != 0;
        menu.helpAvailable = (cmdDet.commandQualifier & 0x80) != 0;

        mCmdParams = new SelectItemParams(cmdDet, menu, titleIconId != null);

        // Load icons data if needed.
        switch(mIconLoadState) {
        case LOAD_NO_ICON:
            return false;
        case LOAD_SINGLE_ICON:
            mIconLoader.loadIcon(titleIconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            break;
        case LOAD_MULTI_ICONS:
            int[] recordNumbers = itemsIconId.recordNumbers;
            if (titleIconId != null) {
                // Create a new array for all the icons (title and items).
                recordNumbers = new int[itemsIconId.recordNumbers.length + 1];
                recordNumbers[0] = titleIconId.recordNumber;
                System.arraycopy(itemsIconId.recordNumbers, 0, recordNumbers,
                        1, itemsIconId.recordNumbers.length);
            }
            mIconLoader.loadIcons(recordNumbers, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            break;
        }
        return true;
    }

    /**
     * Processes EVENT_NOTIFY message from baseband.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     */
    private boolean processEventNotify(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "process EventNotify");

        TextMessage textMsg = new TextMessage();
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        } else {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        textMsg.responseNeeded = false;
        mCmdParams = new DisplayTextParams(cmdDet, textMsg);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes SET_UP_EVENT_LIST proactive command from the SIM card.
     *
     * @param cmdDet Command Details object retrieved.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     */
    private boolean processSetUpEventList(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) {

        UtkLog.d(this, "process SetUpEventList");

        byte[] eventList = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.EVENT_LIST, ctlvs);
        if (ctlv != null) {
            try {
                eventList = ValueParser.retrieveSetupEventList(ctlv);
            } catch (ResultException e) {
                //
            }

            UtkLog.d(this, "eventList:" + IccUtils.bytesToHexString(eventList));

            mCmdParams = new SetupEventListParams(cmdDet, eventList);
        }

        return false;
    }

    /**
     * Processes LAUNCH_BROWSER proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     * @throws ResultException
     */
     private boolean processLaunchBrowser(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "process LaunchBrowser");

        TextMessage confirmMsg = new TextMessage();
        IconId iconId = null;
        String url = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.URL, ctlvs);
        if (ctlv != null) {
            try {
                byte[] rawValue = ctlv.getRawValue();
                int valueIndex = ctlv.getValueIndex();
                int valueLen = ctlv.getLength();
                if (valueLen > 0) {
                    url = GsmAlphabet.gsm8BitUnpackedToString(rawValue,
                            valueIndex, valueLen);
                } else {
                    url = null;
                }
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }

        // parse alpha identifier.
        ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            confirmMsg.text = ValueParser.retrieveAlphaId(ctlv);
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            confirmMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        // parse command qualifier value.
        LaunchBrowserMode mode;
        switch (cmdDet.commandQualifier) {
        case 0x00:
        default:
            mode = LaunchBrowserMode.LAUNCH_IF_NOT_ALREADY_LAUNCHED;
            break;
        case 0x02:
            mode = LaunchBrowserMode.USE_EXISTING_BROWSER;
            break;
        case 0x03:
            mode = LaunchBrowserMode.LAUNCH_NEW_BROWSER;
            break;
        }

        mCmdParams = new LaunchBrowserParams(cmdDet, confirmMsg, url, mode);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

     /**
     * Processes PLAY_TONE proactive command from the SIM card.
     *
     * @param cmdDet Command Details container object.
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.t
     * @throws ResultException
     */
     private boolean processPlayTone(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "process PlayTone");

        Tone tone = null;
        TextMessage textMsg = new TextMessage();
        Duration duration = null;
        IconId iconId = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TONE, ctlvs);
        if (ctlv != null) {
            // Nothing to do for null objects.
            if (ctlv.getLength() > 0) {
                try {
                    byte[] rawValue = ctlv.getRawValue();
                    int valueIndex = ctlv.getValueIndex();
                    int toneVal = rawValue[valueIndex];
                    tone = Tone.fromInt(toneVal);
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(
                            ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
        }
        // parse alpha identifier
        ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        }
        // parse tone duration
        ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
        if (ctlv != null) {
            duration = ValueParser.retrieveDuration(ctlv);
        }
        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        boolean vibrate = (cmdDet.commandQualifier & 0x01) != 0x00;

        textMsg.responseNeeded = false;
        mCmdParams = new PlayToneParams(cmdDet, textMsg, tone, duration, vibrate);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    /**
     * Processes SETUP_CALL proactive command from the SIM card.
     *
     * @param cmdDet Command Details object retrieved from the proactive command
     *        object
     * @param ctlvs List of ComprehensionTlv objects following Command Details
     *        object and Device Identities object within the proactive command
     * @return true if the command is processing is pending and additional
     *         asynchronous processing is required.
     */
     private boolean processSetupCall(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {
        UtkLog.d(this, "process SetupCall");

        Iterator<ComprehensionTlv> iter = ctlvs.iterator();
        ComprehensionTlv ctlv = null;
        // User confirmation phase message.
        TextMessage confirmMsg = new TextMessage();
        // Call set up phase message.
        TextMessage callMsg = new TextMessage();
        TextMessage setupMsg = new TextMessage();
        IconId confirmIconId = null;
        IconId callIconId = null;

        // get confirmation message string.
        ctlv = searchForNextTag(ComprehensionTlvTag.ALPHA_ID, iter);
        if (ctlv != null) {
            ComprehensionTlv temctlv = searchForNextTag(ComprehensionTlvTag.ADDRESS, iter);
            if(temctlv != null)
            {
                UtkLog.d(this, "search confirm message not null");
                confirmMsg.text = ValueParser.retrieveAlphaId(ctlv);
            }
            else
            {
                confirmMsg.text = null;
                setupMsg.text = ValueParser.retrieveAlphaId(ctlv);
            }
            UtkLog.d(this, "confirmMsg = " + confirmMsg.text + ", setupMsg = " + setupMsg.text);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            confirmIconId = ValueParser.retrieveIconId(ctlv);
            confirmMsg.iconSelfExplanatory = confirmIconId.selfExplanatory;
        }

        // get call set up message string.
        ctlv = searchForTag(ComprehensionTlvTag.ADDRESS, ctlvs);
        if (ctlv != null) {
            callMsg.text = ValueParser.retrieveAdress(ctlv);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            callIconId = ValueParser.retrieveIconId(ctlv);
            callMsg.iconSelfExplanatory = callIconId.selfExplanatory;
        }

        mCmdParams = new CallSetupParams(cmdDet, confirmMsg, callMsg, setupMsg);

        if (confirmIconId != null || callIconId != null) {
            mIconLoadState = LOAD_MULTI_ICONS;
            int[] recordNumbers = new int[2];
            recordNumbers[0] = confirmIconId != null
                    ? confirmIconId.recordNumber : -1;
            recordNumbers[1] = callIconId != null ? callIconId.recordNumber
                    : -1;

            mIconLoader.loadIcons(recordNumbers, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;
    }

    private boolean processSendSms(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "processSendSms");
        TextMessage textMsg = new TextMessage();
        IconId iconId = null;
        byte[] smsPdu = null;

        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID,
                ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        } else {
            UtkLog.d(this, "processSendSms : textMsg.text is null");
        }

        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }

        ctlv = searchForTag(ComprehensionTlvTag.CDMA_SMS_TPDU, ctlvs);
        if (ctlv != null) {
            smsPdu = ValueParser.retrieveSmsPdu(ctlv);
        }

        textMsg.responseNeeded = false;
        mCmdParams = new SendSmsParams(cmdDet, textMsg, smsPdu);

        if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }
        return false;

    }

    private boolean processMoreTime(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {
        UtkLog.d(this, "process MoreTime");

        //TODO : May be we should verify the CommandDetails is a legal one
        mCmdParams = new CommandParams(cmdDet);

        return false;
    }

    private boolean processLocalInformation(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {
        UtkLog.d(this, "process LocalInformation");

        mCmdParams = new CommandParams(cmdDet);

        return false;
    }

    //bip start
    private boolean processPollInterval(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "processPollInterval");

        int timeUnit = 0;
        int timeInterval = 0;

        ComprehensionTlv ctlv = null;

        /*DeviceIdentities deviceId = null;

        ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
        if (ctlv != null) {
            deviceId = ValueParser.retrieveDeviceIdentities(ctlv);
        }
        UtkLog.d(this, "deviceId:"+deviceId);*/

        //get timeUnit
        //get timeInterval
        ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
        if (ctlv != null) {
            byte[] rawValue = ctlv.getRawValue();
            int valueIndex = ctlv.getValueIndex();
            int valueLen = ctlv.getLength();

            if (valueLen != 2) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }

            try {
                timeUnit = rawValue[valueIndex++] & 0xFF;
                timeInterval = rawValue[valueIndex] & 0xFF;
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }

        UtkLog.d(this, "time units=" + timeUnit + " time interval=" + timeInterval);

        mCmdParams = new pollIntervalParams(cmdDet, timeUnit, timeInterval);

        return false;
    }

    private boolean processTimerManagement(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "processTimerManagement");

        int timerId = 0;
        byte[] data = null;

        ComprehensionTlv ctlv = null;

        DeviceIdentities deviceId = null;

        ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
        if (ctlv != null) {
            deviceId = ValueParser.retrieveDeviceIdentities(ctlv);
        }
        UtkLog.d(this, "deviceId:" + deviceId);

        //get timer identifier
        ctlv = searchForTag(ComprehensionTlvTag.TIMER_IDENTIFIER, ctlvs);
        if (ctlv != null) {
            byte[] rawValue = ctlv.getRawValue();
            int valueIndex = ctlv.getValueIndex();

            try {
                timerId = rawValue[valueIndex] & 0xFF;
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        UtkLog.d(this, "timerId:" + timerId);

        //get timer value
        ctlv = searchForTag(ComprehensionTlvTag.TIMER_VALUE, ctlvs);
        if (ctlv != null) {
            byte[] rawValue = ctlv.getRawValue();
            int valueIndex = ctlv.getValueIndex();

            try {
                data = new byte[3];
                System.arraycopy(rawValue, valueIndex, data, 0, 3);
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }

        UtkLog.d(this, "timer value=" + IccUtils.bytesToHexString(data));

        mCmdParams = new TimerManagementParams(cmdDet, timerId, data);

        return false;
    }

    private boolean processOpenChannel(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "processOpenChannel");

        Iterator<ComprehensionTlv> iter = null;
        ComprehensionTlv ctlv = null;

        OpenChannelParams chParams = new OpenChannelParams(cmdDet);

        chParams.textMsg = new TextMessage();

        // get text message
        ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            chParams.textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        }

        UtkLog.d(this, "textMsg:" + chParams.textMsg.text);

        // parse icon identifier
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            chParams.iconId = ValueParser.retrieveIconId(ctlv);
            chParams.textMsg.iconSelfExplanatory = chParams.iconId.selfExplanatory;
        }

        UtkLog.d(this, "iconId:" + chParams.iconId);

        // get bearer description
        ctlv = searchForTag(ComprehensionTlvTag.BEARER_DESCRIPTION, ctlvs);
        if (ctlv != null) {
            chParams.bearerDesc = ValueParser.retrieveBearerDesc(ctlv);
        } else {
            //use default bearer
            UtkLog.d(this, "use default bearer");
            chParams.bearerDesc = new BearerDescription(1, BipConstants.BEARER_TYPE_DEFAULT, null);
        }

        UtkLog.d(this, "bearerDesc=" + chParams.bearerDesc);

        // get buffer size
        ctlv = searchForTag(ComprehensionTlvTag.BUFFER_SIZE, ctlvs);
        if (ctlv != null) {
            chParams.bufferSize = ValueParser.retrieveBufSize(ctlv);
        } else {
            UtkLog.d(this, "use default buffer size");
            chParams.bufferSize = BipConstants.BUFFER_SIZE_MAX;
        }

        UtkLog.d(this, "buffersize:" + chParams.bufferSize);

        ComprehensionTlv ctlvDestAddress = null;
        iter = ctlvs.iterator();
        // get UIM-ME interface transport level
        ctlv = searchForNextTag(ComprehensionTlvTag.TRANSPORT_LEVEL, iter);
        if (ctlv != null) {
            chParams.transportLevel = ValueParser.retrieveTransportLevel(ctlv);

            //get dest address
            ctlvDestAddress = searchForNextTag(ComprehensionTlvTag.OTHER_ADDRESS, iter);
            if (ctlvDestAddress != null) {
                chParams.destAddress = ValueParser.retrieveOtherAddress(ctlvDestAddress);
            }
        }

        UtkLog.d(this, "transportLevel:" + chParams.transportLevel);
        UtkLog.d(this, "destAddress:" + chParams.destAddress);

        // get local address
        /*
        If the parameter is present and length is not null, it provides an IP address that
        identifies the CAT application in the address area applicable to the PDN.
        If local address length is null, dynamic local address allocation is required for
         the CAT application.
        If parameter is not present, the terminal may use the terminal default
         local address configuration.
        */
        ctlv = searchForTag(ComprehensionTlvTag.OTHER_ADDRESS, ctlvs);
        if (ctlv != null && ctlv != ctlvDestAddress) {
            chParams.localAddress = ValueParser.retrieveOtherAddress(ctlv);
        }

        UtkLog.d(this, "localAddress:" + chParams.localAddress);

        // get network access name
        ctlv = searchForTag(ComprehensionTlvTag.NETWORK_ACCESS_NAME, ctlvs);
        if (ctlv != null) {
            chParams.networkAccessName = ValueParser.retrieveNAN(ctlv);
        }

        UtkLog.d(this, "networkAccessName:" + chParams.networkAccessName);

        // get user name
        iter = ctlvs.iterator();
        ctlv = searchForNextTag(ComprehensionTlvTag.TEXT_STRING, iter);
        if (ctlv != null) {
            chParams.userName = ValueParser.retrieveTextString(ctlv);
        }

        UtkLog.d(this, "userName:" + chParams.userName);

        //get password
        ctlv = searchForNextTag(ComprehensionTlvTag.TEXT_STRING, iter);
        if (ctlv != null) {
            chParams.userPwd = ValueParser.retrieveTextString(ctlv);
        }

        UtkLog.d(this, "userpassword:" + chParams.userPwd);

        if ((chParams.bearerDesc.bearerType != BipConstants.BEARER_TYPE_DEFAULT) &&
           (chParams.bearerDesc.bearerType != BipConstants.BEARER_TYPE_PACKET_DATA)) {
            throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
        }

        if (chParams.transportLevel == null) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }

        if ((chParams.transportLevel.protocolType !=
            BipConstants.TRANSPORT_TYPE_TCP_CLIENT_REMOTE) &&
            (chParams.transportLevel.protocolType !=
            BipConstants.TRANSPORT_TYPE_UDP_CLIENT_REMOTE) &&
            (chParams.transportLevel.protocolType !=
            BipConstants.TRANSPORT_TYPE_TCP_SERVER)) {
             throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
        }

        mCmdParams = chParams;

        if (chParams.iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(chParams.iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }

        return false;
    }

    //
    private boolean processCloseChannel(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "processCloseChannel");

        ComprehensionTlv ctlv = null;

        TextMessage textMsg = new TextMessage();
        //IconId iconId = null;
        DeviceIdentities deviceId = null;

        ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        }

        UtkLog.d(this, "textMsg:" + textMsg.text);

        ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
        if (ctlv != null) {
            deviceId = ValueParser.retrieveDeviceIdentities(ctlv);
        }

        UtkLog.d(this, "deviceId:" + deviceId);

        /*ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }*/

        if (deviceId != null) {
            mCmdParams = new CloseChannelParams(cmdDet, textMsg, deviceId.destinationId);
        }

        /*if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }*/

        return false;
    }

    //
    private boolean processReceiveData(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "processReceiveData");

        ComprehensionTlv ctlv = null;

        int dataLength = 0;
        DeviceIdentities deviceId = null;

        TextMessage textMsg = new TextMessage();
        //IconId iconId = null;

        ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
        if (ctlv != null) {
            deviceId = ValueParser.retrieveDeviceIdentities(ctlv);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        }

        UtkLog.d(this, "textMsg:" + textMsg.text);
        UtkLog.d(this, "deviceId:" + deviceId);

        /*ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }*/

        ctlv = searchForTag(ComprehensionTlvTag.CHANNEL_DATA_LENGTH, ctlvs);
        if (ctlv != null) {
            dataLength = ValueParser.retrieveReqDataLength(ctlv);
        }

        UtkLog.d(this, "dataLength:" + dataLength);

        if (deviceId != null) {
            mCmdParams = new ReceiveDataParams(cmdDet, dataLength, textMsg, deviceId.destinationId);
        }

        /*if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }*/

        return false;
    }

    //
    private boolean processSendData(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "processSendData");

        ComprehensionTlv ctlv = null;

        byte[] channelData = null;
        DeviceIdentities deviceId = null;

        TextMessage textMsg = new TextMessage();
        //IconId iconId = null;

        ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
        if (ctlv != null) {
            deviceId = ValueParser.retrieveDeviceIdentities(ctlv);
        }

        ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        }

        UtkLog.d(this, "textMsg:" + textMsg.text);
        UtkLog.d(this, "deviceId:" + deviceId);

        /*ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }*/

        ctlv = searchForTag(ComprehensionTlvTag.CHANNEL_DATA, ctlvs);
        if (ctlv != null) {
            channelData = ValueParser.retrieveDataToSend(ctlv);
        }

        if (deviceId != null) {
            mCmdParams = new SendDataParams(cmdDet, channelData, textMsg, deviceId.destinationId);
        }

        /*if (iconId != null) {
            mIconLoadState = LOAD_SINGLE_ICON;
            mIconLoader.loadIcon(iconId.recordNumber, this
                    .obtainMessage(MSG_ID_LOAD_ICON_DONE));
            return true;
        }*/

        return false;
    }

    //
    private boolean processGetChannelStatus(CommandDetails cmdDet,
            List<ComprehensionTlv> ctlvs) throws ResultException {

        UtkLog.d(this, "processGetChannelStatus");

        ComprehensionTlv ctlv = null;

        DeviceIdentities deviceId = null; // ids??

        ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
        if (ctlv != null) {
            deviceId = ValueParser.retrieveDeviceIdentities(ctlv);
        }

        UtkLog.d(this, "deviceId:" + deviceId);

        if (deviceId != null) {
            mCmdParams = new GetChannelStatusParams(cmdDet, deviceId.destinationId);
        }

        return false;
    }
    //bip end
}
