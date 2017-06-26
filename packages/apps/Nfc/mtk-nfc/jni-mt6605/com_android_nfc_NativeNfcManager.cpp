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

#include <errno.h>
#include <pthread.h>
#include <semaphore.h>
#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <sys/queue.h>
#include <hardware/hardware.h>
#include <hardware/nfc.h>
#include <cutils/properties.h>
#include "com_android_nfc.h"
// ---------------------------
// socket related header files
// ---------------------------
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
// --------------------------

#include "com_android_nfc.h"

/****************************************************************************
 * Define
 ****************************************************************************/
#define SUPPORT_EVT_TRANSACTION
#define USE_LOCAL_SOCKET
#define KILL_ZOMBIE_PROCESS
#define NFC_MSG_HDR_SIZE        sizeof(MTK_NFC_MSG_T)

// socket related definitions
#define SOCKET_PORT             7500
#define MAX_RECVBUFF_SIZE       1024
#define SOCKET_RETRY_CNT        10      // retry count if socket connect fail
#define SOCKET_RETRY_INTERVAL   100000 // 100ms
#ifdef USE_LOCAL_SOCKET
#define NFC_LOCAL_SOCKET_ADDR   "/data/nfc_socket/mtknfc_server"
#endif

#define LLCP_DEFAULT_LENGTH 128
#define NFC_JNI_MAXNO_OF_SE 3

#define MODE_IDLE    0x00
#define MODE_READER 0x01
#define MODE_CARD   0x02
#define MODE_P2P    0x04

// L Migration for reader mode
#define TECHNOLOGY_MASK_A            0x01    //NFC Technology A          
#define TECHNOLOGY_MASK_B            0x02    //NFC Technology B             
#define TECHNOLOGY_MASK_F            0x04    //NFC Technology F             
#define TECHNOLOGY_MASK_ISO15693    0x08    //Proprietary Technology       
#define TECHNOLOGY_MASK_B_PRIME        0x10    //Proprietary Technology       
#define TECHNOLOGY_MASK_KOVIO        0x20    //Proprietary Technology       
#define TECHNOLOGY_MASK_ALL         0xFF    //All supported technologies   

#define DEFAULT_TECH_MASK           (TECHNOLOGY_MASK_A \
                                     | TECHNOLOGY_MASK_B \
                                     | TECHNOLOGY_MASK_F \
                                     | TECHNOLOGY_MASK_ISO15693 \
                                     | TECHNOLOGY_MASK_B_PRIME \
                                     | TECHNOLOGY_MASK_KOVIO)


/****************************************************************************
 * Data Structure
 ****************************************************************************/

/****************************************************************************
 * Global Variable
 ****************************************************************************/

#ifdef KILL_ZOMBIE_PROCESS
sem_t kill_process_sem;
#endif

bool    gLowPowerPollingOption  = false ;
int     gSwpTestInNormalMode    = 0; // SWP tset in normal mode
int     gSwpTestPatternNum      = 0; // SWP tset in normal mode
int     gTimeout                = 1000;  

bool    gMtcMosMode             = false;
static  int g_sockfd                    = (-1);
static  pid_t g_nfcmw_pid               = (-1);
static  unsigned int hLlcpWorkingHandle = 0;
static  bool driverConfigured           = FALSE;
static  bool enableDiscoveryErrorFlag   = FALSE;

unsigned char*  sReceiveBuffer[NFC_LLCP_SERVICE_NUM];
unsigned int    sReceiveLength[NFC_LLCP_SERVICE_NUM];
unsigned char   device_connected_flag = 0;
unsigned int    g_llcp_service[NFC_LLCP_SERVICE_NUM];
unsigned char   g_llcp_sertype[NFC_LLCP_SERVICE_NUM];
unsigned char   g_idx_service[NFC_LLCP_ACT_END];
unsigned int    g_llcp_remiu[NFC_LLCP_SERVICE_NUM];
unsigned char   g_llcp_remrw[NFC_LLCP_SERVICE_NUM];
unsigned int    SEInfor[NFC_JNI_MAXNO_OF_SE*2];
unsigned char   g_SeCount       = 0;
unsigned int    g_current_seid  = 0;

struct nfc_jni_callback_data    *g_working_cb_data = NULL;
struct nfc_jni_callback_data    *g_working_cb_data_tag = NULL;
struct nfc_jni_callback_data    g_cb_p2p_data;
struct nfc_jni_callback_data*   g_llcp_cb_data[NFC_LLCP_SERVICE_NUM][NFC_LLCP_ACT_END];
struct nfc_jni_native_data*     g_Discovery_nat = NULL;
struct nfc_jni_native_data*     g_Deactive_nat = NULL;

s_mtk_nfc_jni_se_get_list_rsp_t SEListInfor;

nfc_jni_discovery_info_t        gDiscoveryInfo;  // L migration

extern unsigned int     nfc_jni_ndef_buf_len; // LC add Temp solution
extern unsigned int     nfc_jni_ndef_buf_size; // LC add Temp solution
extern unsigned char    *nfc_jni_ndef_buf;
extern          int     gDefaultRoute; //L migration
//----------------------------------
//  NFC QuickEnable
//----------------------------------
#ifdef MTK_NFC_QUICK_SUPPORT
#define QUICK_ENABLE_CFG "/system/etc/nfc.cfg"
#define QUICK_ENABLE_APP_SYNC   "/sdcard/mtknfcSyncQuickMode"
#define QUICK_ENABLE_CFG_NAME "QUICK_ENABLE"
#define E2_ACFD_CFG_NAME "E2_BIN"
#define E3_ACFD_CFG_NAME "E3_BIN"
#define NFC_BOOTUP_RTY 3
static bool quick_enable_mode = FALSE;  // for Quick Enable MODE
static bool dta_quick_mode = FALSE;  // for  check dat flow
static bool gNfcSlept = FALSE; // for NFC enter sleep mode
static bool gNfc_ACFD_mode = FALSE;  // for NFC enter ACFD polling
#endif
//----------------------------------
//  DTA
//----------------------------------
#ifdef DTA_SUPPORT
struct      nfc_jni_native_data dta_nat;
static      bool    dta_mode = FALSE;
unsigned    char    *dta_rf_cmd_rsp;
unsigned    int     dta_rf_cmd_rsp_len;
unsigned    char    *dta_dep_rsp = NULL;
unsigned    int     dta_dep_rsp_len = 0;

typedef void (*pDetectionHandler_t) (
    unsigned long type,
    unsigned long nError
);
volatile pDetectionHandler_t pDetectionHandler = NULL;
pthread_t dta_client_thread; 
volatile bool dta_client_is_running = FALSE;
volatile unsigned long detection_type = 0;
volatile unsigned long detection_nError = 0;

sem_t dta_client_sem;

#define DTA_MODE_IDENTITY "DtaEnable"
#define DTA_CONFIG_PATH_MAX_LEN    60    // dta config path
#endif
//------------------------------------

//unsigned char g_u1RecvBuff[MAX_RECVBUFF_SIZE];
//unsigned int g_u4RecvBuff_Offset = 0;
//unsigned int g_u4RecvBuff_Length = 0;
//static unsigned int hInComingHandle = 0;
//void   *gHWRef;
// Reader mode only 
//static int temp_mode = 0;

static jmethodID cached_NfcManager_notifyNdefMessageListeners;
static jmethodID cached_NfcManager_notifyLlcpLinkActivation;
static jmethodID cached_NfcManager_notifyLlcpLinkDeactivated;
static jmethodID cached_NfcManager_notifyTransactionListeners;
static jmethodID cached_NfcManager_notifySeFieldActivated;
static jmethodID cached_NfcManager_notifySeFieldDeactivated;

#if 0 // L migration
static jmethodID cached_NfcManager_notifyTargetDeselected;
static jmethodID cached_NfcManager_notifySeApduReceived;
static jmethodID cached_NfcManager_notifySeMifareAccess;
static jmethodID cached_NfcManager_notifySeEmvCardRemoval;
#endif

#ifdef MTK_NFC_HCE_SUPPORT 
static jmethodID cached_NfcManager_notifyHostEmuActivated;
static jmethodID cached_NfcManager_notifyHostEmuData;
static jmethodID cached_NfcManager_notifyHostEmuDeactivated;
#endif

namespace android {

struct nfc_jni_native_data *exported_nat = NULL;

/* Internal functions declaration */
static void nfc_jni_init_callback(void *pContext, NFCSTATUS status);
static void nfc_jni_deinit_callback(void *pContext, NFCSTATUS status);
static void nfc_jni_exit_callback(void *pContext, NFCSTATUS status);
static void nfc_jni_llcp_transport_listen_socket_callback(MTK_NFC_LISTEN_SERVICE_NTF* pSerNtf);
static void nfc_jni_llcp_linkStatus_callback(struct nfc_jni_native_data *Natdata, int status, unsigned char LinkStatus);
static void nfc_jni_set_pnfc_rsp_callback(void *pContext, unsigned int u4Result);
static void nfc_jni_set_discover_rsp_callback(void *pContext, unsigned int u4Result);
static void nfc_jni_set_discover_ntf_callback(void *pContext,
    nfc_jni_native_data *nat,
    unsigned int u4Result,
    unsigned int u4NumDev,
    s_mtk_discover_device_ntf_t rDiscoverDev[DISCOVERY_DEV_MAX_NUM]);
static void nfc_jni_dev_select_rsp_callback(void *pContext, unsigned int u4Result);
static void nfc_jni_dev_activate_ntf_callback(void *pContext,
    nfc_jni_native_data *nat,
    unsigned int u4Result,
    s_mtk_nfc_service_activate_param_t rActivateParam,
    s_mtk_nfc_service_tag_param_t rTagParam);
static void nfc_jni_dev_deactivate_rsp_callback(void *pContext,nfc_jni_native_data *nat,unsigned int u4Result);
static void nfc_jni_dev_deactivate_ntf_callback(void *pContext, 
    nfc_jni_native_data *nat,
    unsigned int u4Result,
    UINT8 u1DeactivateType,
    UINT8 u1DeactivateReason);
static void nfc_jni_se_set_mode_callback(void *pContext,
   unsigned char handle, NFCSTATUS status);
static void nfc_jni_se_get_list_callback(void *pContext, NFCSTATUS status ,
   s_mtk_nfc_jni_se_get_list_rsp_t *prSeGetList);
static void nfc_jni_se_get_cur_se_callback(void *pContext,
   unsigned char handle, NFCSTATUS status);
#ifdef DTA_SUPPORT
static void dta_detection_handler(unsigned long type, unsigned long nError);
static bool dta_file_check();
static bool dta_file_write();
static bool dta_file_clear();
static void nfc_jni_set_PNFC_callback(void *pContext,NFCSTATUS status);
bool JniDtaSetPNFC(int pattern_num);
#endif
#ifdef SUPPORT_EVT_TRANSACTION
static void nfc_jni_dev_event_ntf_callback(nfc_jni_native_data *nat, s_mtk_nfc_service_dev_event_ntf_t *ntf);
static void notifyEvtTransactionToJavaLayer(const char * pAid, int length);
#endif

#ifdef MTK_NFC_HCE_SUPPORT 
static bool nfc_jni_send_raw_frame( UINT8 *raw, UINT32 length);
static bool nfc_jni_set_default_route(UINT32 defaulRoute);
static bool nfc_jni_add_aid_routing( UINT8 *aid, UINT8 aidLen, UINT32 route);
static bool nfc_jni_remove_aid_routing( UINT8 *aid, UINT32 aidLen);
static bool nfc_jni_update_routing(bool state);
static void nfc_jni_send_raw_frame_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_send_raw_frame_rsp_t * prSendRawFrame);
static void nfc_jni_set_default_route_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_set_default_route_rsp_t * prSetDefaultRoute);
static void nfc_jni_add_aid_routing_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_add_aid_rsp_t * prAddAidRouting);    
static void nfc_jni_remove_aid_routing_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_remove_aid_rsp_t * prRemoveAidRouting);
static void nfc_jni_update_routing_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_update_routing_rsp_t * prUpdateRouting);
static void * nfc_jni_notify_host_emu_data (void * pHceNative);
#endif

#ifdef MTK_NFC_QUICK_SUPPORT
static bool quickEnable_cfg_check();
static bool check_dta_quick_mode();
static bool nfc_jni_mtc_mos_mode(bool on_off);
static bool nfc_jni_mtc_mos_PNFC(bool on_off);
#endif

static bool ACFD_cfg_check();

//--- L Migration
static bool nfc_jni_low_power_polling(bool enable);
static bool nfc_jni_low_power_card_mode( bool on_off );
bool nfc_jni_enable_discovery_with_info(struct nfc_jni_native_data * nat,
    nfc_jni_discovery_info_t info);

static void nfc_jni_ActivateSeInterface_callback(void *pContext, NFCSTATUS status, 
    void * MsgPayload);
static bool nfc_jni_picc_mode();



/****************************************************************************
 * MTK NFC JNI Utilities
 ****************************************************************************/
static int android_nfc_socket_init(void)
{
    int sockfd;
#ifdef USE_LOCAL_SOCKET
    struct sockaddr_un dest;   
#else
    struct sockaddr_in dest;
#endif
    char buffer[128];
    int retry_cnt = 0; // retry count for socket connect

    ALOGD("android_nfc_socket_init...\n");

    // create socket
#ifdef USE_LOCAL_SOCKET
    if ((sockfd = socket(AF_LOCAL, SOCK_STREAM, 0)) < 0)   
#else
    if ((sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0)
#endif
    {
       ALOGE("create socket fail, %d, %s\n", errno, strerror(errno));
        return FALSE;
    }

    // initialize value in dest 
    #ifdef USE_LOCAL_SOCKET
    dest.sun_family = AF_LOCAL;
    strcpy (dest.sun_path, NFC_LOCAL_SOCKET_ADDR);
    #else
    dest.sin_family = AF_INET;           // host byte order 
    dest.sin_port = htons(SOCKET_PORT);  // short, network byte order 
    dest.sin_addr.s_addr = INADDR_ANY;   // automatically fill with my IP 
    memset(dest.sin_zero, 0x00, sizeof(dest.sin_zero));
    #endif

    // connect to server - max retry 5 times 
    for (retry_cnt = 0; retry_cnt < SOCKET_RETRY_CNT; retry_cnt++)
    {
        if (connect(sockfd, (struct sockaddr *)&dest, sizeof(dest)) < 0)
        {
            ALOGW("connect socket fail, %d, %s, retry_cnt:%d\n", errno, strerror(errno), retry_cnt);
            usleep(SOCKET_RETRY_INTERVAL);
        }
        else
        {
            break; // connect success, exit loop
        }
    }

    if (retry_cnt == SOCKET_RETRY_CNT)
    {
        ALOGE("android_nfc_socket_init(), reach max retry cnt\n");
        close(sockfd);
        return FALSE;
    }

    // update fd 
    g_sockfd = sockfd;

    ALOGD("android_nfc_socket_init ok\n");
   
    return TRUE;
}

static int android_nfc_socket_send(MTK_NFC_MSG_T *p_msg)
{
    int i4SendLen;

    ALOGD("android_nfc_socket_send...\n");

    if (p_msg == NULL)
    {
        ALOGE("socket send fail: due to msg == null\n");
        return -1;
    }

    if (g_sockfd < 0)
    {
        ALOGE("socket send msg fail: due to invalid g_sockfd: %d\n", g_sockfd);
        return -1;
    }

    ALOGD("socket send msg: type %d, length, %d\n", p_msg->type, p_msg->length);

    /* send data to nfc daemon */
    i4SendLen = send(g_sockfd, (void *)p_msg, sizeof(MTK_NFC_MSG_T) + p_msg->length ,0);
    if (i4SendLen < 0)
    {
        ALOGE("socket send fail: %d, %s\n", errno, strerror(errno));
    }
    else
    {
        ALOGD("android_nfc_socket_send ok (send len: %d)\n", i4SendLen);
    }

    return i4SendLen;
}

static int android_nfc_socket_read(unsigned char *pRecvBuff, int i4RecvLen)
{
    int i4ReadLen;
    int i4TotalReadLen;
    
    ALOGD("android_nfc_socket_read...(pRecvBuff:%p, i4RecvLen:%d)\n", pRecvBuff, i4RecvLen);
       
    if (g_sockfd < 0)
    {
        ALOGE("socket recv msg fail: due to invalid g_sockfd: %d\n", g_sockfd);
        return -1;
    }   
    
    // read data to nfc daemon 
    i4TotalReadLen = 0;
    while (i4TotalReadLen < i4RecvLen)
    {
        i4ReadLen = read(g_sockfd, pRecvBuff, i4RecvLen - i4TotalReadLen);
        if (i4ReadLen < 0)
        {
            ALOGE("socket read fail: %d, %s\n", errno, strerror(errno));
            i4TotalReadLen = i4ReadLen; // keep read fail return value
            break; // exit loop
        }
        else if (i4ReadLen == 0)
        {
            ALOGE("socket read fail due to socket be closed\n");
            i4TotalReadLen = i4ReadLen; // keep read fail return value
            break; // exit loop
        }
        else
        {
            i4TotalReadLen += i4ReadLen;
        }   
      
        ALOGD("android_nfc_socket_read ok (read len: %d, target len: %d)\n", i4TotalReadLen, i4RecvLen);      
    }

    return i4TotalReadLen;
}

int android_nfc_jni_send_msg(unsigned int type, unsigned int len, void *payload)
{  
    MTK_NFC_MSG_T *prmsg;
   
    ALOGD("android_nfc_jni_send_msg...(type: %d, len: %d, payload: %p)\n", 
        type, len, payload);

    // malloc msg
    prmsg = (MTK_NFC_MSG_T *)malloc(sizeof(MTK_NFC_MSG_T) + len);
    if (prmsg == NULL)
    {
        ALOGE("malloc msg fail\n");
        return FALSE;
    }
    else
    {   
        // fill type & length 
        prmsg->type = type;
        prmsg->length = len;      
        if (len > 0) { 
            memcpy((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T), payload, len); 
        }

        // send msg to nfc daemon            
        if (android_nfc_socket_send(prmsg) == -1)
        {
            ALOGE("send msg fail\n");
            free(prmsg);
            return FALSE;
        }
      
        // free msg
        free(prmsg);   
    }
   
    ALOGD("android_nfc_jni_send_msg ok\n");
   
    return TRUE;
}

int android_nfc_jni_recv_msg(MTK_NFC_MSG_T **p_msg)
{  
    int i4ReadLen = 0;
    MTK_NFC_MSG_T msg_hdr;
    void *p_msg_body;
    unsigned char *pBuff;

    ALOGD("android_nfc_jni_recv_msg...\n");

    // read msg header (blocking read)
    pBuff = (unsigned char *)&msg_hdr;
    i4ReadLen = android_nfc_socket_read(pBuff, NFC_MSG_HDR_SIZE);
    if (i4ReadLen <= 0) // error case
    {         
        return FALSE;
    }
    else if (NFC_MSG_HDR_SIZE != i4ReadLen)
    {
        ALOGD("unexpected length (hdr len: %d, read len: %d)\n", NFC_MSG_HDR_SIZE, i4ReadLen);
        return FALSE;
    }
    else
    {
        ALOGD("msg hdr (type: %d, len: %d)\n", msg_hdr.type, msg_hdr.length);

        // malloc msg
        *p_msg = (MTK_NFC_MSG_T *)malloc(NFC_MSG_HDR_SIZE + msg_hdr.length);
        if (*p_msg == NULL)
        {
            ALOGD("malloc fail\n");
            return FALSE;
        }   

        // fill type & length 
        memcpy((unsigned char *)*p_msg, (unsigned char *)&msg_hdr, NFC_MSG_HDR_SIZE); 
    }

    // read msg body (blocking read)
    if (msg_hdr.length > 0)
    {
        p_msg_body = (unsigned char *)*p_msg + NFC_MSG_HDR_SIZE;
        pBuff = (unsigned char *)p_msg_body;
        i4ReadLen = android_nfc_socket_read(pBuff, msg_hdr.length);
        if (i4ReadLen <= 0) // error case
        {         
            return FALSE;
        }
        else if (msg_hdr.length != (UINT32)i4ReadLen)
        {      
            ALOGD("unexpected length (body len: %d, read len %d)\n", msg_hdr.length, i4ReadLen);      
            free(*p_msg);
            *p_msg = NULL;
            return FALSE;
        }
    }

    ALOGD("android_nfc_jni_recv_msg ok\n");

    return TRUE;
}

static int android_nfc_socket_deinit(void)
{
    int ret;

    ALOGD("android_nfc_socket_deinit...\n");

    if (g_sockfd < 0)
    {
        ALOGE("android_nfc_socket_deinit fail, invalid sockfd: %d\n", g_sockfd);
        return FALSE;             
    }

    ret = close(g_sockfd);
    if (ret < 0)
    {
        ALOGE("close socket fail, %d, %s\n", errno, strerror(errno));
        return FALSE;
    }
    else // success
    {
        ALOGD("close socket success\n");
        g_sockfd = -1;
    }

    ALOGD("android_nfc_socket_deinit ok\n");

    return TRUE;
}

#ifdef KILL_ZOMBIE_PROCESS
#include <signal.h>
#include <sys/wait.h>

void clean_up_child_process (int signal_number)
{
   int status;
   int ret;
   int retry_cnt;
   
   ALOGD("clean_up_child_process...(sig_num: %d)\n", signal_number);

   /* Clean up the child process. */
   //wait (&status);
        
   //non-blocking waitpid  
   /*  Wait 250 ms for child process to terminate ,  max retry 5 times */ 
   for (retry_cnt = 0; retry_cnt < 5; retry_cnt++)
   {
      ret = waitpid( g_nfcmw_pid, &status, WNOHANG);
      if ( ret == -1) 
      {
         ALOGD("waitpid error. \n");
         break;; 
      }
      else if ( ret == 0)
      {
         ALOGD("Child process is still running. retry = %d\n", retry_cnt);
         usleep(50000);  //50 ms
      }
      else if ( ret == g_nfcmw_pid)
      {
         ALOGD("Child process has stopped. \n");
         break;
      }
      else
      {
         ALOGD("Unexpected Child process has stopped. pid,%d\n",ret);
         break;
      }          
   }
   
   if (ret <= 0)
   {
       ALOGD("No Child process terminated. \n");
       return;
   }

#ifdef KILL_ZOMBIE_PROCESS
   ALOGD("post sem for kill_process_sem\n");
   sem_post(&kill_process_sem);
#endif
   
   ALOGD("clean_up_child_process status: %d\n", status);
}
#endif

//--------------------------
static void nfc_jni_notify_Field_OnOff ( nfc_jni_native_data *nat,
    mtk_nfc_fieldinfo_ntf_t *ntf )
{
    JNIEnv* e = NULL;

    ALOGD("JNI-CB: nfc_jni_notify_Field_OnOff, %p, %p", nat, ntf);

    //acquire a pointer to the Java virtual machine
    if(nat->vm)
    {
        nat->vm->AttachCurrentThread (&e, NULL);
        if (e == NULL)
        {
            ALOGE("jni env is null");
            return;
        }

        if (ntf->RFFieldStatus == 1)
        {
            //2. Notify NFC service 
            ALOGD ("%s: try notify nfc service Field On", __FUNCTION__);
            e->CallVoidMethod (nat->manager, cached_NfcManager_notifySeFieldActivated);
            if (e->ExceptionCheck())
            {
                e->ExceptionClear();
                ALOGE ("%s: fail notify nfc service", __FUNCTION__);
            }
        }
        else if(ntf->RFFieldStatus == 0)
        {
            //2. Notify NFC service 
            ALOGD ("%s: try notify nfc service Field Off", __FUNCTION__);
            e->CallVoidMethod (nat->manager, cached_NfcManager_notifySeFieldDeactivated);
            if (e->ExceptionCheck())
            {
                e->ExceptionClear();
                ALOGE ("%s: fail notify nfc service", __FUNCTION__);
            }
        }   
        else // error cases
        {
            ALOGD("JNI-CB: nfc_jni_notify_Field_OnOff,unsupported,FieldStatus,%d", ntf->RFFieldStatus);
        }
    }
    else
    {
        //yenchih note: dta mode will no vm , and we can't send field onoff event at this case
        ALOGE("vm is empty");
    }
    
    

    ALOGD("JNI-CB: nfc_jni_notify_Field_OnOff() - EXIT");
}
//-------------------------------


static int android_nfc_daemon_init(void)
{
    pid_t my_pid, parent_pid, child_pid;
    int ret;   

#ifdef KILL_ZOMBIE_PROCESS
    struct sigaction sigchld_action;
    memset (&sigchld_action, 0, sizeof (sigchld_action));
    sigchld_action.sa_handler = &clean_up_child_process;   
    sigchld_action.sa_flags = SA_NOCLDSTOP;   //skip child process stoped signal
    sigaction (SIGCHLD, &sigchld_action, NULL);
#endif

    ALOGD("android_nfc_daemon_init...\n");

    // get and print my pid and my parent's pid
    my_pid = getpid();    
    parent_pid = getppid();
    ALOGD("Parent: my pid is %d\n", my_pid);
    ALOGD("Parent: my parent's pid is %d\n", parent_pid);

    // fork child process to execute nfc daemon 
    if ((child_pid = fork()) < 0)
    {
        ALOGE("fork() fail: %d, (%s)\n", errno, strerror(errno));
        return FALSE;
    }
    else if (child_pid == 0) // child process
    {   
        my_pid = getpid();
        parent_pid = getppid();   
        ALOGD("Child Process fork success!!!\n");
        ALOGD("Child: my pid is: %d\n", my_pid);
        ALOGD("Child: my parent's pid is: %d\n", parent_pid);   
        ALOGD("Child Process execl nfcstackp\n");
        ret = execl("/system/xbin/nfcstackp", "nfcstackp", NULL);
        if (ret == -1)
        {
            ALOGE("execl() fail: %d, %s\n", errno, strerror(errno));
            return FALSE;
        }
    }
    else // parent process
    {
        ALOGD("Parent: my child's pid is: %d\n", child_pid);
        g_nfcmw_pid = child_pid;
    }
   
    ALOGD("android_nfc_daemon_init ok\n");

    return TRUE;
}

static int android_nfc_daemon_deinit(void)
{
    int ret = 0;

    ALOGD("android_nfc_daemon_deinit...\n");

    ret = kill(g_nfcmw_pid, SIGTERM);
    if (ret < 0) // error case
    {
        ALOGE("kill nfcmw(pid:%d) fail: %d, (%s)\n", g_nfcmw_pid, errno, strerror(errno));
        return FALSE;
    }
    else if (ret > 0) // unreasonable return value
    {
        ALOGE("kill nfcmw(pid:%d) fail, unreasonalbe ret: %d\n", g_nfcmw_pid, ret);
        return FALSE;
    }
    else if (ret == 0) // On success, zero is returned
    {
        ALOGD("kill nfcmw(pid:%d) success\n", g_nfcmw_pid);
        g_nfcmw_pid = -1;
    }      

    ALOGD("android_nfc_daemon_deinit ok\n");

    return TRUE;
}

void android_nfc_print_debuglog_array(UINT8* data, UINT32 length)
{
    INT32  maxLength = length * 3 + 20;    
    INT32  index = 0;
    CHAR   *debugMessage;
    UINT32 debugMessageLength = 0;
    debugMessage = (CHAR *)malloc( sizeof(CHAR) * maxLength);
    if (debugMessage != NULL)
    {
        memset(debugMessage, 0x00, maxLength);        
        sprintf(debugMessage, "Recv: ");
        debugMessageLength = strlen(debugMessage);  
        for( index = 0; index < length; index++)
        {       
            sprintf((debugMessage + debugMessageLength),"%02x,",data[index]);
            debugMessageLength = strlen(debugMessage);           
        }
        sprintf((debugMessage + debugMessageLength),"\n");      
        ALOGD ("%s", debugMessage);
        ALOGD ("Recv length,%d", length);
        
        free(debugMessage);
    }
}



//----------------------------------------------------
//   NFC Message Handler
//----------------------------------------------------
static void *nfc_jni_mtk_client_thread(void *arg)
{
    struct nfc_jni_native_data *nat;
    JNIEnv *e;
    JavaVMAttachArgs thread_args;
    MTK_NFC_MSG_T *prmsg = NULL;
    int i4ReadLen = 0;
    int result = FALSE;

    nat = (struct nfc_jni_native_data *)arg;

    thread_args.name = "MTK NFC Message Loop";
    thread_args.version = nat->env_version;
    thread_args.group = NULL;
    if (nat->vm)
    {
        nat->vm->AttachCurrentThread(&e, &thread_args);
    }
    else
    {
        ALOGD("empty vm in nat\n");
    }
    pthread_setname_np(pthread_self(), "message");

    ALOGD("MTK NFC client started\n");
    nat->running = TRUE;
    while(nat->running == TRUE)
    {
        // Fetch next message from the NFC stack message queue 
        // Notice: don't free this msg due to use global var 
        result = android_nfc_jni_recv_msg(&prmsg);      
        if (result != TRUE) // error case
        {
            nat->running = FALSE;
            break; // exit loop
        }      
        switch(prmsg->type)
        {
            case MTK_NFC_INIT_RSP:
            {
                s_mtk_nfc_init_rsp *prInitRsp = NULL;
            
                // receive init resp msg from nfc daemon
                prInitRsp = (s_mtk_nfc_init_rsp *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
            
                // callback to unlock semaphore
                nfc_jni_init_callback(g_working_cb_data, prInitRsp->result);
                break;
            }

            case MTK_NFC_DEINIT_RSP:
            {
                s_mtk_nfc_init_rsp *prDeInitRsp = NULL;
             
                // receive init resp msg from nfc daemon
                prDeInitRsp = (s_mtk_nfc_init_rsp *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
             
                // callback to unlock semaphore
                nfc_jni_deinit_callback(g_working_cb_data, prDeInitRsp->result);
                break;
            }
         
            case MTK_NFC_P2P_LINK_NTF:
            {
                MTK_NFC_LLCP_LINK_STATUS* pLink = NULL;
                pLink = (MTK_NFC_LLCP_LINK_STATUS*)((unsigned char*)prmsg+sizeof(MTK_NFC_MSG_T));
                if (pLink != NULL)
                {
                    ALOGD("MTK_NFC_P2P_LINK_NTF,%d", pLink->ret);
                    nfc_jni_llcp_linkStatus_callback(g_Discovery_nat, pLink->ret, pLink->elink);
                }
                break;
            }
            case MTK_NFC_P2P_CREATE_CLIENT_RSP:
            case MTK_NFC_P2P_CREATE_SERVER_RSP:
            {
                MTK_NFC_LLCP_RSP_SERVICE* pSerRsp = NULL;
                unsigned char u1Idx = 0;
             
                if (prmsg->type == MTK_NFC_P2P_CREATE_CLIENT_RSP)
                {
                    ALOGD("MTK_NFC_P2P_CREATE_CLIENT_RSP,Type,%d", prmsg->type);
                }
                else if (prmsg->type == MTK_NFC_P2P_CREATE_SERVER_RSP)
                {
                    ALOGD("MTK_NFC_P2P_CREATE_SERVER_RSP,Type,%d", prmsg->type);
                }
                pSerRsp = (MTK_NFC_LLCP_RSP_SERVICE*)((unsigned char*)prmsg+sizeof(MTK_NFC_MSG_T));

                if (pSerRsp != NULL)
                {             
                    ALOGD("ret,%d,0x%x", pSerRsp->ret, pSerRsp->llcp_handle);
                    g_cb_p2p_data.status = pSerRsp->ret;
                    if (pSerRsp->ret == 0)
                    {
                        hLlcpWorkingHandle =  pSerRsp->llcp_handle;
                        for (u1Idx = 0; u1Idx < NFC_LLCP_SERVICE_NUM ; u1Idx++)
                        {
                            if (g_llcp_service[u1Idx] == 0)
                            {
                                g_llcp_service[u1Idx] = hLlcpWorkingHandle;
                                ALOGD("Service,idx,%d,0x%x", u1Idx, g_llcp_service[u1Idx]);
                                if (prmsg->type == MTK_NFC_P2P_CREATE_SERVER_RSP)
                                {
                                    g_llcp_sertype[u1Idx] = 1;
                                }
                                else
                                {
                                    g_llcp_sertype[u1Idx] = 2;
                                }
                                break;
                            }
                        }
                        if(u1Idx >= NFC_LLCP_SERVICE_NUM)
                        {
                            ALOGD("Service,idx,%d undefine", u1Idx);
                        }
                    } 
                    else
                    {
                        hLlcpWorkingHandle = 0;
                    }
                }
                sem_post(&g_cb_p2p_data.sem);
                break;
            }
            case MTK_NFC_P2P_CREATE_SERVER_NTF:
            {
                MTK_NFC_LISTEN_SERVICE_NTF* pSerNtf = NULL;
             
                pSerNtf = (MTK_NFC_LISTEN_SERVICE_NTF*)((unsigned char*)prmsg+sizeof(MTK_NFC_MSG_T));

                if (pSerNtf  != NULL)
                {
                    ALOGD("MTK_NFC_P2P_CREATE_SERVER_NTF,ret,%d", pSerNtf->ret );
                    if (pSerNtf->ret == 0)
                    {
                        nfc_jni_llcp_transport_listen_socket_callback(pSerNtf);
                    }
                }
                break;
            } 
            case MTK_NFC_P2P_SOCKET_STATUS_NTF:
            case MTK_NFC_P2P_CONNECTION_NTF:
            {
                MTK_NFC_CONNECTION_NTF* pConnNtf = NULL;
             
                ALOGD("STATUS_NTF,%d", (prmsg->type));
                pConnNtf = (MTK_NFC_CONNECTION_NTF*)((unsigned char*)prmsg+sizeof(MTK_NFC_MSG_T));         
                if (pConnNtf != NULL)
                {
                    ALOGD("STATUS_NTF,ret,%d,code,%d" , pConnNtf->ret, pConnNtf->errcode);              
                    if (prmsg->type == MTK_NFC_P2P_CONNECTION_NTF)
                    {     
                        nfc_jni_connect_callback( pConnNtf->errcode,  pConnNtf->ret);
                    }
                }
                break;
            }
            case MTK_NFC_P2P_SEND_DATA_RSP:
            case MTK_NFC_P2P_DISC_RSP:
            case MTK_NFC_P2P_ACCEPT_SERVER_RSP:
            case MTK_NFC_P2P_CONNECT_CLIENT_RSP:
            { 
                MTK_NFC_LLCP_RSP* pRsp = NULL;
                int* pRet = NULL;
              
                pRsp = (MTK_NFC_LLCP_RSP*)((unsigned char*)prmsg+sizeof(MTK_NFC_MSG_T));  
                pRet = (int*)((unsigned char*)prmsg+sizeof(MTK_NFC_MSG_T));
                ALOGD("RSP Type,%d" , (prmsg->type));
                if (prmsg->type == MTK_NFC_P2P_DISC_RSP)
                {
                    nfc_jni_disconnect_callback(pRsp);
                }
                else if (prmsg->type == MTK_NFC_P2P_SEND_DATA_RSP)
                {
                    nfc_jni_llcp_send_callback(pRsp);
                }                         
                else if (prmsg->type == MTK_NFC_P2P_ACCEPT_SERVER_RSP)
                {
                    nfc_jni_llcp_accept_socket_callback((*pRet));
                }
                else if (prmsg->type == MTK_NFC_P2P_CONNECT_CLIENT_RSP)
                {
                    if ( pRsp->ret!= 0)
                    {
                        nfc_jni_connect_callback( 0xFF, (*pRet));
                    }
                }
                else
                {
                    ALOGE("RSP Type,%d" , (prmsg->type));
                }
                break;
            }
            case MTK_NFC_P2P_RECV_DATA_RSP:
            {
                MTK_NFC_RECVDATA_RSP* pSerRsp = NULL;

                pSerRsp = (MTK_NFC_RECVDATA_RSP*)((unsigned char*)prmsg+sizeof(MTK_NFC_MSG_T));
                nfc_jni_llcp_receive_callback(pSerRsp->ret,(unsigned char)pSerRsp->sap,  (unsigned char*)pSerRsp->llcp_data_recv_buf.buffer, pSerRsp->llcp_data_recv_buf.length, pSerRsp->service_handle);
                break;
            }         

            case MTK_NFC_P2P_GET_REM_SETTING_RSP:
            {
                MTK_NFC_GET_REM_SOCKET_RSP* pSetRsp = NULL;

                //ALOGD("MTK_NFC_P2P_GET_REM_SETTING_RSP");
                pSetRsp = (MTK_NFC_GET_REM_SOCKET_RSP*)((unsigned char*)prmsg+sizeof(MTK_NFC_MSG_T));
                nfc_jni_llcp_remote_setting_callback(pSetRsp);
                break;
            }
            case MTK_NFC_EXIT_RSP:
            {
                ALOGD("MTK_NFC_EXIT_RSP\n");

                // deinit socket here because this is the last socket access!!!
                android_nfc_socket_deinit();

                // ready to exit thread
                nat->running = FALSE;
            
                // callback to unlock semaphore
                nfc_jni_exit_callback(g_working_cb_data, 0); // always success
            
                break;
            }
            case MTK_NFC_SET_DISCOVER_RSP:
            {
                s_mtk_nfc_service_set_discover_resp_t *prSetDiscoverRsp = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_SET_DISCOVER_RSP");
                // receive init resp msg from nfc daemon
                prSetDiscoverRsp = (s_mtk_nfc_service_set_discover_resp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
              
                // callback to unlock semaphore
                nfc_jni_set_discover_rsp_callback(g_working_cb_data, prSetDiscoverRsp->u4Result);
                break;
            }
            case MTK_NFC_SET_DISCOVER_NTF:
            {
                s_mtk_nfc_service_set_discover_ntf_t *prSetDiscoverNtf = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_SET_DISCOVER_NTF");
                // receive init resp msg from nfc daemon
                prSetDiscoverNtf = (s_mtk_nfc_service_set_discover_ntf_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
              
                // callback to unlock semaphore
                nfc_jni_set_discover_ntf_callback(g_working_cb_data,
                    //nat,
                    g_Discovery_nat,
                    prSetDiscoverNtf->u4Result,
                    prSetDiscoverNtf->u4NumDicoverDev,
                    prSetDiscoverNtf->rDiscoverDev);
                break;
            }
            case MTK_NFC_DEV_SELECT_RSP:
            {
                s_mtk_nfc_service_dev_select_resp_t *prDevSelectRsp = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_DEV_SELECT_RSP");
                // receive init resp msg from nfc daemon
                prDevSelectRsp = (s_mtk_nfc_service_dev_select_resp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
              
                // callback to unlock semaphore
                nfc_jni_dev_select_rsp_callback(g_working_cb_data, prDevSelectRsp->u4Result);
                break;
            }
            case MTK_NFC_DEV_ACTIVATE_NTF:
            {
                s_mtk_nfc_service_dev_activate_ntf_t *prDevActivateNtf = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_DEV_ACTIVATE_NTF");
                // receive init resp msg from nfc daemon
                prDevActivateNtf = (s_mtk_nfc_service_dev_activate_ntf_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
              
                // callback to unlock semaphore
                ALOGD("JNI, DicoveryMode[0x%02X]\r\n", prDevActivateNtf->activate_ntf.rRfTechParam.u1Mode);
                #ifdef DTA_SUPPORT
                if ((dta_mode == true) && (pDetectionHandler != NULL))
                {
                    unsigned long tag_type = 0xFFFF;

                    if (0x01 == prDevActivateNtf->activate_ntf.u1Protocol)
                    {
                        ALOGD("Discovered T1T");
                        tag_type = 0x0001; // t1t
                    }
                    else if (0x02 == prDevActivateNtf->activate_ntf.u1Protocol)
                    {
                        ALOGD("Discovered T2T");
                        tag_type = 0x0002; // t2t
                    }
                    else if (0x03 == prDevActivateNtf->activate_ntf.u1Protocol)
                    {
                        ALOGD("Discovered T3T");
                        tag_type = 0x0003; // t3t
                    }
                    else if (0x04 == prDevActivateNtf->activate_ntf.u1Protocol)
                    {
                        ALOGD("Discovered T4T");
                        tag_type = 0x0004; // t4t, iso-dep
                    }
                    else
                    {
                        if ((prDevActivateNtf->activate_ntf.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_A)
                                || (prDevActivateNtf->activate_ntf.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_A_ACTIVE)
                                || (prDevActivateNtf->activate_ntf.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_F)
                                || (prDevActivateNtf->activate_ntf.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_F_ACTIVE))
                        {
                            ALOGD("Discovered P2P Target");
                            tag_type = 0x0005; // p2p target
                        }
                        else
                        {  
                            ALOGD("Discovered P2P Initiator");
                            tag_type = 0x0006; // p2p initiator
                        }
                    }
                    dta_detection_handler(tag_type,0);// 0 is success
                }
                else
                #endif
                {
                    nfc_jni_dev_activate_ntf_callback(g_working_cb_data,
                        //nat,
                        g_Discovery_nat,
                        0,
                        prDevActivateNtf->activate_ntf,
                        prDevActivateNtf->sTagParams);
                }
                break;
            }
            case MTK_NFC_DEV_DEACTIVATE_RSP:
            {
                s_mtk_nfc_service_dev_deactivate_resp_t *prDevDeactivateRsp = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_DEV_DEACTIVATE_RSP");
                // receive init resp msg from nfc daemon
                prDevDeactivateRsp = (s_mtk_nfc_service_dev_deactivate_resp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
              
                // callback to unlock semaphore
                nfc_jni_dev_deactivate_rsp_callback(g_working_cb_data,nat, prDevDeactivateRsp->u4Result);
                break;
            }
            case MTK_NFC_DEV_DEACTIVATE_NTF:
            {
                s_mtk_nfc_service_dev_deactivate_ntf_t *prDevDeactivateNtf = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_DEV_DEACTIVATE_NTF");
                // receive init resp msg from nfc daemon
                prDevDeactivateNtf = (s_mtk_nfc_service_dev_deactivate_ntf_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
              
                ALOGD("JNI-CLIENT: MTK_NFC_DEV_DEACTIVATE_NTF, %p,T,%d,R,%d", prDevDeactivateNtf, 
                         prDevDeactivateNtf->u1DeactType, prDevDeactivateNtf->u1DeactReason   );              
                // callback to unlock semaphore
                nfc_jni_dev_deactivate_ntf_callback(g_working_cb_data, 
                    nat,
                    0,
                    prDevDeactivateNtf->u1DeactType,
                    prDevDeactivateNtf->u1DeactReason);
                break;
            }
            case MTK_NFC_JNI_SE_MODE_SET_RSP: 
            {
                s_mtk_nfc_jni_se_set_mode_rsp_t *prSeModeSet = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_SE_MODE_SET_RSP");
                // receive init resp msg from nfc daemon
                prSeModeSet = (s_mtk_nfc_jni_se_set_mode_rsp_t*)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));

                nfc_jni_se_set_mode_callback(g_working_cb_data, prSeModeSet->sehandle, prSeModeSet->status);             
            }
                break;
            case MTK_NFC_GET_SELIST_RSP:
            {
                s_mtk_nfc_jni_se_get_list_rsp_t *prSeGetList = NULL;
        
                ALOGD("JNI-CLIENT: MTK_NFC_GET_SELIST_RSP");
                // receive init resp msg from nfc daemon
                 prSeGetList = (s_mtk_nfc_jni_se_get_list_rsp_t*)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
        
                memset( (UINT8*)&SEListInfor, 0, sizeof(s_mtk_nfc_jni_se_get_list_rsp_t));
                memcpy( (UINT8*)&SEListInfor, (UINT8*)prSeGetList, sizeof(s_mtk_nfc_jni_se_get_list_rsp_t));
       
                 nfc_jni_se_get_list_callback(g_working_cb_data, prSeGetList->status, &SEListInfor);            
            }
                break;
            case MTK_NFC_JNI_SE_GET_CUR_SE_RSP:
            {
                s_mtk_nfc_jni_se_get_cur_se_rsp_t *prSeGetCurSe = NULL;
        
                ALOGD("JNI-CLIENT: MTK_NFC_GET_SELIST_RSP");
                // receive init resp msg from nfc daemon
                 prSeGetCurSe = (s_mtk_nfc_jni_se_get_cur_se_rsp_t*)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
        
                 nfc_jni_se_get_cur_se_callback(g_working_cb_data, prSeGetCurSe->sehandle, prSeGetCurSe->status);            
            }
                break;

#if 1// READER MODE
            case MTK_NFC_JNI_CHECK_NDEF_RSP:
            {
             
                mtk_nfc_jni_CkNdef_CB_t *pCkNdefRsp = NULL; 
                ALOGD("MTK_NFC_JNI_CHECK_NDEF_RSP");
                // receive resp msg from nfc daemon
                pCkNdefRsp = (mtk_nfc_jni_CkNdef_CB_t *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
                ALOGE("MTK_NFC_JNI_CHECK_NDEF_RSP, result:%d",pCkNdefRsp->result);
                if( MTK_NFC_SUCCESS== pCkNdefRsp->result)
                {
                    memcpy(g_working_cb_data_tag->pContext, &pCkNdefRsp->ndefInfo, sizeof(mtk_nfc_CkNdef_CB_t)); 
                }
                else
                {
                    memset(g_working_cb_data_tag->pContext, 0, sizeof(mtk_nfc_CkNdef_CB_t));
                }
                nfc_jni_ckeck_ndef_callback(g_working_cb_data_tag, pCkNdefRsp->result);
                break;
            }
            case MTK_NFC_JNI_TAG_READ_RSP:
            {
                s_mtk_nfc_jni_doread_rsp_t *pRsp = NULL; 
                // receive resp msg from nfc daemon
                pRsp = (s_mtk_nfc_jni_doread_rsp_t *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));

                ALOGD("pRsp,pRsp->len(%d)",pRsp->len);
                nfc_jni_ndef_buf_len = pRsp->len;
                if((nfc_jni_ndef_buf_size < nfc_jni_ndef_buf_len) || (NULL == nfc_jni_ndef_buf) )
                {
                     ALOGD("pRsp,nfc_jni_ndef_buf_size(%d),nfc_jni_ndef_buf_len(%d)",nfc_jni_ndef_buf_size,nfc_jni_ndef_buf_len);
                    if(NULL != nfc_jni_ndef_buf)
                    {
                        free(nfc_jni_ndef_buf);
                    }
                    nfc_jni_ndef_buf = (uint8_t*)malloc(nfc_jni_ndef_buf_len);  
                    nfc_jni_ndef_buf_size = nfc_jni_ndef_buf_len;
                    ALOGD("pRsp,realloc,memory size(%d)",nfc_jni_ndef_buf_len);
                }
                memcpy(nfc_jni_ndef_buf, &pRsp->data, pRsp->len);
                nfc_jni_doRead_callback(g_working_cb_data_tag, pRsp->result);
                break;
            }
            case MTK_NFC_JNI_TAG_TRANSCEIVE_RSP:
            {
                s_mtk_nfc_jni_transceive_data_t *pRsp = NULL; 
                // receive resp msg from nfc daemon
                pRsp = (s_mtk_nfc_jni_transceive_data_t *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
                ALOGD("MTK_NFC_JNI_TAG_TRANSCEIVE_RSP,pRsp,pRsp->len[%d]",pRsp->datalen);
#ifdef DTA_SUPPORT
                if (dta_mode == true)
                {
                    dta_rf_cmd_rsp_len = pRsp->datalen;
                    ALOGD("malloc buffer for DTA,%d",dta_rf_cmd_rsp_len);
                    dta_rf_cmd_rsp = (unsigned char*)malloc(dta_rf_cmd_rsp_len);
                    ALOGD("buffer addr, %p",dta_rf_cmd_rsp);
                    memcpy(dta_rf_cmd_rsp, &pRsp->databuffer, dta_rf_cmd_rsp_len);
                    ALOGD("start of buffer,[%x]",*dta_rf_cmd_rsp);
                }
                else
                {
                    nfc_jni_ndef_buf_len = pRsp->datalen;
             
                    ALOGD("len copy_(%d),(%d)",nfc_jni_ndef_buf_len,pRsp->datalen);
                    if((nfc_jni_ndef_buf_size < nfc_jni_ndef_buf_len) || (NULL == nfc_jni_ndef_buf) )
                    {
                         ALOGD("pRsp,nfc_jni_ndef_buf_size(%d),nfc_jni_ndef_buf_len(%d)",nfc_jni_ndef_buf_size,nfc_jni_ndef_buf_len);
                        if(NULL != nfc_jni_ndef_buf)
                        {
                            free(nfc_jni_ndef_buf);
                        }
                        nfc_jni_ndef_buf = (uint8_t*)malloc(nfc_jni_ndef_buf_len+1);  //for felica
                        nfc_jni_ndef_buf_size = nfc_jni_ndef_buf_len;
                        ALOGD("pRsp,realloc,memory size(%d)",nfc_jni_ndef_buf_len);
                    }             
                    memcpy(nfc_jni_ndef_buf, &pRsp->databuffer, pRsp->datalen);
                }
#else
                {
                    nfc_jni_ndef_buf_len = pRsp->datalen;
             
                    ALOGD("len copy(%d),(%d)",nfc_jni_ndef_buf_len,pRsp->datalen);
                    if((nfc_jni_ndef_buf_size < nfc_jni_ndef_buf_len) || (NULL == nfc_jni_ndef_buf) )
                    {
                         ALOGD("pRsp,nfc_jni_ndef_buf_size(%d),nfc_jni_ndef_buf_len(%d)",nfc_jni_ndef_buf_size,nfc_jni_ndef_buf_len);
                        if(NULL != nfc_jni_ndef_buf)
                        {
                            free(nfc_jni_ndef_buf);
                        }
                        nfc_jni_ndef_buf = (uint8_t*)malloc(nfc_jni_ndef_buf_len+1);  //for felica
                        nfc_jni_ndef_buf_size = nfc_jni_ndef_buf_len;
                        ALOGD("pRsp,realloc,memory size(%d)",nfc_jni_ndef_buf_len);
                    }             
                    memcpy(nfc_jni_ndef_buf, &pRsp->databuffer, pRsp->datalen);
                }
#endif
                ALOGD("prepare to enter callback,nfc_jni_ndef_buf_len(%d),",nfc_jni_ndef_buf_len);
                nfc_jni_doTransceive_callback(g_working_cb_data_tag, pRsp->result);
                break;
            }
            case MTK_NFC_JNI_TAG_WRITE_RSP:
            {
                s_mtk_nfc_jni_rsp *pRsp = NULL; 
                // receive resp msg from nfc daemon
                pRsp = (s_mtk_nfc_jni_rsp *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
                nfc_jni_doWrite_callback(g_working_cb_data_tag, pRsp->result);
                break;
            }
            case MTK_NFC_JNI_TAG_PRESENCE_CK_RSP:
            {
                s_mtk_nfc_jni_rsp *pRsp = NULL; 
                // receive resp msg from nfc daemon
                pRsp = (s_mtk_nfc_jni_rsp *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
                nfc_jni_doPresenceCK_callback(g_working_cb_data_tag, pRsp->result);
                break;
            }
            case MTK_NFC_JNI_TAG_FORMAT_RSP:
            {
                s_mtk_nfc_jni_rsp *pRsp = NULL; 
                // receive resp msg from nfc daemon
                pRsp = (s_mtk_nfc_jni_rsp *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
                nfc_jni_doFormat_callback(g_working_cb_data_tag, pRsp->result);
                break;
            }
            case MTK_NFC_JNI_TAG_MAKEREADONLY_REQ:
            {
                s_mtk_nfc_jni_rsp *pRsp = NULL; 
                // receive resp msg from nfc daemon
                pRsp = (s_mtk_nfc_jni_rsp *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
                nfc_jni_doMakereadOnly_callback(g_working_cb_data_tag, pRsp->result);
                break;
            }
#endif
            case MTK_NFC_TEST_PNFC_RSP:
            {
                s_mtk_nfc_pnfc_rsq *prSetPnfcRsp = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_TEST_PNFC_RSP");
                // receive init resp msg from nfc daemon
                prSetPnfcRsp = (s_mtk_nfc_pnfc_rsq *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
              
                nfc_jni_set_pnfc_rsp_callback(g_working_cb_data, prSetPnfcRsp->u4result);
                break;
            }
            case MTK_NFC_SW_VERSION_RESPONSE:
            {
                s_mtk_nfc_sw_Version_rsp_t *pRsp = NULL; 
                pRsp = (s_mtk_nfc_sw_Version_rsp_t *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
                ALOGD("MTK NFC MW ver : %s\n", pRsp->mw_ver);
                ALOGD("MTK NFC FW ver : 0x%x\n", pRsp->fw_ver);
                ALOGD("MTK NFC HW ver : 0x%x\n", pRsp->hw_ver);
                nfc_jni_deinit_callback(g_working_cb_data, 0);
                break;
            }
#ifdef DTA_SUPPORT         
            case MTK_NFC_JNI_P2P_TRANSCEIVE_RSP:
            {
                MTK_NFC_P2P_TRANSCEIVE_DATA *pRsp = NULL; 
                // receive resp msg from nfc daemon
                pRsp = (MTK_NFC_P2P_TRANSCEIVE_DATA *)((UINT8 *)prmsg + sizeof(MTK_NFC_MSG_T));
                ALOGD("MTK_NFC_JNI_P2P_TRANSCEIVE_RSP,pRsp->len(%d)",pRsp->data_buf.length);

                if (dta_mode == true)
                {
                    dta_dep_rsp_len = pRsp->data_buf.length;
                    ALOGD("malloc buffer for DTA,%d",dta_dep_rsp_len);
                    dta_dep_rsp = (unsigned char*)malloc(dta_dep_rsp_len);
                    ALOGD("buffer addr,%p",dta_dep_rsp);
                    memcpy(dta_dep_rsp , pRsp->data_buf.buffer, dta_dep_rsp_len);
                    ALOGD("start of buffer,[%x]",*dta_dep_rsp);
                }
             
                ALOGD("prepare to enter callback");
                nfc_jni_doDepExchange_callback(g_working_cb_data, pRsp->ret);
                break;
            }         
#endif
#ifdef SUPPORT_EVT_TRANSACTION
            case MTK_NFC_DEV_EVT_NTF:
            {
                s_mtk_nfc_service_dev_event_ntf_t *prDevEventNtf = NULL;

                ALOGD("JNI-CLIENT: SUPPORT_EVT_TRANSACTION");
             
                prDevEventNtf = (s_mtk_nfc_service_dev_event_ntf_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
              
                nfc_jni_dev_event_ntf_callback(nat, prDevEventNtf);
                break;             
            }
#endif
#ifdef DTA_SUPPORT
            case MTK_NFC_EM_PNFC_CMD_RSP: 
            {
                s_mtk_nfc_em_pnfc_new_rsp *prPNFCRsp = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_EM_PNFC_CMD_RSP");

                prPNFCRsp = (s_mtk_nfc_em_pnfc_new_rsp*)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));

                nfc_jni_set_PNFC_callback(g_working_cb_data, prPNFCRsp->result);    
                   
                break;    
            }
#endif         
            case MTK_NFC_JNI_SE_OPEN_CONN_RSP:
            {
                s_mtk_nfc_jni_se_open_conn_rsp *prRsp = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_JNI_SE_OPEN_CONN_RSP");

                prRsp = (s_mtk_nfc_jni_se_open_conn_rsp*)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));

                nfc_jni_se_open_conn_callback(g_working_cb_data, prRsp->status, (UINT32)prRsp->conn_id);

                break;
            }

            case MTK_NFC_JNI_SE_CLOSE_CONN_RSP:
            {
                s_mtk_nfc_jni_se_close_conn_rsp *prRsp = NULL;

                ALOGD("JNI-CLIENT: MTK_NFC_JNI_SE_CLOSE_CONN_RSP");

                prRsp = (s_mtk_nfc_jni_se_close_conn_rsp*)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));

                nfc_jni_se_close_conn_callback(g_working_cb_data, prRsp->status);

                break;
            }     

            case MTK_NFC_JNI_SE_SEND_DATA_RSP:
            {
                s_mtk_nfc_jni_se_send_data_rsp *prRsp = NULL;
         
                ALOGD("JNI-CLIENT: MTK_NFC_JNI_SE_SEND_DATA_RSP");
         
                prRsp = (s_mtk_nfc_jni_se_send_data_rsp*)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
                
                nfc_jni_se_send_data_callback(g_working_cb_data, prRsp);
                
                break;
            }

            case MTK_NFC_JNI_SELECT_RF_INTERFACE_RSP:
            {
                mtk_nfc_select_rf_interface_rsp_t *prRsp = NULL;
            
                ALOGD("JNI-CLIENT: MTK_NFC_JNI_SELECT_RF_INTERFACE_RSP");

                prRsp = (mtk_nfc_select_rf_interface_rsp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
         
                nfc_jni_select_rf_interface_callback(g_working_cb_data, prRsp->u4Result);
            
                break;
            }

#ifdef MTK_NFC_HCE_SUPPORT 
         /// -- HCE [START]
            case MTK_NFC_JNI_SEND_RAW_FRAME_RSP:
            {
                mtk_nfc_ee_send_raw_frame_rsp_t *prRsp = NULL;         
                ALOGD("JNI-CLIENT: MTK_NFC_JNI_SEND_RAW_FRAME_RSP");         
                prRsp = (mtk_nfc_ee_send_raw_frame_rsp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));         
                nfc_jni_send_raw_frame_callback(g_working_cb_data, prRsp->result, prRsp);             
                break;            
            }
         
            case MTK_NFC_JNI_SET_DEFAULT_ROUTE_RSP:
            {
                mtk_nfc_ee_set_default_route_rsp_t *prRsp = NULL;         
                ALOGD("JNI-CLIENT: MTK_NFC_JNI_SET_DEFAULT_ROUTE_RSP");         
                prRsp = (mtk_nfc_ee_set_default_route_rsp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));         
                nfc_jni_set_default_route_callback(g_working_cb_data, prRsp->result, prRsp);             
                break;
            }
         
            case MTK_NFC_JNI_ADD_AID_ROUTING_RSP:
            {
                mtk_nfc_ee_add_aid_rsp_t *prRsp = NULL;         
                ALOGD("JNI-CLIENT: MTK_NFC_JNI_ADD_AID_ROUTING_RSP");         
                prRsp = (mtk_nfc_ee_add_aid_rsp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));         
                nfc_jni_add_aid_routing_callback(g_working_cb_data, prRsp->result, prRsp);             
                break;
            }
         
            case MTK_NFC_JNI_REMOVE_AID_ROUTING_RSP:
            {
                mtk_nfc_ee_remove_aid_rsp_t *prRsp = NULL;         
                ALOGD("JNI-CLIENT: MTK_NFC_JNI_REMOVE_AID_ROUTING_RSP");         
                prRsp = (mtk_nfc_ee_remove_aid_rsp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));         
                nfc_jni_remove_aid_routing_callback(g_working_cb_data, prRsp->result, prRsp);            
                break;
            }

            case MTK_NFC_JNI_UPDATE_ROUTING_RSP:
            {
                mtk_nfc_ee_update_routing_rsp_t *prRsp = NULL;         
                ALOGD("JNI-CLIENT: MTK_NFC_JNI_UPDATE_ROUTING_RSP");         
                prRsp = (mtk_nfc_ee_update_routing_rsp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));         
                nfc_jni_update_routing_callback(g_working_cb_data, prRsp->result, prRsp);              
                break;
            }

            case MTK_NFC_JNI_NOTIFY_HCE_DATA_RSP:
            {
                mtk_nfc_ee_notify_host_emu_data_t * prRsp = NULL;
                ALOGD("JNI-CLIENT: MTK_NFC_JNI_NOTIFY_HCE_DATA_RSP");
                prRsp = (mtk_nfc_ee_notify_host_emu_data_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
                //create thread to call JAVA method to avoid blocking socket read thread. 
                {
                    pthread_t th;
                    nfc_jni_hce_native_data_t hceNative;
                    hceNative.vm = nat->vm;
                    hceNative.manager = nat->manager;
                    memcpy(&(hceNative.notify_hce_data), prRsp, sizeof(mtk_nfc_ee_notify_host_emu_data_t));
                    pthread_create( &th, NULL, nfc_jni_notify_host_emu_data, (void *) &hceNative );
                }               
                break;
            }   
            /// -- HCE [END]
#endif
            case MTK_NFC_JNI_FIELD_ONOFF_NTF:
            {
                mtk_nfc_fieldinfo_ntf_t* prNtfRsp = (mtk_nfc_fieldinfo_ntf_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));
                nfc_jni_notify_Field_OnOff(nat,prNtfRsp);
                break;
            }
            case MTK_NFC_JNI_ACTIVATE_SE_INTERFACE_RSP:// for proactive command : ACTIVATE , rsp
            {
                mtk_nfc_activate_se_interface_rsp_t * prRsp = NULL;     
                ALOGD("JNI-CLIENT: MTK_NFC_JNI_ACTIVATE_SE_INTERFACE_RSP");         
                prRsp = (mtk_nfc_activate_se_interface_rsp_t *)((UINT8*)prmsg + sizeof(MTK_NFC_MSG_T));         
                nfc_jni_ActivateSeInterface_callback(g_working_cb_data, prRsp->u4Result, prRsp);               
                break;
            }
            default:
                ALOGE("unexpected msg type: %d\n", prmsg->type);
        }

        if (prmsg) {
            free(prmsg);
            ALOGD("free prmsg");
        } else {
            ALOGD("prmsg is null, no need to  free.");
        }
    }   
    ALOGD("MTK NFC client stopped\n");
    if (nat->vm)
    {
        nat->vm->DetachCurrentThread();
    }
    else
    {
        ALOGD("NULL VM");
    }

    return NULL;
}

// ---> Reader Mode
void nfc_jni_ckeck_ndef_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;
    
    ALOGE("nfc_jni_ckeck_ndef_callbackLC,%d", status);    
    pContextData->status = status;
    sem_post(&pContextData->sem);
}
void nfc_jni_doTransceive_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    ALOGD("nfc_jni_doTransceive_callback,%d", status);    
    if(pContextData != NULL)
    {
        pContextData->status = status;
        sem_post(&pContextData->sem);
        ALOGE("nfc_jni_doTransceive_callback,%d,%d\n",pContextData->status,status);
    }
    else
    {
        ALOGD("nfc_jni_doTransceive_callback->status is NULL");     
    }
}
void nfc_jni_doRead_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    ALOGD("nfc_jni_doRead_callback,%d", status);    
    if(pContextData != NULL)
    {
        pContextData->status = status;
        sem_post(&pContextData->sem);
    }
    else
    {
        ALOGD("nfc_jni_doRead_callback->status is NULL"); 
    }
}
void nfc_jni_doWrite_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    ALOGD("nfc_jni_doWrite_callback,%d", status);    
    if(pContextData != NULL)
    {
        pContextData->status = status;
        sem_post(&pContextData->sem);
    }
    else
    {
        ALOGD("nfc_jni_doWrite_callback->status is NULL");     
    }
}
void nfc_jni_doPresenceCK_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    ALOGD("nfc_jni_doPresenceCK_callback(%d)", status);    
    if(pContextData != NULL)
    {  
        pContextData->status = status;
        ALOGD("nfc_jni_doPresenceCK_ContextData->status(%x)", pContextData->status); 
        sem_post(&pContextData->sem);
    }
    else
    {
        ALOGD("nfc_jni_doPresenceCK_ContextData->status is NULL");     
    }
}
void nfc_jni_doFormat_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    ALOGD("nfc_jni_doFormat_callback,%d", status);    
    if(pContextData != NULL)
    {  
        pContextData->status = status;
        sem_post(&pContextData->sem);
    }
    else
    {
        ALOGD("nfc_jni_doFormat_callback->status is NULL");     
    }
}
void nfc_jni_doMakereadOnly_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    ALOGD("nfc_jni_doMakereadOnly_callback,%d", status);    
    if(pContextData != NULL)
    {
        pContextData->status = status;
        sem_post(&pContextData->sem);
    }
    else
    {
        ALOGD("nfc_jni_doMakereadOnly_callback->status is NULL");     
    }
}
// <--- Reader Mode

#ifdef DTA_SUPPORT
void nfc_jni_doDepExchange_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;
    
    ALOGD("nfc_jni_doDepExchange_callback,%d", status);    
    pContextData->status = status;
    
    ALOGE("nfc_jni_doDepExchange_callback,%d,%d\n",pContextData->status,status);
    
    sem_post(&pContextData->sem);
}
#endif

static void force_clear_all_resource(nfc_jni_native_data *nat)
{
    int ret;
    void *status;

    ALOGD("force_clear_all_resource...\n");

    // - clear socket interface
    android_nfc_socket_deinit();

    // - force killing nfcmw process
    android_nfc_daemon_deinit();

    // - force killing mtk client thread
    if ((nat != NULL) && (nat->thread != -1))
    {
        /* Wake up the read thread so it can exit */
        // - TBD by hiki

        /* block current thread until mtk client thread exit */ 
        ret = pthread_join(nat->thread, &status);
        if (ret != 0)
        {
            ALOGD("ERROR; return code from pthread_join() is %d\n", ret);
        }
        else
        {
            ALOGD("Main: completed join with thread and return status: %ld\n", (long)status);
        }
        nat->thread = -1;
    }

    // release Tag resource
    if(nfc_jni_ndef_buf != NULL)
    {
        free(nfc_jni_ndef_buf);
        nfc_jni_ndef_buf_len = 0;
        nfc_jni_ndef_buf_size = 0;
        ALOGD("Tag resource release\n");
    }
    ALOGD("force_clear_all_resource ok\n");
}

void kill_mtk_client(nfc_jni_native_data *nat)
{
    int result = FALSE;
    int bStackReset = FALSE;
    struct timespec ts;   
    struct nfc_jni_callback_data cb_data;

    ALOGD("kill_mtk_client...\n");

    #ifdef KILL_ZOMBIE_PROCESS
    ALOGD("create sem for kill_process_sem\n");
    if(sem_init(&kill_process_sem, 0, 0) == -1)
    {
        ALOGE("Semaphore creation failed (errno=0x%08x)\n", errno);
    }
    #endif

    // Create the local semaphore
    if (nfc_cb_data_init(&cb_data, NULL))
    {
        // assign working callback data pointer
        g_working_cb_data = &cb_data;
   
        // send deinit req msg to nfc daemon
        result = android_nfc_jni_send_msg(MTK_NFC_EXIT_REQ, 0, NULL);
        if (result == TRUE)
        {
            /* Wait for callback response (5s timeout) */
            clock_gettime(CLOCK_REALTIME, &ts);
            ts.tv_sec += 5;
            if(sem_timedwait(&cb_data.sem, &ts) == -1)
            {
                ALOGW("sem_timedwait fail: %d, %s\n", errno, strerror(errno));
                bStackReset = TRUE;

                // increase sem_count to advoid sem_destroy fail
                ALOGW("increase sem_count to advoid sem_destroy fail");
                sem_post(&cb_data.sem);            
            }
         
            /* Check Status (0: success, 1: fail) */
            if (cb_data.status != 0)
            {
                ALOGE("Failed to kill_mtk_client\n");
                bStackReset = TRUE;
            }      
        }
        else
        {
            ALOGE("send MTK_NFC_EXIT_REQ fail\n");
            bStackReset = TRUE;          
        }
   
        // clear working callback data pointer
        g_working_cb_data = NULL;
      
        nfc_cb_data_deinit(&cb_data);
    }
    else
    {
        ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
        bStackReset = TRUE;      
    }

    if(bStackReset == TRUE)
    {
        // force clear all resource
        force_clear_all_resource(nat);   
    }

    #ifdef KILL_ZOMBIE_PROCESS
    ALOGD("wait sem for kill_process_sem\n");

    /* Wait for callback response (2s timeout) */
    clock_gettime(CLOCK_REALTIME, &ts);
    ts.tv_sec += 2;
    if(sem_timedwait(&kill_process_sem, &ts) == -1)
    {
        ALOGW("sem_timedwait fail: %d, %s\n", errno, strerror(errno));

        // increase sem_count to advoid sem_destroy fail
        ALOGW("increase sem_count to advoid sem_destroy fail");
        sem_post(&kill_process_sem);     
    }

    ALOGD("destory sem for kill_process_sem\n");
    if (sem_destroy(&kill_process_sem))
    {
        ALOGE("Failed to destroy semaphore (errno=0x%08x)", errno);
    }   
    #endif

    ALOGD("kill_mtk_client ok\n");
}


//---  Initialization function ---
static int nfc_jni_initialize(struct nfc_jni_native_data *nat) 
{
    struct nfc_jni_callback_data cb_data;
    int result = FALSE;
    int ret = -1;
    int retry_cnt = 0; // retry count for socket creation
    MTK_NFC_MSG_T *pInitMsg = NULL;

    ALOGD("Start Initialization\n");

    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        goto clean_and_return;
    }

    #ifdef DTA_SUPPORT
    // use file to sync. dta mode flag between Device Test APP & NFC JNI native
    dta_mode = dta_file_check();
    #endif

    /* assign working callback data pointer */
    g_working_cb_data = &cb_data;

    /* Reset device connected handle */
    device_connected_flag = 0;

    /* ***************************************** */
    /* Initialize MTK NFC JNI                    */
    /* ***************************************** */   


    if (!driverConfigured)
    {
        // fork child process to run mtk nfc daemon
        ret = android_nfc_daemon_init();
        if (ret == FALSE)
        {
            ALOGE("android_nfc_daemon_init() fail\n");
            goto clean_and_return;
        }

        // Initialize socket interface
        ret = android_nfc_socket_init();
        if (ret == FALSE)
        {
            ALOGE("android_nfc_socket_init() fail\n");
            goto clean_and_return;
        }      

        // Create thread to receive msg from daemon
        if (pthread_create(&(nat->thread), NULL, 
             nfc_jni_mtk_client_thread, nat) != 0)
        {
            ALOGE("pthread_create failed\n");
            goto clean_and_return;
        }

        driverConfigured = TRUE;
    }

    /* ***************************************** */
    /* Initialize MTK NFC Middleware             */
    /* ***************************************** */
    // send init req msg to nfc daemon
    ret = android_nfc_jni_send_msg(MTK_NFC_INIT_REQ, 0, NULL);
    if (ret == FALSE)
    {
        ALOGE("send MTK_NFC_INIT_REQ fail\n");
        goto clean_and_return;
    }

    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }

    // Initialization Status (0: success, 1: fail)
    if (cb_data.status != 0)
    {
        ALOGE("Initialization fail, stauts: %d\n", cb_data.status);
        goto clean_and_return;
    }

    // Since DTA will run operation test with this API, set the DTA test flag here
    // If DTA initialized, set to operation mode first. 
    // If DTA run other tests, the test mode will be set in DTA enable discovery API
    if (dta_mode)
    {
        nat->rDiscoverConfig.u1DtaTestMode = 0x01; 
        //nat->rDiscoverConfig.i4DtaPatternNum = 0x00;      
        {
            FILE *fp;
            int idx = 0;
            char pData[] = DTA_MODE_IDENTITY;

            fp=fopen("/sdcard/mtknfcdtaPatternNum.txt","r+b");
            if (fp == NULL)
            {
                ALOGE("%s: can not open dta file", __FUNCTION__);
                //return;// FALSE;
            }
            else
            {
                fscanf(fp,"%d",&idx);
                fclose(fp);
            }        
            nat->rDiscoverConfig.i4DtaPatternNum = idx;   
        }
        ALOGD("nat->rDiscoverConfig.i4DtaPatternNum,%d", nat->rDiscoverConfig.i4DtaPatternNum);

        //set config path for operation test @mingyen
        {
            FILE *fp;
            char path[DTA_CONFIG_PATH_MAX_LEN];
            memset(path, 0, DTA_CONFIG_PATH_MAX_LEN);

            fp = fopen("/sdcard/mtknfcdtaConfigPath.txt","r+b");
            if (fp == NULL)
            {
                ALOGE("%s: can not open dta file ", __FUNCTION__);
            }
            else
            {
                // "%59c" avoid buffer over flow when path lenth > DTA_CONFIG_PATH_MAX_LEN
        // work around 150205 leo
                fscanf(fp, "%s", path);
                memcpy(nat->rDiscoverConfig.au1ConfigPath, path, strlen(path));
                fclose(fp);                    
            }
        }
        ALOGD("path : nat->rDiscoverConfig.au1ConfigPath,%s", nat->rDiscoverConfig.au1ConfigPath);
    }

    #if 0 // hiki, test SW ver
    /* ***************************************** */
    /* Check NFC MW/FW/HW Version                */
    /* ***************************************** */
    // send init req msg to nfc daemon
    ret = android_nfc_jni_send_msg(MTK_NFC_SW_VERSION_QUERY, 0, NULL);
    if (ret == FALSE)
    {
      ALOGE("send MTK_NFC_SW_VERSION_QUERY fail\n");
      goto clean_and_return;
    }

    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
      ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
      goto clean_and_return;
    }

    // Initialization Status (0: success, 1: fail)
    if (cb_data.status != 0)
    {
      ALOGE("MTK_NFC_SW_VERSION_QUERY fail, stauts: %d\n", cb_data.status);
      goto clean_and_return;
    }
    #endif

    ALOGI("NFC Initialized");
#ifdef MTK_NFC_QUICK_SUPPORT
        if (gNfcSlept == TRUE || gNfc_ACFD_mode == TRUE)
        {
            // init Quick Enable flow , when NFC setting off --> NFC Enter idle status
            nfc_jni_mtc_mos_mode(FALSE);
            ret = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
            // nfc_jni_deactivate Status (0: success, 1: fail)
            if (ret != 0)
            {
                ALOGE("send MTK_NFC_Deactivate_REQ fail\n");
                result = FALSE;
                goto clean_and_return;
            }
            else
            {
                gNfcSlept = FALSE;
                gNfc_ACFD_mode = FALSE;
            }
        }
#endif
    result = TRUE;

    clean_and_return:
    /* clear working callback data pointer */
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    /* clear all resource if init fail */
    if (result != TRUE)
    {
        if(nat)
        {
            kill_mtk_client(nat); // notice: need to clear working callback data first
            // clear control flag
            driverConfigured = FALSE;
            quick_enable_mode = FALSE;
            gNfcSlept = FALSE;
            gNfc_ACFD_mode = FALSE;
        }
    }   

    return result;
}
    /*******************************************************************************
    ** Author : Leo.Kuo
    **
    ** Function:        check_dta_quick_mode
    **
    ** Arguments:        QUICK_ENABLE_APP_SYNC
    **
    ** Description:
    **
    ** Returns:         bool : 0: FALSE , 1:TRUE;
    **
    *******************************************************************************/

static bool check_dta_quick_mode()
{
    ALOGD("[QE] %s start",__FUNCTION__);

     FILE *fp;

     fp=fopen(QUICK_ENABLE_APP_SYNC,"r");
    if (fp == NULL)
    {
       ALOGE("%s: can not open dta file", __FUNCTION__);
       return FALSE;// FALSE;
    }else
     {
        return TRUE;
    }
}

void dta_nfc_jni_set_dta_quick_mode(int flag)
{
    ALOGD("[QE] %s start ,Mode :%d",__FUNCTION__,flag);

        if(flag == TRUE){
           // save a tempfile
           FILE *fp;

           fp=fopen(QUICK_ENABLE_APP_SYNC,"w");
           if (fp == NULL)
           {
               ALOGE("[QE]%s: can not open dta file", __FUNCTION__);
           }
           fclose(fp);
        }else if(flag ==FALSE){

           if( remove( QUICK_ENABLE_APP_SYNC) != 0 )
               ALOGE( "[QE]%s:Error deleting file",__FUNCTION__ );
           else
               ALOGE( "[QE]%s:File successfully deleted",__FUNCTION__);

        }else if(flag == -1){
           ALOGD("[QE]%s force clear all !",__FUNCTION__);
           int result = FALSE;
           int bStackReset = FALSE;
           struct timespec ts;   
           struct nfc_jni_callback_data cb_data;
           ALOGD("[QE]force_clear_all_resource...\n");
           // - clear socket interface
           android_nfc_socket_deinit();
           // - force killing nfcmw process
           android_nfc_daemon_deinit();
           ALOGD("[QE]kill_mtk_client ok\n");
        }
        // set dta file for framework usage
}
/*******************************************************************************
** Author : Leo.Kuo 2015/07/09
**
** Function:        nfc_jni_deinitialize
**
** Arguments:        nfc_jni_native_data *nat
**
** Description:        check curMode
**                     if MODE_CARE == nat->rDiscoverConfig.u1OpMode --> phone shutdown
**                         1.W15Q3 NFC_FULL_POWER_CARD_MODE for PICC flow
**                     if MODE_IDLE == nat->rDiscoverConfig.u1OpMode --> NFC setting off
**                        1.W15Q2 NFC_QUICK_ENABLE
**
** Returns:         return_type
**
*******************************************************************************/
static int nfc_jni_deinitialize(struct nfc_jni_native_data *nat) 
{
    int result = 0;
    struct timespec ts;   
    struct nfc_jni_callback_data cb_data;
    bool NFC_FULL_POWER_CARD_MODE = false;

    ALOGD("%s,opMode:%d ",__FUNCTION__,nat->rDiscoverConfig.u1OpMode);
    if (MODE_CARD == nat->rDiscoverConfig.u1OpMode) {
         ALOGD("%s,Enter only card mode and  do phone shutdown special flow !",__FUNCTION__);
        /*Enter only card mode and  do phone shutdown special flow*/
        // 1.Support NFC_FULL_POWER_CARD_MODE for PICC flow
        NFC_FULL_POWER_CARD_MODE = ACFD_cfg_check();
        if (TRUE == NFC_FULL_POWER_CARD_MODE) {
            gNfc_ACFD_mode = TRUE;
            ALOGD("[K5]Start NFC_FULL_POWER_CARD_MODE && De-Initialization\n");
            //get NFC mode
            ALOGD("[K5]De-Initialization OpMode =%d",
                    nat->rDiscoverConfig.u1OpMode);
            //u10pMode ==2 , Enter only card mode

            ALOGD("[K5] Enter host off only Card mode ");
            //1.set card mode polling
            //2.send PNFC 407,1 Enter PICC Flow
            //3.don't kill nfcstackp
            // card mode
            nfc_jni_enable_discovery_with_info(nat, gDiscoveryInfo);

            // send PNFC 407,1
            result = nfc_jni_picc_mode();
            ALOGD("[K5] nfc_jni_picc_mode() result:%d ", result);
            //direct return , do nothig....
            ALOGD("[K5] Don't kill nfcstackp !");
            return TRUE;
        }

    }else if(MODE_IDLE == nat->rDiscoverConfig.u1OpMode){
        ALOGD("%s,Enter NFC Setting off flow !",__FUNCTION__);
        /*Enter NFC Setting off flow*/
        // 1. read nfc.cfg for quick enable mode ON/OFF
        // 2. enter mtc mos mode
#ifdef MTK_NFC_QUICK_SUPPORT
        if(FALSE == quick_enable_mode)
        {
            quick_enable_mode = quickEnable_cfg_check();
        }

        if (TRUE == quick_enable_mode)
        {
            ALOGD("[QE] %s quick_enable = %d", __FUNCTION__, quick_enable_mode);
            ALOGD("[QE]NFC Deinitialized");
            ALOGD("[QE]Check EM/DTA sync flow ");

            if (FALSE == dta_quick_mode)
            {
                dta_quick_mode = check_dta_quick_mode();
            }

            if (TRUE == dta_quick_mode)
            {
                ALOGD("[QE]Enter EM/DTA/Normal deinit flow ");
                dta_nfc_jni_set_dta_quick_mode (FALSE);
                dta_quick_mode = FALSE;
            }
            else
            {

                ALOGD("[QE] NFC De-Initialization nothing ...NFC Enter MTC Mos Mode Start.");
                nfc_jni_mtc_mos_mode (TRUE);
                result = nfc_jni_enable_discovery_with_info(nat, gDiscoveryInfo);
                ALOGD("[QE]%s: Enter NFC sleep Mode: result %d", __FUNCTION__, result);
                gNfcSlept = TRUE;
                //return TRUE;
            }
        }
#endif
    }
    ALOGD("Start De-Initialization\n");

    /* Create the local semaphore */
    if (!driverConfigured)
    {
        ALOGI("NFC already Deinitialized\n");
        return TRUE;
    }

    /* Create the local semaphore */
    if (nfc_cb_data_init(&cb_data, NULL))
    {
        // assign working callback data pointer
        g_working_cb_data = &cb_data;

        result = android_nfc_jni_send_msg(MTK_NFC_DEINIT_REQ, 0, NULL);
        if (result == TRUE)
        {         
            /* Wait for callback response (5s timeout) */
            clock_gettime(CLOCK_REALTIME, &ts);
            ts.tv_sec += 5;                  
            if(sem_timedwait(&cb_data.sem, &ts) == -1)
            {
                ALOGW("sem_timedwait fail: %d, %s\n", errno, strerror(errno));

                // increase sem_count to advoid sem_destroy fail
                ALOGW("increase sem_count to advoid sem_destroy fail");
                sem_post(&cb_data.sem);            
            }

            /* DeInitialization Status (0: success, 1: fail) */
            if (cb_data.status != 0)
            {
                ALOGE("Failed to deinit the stack\n");
            }      
        }
        else
        {
            ALOGE("send MTK_NFC_DEINIT_REQ fail\n");
        }

        // clear working callback data pointer
        g_working_cb_data = NULL;

        nfc_cb_data_deinit(&cb_data);
    }
    else
    {
        ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
    }
    if(FALSE == gNfcSlept) {
    // kill mtk client thread & nfcstackp child process
        kill_mtk_client(nat);
      
        driverConfigured = FALSE;
    }
    #ifdef DTA_SUPPORT
    // use file to sync. dta mode flag between Device Test APP & NFC JNI native
    dta_file_clear();
    dta_mode = FALSE;
    #endif

    ALOGI("NFC Deinitialized\n");

    return result;
}

/* Enable Polling Loop */
int nfc_jni_enable_discovery(struct nfc_jni_native_data *nat){

    struct nfc_jni_callback_data cb_data;
    int result = FALSE;
    int ret = -1;
    s_mtk_nfc_service_set_discover_req_t rSetDiscover;
    uint16_t PayloadSize = sizeof(s_mtk_nfc_service_set_discover_req_t);
       
    ALOGD("nfc_jni_enable_discovery()");
 
    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("nfc_jni_enable_discovery(), CB INIT FAIL");
        goto clean_and_return;
    }

    g_Discovery_nat = nat;
    ALOGD("nfc_jni_enable_discovery(),nat, %p", g_Discovery_nat);    
    if (!nat)
    {
        ALOGD("nfc_jni_enable_discovery(), INVALID NAT");
        goto clean_and_return;
    }
 
    /* assign working callback data pointer */
    g_working_cb_data = &cb_data;
 
    /* Reset device connected handle */
    device_connected_flag = 0;
 
    if (nat->fgPollingEnabled)
    {
        result = TRUE;
        goto clean_and_return;
    }

    if (nat->rDiscoverConfig.u1OpMode == 0)
    {
        ALOGD("Polling Loop Opmode is 0!!");
        result = TRUE;
        goto clean_and_return;
    }
    /* ***************************************** */
    /* Enable Polling Loop                       */
    /* ***************************************** */
    // send set discover req msg to nfc daemon
    
    memset(&rSetDiscover,0,sizeof(s_mtk_nfc_service_set_discover_req_t));
    memcpy(&rSetDiscover,&nat->rDiscoverConfig,sizeof(s_mtk_nfc_service_set_discover_req_t));

    ALOGD("JNI-Dicover,dur[%d],opmode[0x%02X],TypeA[%d],p2pRole[%d],[%d],p2pTypeF[%d]",rSetDiscover.u2TotalDuration,rSetDiscover.u1OpMode,rSetDiscover.reader_setting.fgEnTypeA ,
               rSetDiscover.p2p_setting.fgEnInitiatorMode, rSetDiscover.p2p_setting.fgEnPassiveMode, rSetDiscover.p2p_setting.fgEnTypeF );
    ret = android_nfc_jni_send_msg(MTK_NFC_SET_DISCOVER_REQ, PayloadSize, &rSetDiscover);

    if (ret == FALSE)
    {
        ALOGE("send MTK_NFC_SET_DISCOVER_REQ fail\n");
        goto clean_and_return;
    }
    
    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }
    ALOGD("nfc_jni_enable_discovery(), done");
    // Initialization Status (0: success, 1: fail)
    if(cb_data.status != 0)
    {
        enableDiscoveryErrorFlag = TRUE;
        ALOGE("nfc_jni_enable_discovery fail, stauts: %d, enableDiscoveryErrorFlag,%d \n",
            cb_data.status, enableDiscoveryErrorFlag);
        goto clean_and_return;
    }
    nat->fgPollingEnabled = TRUE;
    result = TRUE;
    
 clean_and_return:
    /* clear working callback data pointer */
    g_working_cb_data = NULL;
    nfc_cb_data_deinit(&cb_data);

    if (result != TRUE)
    {       
        ALOGD("nfc_jni_enable_discovery() result is FALSE");
    }    
    return result;
}


bool nfc_jni_enable_discovery_with_info(struct nfc_jni_native_data * nat,
    nfc_jni_discovery_info_t info) {

    //struct nfc_jni_callback_data cb_data;
    //struct nfc_jni_native_data *nat = NULL;
    int result = -1;
    int tech_mask = DEFAULT_TECH_MASK;
    bool response = false;
    
    ALOGD("%s: TechMask,0x%02x,LowPowerPolling,%d,ReaderMode,%d,EnableHostRouting,%d,EnableP2P,%d,restart,%d ,MtcMosMode,%d",
        __FUNCTION__ , info.technologies_mask, info.enable_lptd, info.reader_mode, info.enable_host_routing,
        info.enable_p2p,info.restart,gMtcMosMode);
    
    // 1. check nat  
    if (!nat)
    {
        ALOGE("%s: Invalid NAT", __FUNCTION__);
        goto clean_and_return;
    }
    
    // 2. Check technologies mask    
    if (info.technologies_mask == -1 )  //default value
    {
        tech_mask = nat->tech_mask;
    }    
    else
    {
        tech_mask = info.technologies_mask;
    }    
    ALOGD ("%s: tech_mask = 0x%02x", __FUNCTION__, tech_mask);

    //start polling when card mode enable on lock screen
    //if (nat->fgPollingEnabled && !(info.restart) )
    if (nat->fgPollingEnabled && !(info.restart) && tech_mask == 0)
    {
        ALOGE ("%s: already discovering(card mode and tech ==0 )", __FUNCTION__);
        goto clean_and_return;
    }    
    
       
    // 3.  If RF-Discover is  enable, stop RF-discover.
    result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
    ALOGD("%s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
    
    // 4. Check polling configuration    
    if (tech_mask != 0)
    {
        ALOGD("%s Before:A,%d,B,%d,F,%d,V,%d,BP,%d,K,%d", __FUNCTION__, 
            nat->rDiscoverConfig.reader_setting.fgEnTypeA,
            nat->rDiscoverConfig.reader_setting.fgEnTypeB,
            nat->rDiscoverConfig.reader_setting.fgEnTypeF,
            nat->rDiscoverConfig.reader_setting.fgEnTypeV,
            nat->rDiscoverConfig.reader_setting.fgEnTypeBP,
            nat->rDiscoverConfig.reader_setting.fgEnTypeK);  
        //reader mode only
        if (info.reader_mode)
        {
            nat->rDiscoverConfig.u1OpMode = MODE_READER;
            ALOGD("%s u1OpMode, %d (reader mode only)", __FUNCTION__,nat->rDiscoverConfig.u1OpMode);
        }
        else
        {
            //nat->rDiscoverConfig.u1OpMode = (MODE_READER|MODE_CARD|MODE_P2P);
            ALOGD("%s u1OpMode, %d", __FUNCTION__,nat->rDiscoverConfig.u1OpMode);
        }

#if 0  //skip enable_p2p parameter for MTK solution.
        //p2p mode
        nat->rDiscoverConfig.u1OpMode &= ~MODE_P2P; //clear p2p
        
        if (info.enable_p2p)
        {
            nat->rDiscoverConfig.u1OpMode |= MODE_P2P;
            ALOGD("%s u1OpMode, %d (enable_p2p)", __FUNCTION__, nat->rDiscoverConfig.u1OpMode);
        }
        else
        {            
            ALOGD("%s u1OpMode, %d (disable_p2p)", __FUNCTION__, nat->rDiscoverConfig.u1OpMode);
        }
#endif
        
        nat->rDiscoverConfig.reader_setting.fgEnTypeA = ((tech_mask & TECHNOLOGY_MASK_A ) != 0);
        nat->rDiscoverConfig.reader_setting.fgEnTypeB = ((tech_mask & TECHNOLOGY_MASK_B ) != 0); 
        nat->rDiscoverConfig.reader_setting.fgEnTypeF = ((tech_mask & TECHNOLOGY_MASK_F ) != 0);
        nat->rDiscoverConfig.reader_setting.fgEnTypeV = ((tech_mask & TECHNOLOGY_MASK_ISO15693 ) != 0);
        nat->rDiscoverConfig.reader_setting.fgEnTypeBP= ((tech_mask & TECHNOLOGY_MASK_B_PRIME ) != 0);
        nat->rDiscoverConfig.reader_setting.fgEnTypeK = ((tech_mask & TECHNOLOGY_MASK_KOVIO ) != 0);

        ALOGD("%s After:A,%d,B,%d,F,%d,V,%d,BP,%d,K,%d", __FUNCTION__, 
            nat->rDiscoverConfig.reader_setting.fgEnTypeA,
            nat->rDiscoverConfig.reader_setting.fgEnTypeB,
            nat->rDiscoverConfig.reader_setting.fgEnTypeF,
            nat->rDiscoverConfig.reader_setting.fgEnTypeV,
            nat->rDiscoverConfig.reader_setting.fgEnTypeBP,
            nat->rDiscoverConfig.reader_setting.fgEnTypeK);
    }
    else
    {
        nat->rDiscoverConfig.u1OpMode = MODE_CARD;  // card mode only
        ALOGD("%s: No technologies configured, stop polling.(card mode only)OpMode, %d",
            __FUNCTION__, nat->rDiscoverConfig.u1OpMode);        
    }

    // 5. Check listen configuration
    if (info.enable_host_routing)
    {       
        // Update routing
        result = nfc_jni_update_routing(true); // true : enable , false : disable
        ALOGD("%s: nfc_jni_update_routing done, result %d", __FUNCTION__, result);
    }
    else
    {
        result = nfc_jni_update_routing(false); // true : enable , false : disable
        ALOGD("%s: nfc_jni_update_routing done, result %d", __FUNCTION__, result);
    }

    // 6. Check LowPowerPolling
    if ( gLowPowerPollingOption )
    {
        result = nfc_jni_low_power_polling(info.enable_lptd);
        ALOGD("%s: nfc_jni_low_power_polling done, result %d", __FUNCTION__, result);  
    }
    else
    {
        result = nfc_jni_low_power_polling(gLowPowerPollingOption);
        ALOGD("%s: not support low power polling", __FUNCTION__);
    }

#if 0   
    //7. low power card mode
    // -- SWP test  in normal flow
    if (TRUE == gSwpTestInNormalMode )
    {
        // TODO : need verify 
        ALOGD("%s : SWP test in normal flow. Do not send PNFC",__FUNCTION__);
        nat->rDiscoverConfig.u1OpMode = 0x02; // card mode
        nat->rDiscoverConfig.u1DtaTestMode = 0x04; //SWP Test
        nat->rDiscoverConfig.i4DtaPatternNum = gSwpTestPatternNum; 
        ALOGD("opMode,%d,DtaTestMode,%d,DtaPatternNum,%d",
            nat->rDiscoverConfig.u1OpMode,nat->rDiscoverConfig.u1DtaTestMode,nat->rDiscoverConfig.i4DtaPatternNum);
    }
    else 
    {
        result = nfc_jni_low_power_card_mode(nat->rDiscoverConfig.u1OpMode);
        ALOGD("%s: nfc_jni_low_power_card_mode done, result %d", __FUNCTION__, result); 
    }
#endif
    // 8 Check Quick Enable Mode
    if(TRUE == gMtcMosMode){
        //send PNFC 406,1
        result = nfc_jni_mtc_mos_PNFC(TRUE);
        //before mode =0 , always change mode = 2 (card mode only)
        nat->rDiscoverConfig.u1OpMode = MODE_CARD;
    }
    else
    {   // send PNFC 406,0
        result = nfc_jni_mtc_mos_PNFC(FALSE);
    }
    

    
    // 9. Actually start discovery
    result = nfc_jni_enable_discovery(nat);
    ALOGD("%s: nfc_jni_enable_discovery done, result %d", __FUNCTION__, result);  

    if (result == TRUE) {
        response = true;
    } else {
        response = false;
    }
    
clean_and_return:
    
    ALOGD("%s, done, result %d", __FUNCTION__, result);      
        
    return response;

}


// Deactivate flow in NCI 
// type: 0:idle, 1: sleep, 2: sleep_AF, 3: discovery
int nfc_jni_deactivate(struct nfc_jni_native_data *nat, int type){

    struct nfc_jni_callback_data cb_data;
    int result = FALSE;
    int ret = -1;
    s_mtk_nfc_service_dev_deactivate_req_t rDevDeactivate;
    uint16_t PayloadSize = sizeof(s_mtk_nfc_service_dev_deactivate_req_t);

      
    ALOGD("nfc_jni_deactivate()");

    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
       goto clean_and_return;
    }
 
    /* assign working callback data pointer */
    g_working_cb_data = &cb_data;

    g_Deactive_nat = nat;

    if (!nat)
    {
       goto clean_and_return;
    }

    /* Reset device connected handle */
    device_connected_flag = 0;
 
    /* ***************************************** */
    /* Enable Polling Loop                       */
    /* ***************************************** */
    // send set discover req msg to nfc daemon


    rDevDeactivate.u1DeactType = type;
    ret = android_nfc_jni_send_msg(MTK_NFC_DEV_DEACTIVATE_REQ, PayloadSize, &rDevDeactivate);
    nat->fgHostRequestDeactivate = TRUE;
   
    if (ret == FALSE)
    {
        ALOGE("send MTK_NFC_DEACTIVATE_REQ fail\n");
        goto clean_and_return;
    }
    
    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }
    
    // Initialization Status (0: success, 1: fail)
    if(cb_data.status != 0)
    {
        ALOGE("nfc_jni_deactivate fail, stauts: %d\n", cb_data.status);
        //goto clean_and_return;
    }
 
    result = cb_data.status;
 
 clean_and_return:
 
    /* clear working callback data pointer */
    g_working_cb_data = NULL;
 
    nfc_cb_data_deinit(&cb_data);

    return result;
}

static int is_user_build() {
    
    char value[PROPERTY_VALUE_MAX];
    
    property_get("ro.build.type", value, "");
    
    return !strncmp("user", value, PROPERTY_VALUE_MAX);
}

/*
 * Last-chance fallback when there is no clean way to recover
 * Performs a software reset
  */
void emergency_recovery(struct nfc_jni_native_data *nat) {
#if 1 // bugfix for user load ANR
    ALOGE("emergency_recovery: force restart of NFC service");
#else
    if (!is_user_build()) {
       ALOGE("emergency_recovery: force restart of NFC service");
    } else {
       // dont recover immediately, so we can debug
       unsigned int t;
       for (t=1; t < 1000000; t <<= 1) {
           ALOGE("emergency_recovery: NFC stack dead-locked");
           sleep(t);
       }
    }
#endif   

    // kill child process nfcstackp
    kill_mtk_client(nat);

    // force a noisy crash
    abort();  
}


/****************************************************************************
 * Callbacks
 ****************************************************************************/

static void nfc_jni_init_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    ALOGD("nfc_jni_init_callback, %d, %p\n", status, pContext);

    if (pContextData != NULL)
    {
        pContextData->status = status;

        if (status != 0) //0 success, 1 fail
        {
            driverConfigured = FALSE;
            ALOGD("nfc_jni_init_callback, init fail, set driverConfigured = FALSE.\n");
        }
        sem_post(&pContextData->sem);
    }   
}

static void nfc_jni_deinit_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    ALOGD("nfc_jni_deinit_callback, %d, %p\n", status, pContext);

    if (pContextData != NULL)
    {
        pContextData->status = status;
        sem_post(&pContextData->sem);
    }
}

static void nfc_jni_exit_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    ALOGD("nfc_jni_exit_callback, %d, %p\n", status, pContext);

    if (pContextData != NULL)
    {
        pContextData->status = status;
        sem_post(&pContextData->sem);
    }      
}

static void nfc_jni_llcp_transport_listen_socket_callback(MTK_NFC_LISTEN_SERVICE_NTF* pSerNtf)
{  
    nfc_jni_listen_data_t * pListenData = NULL;
    nfc_jni_native_monitor_t* pMonitor = nfc_jni_get_monitor();
    int idx = 0;

    //  Handle MTK_NFC_P2P_CREATE_SERVER_NTF 

    for (idx = 0; idx < NFC_LLCP_SERVICE_NUM ; idx++)
    {
        if (pSerNtf->service_handle == g_llcp_service[idx])
        {  
            ALOGD("nfc_jni_llcp_transport_listen_socket_callback socket handle,%d,%x,%x",idx, pSerNtf->service_handle, g_llcp_service[idx]);   
            break;
        }
    }
   

    if (idx < NFC_LLCP_SERVICE_NUM)
    {    
        if (pSerNtf->incoming_handle != 0)
        {
            /* Store the connection request */
            pListenData = (nfc_jni_listen_data_t*)malloc(sizeof(nfc_jni_listen_data_t));

            if (pListenData == NULL)
            {
                ALOGE("Failed to create structure to handle incoming LLCP connection request");
                return;
            }
            pListenData->pServerSocket = pSerNtf->service_handle;
            pListenData->pIncomingSocket = pSerNtf->incoming_handle;

            LIST_INSERT_HEAD(&pMonitor->incoming_socket_head, pListenData, entries);
            /* Signal pending accept operations that the list is updated */
            pthread_cond_broadcast(&pMonitor->incoming_socket_cond[idx]);
            ALOGD("unlock,nfc_jni_llcp_transport_listen_socket_callback");           
        }
        else
        {
            ALOGE("incoming_handle is empty");
            return;
        }
    }
    else
    {
        ALOGE("socket_callback,%d", idx );
        return;
    }
}


/****************************************************************************
 * NFCManager methods
 ****************************************************************************/

static void nfc_jni_set_pnfc_rsp_callback(void *pContext, unsigned int u4Result)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_set_pnfc_rsp_callback", u4Result);

    ALOGD("JNI-CB: nfc_jni_set_pnfc_rsp_callback");

    pContextData->status = (NFCSTATUS)u4Result;

    sem_post(&pContextData->sem);
}

static void nfc_jni_set_discover_rsp_callback(void *pContext, unsigned int u4Result)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_set_discover_callback", u4Result);
    
    ALOGD("JNI-CB: nfc_jni_set_discover_rsp_callback");

    pContextData->status = (NFCSTATUS)u4Result;

    sem_post(&pContextData->sem);
}

void vAddActivateNotifiedRfParam (
    nfc_jni_native_data *nat,
    s_mtk_nfc_service_activate_param_t* prActivateParam,
    s_mtk_nfc_service_tag_param_t* prTagParam)
{
    static const char fn [] = "NfcTag::discoverTechnologies (activation)";
    ALOGD ("%s: enter", fn);

    nat->u4NumDevTech = 0;

    //save the stack's data structure for interpretation later
    memcpy (&(nat->rDevTagParam), (prTagParam), sizeof(s_mtk_nfc_service_tag_param_t));

    switch (prActivateParam->u1Protocol)
    {
    case NFC_DEV_PROTOCOL_T1T:
        nat->au4TechList [nat->u4NumDevTech] = TARGET_TYPE_ISO14443_3A; //is TagTechnology.NFC_A by Java API
        nat->au4TechIndex[nat->u4NumDevTech] = 0;
        nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
        break;

    case NFC_DEV_PROTOCOL_T2T:
        // could be MifFare UL or Classic or Kovio
        {
            unsigned char u1PaSelRsp = prActivateParam->rRfTechParam.TechParam.rTechParamPollA.u1SelRsp;
            unsigned char u1PaNfcId1_0 = prActivateParam->rRfTechParam.TechParam.rTechParamPollA.au1Nfcid1[0]; 
            
            if (((u1PaNfcId1_0 == 0x04) && (u1PaSelRsp == 0)) ||
                (u1PaSelRsp == 0x18) ||
                (u1PaSelRsp == 0x08))
            {
                if (u1PaSelRsp == 0)
                {
                    nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
                    nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
                    nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
                    //save the stack's data structure for interpretation later
                    memcpy (&(nat->rDiscoverDev[nat->u4NumDevTech].rRfTechParam), &(prActivateParam->rRfTechParam), sizeof(s_mtk_rf_tech_param_t));
                    nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_MIFARE_UL; //is TagTechnology.MIFARE_ULTRALIGHT by Java API
                    nat->au4TechIndex[nat->u4NumDevTech] = 0;
                }
                else
                {
                    nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
                    nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
                    nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
                    //save the stack's data structure for interpretation later
                    memcpy (&(nat->rDiscoverDev[nat->u4NumDevTech].rRfTechParam), &(prActivateParam->rRfTechParam), sizeof(s_mtk_rf_tech_param_t));
                    nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_MIFARE_CLASSIC; //is TagTechnology.MIFARE_ULTRALIGHT by Java API
                    nat->au4TechIndex[nat->u4NumDevTech] = 0;
                }
                nat->u4NumDevTech++;
            }            
            nat->au4TechList [nat->u4NumDevTech] = TARGET_TYPE_ISO14443_3A;  //is TagTechnology.NFC_A by Java API
            nat->au4TechIndex[nat->u4NumDevTech] = 0;
            nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
            nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
            nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
        }
        break;

    case NFC_DEV_PROTOCOL_T3T:
        nat->au4TechList [nat->u4NumDevTech] = TARGET_TYPE_FELICA;
        nat->au4TechIndex[nat->u4NumDevTech] = 0;
        nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
        break;

    case NFC_DEV_PROTOCOL_ISO_DEP: //type-4 tag uses technology ISO-DEP and technology A or B
        {
            unsigned char u1DiscoverMode = prActivateParam->rRfTechParam.u1Mode;
            nat->au4TechList [nat->u4NumDevTech] = TARGET_TYPE_ISO14443_4; //is TagTechnology.ISO_DEP by Java API
            nat->au4TechIndex[nat->u4NumDevTech] = 0;
            nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
            nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
            nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
            if ( (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_POLL_A) ||
                    (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_POLL_A_ACTIVE) ||
                    (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_LISTEN_A) ||
                    (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_LISTEN_A_ACTIVE) )
            {
                nat->u4NumDevTech++;
                nat->au4TechIndex[nat->u4NumDevTech] = 0;
                nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
                nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
                nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
                nat->au4TechList [nat->u4NumDevTech] = TARGET_TYPE_ISO14443_3A; //is TagTechnology.NFC_A by Java API
                //save the stack's data structure for interpretation later
                memcpy (&(nat->rDiscoverDev[nat->u4NumDevTech].rRfTechParam), &(prActivateParam->rRfTechParam), sizeof(s_mtk_rf_tech_param_t));
            }
            else if ( (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_POLL_B) ||
                    (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_LISTEN_B))// ||
    //                (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_POLL_B_PRIME) ||
    //                (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_LISTEN_B_PRIME) )
            {
                nat->u4NumDevTech++;
                nat->au4TechIndex[nat->u4NumDevTech] = 0;
                nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
                nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
                nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
                nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_ISO14443_3B; //is TagTechnology.NFC_B by Java API
                //save the stack's data structure for interpretation later
                memcpy (&(nat->rDiscoverDev[nat->u4NumDevTech].rRfTechParam), &(prActivateParam->rRfTechParam), sizeof(s_mtk_rf_tech_param_t));
            }
        }
        break;

    case NFC_DEV_PROTOCOL_15693: //is TagTechnology.NFC_V by Java API
        nat->au4TechList [nat->u4NumDevTech] = TARGET_TYPE_ISO15693;
        nat->au4TechIndex[nat->u4NumDevTech] = 0;
        nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
        break;
    case NFC_DEV_PROTOCOL_KOVIO:
        nat->au4TechList [nat->u4NumDevTech] = TARGET_TYPE_KOVIO_BAR_CODE;
        nat->au4TechIndex[nat->u4NumDevTech] = 0;
        nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
        break;
    case NFC_DEV_PROTOCOL_CI_CARD:
        nat->au4TechList [nat->u4NumDevTech] = TARGET_TYPE_ISO14443_3B; //is TagTechnology.NFC_A by Java API
        nat->au4TechIndex[nat->u4NumDevTech] = 0;
        nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
        //save the stack's data structure for interpretation later
        memcpy (&(nat->rDiscoverDev[nat->u4NumDevTech].rRfTechParam), &(prActivateParam->rRfTechParam), sizeof(s_mtk_rf_tech_param_t));
        android_nfc_print_debuglog_array(&nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollB.au1SensbRes[4],20);
        android_nfc_print_debuglog_array(&prActivateParam->rRfTechParam.TechParam.rTechParamPollB.au1SensbRes[4],20);
        break;
    default:
        ALOGE ("%s: Protocol Unknown?, protocol (%d)", fn,prActivateParam->u1Protocol);
        nat->au4TechList [nat->u4NumDevTech] = TARGET_TYPE_UNKNOWN;
        nat->au4TechIndex[nat->u4NumDevTech] = 0;
        nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prActivateParam->u1RfDiscId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prActivateParam->u1Protocol;
        break;
    }

    nat->u4NumDevTech++;
    for (int i=0; i < (int) nat->u4NumDevTech; i++)
    {
        ALOGD ("%s: index=%d; Tech=%d,techindex=%d; handle=%d, id = %d; nfc type (protocol)=%d", fn,
                i, nat->au4TechList[i], nat->au4TechIndex[i], nat->apTechHandles[i],nat->au4DevId[i],nat->au4TechProtocols[i]);
    }
    ALOGD ("%s: exit", fn);
}

void vAddDiscoverNotifiedTech (
    nfc_jni_native_data *nat,
    s_mtk_discover_device_ntf_t* prDiscoveryNtfParam,
    int index)
{
    static const char fn [] = "vAddDiscoverNotifiedTech";
    s_mtk_rf_tech_param_t* prRfParam = &prDiscoveryNtfParam->rRfTechParam;

    ALOGD ("%s: enter: rf disc. id=%u; protocol=%u, mNumTechList=%u", fn, prDiscoveryNtfParam->u1DiscoverId, prDiscoveryNtfParam->u1Protocol, nat->u4NumDevTech);
    if (nat->u4NumDevTech >= NUM_MAX_TECH)
    {
        ALOGE ("%s: exceed max=%d", fn, NUM_MAX_TECH);
        goto TheEnd;
    }
    
    //save the stack's data structure for interpretation later
    memcpy (&(nat->rDiscoverDev[nat->u4NumDevTech].rRfTechParam), prRfParam, sizeof(s_mtk_rf_tech_param_t));

    switch (prDiscoveryNtfParam->u1Protocol)
    {
    case NFC_DEV_PROTOCOL_T1T:
        nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_ISO14443_3A; //is TagTechnology.NFC_A by Java API
        nat->au4TechIndex[nat->u4NumDevTech] = index;        
        nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
        break;

    case NFC_DEV_PROTOCOL_T2T:
        {
            unsigned char u1PaSelRsp = prDiscoveryNtfParam->rRfTechParam.TechParam.rTechParamPollA.u1SelRsp; 
            
            //type-2 tags are identitical to Mifare Ultralight, so Ultralight is also discovered
            if (u1PaSelRsp == 0)
            {
                // mifare Ultralight
                nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
                nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
                nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
                nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_MIFARE_UL; //is TagTechnology.MIFARE_ULTRALIGHT by Java API
                nat->au4TechIndex[nat->u4NumDevTech] = index;
            }
            else
            {
                // mifare Ultralight
                nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
                nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
                nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
                nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_MIFARE_CLASSIC; //is TagTechnology.MIFARE_ULTRALIGHT by Java API
                nat->au4TechIndex[nat->u4NumDevTech] = index;
            }
            if (nat->u4NumDevTech <= NUM_MAX_TECH-2) 
            {
            // nat->u4NumDevTech : size:[0~8] do it by leo.kuo
                nat->u4NumDevTech++;
                nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
                nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
                nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
                nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_ISO14443_3A;  //is TagTechnology.NFC_A by Java API
                nat->au4TechIndex[nat->u4NumDevTech] = index;
    
            //save the stack's data structure for interpretation later
                memcpy (&(nat->rDiscoverDev[nat->u4NumDevTech].rRfTechParam), prRfParam, sizeof(s_mtk_rf_tech_param_t));
            }
            else
            {
                 ALOGE ("%s NFC_DEV_PROTOCOL_T2T: exceed max=%d", fn, NUM_MAX_TECH);
                 goto TheEnd;
            }
        }
        break;

    case NFC_DEV_PROTOCOL_T3T:
        nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
        nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_FELICA;
        nat->au4TechIndex[nat->u4NumDevTech] = index;
        break;

    case NFC_DEV_PROTOCOL_ISO_DEP: //type-4 tag uses technology ISO-DEP and technology A or B
        {
            unsigned char u1DiscoverMode = prDiscoveryNtfParam->rRfTechParam.u1Mode;
            
            nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_ISO14443_4; //is TagTechnology.ISO_DEP by Java API
            nat->au4TechIndex[nat->u4NumDevTech] = index;
            nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
            nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
            nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
            if (nat->u4NumDevTech <= NUM_MAX_TECH-2) 
            {
            // nat->u4NumDevTech : size:[0~8] do it by leo.kuo

                if ( (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_POLL_A) ||
                    (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_POLL_A_ACTIVE) ||
                    (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_LISTEN_A) ||
                    (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_LISTEN_A_ACTIVE))
                {
            
                    nat->u4NumDevTech++;
                    nat->au4TechIndex[nat->u4NumDevTech] = index;
                    nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
                    nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
                    nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
                    nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_ISO14443_3A; //is TagTechnology.NFC_A by Java API
                    //save the stack's data structure for interpretation later
                    memcpy (&(nat->rDiscoverDev[nat->u4NumDevTech].rRfTechParam), prRfParam, sizeof(s_mtk_rf_tech_param_t));
                }
                else if ( (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_POLL_B) ||
                        (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_LISTEN_B))// ||
      //                (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_POLL_B_PRIME) ||
      //                (u1DiscoverMode == NFC_DEV_DISCOVERY_TYPE_LISTEN_B_PRIME) )
                {
                    nat->u4NumDevTech++;
                    nat->au4TechIndex[nat->u4NumDevTech] = index;
                    nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
                    nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
                    nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
                    nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_ISO14443_3B; //is TagTechnology.NFC_B by Java API
                    //save the stack's data structure for interpretation later
                    memcpy (&(nat->rDiscoverDev[nat->u4NumDevTech].rRfTechParam), prRfParam, sizeof(s_mtk_rf_tech_param_t));
                }
            }
            else
            {
                 ALOGE ("%s NFC_DEV_PROTOCOL_ISO_DEP: exceed max=%d", fn, NUM_MAX_TECH);
                 goto TheEnd;
            }
        }
        break;

    case NFC_DEV_PROTOCOL_15693: //is TagTechnology.NFC_V by Java API
         nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
        nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_ISO15693;
        nat->au4TechIndex[nat->u4NumDevTech] = index;
        break;

    default:
        ALOGE ("%s: unknown protocol ????", fn);
        nat->au4TechList[nat->u4NumDevTech] = TARGET_TYPE_UNKNOWN;
        nat->au4TechIndex[nat->u4NumDevTech] = index;
        nat->apTechHandles[nat->u4NumDevTech] = nat->u4NumDevTech;
        nat->au4DevId[nat->u4NumDevTech] = prDiscoveryNtfParam->u1DiscoverId;
        nat->au4TechProtocols[nat->u4NumDevTech] = prDiscoveryNtfParam->u1Protocol;
        break;
    }

    nat->u4NumDevTech++;
    if (prDiscoveryNtfParam->fgMore == FALSE)
    {
        for (int i=0; i < (int)nat->u4NumDevTech; i++)
        {
            ALOGD ("%s: index=%d; RfIdx[%d] tech=%d; handle=%d, id = %d; nfc type (protocol)=%d", fn,
                    i, nat->au4TechList[i], nat->au4TechIndex[i], nat->apTechHandles[i],nat->au4DevId[i], nat->au4TechProtocols[i]);
        }
    }

TheEnd:
    
    ALOGD ("%s: exit", fn);
    
}


static unsigned int vCheckP2pAvailability(nfc_jni_native_data *nat)
{
    unsigned int i;
    unsigned int u4Rtn = 0; // no P2p

    for(i = 0; i < nat->u4NumDevTech; i++)
    {
        if (nat->au4TechProtocols[i] == NFC_DEV_PROTOCOL_NFC_DEP)
        {
            //if remote device supports P2P
            ALOGD ("vCheckP2pAvailability(): P2P Available");
            u4Rtn = i;
            break;
        }
    }
    return u4Rtn;
}

static void nfc_jni_set_discover_ntf_callback(void *pContext,
    nfc_jni_native_data *nat,
    unsigned int u4Result,
    unsigned int u4NumDev,
    s_mtk_discover_device_ntf_t rDiscoverDev[DISCOVERY_DEV_MAX_NUM])
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;
 
    LOG_CALLBACK("nfc_jni_set_discover_ntf_callback", u4Result);
    ALOGD("JNI-CB: nfc_jni_set_discover_ntf_callback");
 
    pContextData->status = (NFCSTATUS)u4Result;
    memset(&nat->rDiscoverDev[0],0,DISCOVERY_DEV_MAX_NUM*sizeof(s_mtk_discover_device_ntf_t));
    nat->u4NumDicoverDev = 0;
    if(u4Result == 0)
    {
        unsigned int count;

        nat->u4NumDicoverDev = u4NumDev;
        // fill discover data information
        memset(&nat->rDiscoverDev[0],0,DISCOVERY_DEV_MAX_NUM*sizeof(s_mtk_discover_device_ntf_t));
        for(count = 0; count < u4NumDev; count++)
        {
        vAddDiscoverNotifiedTech(nat,&rDiscoverDev[count], count);
        }
    }

    // select device, p2p first, or the first tag
    if (vCheckP2pAvailability(nat) > 0)
    {
        // select p2p, prefer F more than A
        unsigned int i; 
        unsigned char u1DevId = 0;
        s_mtk_nfc_service_dev_select_req_t rSelectDev;
        uint16_t PayloadSize = sizeof(s_mtk_nfc_service_dev_select_req_t);

        for (i = 0; i < nat->u4NumDevTech; i++)
        {
            unsigned char u1Mode = nat->rDiscoverDev[i].rRfTechParam.u1Mode;

            if (nat->au4TechProtocols[i] != NFC_DEV_PROTOCOL_NFC_DEP)
            {
                continue;
            }
            
            if ( (u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_F) ||
                 (u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_F_ACTIVE) )
            {
                u1DevId = (unsigned char)nat->au4DevId[i];
                break; //no need to search further
            }
            else if ( (u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_A) ||
                    (u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_A_ACTIVE) )
            {
                // choose first A
                if (u1DevId == 0)
                {
                    u1DevId = (unsigned char)nat->au4DevId[i];
                }
            }
        }
        memset(&rSelectDev, 0, sizeof(s_mtk_nfc_service_dev_select_req_t));
        rSelectDev.u1ID = u1DevId;
        rSelectDev.u1Protocol = NFC_DEV_PROTOCOL_NFC_DEP;
        rSelectDev.u1Interface = NFC_DEV_INTERFACE_NFC_DEP;

        ALOGD("send MTK_NFC_DEV_SELECT_REQ for P2P device with ID[%d]\n",u1DevId);
        if (FALSE == android_nfc_jni_send_msg(MTK_NFC_DEV_SELECT_REQ, PayloadSize, &rSelectDev))
        {
            ALOGE("send MTK_NFC_DEV_SELECT_REQ fail\n");
        }
        // clear Tag Information, because P2P selected
    }
    else
    {
        // select first tag
        s_mtk_nfc_service_dev_select_req_t rSelectDev;
        unsigned int PayloadSize = sizeof(s_mtk_nfc_service_dev_select_req_t);
        unsigned char u1RfInterface = NFC_DEV_INTERFACE_FRAME;

        if (nat->au4TechProtocols[0] == NFC_DEV_PROTOCOL_ISO_DEP)
        {
            u1RfInterface = NFC_DEV_INTERFACE_ISO_DEP;
        }
        else if (nat->au4TechProtocols[0] == NFC_DEV_PROTOCOL_NFC_DEP)
            u1RfInterface = NFC_DEV_INTERFACE_NFC_DEP;
        else
            u1RfInterface = NFC_DEV_INTERFACE_FRAME;

        memset(&rSelectDev, 0, sizeof(s_mtk_nfc_service_dev_select_req_t));
        rSelectDev.u1ID = nat->au4DevId[0];
        rSelectDev.u1Protocol = nat->au4TechProtocols[0];
        rSelectDev.u1Interface = u1RfInterface;

        ALOGD("send MTK_NFC_DEV_SELECT_REQ for 1st Tag with ID[%d],Protoco[0x%02X],Interface[0x%02X]\n",rSelectDev.u1ID, rSelectDev.u1Protocol,rSelectDev.u1Interface);
        if (FALSE == android_nfc_jni_send_msg(MTK_NFC_DEV_SELECT_REQ, PayloadSize, &rSelectDev))
        {
            ALOGE("send MTK_NFC_DEV_SELECT_REQ fail\n");
        }
    }
   
}

static void nfc_jni_dev_select_rsp_callback(void *pContext, unsigned int u4Result)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_dev_select_rsp_callback", u4Result);
    ALOGD("JNI-CB: nfc_jni_dev_select_rsp_callback, DO NOTHING NOW, wait activate notify");
    #if 0
    pContextData->status = (NFCSTATUS)u4Result;
    // to update the device inoformation
    sem_post(&pContextData->sem);
    #endif
}

void vAddTagTechList(JNIEnv* e, jclass tag_cls, jobject tag, nfc_jni_native_data *nat)
{
    static const char fn [] = "vAddTagTechList";
    ALOGD ("%s", fn);
    jfieldID f = NULL;

    //create objects that represent NativeNfcTag's member variables
    jintArray techList     = e->NewIntArray (nat->u4NumDevTech);
    jintArray handleList   = e->NewIntArray (nat->u4NumDevTech);
    jintArray typeList     = e->NewIntArray (nat->u4NumDevTech);

    jint* technologies = e->GetIntArrayElements (techList,     NULL);
    jint* handles      = e->GetIntArrayElements (handleList,   NULL);
    jint* types        = e->GetIntArrayElements (typeList,     NULL);
    for (int i = 0; i < (int)nat->u4NumDevTech; i++)
    {
        technologies [i] = nat->au4TechList[i];
        handles [i]      = nat->apTechHandles[i];
        types [i]        = nat->au4TechProtocols[i];
    }
    e->ReleaseIntArrayElements (techList,     technologies, 0);
    e->ReleaseIntArrayElements (handleList,   handles,      0);
    e->ReleaseIntArrayElements (typeList,     types,        0);

    f = e->GetFieldID (tag_cls, "mTechList", "[I");
    e->SetObjectField (tag, f, techList);

    f = e->GetFieldID (tag_cls, "mTechHandles", "[I");
    e->SetObjectField (tag, f, handleList);

    f = e->GetFieldID (tag_cls, "mTechLibNfcTypes", "[I");
    e->SetObjectField (tag, f, typeList);
}

//fill NativeNfcTag's members: mHandle, mConnectedTechnology
void vAddTechConnectedIndex (JNIEnv* e, jclass tag_cls, jobject tag)
{
    static const char fn [] = "vAddTechConnectedIndex";
    ALOGD ("%s", fn);
    jfieldID f = NULL;

    f = e->GetFieldID (tag_cls, "mConnectedTechIndex", "I");
    e->SetIntField (tag, f, (jint) 0);
}

void vAddTagPollBytes (
    JNIEnv* e, 
    jclass tag_cls, 
    jobject tag, 
    nfc_jni_native_data *nat, 
    s_mtk_nfc_service_tag_param_t* prTagParam)
{
    static const char fn [] = "vAddTagPollBytes";
    jfieldID f = NULL;
    jbyteArray pollBytes = e->NewByteArray (0);
    jobjectArray techPollBytes = e->NewObjectArray (nat->u4NumDevTech, e->GetObjectClass(pollBytes), 0);
    int len = 0;

    for (int i = 0; i < (int)nat->u4NumDevTech; i++)
    {
        ALOGD ("%s: index=%d; rf tech params mode=%u", fn, i, nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.u1Mode);
        switch (nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.u1Mode)
        {
        case NFC_DEV_DISCOVERY_TYPE_POLL_A:
        case NFC_DEV_DISCOVERY_TYPE_POLL_A_ACTIVE:
        case NFC_DEV_DISCOVERY_TYPE_LISTEN_A:
        case NFC_DEV_DISCOVERY_TYPE_LISTEN_A_ACTIVE:
            ALOGD ("%s: tech A", fn);
            pollBytes = e->NewByteArray (2);
            e->SetByteArrayRegion (pollBytes, 0, 2,
                    (jbyte*) &nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.TechParam.rTechParamPollA.au1SensRes);
            break;

        case NFC_DEV_DISCOVERY_TYPE_POLL_B:
//        case NFC_DEV_DISCOVERY_TYPE_POLL_B_PRIME:
        case NFC_DEV_DISCOVERY_TYPE_LISTEN_B:
//        case NFC_DEV_DISCOVERY_TYPE_LISTEN_B_PRIME:
            if (nat->au4TechList[i] == TARGET_TYPE_ISO14443_3B) //is TagTechnology.NFC_B by Java API
            {
                /*****************
                see NFC Forum Digital Protocol specification; section 5.6.2;
                in SENSB_RES response, byte 6 through 9 is Application Data, byte 10-12 or 13 is Protocol Info;
                used by public API: NfcB.getApplicationData(), NfcB.getProtocolInfo();
                *****************/
                ALOGD ("%s: tech B; TARGET_TYPE_ISO14443_3B", fn);
                
                len = nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.TechParam.rTechParamPollB.u1SensbResLen;
                len = len - 4; //subtract 4 bytes for NFCID0 at byte 2 through 5
                pollBytes = e->NewByteArray (len);
                e->SetByteArrayRegion (pollBytes, 0, len, (jbyte*) ( &nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.TechParam.rTechParamPollB.au1SensbRes[4]));
                android_nfc_print_debuglog_array(&nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollB.au1SensbRes[4],len);
            }
            else
                pollBytes = e->NewByteArray (0);
            break;

        case NFC_DEV_DISCOVERY_TYPE_POLL_F:
        case NFC_DEV_DISCOVERY_TYPE_POLL_F_ACTIVE:
        case NFC_DEV_DISCOVERY_TYPE_LISTEN_F:
        case NFC_DEV_DISCOVERY_TYPE_LISTEN_F_ACTIVE:
            {
                /****************
                see NFC Forum Type 3 Tag Operation Specification; sections 2.3.2, 2.3.1.2;
                see NFC Forum Digital Protocol Specification; sections 6.6.2;
                PMm: manufacture parameter; 8 bytes;
                System Code: 2 bytes;
                ****************/
                ALOGD ("%s: tech F", fn);
                UINT8 au1Result [FELICA_PM_MAX_LEN+FELICA_SYS_CODE_MAX_LEN]; //return result to NFC service
                memset (au1Result, 0, FELICA_PM_MAX_LEN+FELICA_SYS_CODE_MAX_LEN);
                len =  FELICA_PM_MAX_LEN+FELICA_SYS_CODE_MAX_LEN;

                /****
                for (int ii = 0; ii < mTechParams [i].param.pf.sensf_res_len; ii++)
                {
                    ALOGD ("%s: tech F, sendf_res[%d]=%d (0x%x)",
                          fn, ii, mTechParams [i].param.pf.sensf_res[ii],mTechParams [i].param.pf.sensf_res[ii]);
                }
                ***/
                
                /* JIS_X_6319_4: PAD0 (2 byte), PAD1 (2 byte), MRTI(2 byte), PAD2 (1 byte), RC (2 byte) */
                
                memcpy (&au1Result[0], &nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.TechParam.rTechParamPollF.au1PM[0], FELICA_PM_MAX_LEN); //copy PMm

                {

                    au1Result [8] = nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.TechParam.rTechParamPollF.au1SystemCode[0];
                    au1Result [9] = nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.TechParam.rTechParamPollF.au1SystemCode[1];
                    ALOGD ("%s: tech F; sys code=0x%X 0x%X", fn, au1Result [8], au1Result [9]);
                }
                pollBytes = e->NewByteArray (len);
                e->SetByteArrayRegion (pollBytes, 0, len, (jbyte*) au1Result);
            }
            break;

        case NFC_DEV_DISCOVERY_TYPE_POLL_ISO15693:
        case NFC_DEV_DISCOVERY_TYPE_LISTEN_ISO15693:
            {
                ALOGD ("%s: tech iso 15693", fn);
                //iso 15693 response flags: 1 octet
                //iso 15693 Data Structure Format Identifier (DSF ID): 1 octet
                //used by public API: NfcV.getDsfId(), NfcV.getResponseFlags();
                uint8_t data [2]= {prTagParam->TagParam.r15693TagParam.u1Afi,prTagParam->TagParam.r15693TagParam.u1Dsfid};

                pollBytes = e->NewByteArray (2);
                e->SetByteArrayRegion (pollBytes, 0, 2, (jbyte *) data);
            }
            break;

        default:
            ALOGE ("%s: tech unknown ????", fn);
            pollBytes = e->NewByteArray(0);
            break;
        } //switch: every type of technology
        e->SetObjectArrayElement (techPollBytes, i, pollBytes);
    } //for: every technology in the array
    f = e->GetFieldID (tag_cls, "mTechPollBytes", "[[B");
    e->SetObjectField (tag, f, techPollBytes);
}

void vAddTagActivatedData (
    JNIEnv* e, 
    jclass tag_cls, 
    jobject tag, 
    nfc_jni_native_data *nat, 
    s_mtk_nfc_service_tag_param_t* prTagParam)
{
    static const char fn [] = "vAddTagActivatedData";
    jfieldID f = NULL;
    jbyteArray actBytes = e->NewByteArray (0);
    jobjectArray techActBytes = e->NewObjectArray (nat->u4NumDevTech, e->GetObjectClass(actBytes), 0);
    jbyteArray uid = NULL;
    int len = 0;

    for (int i = 0; i < (int)nat->u4NumDevTech; i++)
    {
        ALOGD ("%s: index=%d", fn, i);
        switch (nat->au4TechProtocols[i])
        {
        case NFC_DEV_PROTOCOL_T1T:
            {
                ALOGD ("%s: T1T; tech A", fn);
                actBytes = e->NewByteArray (1);
                e->SetByteArrayRegion (actBytes, 0, 1,
                        (jbyte*) &nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.TechParam.rTechParamPollA.u1SelRsp);
            }
            break;

        case NFC_DEV_PROTOCOL_T2T:
            {
                ALOGD ("%s: T2T; tech A", fn);
                actBytes = e->NewByteArray (1);
                e->SetByteArrayRegion (actBytes, 0, 1,
                        (jbyte*) &nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.TechParam.rTechParamPollA.u1SelRsp);
            }
            break;

        case NFC_DEV_PROTOCOL_T3T: //felica
            {
                ALOGD ("%s: T3T; felica; tech F", fn);
                //really, there is no data
                actBytes = e->NewByteArray (0);
            }
            break;

        case NFC_DEV_PROTOCOL_ISO_DEP: //t4t
            {
                if (nat->au4TechList[i] == TARGET_TYPE_ISO14443_4) //is TagTechnology.ISO_DEP by Java API
                {
                    unsigned char u1Mode = nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.u1Mode;
                    if ( (u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_A) ||
                            (u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_A_ACTIVE) ||
                            (u1Mode == NFC_DEV_DISCOVERY_TYPE_LISTEN_A) ||
                            (u1Mode == NFC_DEV_DISCOVERY_TYPE_LISTEN_A_ACTIVE) )
                    {
                        //see NFC Forum Digital Protocol specification, section 11.6.2, "RATS Response"; search for "historical bytes";
                        //copy historical bytes into Java object;
                        //the public API, IsoDep.getHistoricalBytes(), returns this data;
                        if (nat->rDevActivateParam.rIntfaceParam.u1Type == NFC_DEV_INTERFACE_ISO_DEP)
                        {
                            s_mtk_nfc_service_PA_iso_dep_param_t* prPaIsoParam = &nat->rDevActivateParam.rIntfaceParam.InterfaceParam.rPAIsoDep;
                            ALOGD ("%s: T4T-A; ISO_DEP for tech A; copy historical bytes; len=%u", fn, prPaIsoParam->u1HistoricalByteLen);
                            actBytes = e->NewByteArray (prPaIsoParam->u1HistoricalByteLen);
                            if (prPaIsoParam->u1HistoricalByteLen > 0)
                                e->SetByteArrayRegion (actBytes, 0, prPaIsoParam->u1HistoricalByteLen, (jbyte*) (&prPaIsoParam->au1HistoricalByte));
                        }
                        else
                        {
                            ALOGE ("%s: T4T-A; ISO_DEP for tech A; wrong interface=%u", fn, nat->rDevActivateParam.rIntfaceParam.u1Type);
                            actBytes = e->NewByteArray (0);
                        }
                    }
                    else if ( (u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_B) ||
                            (u1Mode == NFC_DEV_DISCOVERY_TYPE_LISTEN_B))// ||
//                            (u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_B_PRIME) ||
//                            (u1Mode == NFC_DEV_DISCOVERY_TYPE_LISTEN_B_PRIME) )
                    {
                        //see NFC Forum Digital Protocol specification, section 12.6.2, "ATTRIB Response";
                        //copy higher-layer response bytes into Java object;
                        //the public API, IsoDep.getHiLayerResponse(), returns this data;
                        if (nat->rDevActivateParam.rIntfaceParam.u1Type == NFC_DEV_INTERFACE_ISO_DEP)
                        {
                            s_mtk_nfc_service_PB_iso_dep_param_t* prPbIsoParam = &nat->rDevActivateParam.rIntfaceParam.InterfaceParam.rPBIsoDep;
                            ALOGD ("%s: T4T-A; ISO_DEP for tech B; copy response bytes; len=%u", fn, prPbIsoParam->u1HiInfoLen);
                            actBytes = e->NewByteArray (prPbIsoParam->u1HiInfoLen);
                            if (prPbIsoParam->u1HiInfoLen > 0)
                               {
                                e->SetByteArrayRegion (actBytes, 0, prPbIsoParam->u1HiInfoLen, (jbyte*) (&prPbIsoParam->au1HiInfo[0]));
                              }
                        }
                        else
                        {
                            ALOGE ("%s: T4T-B; ISO_DEP for tech B; wrong interface=%u", fn, nat->rDevActivateParam.rIntfaceParam.u1Type);
                            actBytes = e->NewByteArray (0);
                        }
                    }
                }
                else if (nat->au4TechList[i] == TARGET_TYPE_ISO14443_3A) //is TagTechnology.NFC_A by Java API
                {
                    ALOGD ("%s: T4T; tech A", fn);
                    actBytes = e->NewByteArray (1);
                    e->SetByteArrayRegion (actBytes, 0, 1, (jbyte*) &nat->rDiscoverDev[nat->au4TechIndex[i]].rRfTechParam.TechParam.rTechParamPollA.u1SelRsp);
                }
                else
                {
                    actBytes = e->NewByteArray (0);
                }
            } //case NFC_PROTOCOL_ISO_DEP: //t4t
            break;

        case NFC_DEV_PROTOCOL_15693:
            {
                ALOGD ("%s: tech iso 15693", fn);
                //iso 15693 response flags: 1 octet
                //iso 15693 Data Structure Format Identifier (DSF ID): 1 octet
                //used by public API: NfcV.getDsfId(), NfcV.getResponseFlags();
                uint8_t data [2]= {prTagParam->TagParam.r15693TagParam.u1Afi, prTagParam->TagParam.r15693TagParam.u1Dsfid};
                actBytes = e->NewByteArray (2);
                e->SetByteArrayRegion (actBytes, 0, 2, (jbyte *) data);
            }
            break;
        case NFC_DEV_PROTOCOL_CI_CARD: //felica
            {
                ALOGD ("%s: NFC_DEV_PROTOCOL_CI_CARD", fn);
                //really, there is no data
                actBytes = e->NewByteArray (0);
            }
            break;
        default:
            ALOGD ("%s: tech unknown ????", fn);
            actBytes = e->NewByteArray (0);
            break;
        }//switch
        e->SetObjectArrayElement (techActBytes, i, actBytes);
    } //for: every technology in the array
    f = e->GetFieldID (tag_cls, "mTechActBytes", "[[B");
    e->SetObjectField (tag, f, techActBytes);
}

void vAddTagUid (
    JNIEnv* e, 
    jclass tag_cls, 
    jobject tag, 
    nfc_jni_native_data *nat, 
    s_mtk_nfc_service_tag_param_t* prTagParam)
{
    static const char fn [] = "vAddTagUid";
    jfieldID f = NULL;
    int len = 0;
    jbyteArray uid = NULL;

    switch (nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.u1Mode)
    {

    case NFC_DEV_DISCOVERY_TYPE_POLL_K_PASSIVE:
        ALOGD ("%s: tech k", fn);
        ALOGD ("u1DataFormat,%d", (nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollK.u1DataFormat));
        if((0x00) == (nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollK.u1DataFormat & (0xC0))    )
        {
            len = 16;
        }
        else
        {
            len = 32;
        }
        uid = e->NewByteArray (len);
        e->SetByteArrayRegion (uid, 0, len,
                (jbyte*) &nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollK.au1Data[0]);
        break;

    case NFC_DEV_DISCOVERY_TYPE_POLL_A:
    case NFC_DEV_DISCOVERY_TYPE_POLL_A_ACTIVE:
    case NFC_DEV_DISCOVERY_TYPE_LISTEN_A:
    case NFC_DEV_DISCOVERY_TYPE_LISTEN_A_ACTIVE:
        ALOGD ("%s: tech A", fn);
        len = nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollA.u1Nfcid1Len;
        uid = e->NewByteArray (len);
        e->SetByteArrayRegion (uid, 0, len,
                (jbyte*) &nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollA.au1Nfcid1[0]);
        break;

    case NFC_DEV_DISCOVERY_TYPE_POLL_B:
//    case NFC_DEV_DISCOVERY_TYPE_POLL_B_PRIME:
    case NFC_DEV_DISCOVERY_TYPE_LISTEN_B:
//    case NFC_DEV_DISCOVERY_TYPE_LISTEN_B_PRIME:
        ALOGD ("%s: tech B", fn);
        
        len = nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollB.Nfcid_len;
        uid = e->NewByteArray (len);
        e->SetByteArrayRegion (uid, 0, len,
                (jbyte*) &nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollB.au1Nfcid0[0]);
        android_nfc_print_debuglog_array(nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollB.au1Nfcid0,len);
        break;

    case NFC_DEV_DISCOVERY_TYPE_POLL_F:
    case NFC_DEV_DISCOVERY_TYPE_POLL_F_ACTIVE:
    case NFC_DEV_DISCOVERY_TYPE_LISTEN_F:
    case NFC_DEV_DISCOVERY_TYPE_LISTEN_F_ACTIVE:
        ALOGD ("%s: tech F", fn);
        uid = e->NewByteArray (FELICA_ID_MAX_LEN);
        e->SetByteArrayRegion (uid, 0, FELICA_ID_MAX_LEN,
                (jbyte*) &nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollF.au1IDm);
        break;

    case NFC_DEV_DISCOVERY_TYPE_POLL_ISO15693:
    case NFC_DEV_DISCOVERY_TYPE_LISTEN_ISO15693:
        {
            ALOGD ("%s: tech iso 15693", fn);
            jbyte data [NFC_ISO15693_UID_MAX_LEN];  //8 bytes
            for (int i=0; i<NFC_ISO15693_UID_MAX_LEN; ++i) //reverse the ID
                data[i] = nat->rDiscoverDev[nat->au4TechIndex[0]].rRfTechParam.TechParam.rTechParamPollV.au1Uid[NFC_ISO15693_UID_MAX_LEN - i - 1];
            uid = e->NewByteArray (NFC_ISO15693_UID_MAX_LEN);
            e->SetByteArrayRegion (uid, 0, NFC_ISO15693_UID_MAX_LEN, data);
        }
        break;

    default:
        ALOGE ("%s: tech unknown ????", fn);
        uid = e->NewByteArray (0);
        break;
    } //if
    f = e->GetFieldID(tag_cls, "mUid", "[B");
    e->SetObjectField(tag, f, uid);
}

void vAddNfcTag (nfc_jni_native_data *nat,
    s_mtk_nfc_service_activate_param_t rActivateParam,
    s_mtk_nfc_service_tag_param_t rTagParam)
{
    static const char fn [] = "vAddNfcTag";
    ALOGD ("%s: enter", fn);
    JNIEnv* e = NULL;
    jclass tag_cls = NULL;
    jmethodID ctor = NULL;
    jobject tag = NULL;

    //acquire a pointer to the Java virtual machine
    nat->vm->AttachCurrentThread (&e, NULL);
    if (e == NULL)
    {
        ALOGE("%s: jni env is null", fn);
        goto TheEnd;
    }

    tag_cls = e->GetObjectClass (nat->cached_NfcTag);
    if (e->ExceptionCheck())
    {
        e->ExceptionClear();
        ALOGE("%s: failed to get class", fn);
        goto TheEnd;
    }

    //create a new Java NativeNfcTag object
    ctor = e->GetMethodID (tag_cls, "<init>", "()V");
    tag = e->NewObject (tag_cls, ctor);

    //fill NativeNfcTag's mProtocols, mTechList, mTechHandles, mTechLibNfcTypes
    vAddTagTechList (e, tag_cls, tag,nat);

    //fill NativeNfcTag's members: mHandle, mConnectedTechnology
    vAddTechConnectedIndex (e, tag_cls, tag);

    //fill NativeNfcTag's members: mTechPollBytes
    vAddTagPollBytes (e, tag_cls, tag, nat,&rTagParam);

    //fill NativeNfcTag's members: mTechActBytes
    vAddTagActivatedData (e, tag_cls, tag, nat, &rTagParam);

    //fill NativeNfcTag's members: mUid
    vAddTagUid (e, tag_cls, tag, nat, &rTagParam);

    if (nat->tag != NULL) {
        e->DeleteGlobalRef (nat->tag);
    }
    nat->tag = e->NewGlobalRef (tag);

    //notify NFC service about this new tag
    ALOGD ("%s: try notify nfc service", fn);
    e->CallVoidMethod (nat->manager, cached_NfcManager_notifyNdefMessageListeners, tag);
    if (e->ExceptionCheck())
    {
        e->ExceptionClear();
        ALOGE ("%s: fail notify nfc service", fn);
    }
    e->DeleteLocalRef (tag);

TheEnd:
    
    nat->vm->DetachCurrentThread ();
    
    ALOGD ("%s: exit", fn);
    
}

void vAddNfcP2p (nfc_jni_native_data *nat,
    s_mtk_nfc_service_activate_param_t rActivateParam)
{
    static const char fn [] = "vAddNfcP2p";
    JNIEnv* e = NULL;
    jclass tag_cls = NULL;
    jmethodID ctor = NULL;
    jobject tag = NULL;
    jfieldID f;
    jbyteArray tagUid;
    jbyteArray generalBytes = NULL;

    ALOGD ("%s: enter", fn);

    //acquire a pointer to the Java virtual machine
    nat->vm->AttachCurrentThread (&e, NULL);
    if (e == NULL)
    {
        ALOGE("%s: jni env is null", fn);
        goto TheEnd;
    }

     tag_cls = e->GetObjectClass (nat->cached_P2pDevice);
     if (e->ExceptionCheck())
     {
         e->ExceptionClear();
         ALOGE("%s: failed to get class", fn);
         goto TheEnd;
     }
 
     //create a new Java NativeNfcTag object
     ctor = e->GetMethodID (tag_cls, "<init>", "()V");
     tag = e->NewObject (tag_cls, ctor);
     
    
     /* Set P2P Target mode */
     f = e->GetFieldID(tag_cls, "mMode", "I"); 
     
     if ((rActivateParam.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_A)
         || (rActivateParam.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_A_ACTIVE)
         || (rActivateParam.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_F)
         || (rActivateParam.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_POLL_F_ACTIVE))
     {
         ALOGD("Discovered P2P Target");
         e->SetIntField(tag, f, (jint)MODE_P2P_TARGET);
     }
     else
     {  
         ALOGD("Discovered P2P Initiator");
         e->SetIntField(tag, f, (jint)MODE_P2P_INITIATOR);
     }
         
     /* Set tag handle */
     f = e->GetFieldID(tag_cls, "mHandle", "I");
     e->SetIntField(tag, f,(jint)0x9876);
     TRACE("Target handle = 0x%08x",0x9876);
 
     if (nat->tag != NULL) {
         e->DeleteGlobalRef (nat->tag);
     }
     nat->tag = e->NewGlobalRef (tag);
 
     //notify NFC service about this new tag
     ALOGD ("%s: try notify nfc service", fn);
     e->CallVoidMethod (nat->manager, cached_NfcManager_notifyLlcpLinkActivation, tag);
     if (e->ExceptionCheck())
     {
         e->ExceptionClear();
         ALOGE ("%s: fail notify nfc service", fn);
     }
     e->DeleteLocalRef (tag);
    
     if (device_connected_flag !=1 )
     {
         ALOGE ("llcp link is down");
     }
TheEnd:
    
    nat->vm->DetachCurrentThread ();
    
    ALOGD ("%s: exit", fn);
    
}

// -->  L Migration
#ifdef MTK_NFC_QUICK_SUPPORT
static bool nfc_jni_mtc_mos_mode(bool on_off){
    ALOGD("[QE]%s",__FUNCTION__);
    bool response = false;
    int  result   = 0;
    if(true == on_off){
        ALOGD("[QE]%s :NFC Enter MTC MOS MODE",__FUNCTION__);
		gMtcMosMode = true;
		//nat->rDiscoverConfig.u1OpMode = 0x02;
        // only card mode
		//response = nfc_jni_enable_discovery_with_info(nat, gDiscoveryInfo);

    }else{
        ALOGD("[QE]%s :NFC Leave MTC MOS MODE",__FUNCTION__);
		gMtcMosMode = false;
    }
    return response;
}
/*******************************************************************************
** Author : Leo.Kuo
**
** Function:        nfc_jni_mtc_mos_PNFC
**
** Arguments:        on_off
**
** Description:      TRUE : PNFC 406,1
**                      FALSE : PNFC 406,0
**
** Returns:         bool
**
*******************************************************************************/
static bool nfc_jni_mtc_mos_PNFC(bool on_off)
{
    bool response = false;
    int  result   = 0;
    struct nfc_jni_callback_data cb_data;
    s_mtk_nfc_em_pnfc_req rSetPnfc;
    uint16_t payloadSize = sizeof(s_mtk_nfc_em_pnfc_req);

    ALOGD ("%s: enter, on_off, %d", __FUNCTION__, on_off);
    // 1.
    memset(&rSetPnfc,0,sizeof(rSetPnfc));

    //if ((mode & MODE_CARD) != 0)
    //true on
    if (true == on_off)
    {
        unsigned char pnfc[] = "$PNFC406,1*";

        rSetPnfc.action = 1;
        rSetPnfc.datalen = sizeof(pnfc);
        memcpy(&rSetPnfc.data[0],pnfc,sizeof(pnfc));
        ALOGD("Send PNFC 406,1");
    }
    else
    {
        unsigned char pnfc[] = "$PNFC406,0*";

        rSetPnfc.action = 1;
        rSetPnfc.datalen = sizeof(pnfc);
        memcpy(&rSetPnfc.data[0],pnfc,sizeof(pnfc));
        ALOGD("Send PNFC 406,0");
    }

    // 2.
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("%s: cb_data init fail", __FUNCTION__);
        response = false;
        goto clean_and_return;
    }

    // 3. Assign working callback data pointer
    g_working_cb_data = &cb_data;

    result = android_nfc_jni_send_msg(MTK_NFC_TEST_PNFC, payloadSize, &rSetPnfc);

    if (result == FALSE)
    {
        ALOGE("send MTK_NFC_TEST_PNFC fail\n");
        goto clean_and_return;
    }

    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }

    // Initialization Status (0: success, 1: fail)
    if(cb_data.status == 0) // 0: Success, 1: Fail
    {
        ALOGD("%s: done",__FUNCTION__);
        response = true;
    }
    else
    {
        ALOGE("%s: fail, stauts: %u\n", __FUNCTION__, cb_data.status);
        response = false;
        goto clean_and_return;
    }
clean_and_return:

    //clear working callback data pointer
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    ALOGD ("%s: exit", __FUNCTION__);

    return response;

    }
#endif
static bool nfc_jni_picc_mode()
{
    bool response = false;
    int  result   = FALSE;
    struct nfc_jni_callback_data cb_data;
    s_mtk_nfc_em_pnfc_req rSetPnfc;
    uint16_t payloadSize = sizeof(s_mtk_nfc_em_pnfc_req);   

    ALOGD ("%s: enter, %d", __FUNCTION__);
    // 1.
    memset(&rSetPnfc,0,sizeof(rSetPnfc));
    
    unsigned char pnfc[] = "$PNFC407,1*";

    rSetPnfc.action = 1;
    rSetPnfc.datalen = sizeof(pnfc);
    memcpy(&rSetPnfc.data[0],pnfc,sizeof(pnfc));
    ALOGD("Send PNFC 407,1");

    // 2.
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("%s: cb_data init fail", __FUNCTION__);
        response = false;
        goto clean_and_return;
    }

    // 3. Assign working callback data pointer
    g_working_cb_data = &cb_data;
    
    result = android_nfc_jni_send_msg(MTK_NFC_TEST_PNFC, payloadSize, &rSetPnfc);

    if (result == FALSE)
    {
        ALOGE("send MTK_NFC_TEST_PNFC fail\n");
        goto clean_and_return;
    }
    
    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }

    // Initialization Status (0: success, 1: fail)
    if(cb_data.status == 0) // 0: Success, 1: Fail
    {
        ALOGD("%s: done",__FUNCTION__);
        response = true;
    }
    else
    {
        ALOGE("%s: fail, stauts: %u\n", __FUNCTION__, cb_data.status);
        response = false;
        goto clean_and_return;    
    }  

clean_and_return:
    
    //clear working callback data pointer
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    ALOGD ("%s: exit", __FUNCTION__);
    
    return response;
    
}
static bool nfc_jni_low_power_card_mode( bool on_off )
{
    bool response = false;
    int  result   = FALSE;
    struct nfc_jni_callback_data cb_data;
    s_mtk_nfc_em_pnfc_req rSetPnfc;
    uint16_t payloadSize = sizeof(s_mtk_nfc_em_pnfc_req);   

    ALOGD ("%s: enter, on_off, %d", __FUNCTION__, on_off);
    // 1.
    memset(&rSetPnfc,0,sizeof(rSetPnfc));
    
    //if ((mode & MODE_CARD) != 0) 
    if (on_off)
    {
        unsigned char pnfc[] = "$PNFC403,1*"; 

        rSetPnfc.action = 1;
        rSetPnfc.datalen = sizeof(pnfc);
        memcpy(&rSetPnfc.data[0],pnfc,sizeof(pnfc));
        ALOGD("Send PNFC 403,1");
    }
    else
    {
        unsigned char pnfc[] = "$PNFC403,0*"; 

        rSetPnfc.action = 1;
        rSetPnfc.datalen = sizeof(pnfc);
        memcpy(&rSetPnfc.data[0],pnfc,sizeof(pnfc));
        ALOGD("Send PNFC 403,0");
    }

    // 2.
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("%s: cb_data init fail", __FUNCTION__);
        response = false;
        goto clean_and_return;
    }

    // 3. Assign working callback data pointer
    g_working_cb_data = &cb_data;
    
    result = android_nfc_jni_send_msg(MTK_NFC_TEST_PNFC, payloadSize, &rSetPnfc);

    if (result == FALSE)
    {
        ALOGE("send MTK_NFC_TEST_PNFC fail\n");
        goto clean_and_return;
    }
    
    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }

    // Initialization Status (0: success, 1: fail)
    if(cb_data.status == 0) // 0: Success, 1: Fail
    {
        ALOGD("%s: done",__FUNCTION__);
        response = true;
    }
    else
    {
        ALOGE("%s: fail, stauts: %u\n", __FUNCTION__, cb_data.status);
        response = false;
        goto clean_and_return;    
    }  

clean_and_return:
    
    //clear working callback data pointer
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    ALOGD ("%s: exit", __FUNCTION__);
    
    return response;
    
}


//case 203:                       // Enable Polling Loop
//case 202:                       // Disable Low Polling Loop
static bool nfc_jni_low_power_polling(bool enable)
{
    bool response = false;
    int  result   = FALSE;
    struct nfc_jni_callback_data cb_data;
    s_mtk_nfc_em_pnfc_req rSetPnfc;
    uint16_t payloadSize = sizeof(s_mtk_nfc_em_pnfc_req);   

    ALOGD ("%s: enter, enable, %d", __FUNCTION__, enable);
    // 1.
    memset(&rSetPnfc,0,sizeof(rSetPnfc));
    if (enable) 
    {
        unsigned char pnfc[] = "$PNFC203*"; 

        rSetPnfc.action = 1;
        rSetPnfc.datalen = sizeof(pnfc);
        memcpy(&rSetPnfc.data[0],pnfc,sizeof(pnfc));
        ALOGD("Send PNFC 203");
    }
    else
    {
        unsigned char pnfc[] = "$PNFC202*"; 

        rSetPnfc.action = 1;
        rSetPnfc.datalen = sizeof(pnfc);
        memcpy(&rSetPnfc.data[0],pnfc,sizeof(pnfc));
        ALOGD("Send PNFC 202");
    }

    // 2.
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("%s: cb_data init fail", __FUNCTION__);
        response = false;
        goto clean_and_return;
    }

    // 3. Assign working callback data pointer
    g_working_cb_data = &cb_data;
    
    result = android_nfc_jni_send_msg(MTK_NFC_TEST_PNFC, payloadSize, &rSetPnfc);

    if (result == FALSE)
    {
        ALOGE("send MTK_NFC_TEST_PNFC fail\n");
        goto clean_and_return;
    }
    
    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }

    // Initialization Status (0: success, 1: fail)
    if(cb_data.status == 0) // 0: Success, 1: Fail
    {
        ALOGD("%s: done",__FUNCTION__);
        response = true;
    }
    else
    {
        ALOGE("%s: fail, stauts: %u\n", __FUNCTION__, cb_data.status);
        response = false;
        goto clean_and_return;    
    }  

clean_and_return:
    
    //clear working callback data pointer
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    ALOGD ("%s: exit", __FUNCTION__);
    
    return response;

}

// <--  L Migration    

#ifdef MTK_NFC_HCE_SUPPORT 
/// -- HCE [START]

void nfc_jni_notify_host_emu_activated (struct nfc_jni_native_data *nat)
{
    JNIEnv* e = NULL;
    ALOGD ("[HCE] %s: enter", __FUNCTION__);

    //acquire a pointer to the Java virtual machine
    nat->vm->AttachCurrentThread (&e, NULL);
    if (e == NULL)
    {
        ALOGE("[HCE] %s: jni env is null", __FUNCTION__);
        goto clean_and_return;
    }
 
    //notify NFC service about this new tag
    ALOGD ("[HCE] %s: try notify nfc service", __FUNCTION__);
    e->CallVoidMethod (nat->manager, cached_NfcManager_notifyHostEmuActivated);
    if (e->ExceptionCheck())
    {
        e->ExceptionClear();
        ALOGE ("[HCE] %s: fail notify nfc service", __FUNCTION__);
    }
    
clean_and_return:
    
    nat->vm->DetachCurrentThread ();
    
    ALOGD ("[HCE] %s: exit", __FUNCTION__);
    
}

void nfc_jni_notify_host_emu_deactivated (struct nfc_jni_native_data *nat)
{
    JNIEnv* e = NULL;
    ALOGD ("[HCE] %s: enter", __FUNCTION__);

    //acquire a pointer to the Java virtual machine
    nat->vm->AttachCurrentThread (&e, NULL);
    if (e == NULL)
    {
        ALOGE("[HCE] %s: jni env is null", __FUNCTION__);
        goto clean_and_return;
    }
 
    //notify NFC service about this new tag
    ALOGD ("[HCE] %s: try notify nfc service", __FUNCTION__);
    e->CallVoidMethod (nat->manager, cached_NfcManager_notifyHostEmuDeactivated);
    if (e->ExceptionCheck())
    {
        e->ExceptionClear();
        ALOGE ("[HCE] %s: fail notify nfc service", __FUNCTION__);
    }

clean_and_return:
    
    nat->vm->DetachCurrentThread ();
    
    ALOGD ("[HCE] %s: exit", __FUNCTION__);

}

static void * nfc_jni_notify_host_emu_data (void * arg)
{
    jobject j_data = NULL;
    JNIEnv* e = NULL;    
    INT32 length;
    UINT8 *data;
    
    nfc_jni_hce_native_data_t * native_data = (nfc_jni_hce_native_data_t *)arg;
    
    ALOGD ("[HCE] %s: enter", __FUNCTION__);
    //0. init data
    length = native_data->notify_hce_data.u4DataLen;
    data = native_data->notify_hce_data.au1Data;

    //1. Acquire a pointer to the Java virtual machine
    native_data->vm->AttachCurrentThread (&e, NULL);
    if (e == NULL)
    {
        ALOGE("[HCE] %s: jni env is null", __FUNCTION__);
        goto clean_and_return;
    }

    j_data = e->NewByteArray(length);    
    e->SetByteArrayRegion((jbyteArray)j_data, 0, length, (jbyte *)data);    
    if(e->ExceptionCheck()) 
    {
        goto clean_and_return;
    }

    //debug message
    {
        INT32  maxLength = length * 3 + 20;    
        INT32  index = 0;
        CHAR   *debugMessage;
        UINT32 debugMessageLength = 0;
        debugMessage = (CHAR *)malloc( sizeof(CHAR) * maxLength);
        if (debugMessage != NULL)
        {
            memset(debugMessage, 0x00, maxLength);        
            sprintf(debugMessage, "Recv: ");
            debugMessageLength = strlen(debugMessage);  
            for( index = 0; index < length; index++)
            {       
                sprintf((debugMessage + debugMessageLength),"%02x,",data[index]);
                debugMessageLength = strlen(debugMessage);           
            }
            sprintf((debugMessage + debugMessageLength),"\n");      
            ALOGD ("[HCE] %s", debugMessage);
            ALOGD ("[HCE] Recv length,%d", length);
            
            free(debugMessage);
        }
    }

    //2. Notify NFC service 
    ALOGD ("[HCE] %s: try notify nfc service", __FUNCTION__);
    e->CallVoidMethod (native_data->manager, cached_NfcManager_notifyHostEmuData, j_data);
    if (e->ExceptionCheck())
    {
        e->ExceptionClear();
        ALOGE ("[HCE] %s: fail notify nfc service", __FUNCTION__);
    }

clean_and_return:
    //3. De-Init  todo : 
    if(j_data != NULL)
    {
       e->DeleteLocalRef(j_data);
    }
    
    native_data->vm->DetachCurrentThread ();
    
    ALOGD ("[HCE] %s: exit", __FUNCTION__);

    return NULL;
}

static bool nfc_jni_send_raw_frame( UINT8 *data, UINT32 length)
{
    bool response = false;
    int  result   = FALSE;
    struct nfc_jni_callback_data cb_data;
    mtk_nfc_ee_send_raw_frame_req_t  rSendRawFrame;
    UINT16 payloadSize = sizeof(mtk_nfc_ee_send_raw_frame_req_t);    
    
    ALOGD ("[HCE] %s: enter", __FUNCTION__);

    // 1. Validate parameters
    if (data == NULL || length == 0 || length > MTK_NFC_SE_SEND_DATA_MAX_LEN )
    {
        ALOGE ("[HCE] %s: Null data , length = %d", __FUNCTION__, length);
        return false;
    }
    
    // 2.  Create the local semaphore 
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("[HCE] %s: cb_data init fail", __FUNCTION__);
        response = false;
        goto clean_and_return;
    }

    // 3. Assign working callback data pointer
    g_working_cb_data = &cb_data;

    //debug message
    {
        INT32  maxLength = length * 3 + 20;    
        INT32  index = 0;
        CHAR   *debugMessage;
        UINT32 debugMessageLength = 0;
        debugMessage = (CHAR *)malloc( sizeof(CHAR) * maxLength);
        if (debugMessage != NULL)
        {
            memset(debugMessage, 0x00, maxLength);        
            sprintf(debugMessage, "Send: ");
            debugMessageLength = strlen(debugMessage); 
            for( index = 0; index < (INT32)length; index++)
            {       
                sprintf((debugMessage + debugMessageLength),"%02x,",data[index]);
                debugMessageLength = strlen(debugMessage);          
            }
            sprintf((debugMessage + debugMessageLength),"\n");      
            ALOGD ("[HCE] %s", debugMessage);
            ALOGD ("[HCE] Recv length,%d", length);
            
            free(debugMessage);
        }
    }

    // 4. Send Raw Frame req msg to nfcstackp
    memset( &rSendRawFrame, 0, payloadSize);    
    rSendRawFrame.u4DataLen = length;
    memcpy(&(rSendRawFrame.au1Data), data, length);
    
    result = android_nfc_jni_send_msg( MTK_NFC_JNI_SEND_RAW_FRAME_REQ, payloadSize, &rSendRawFrame);

    if (result == FALSE)
    {
        ALOGE("[HCE] %s: Send MTK_NFC_JNI_SEND_RAW_FRAME_REQ fail.",__FUNCTION__);
        response = false;
        goto clean_and_return;
    }
    // 5. Wait for callback response    
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("[HCE] %s: Failed to wait for semaphore (errno=0x%08x)", __FUNCTION__, errno);
        response = false;
        goto clean_and_return;
    }
    
    if(cb_data.status == 0) // 0: Success, 1: Fail
    {
        ALOGD("[HCE] %s: done",__FUNCTION__);
        response = true;
    }
    else
    {
        ALOGE("[HCE] %s: fail, stauts: %u\n", __FUNCTION__, cb_data.status);
        response = false;
        goto clean_and_return;    
    }

clean_and_return:

    // 6. De-Init : clear working callback data pointer 
    g_working_cb_data = NULL;
 
    nfc_cb_data_deinit(&cb_data);

    ALOGD ("[HCE] %s: exit", __FUNCTION__);
    
    return response;

}

static bool nfc_jni_set_default_route(UINT32 defaultRoute)
{
    bool response = false;
    int  result   = FALSE;
    struct nfc_jni_callback_data cb_data;
    mtk_nfc_ee_set_default_route_req_t  rSetDefaultRoute;
    UINT16 payloadSize = sizeof(mtk_nfc_ee_set_default_route_req_t);    
    
    ALOGD ("[HCE] %s: enter", __FUNCTION__);
    
    // 1.  Create the local semaphore 
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("[HCE] %s CB INIT FAIL", __FUNCTION__);
        response = false;
        goto clean_and_return;
    }

    // 2. Assign working callback data pointer
    g_working_cb_data = &cb_data;

    // 3. Send set default route req msg to nfcstackp
    memset( &rSetDefaultRoute, 0, payloadSize);
    
    rSetDefaultRoute.default_route = (uint8_t)(defaultRoute & 0xFF);
    
    result = android_nfc_jni_send_msg( MTK_NFC_JNI_SET_DEFAULT_ROUTE_REQ, payloadSize, &rSetDefaultRoute);

    if (result == FALSE)
    {
        ALOGE("[HCE] %s: Send MTK_NFC_JNI_SET_DEFAULT_ROUTE_REQ fail.",__FUNCTION__);
        response = false;
        goto clean_and_return;
    }
    
    // 4. Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("[HCE] %s: Failed to wait for semaphore (errno=0x%08x)", __FUNCTION__, errno);
        response = false;
        goto clean_and_return;
    }    
    
    if(cb_data.status == 0) // 0: Success, 1: Fail
    {
        ALOGD("[HCE] %s: done",__FUNCTION__);
        response = true;
    }
    else
    {
        ALOGE("[HCE] %s: fail, stauts: %u\n", __FUNCTION__, cb_data.status);
        response = false;
        goto clean_and_return;    
    }

    // update default route
    gDefaultRoute = defaultRoute;
    ALOGE("[HCE] %s: Update gDefaultRoute,%d", __FUNCTION__, gDefaultRoute);
        
clean_and_return:

    // 5. De-Init : clear working callback data pointer 
    g_working_cb_data = NULL;
 
    nfc_cb_data_deinit(&cb_data);

    ALOGD ("[HCE] %s: exit", __FUNCTION__);
    
    return response;
}

static bool nfc_jni_add_aid_routing( UINT8 *aid, UINT8 aidLen, UINT32 route)
{
    bool response = false;
    int  result   = FALSE;
    struct nfc_jni_callback_data cb_data;
    mtk_nfc_ee_add_aid_req_t  rAddAidRouting;
    UINT16 payloadSize = sizeof(mtk_nfc_ee_add_aid_req_t);    
    
    ALOGD ("[HCE] %s: enter", __FUNCTION__);
    
    // 1. Validate parameters
    if (aid == NULL || aidLen == 0 || aidLen > MTK_NFC_MAX_AID_LEN )
    {
        ALOGE ("[HCE] %s: Bad AID , AidLen = %d", __FUNCTION__, aidLen);
        return false;
    }

    // 2.  Create the local semaphore 
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("[HCE] %s CB INIT FAIL", __FUNCTION__);
        response = false;
        goto clean_and_return;
    }

    // 3. Assign working callback data pointer
    g_working_cb_data = &cb_data;

    // 4. Send add aid routing req msg to nfcstackp
    memset( &rAddAidRouting, 0, payloadSize);
    
    rAddAidRouting.nfcee_id = (uint8_t)(route & 0xFF);
    rAddAidRouting.aid_len = aidLen;
    memcpy(&(rAddAidRouting.aid), aid, aidLen);

    //debug message
    {
        INT32  maxLength = aidLen * 3 + 20;
        INT32  index = 0;
        CHAR   *debugMessage;
        UINT32 debugMessageLength = 0;   
        debugMessage = (CHAR *)malloc( sizeof(CHAR) * maxLength);
        if (debugMessage != NULL)
        {
            memset(debugMessage, 0x00, maxLength); 
            sprintf(debugMessage, "Aid: ");
            debugMessageLength = strlen(debugMessage);
            for( index = 0; index < (INT32)aidLen; index++)
            {
               sprintf((debugMessage + debugMessageLength),"%02x,",rAddAidRouting.aid[index]);
               debugMessageLength = strlen(debugMessage);       
            }
            sprintf((debugMessage + debugMessageLength),"\n");        
            ALOGD ("[HCE] %s", debugMessage);
            ALOGD ("[HCE] aidLen,%d", (INT32)aidLen);
            ALOGD ("[HCE] %s : nfcee_id,%d, aid_len,%d", __FUNCTION__, rAddAidRouting.nfcee_id, rAddAidRouting.aid_len);

            free(debugMessage);
        }
    }
    
    result = android_nfc_jni_send_msg( MTK_NFC_JNI_ADD_AID_ROUTING_REQ, payloadSize, &rAddAidRouting);

    if (result == FALSE)
    {
        ALOGE("[HCE] %s: Send MTK_NFC_ADD_AID_ROUTING fail.",__FUNCTION__);
        response = false;
        goto clean_and_return;
    }
    
    // 5. Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("[HCE] %s: Failed to wait for semaphore (errno=0x%08x)", __FUNCTION__, errno);
        response = false;
        goto clean_and_return;
    }    
    
    if(cb_data.status == 0) // 0: Success, 1: Fail
    {
        ALOGD("[HCE] %s: done",__FUNCTION__);
        response = true;
    }
    else
    {
        ALOGE("[HCE] %s: fail, stauts: %u\n", __FUNCTION__, cb_data.status);
        response = false;
        goto clean_and_return;    
    }

clean_and_return:

    // 6. De-Init : clear working callback data pointer 
    g_working_cb_data = NULL;
 
    nfc_cb_data_deinit(&cb_data);

    ALOGD ("[HCE] %s: exit", __FUNCTION__);
    
    return response;
}

static bool nfc_jni_remove_aid_routing( UINT8 *aid, UINT32 aidLen)
{
    bool response = false;
    int  result   = FALSE;
    struct nfc_jni_callback_data cb_data;
    mtk_nfc_ee_remove_aid_req_t  rRemoveAidRouting;
    UINT16 payloadSize = sizeof(mtk_nfc_ee_remove_aid_req_t);  

    
    // 1. Validate parameters
    if (aid == NULL || aidLen == 0 || aidLen > MTK_NFC_MAX_AID_LEN)
    {
        ALOGE ("[HCE] %s: Bad AID , AidLen = %d", __FUNCTION__,aidLen);
        return false;
    }
    // 2.  Create the local semaphore 
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("[HCE] %s: CB INIT FAIL", __FUNCTION__);
        response = false;
        goto clean_and_return;
    }

    // 3. Assign working callback data pointer
    g_working_cb_data = &cb_data;

    // 4. Send remove aid routing req msg to nfcstackp
    memset( &rRemoveAidRouting, 0, payloadSize);
    
    rRemoveAidRouting.aid_len = aidLen;
    memcpy(&(rRemoveAidRouting.aid), aid, aidLen);

    //debug message
    {
        INT32  maxLength = aidLen * 3 + 20;
        INT32  index = 0;
        CHAR   *debugMessage;
        UINT32 debugMessageLength = 0;
        debugMessage = (CHAR *)malloc( sizeof(CHAR) * maxLength);
        if (debugMessage != NULL)
        {
            memset(debugMessage, 0x00, maxLength); 
            sprintf(debugMessage, "Aid : ");
            debugMessageLength = strlen(debugMessage);            
            for( index = 0; index < (int)aidLen; index++)
            {
               sprintf((debugMessage + debugMessageLength),"%02x,",rRemoveAidRouting.aid[index]);
               debugMessageLength = strlen(debugMessage);       
            }
            sprintf((debugMessage + debugMessageLength),"\n");        
            ALOGD ("[HCE] %s", debugMessage);
            ALOGD ("[HCE] length,%d", rRemoveAidRouting.aid_len);
            
            free(debugMessage);
        }
    }
    
    result = android_nfc_jni_send_msg( MTK_NFC_JNI_REMOVE_AID_ROUTING_REQ, payloadSize, &rRemoveAidRouting);

    if (result == FALSE)
    {
        ALOGE("[HCE] %s: Send MTK_NFC_REMOVE_AID_ROUTING fail.",__FUNCTION__);
        response = false;
        goto clean_and_return;
    }
    
    // 5. Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("[HCE] %s: Failed to wait for semaphore (errno=0x%08x)", __FUNCTION__, errno);
        response = false;
        goto clean_and_return;
    }    
    
    if(cb_data.status == 0) // 0: Success, 1: Fail
    {
        ALOGD("[HCE] %s: done",__FUNCTION__);
        response = true;
    }
    else
    {
        ALOGE("[HCE] %s: fail, stauts: %u\n", __FUNCTION__ , cb_data.status);
        response = false;
        goto clean_and_return;    
    }

clean_and_return:

    // 6. De-Init : clear working callback data pointer 
    g_working_cb_data = NULL;
 
    nfc_cb_data_deinit(&cb_data);

    ALOGD ("[HCE] %s: exit", __FUNCTION__);
    
    return response;

}

static bool nfc_jni_update_routing(bool state)
{   
    bool response = false;
    int  result   = FALSE;
    struct nfc_jni_callback_data cb_data;
    mtk_nfc_ee_update_routing_req_t  rUpdateRouting;
    UINT16 payloadSize = sizeof(mtk_nfc_ee_update_routing_req_t);  

    ALOGD ("[HCE] %s: enter, state,%d", __FUNCTION__, state);
    
    // 1.  Create the local semaphore 
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
        ALOGD("[HCE] %s: CB INIT FAIL", __FUNCTION__);
        response = false;
        goto clean_and_return;
    }

    // 2. Assign working callback data pointer
    g_working_cb_data = &cb_data;

    // 3. Send remove aid routing req msg to nfcstackp
    memset( &rUpdateRouting, 0, payloadSize);
    
    if (state)
    {
        rUpdateRouting.state = 1; //enable
    }
    else
    {
        rUpdateRouting.state = 0; //disable
    }
    
    result = android_nfc_jni_send_msg( MTK_NFC_JNI_UPDATE_ROUTING_REQ, payloadSize, &rUpdateRouting);

    if (result == FALSE)
    {
        ALOGE("[HCE] %s: Send MTK_NFC_JNI_UPDATE_ROUTING_REQ fail.",__FUNCTION__);
        response = false;
        goto clean_and_return;
    }
    
    // 4. Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
        ALOGE("[HCE] %s: Failed to wait for semaphore (errno=0x%08x)", __FUNCTION__, errno);
        response = false;
        goto clean_and_return;
    }    
    
    if(cb_data.status == 0) // 0: Success, 1: Fail
    {
        ALOGD("[HCE] %s: done",__FUNCTION__);
        response = true;
    }
    else
    {
        ALOGE("[HCE] %s: fail, stauts: %u\n", __FUNCTION__, cb_data.status);
        response = false;
        goto clean_and_return;    
    }

clean_and_return:

    // 5. De-Init : clear working callback data pointer 
    g_working_cb_data = NULL;
 
    nfc_cb_data_deinit(&cb_data);

    ALOGD ("[HCE] %s: exit", __FUNCTION__);
    
    return response;
}

static void nfc_jni_send_raw_frame_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_send_raw_frame_rsp_t * prSendRawFrame)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("[HCE] nfc_jni_send_raw_frame_callback", status);
    pContextData->status = status;
    pContextData->pContext = (void*)prSendRawFrame; 
    sem_post(&pContextData->sem);
}

static void nfc_jni_set_default_route_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_set_default_route_rsp_t * prSetDefaultRoute)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("[HCE] nfc_jni_set_default_route_callback", status);
    pContextData->status = status;
    pContextData->pContext = (void*)prSetDefaultRoute; 
    sem_post(&pContextData->sem);
}

static void nfc_jni_add_aid_routing_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_add_aid_rsp_t * prAddAidRouting)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("[HCE] nfc_jni_add_aid_routing_callback", status);
    pContextData->status = status;
    pContextData->pContext = (void*)prAddAidRouting; 
    sem_post(&pContextData->sem);
}

static void nfc_jni_remove_aid_routing_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_remove_aid_rsp_t * prRemoveAidRouting)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("[HCE] nfc_jni_remove_aid_routing_callback", status);
    pContextData->status = status;
    pContextData->pContext = (void*)prRemoveAidRouting; 
    sem_post(&pContextData->sem);
}

static void nfc_jni_update_routing_callback(void *pContext, NFCSTATUS status, 
    mtk_nfc_ee_update_routing_rsp_t * prUpdateRouting)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("[HCE] nfc_jni_update_routing_callback", status);
    pContextData->status = status;
    pContextData->pContext = (void*)prUpdateRouting; 
    sem_post(&pContextData->sem);
}

/// -- HCE [STOP]
#endif

static void nfc_jni_ActivateSeInterface_callback(void *pContext, NFCSTATUS status, 
    void * MsgPayload)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_ActivateSeInterface_callback", status);
    pContextData->status = status;
    pContextData->pContext = (void*)MsgPayload;
    sem_post(&pContextData->sem); // release com_android_nfc_NfcManager_doActivateSecureElementInterfaceById()  's Semaphore
}

static void nfc_jni_dev_activate_ntf_callback(void *pContext,
    nfc_jni_native_data *nat,
    unsigned int u4Result,
    s_mtk_nfc_service_activate_param_t rActivateParam,
    s_mtk_nfc_service_tag_param_t rTagParam)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_dev_activate_ntf_callback", u4Result);
    ALOGD("JNI-CB: nfc_jni_dev_activate_ntf_callback, %p", nat);

    // copy data to native

    if(u4Result == 0)
    {
        
        ALOGD("Id[0x%02X]",rActivateParam.u1RfDiscId);
        memcpy(&nat->rDevActivateParam, &rActivateParam, sizeof(s_mtk_nfc_service_activate_param_t));
        memcpy(&nat->rDevTagParam, &rTagParam, sizeof(s_mtk_nfc_service_tag_param_t));
        ALOGD("JNI, client act ntf cb [0x%02X]\r\n", nat->rDevActivateParam.rRfTechParam.u1Mode);
    }
    // fill discover data because no discover notify
    #if 0
    {
        s_mtk_discover_device_ntf_t rDiscoverNtfData;

        memset(&rDiscoverNtfData, 0, sizeof(s_mtk_discover_device_ntf_t));
        rDiscoverNtfData.u1DiscoverId = rActivateParam.u1RfDiscId;
        rDiscoverNtfData.u1Protocol = rActivateParam.u1Protocol;
        memcpy(&rDiscoverNtfData.rRfTechParam, &rActivateParam.rRfTechParam, sizeof(s_mtk_discover_device_ntf_t));
        rDiscoverNtfData.fgMore = 0;        
        vAddDiscoverNotifiedTech(nat,&rDiscoverNtfData);
    }
    #endif
    vAddActivateNotifiedRfParam(nat, &rActivateParam, &rTagParam);
    memcpy(&nat->rDiscoverDev[0].rRfTechParam,&rActivateParam.rRfTechParam, sizeof(s_mtk_rf_tech_param_t));
    nat->rDiscoverDev[0].u1Protocol = rActivateParam.u1Protocol;
    nat->rDiscoverDev[0].u1DiscoverId = rActivateParam.u1RfDiscId;

    // to update the device inoformation
    // modify activated flag before add p2p device, because framework may call deactivate in vAddNfcP2p
    if (1 == nat->u1DevActivated)
    {
        nat->u1DevActivated = 2;
        if (pContext == NULL)
        {
           ALOGD("NULL context in activation notify");
           return;
        }
        sem_post(&pContextData->sem); // re activate case from reconnect        
    }
    else if (0 == nat->u1DevActivated)
    {
        nat->u1DevActivated = 2;
    }

    if ((rActivateParam.rRfTechParam.u1Mode >= NFC_DEV_DISCOVERY_TYPE_LISTEN_A)
        && (rActivateParam.u1Protocol != NFC_DEV_PROTOCOL_NFC_DEP))
    {
        ALOGD("Remote Device is READER");
        #ifdef MTK_NFC_HCE_SUPPORT 
        /// -- HCE [START]
        ALOGD("[HCE] %s: u1Mode,%d,u1Prorocol,%d", __FUNCTION__, 
                rActivateParam.rRfTechParam.u1Mode, rActivateParam.u1Protocol);
        if (NULL != nat)
        {
            if ( (rActivateParam.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_LISTEN_A ||
                  rActivateParam.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_LISTEN_B ) &&
                  (rActivateParam.u1Protocol == NFC_DEV_PROTOCOL_ISO_DEP) )
            {
                nfc_jni_notify_host_emu_activated(nat);
            }    
        }
        else
        {
            ALOGD("nat is NULL");
        }
        /// -- HCE [END]
        #endif
    }
    else
    {
        if ((rActivateParam.u1Protocol == NFC_DEV_PROTOCOL_NFC_DEP)
           && (rActivateParam.rIntfaceParam.u1Type == NFC_DEV_INTERFACE_NFC_DEP))
        {
            vAddNfcP2p(nat, rActivateParam);
        }
        else
        {
            vAddNfcTag(nat, rActivateParam, rTagParam);
        }
    }
       
}

static void nfc_jni_dev_deactivate_rsp_callback(void *pContext,nfc_jni_native_data *nat,unsigned int u4Result)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_dev_deactivate_rsp_callback", u4Result);
    
    ALOGD("JNI-CB: nfc_jni_dev_deactivate_rsp_callback,[%p], %p", pContext, nat);

    if (pContextData != NULL)
    {
        pContextData->status = (NFCSTATUS)u4Result;
        ALOGD("JNI-CB: nfc_jni_dev_deactivate_rsp_callback,assing status,%d",u4Result);
        // to update the device inoformation
        if (u4Result != 3) // not wait notification
        {
            ALOGD("JNI-CB: nfc_jni_dev_deactivate_rsp_callback,do not wait, direct post sem[%p], nat->fgPollingEnabled,%d(set to false)",
                    &pContextData->sem, nat->fgPollingEnabled);
            nat->fgPollingEnabled = FALSE;
            nat->fgChangeToFrameRfInterface = FALSE;
            nat->fgHostRequestDeactivate = FALSE;
            nat->u1DevActivated = 0;
            nat->u4NumDicoverDev = 0;
            memset(&nat->rDiscoverDev[0],0,DISCOVERY_DEV_MAX_NUM*sizeof(s_mtk_discover_device_ntf_t));
            memset(&nat->rDevActivateParam,0,sizeof(s_mtk_nfc_service_activate_param_t));
            memset(&nat->au4TechList[0],0,NUM_MAX_TECH*sizeof(unsigned int));
            memset(&nat->au4TechIndex[0],0,NUM_MAX_TECH*sizeof(unsigned int));
            memset(&nat->apTechHandles[0],0,NUM_MAX_TECH*sizeof(MTK_NFC_HANDLER));
            memset(&nat->au4DevId[0],0,NUM_MAX_TECH*sizeof(unsigned int));
            memset(&nat->au4TechProtocols[0],0,NUM_MAX_TECH*sizeof(unsigned int));

            sem_post(&pContextData->sem);
        }
        else
        {
            ALOGD("JNI-CB: nfc_jni_dev_deactivate_rsp_callback,success, wait deact ntf to post sem]");
            //sem_post(&pContextData->sem); // temp because dsp doesn't responds deact ntf
        }
    }
    else
    {
        ALOGE("JNI-CB: nfc_jni_dev_deactivate_rsp_callback,pContextData");
    }
}

static void nfc_jni_dev_deactivate_ntf_callback(void *pContext, 
    nfc_jni_native_data *nat,
    unsigned int u4Result,
    UINT8 u1DeactivateType,
    UINT8 u1DeactivateReason)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_dev_deactivate_ntf_callback");
    ALOGD("JNI-CB: nfc_jni_dev_deactivate_ntf_callback, %p, %p,%d", nat, pContextData, (unsigned int)u4Result);

    #ifdef MTK_NFC_HCE_SUPPORT 
    if (NULL != nat)
    {
        if ( ( nat->rDevActivateParam.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_LISTEN_A ||
               nat->rDevActivateParam.rRfTechParam.u1Mode == NFC_DEV_DISCOVERY_TYPE_LISTEN_B ) &&
              ( nat->rDevActivateParam.u1Protocol == NFC_DEV_PROTOCOL_ISO_DEP) )
        {
            ALOGD("[HCE] %s: u1Mode, %d, u1Protocol, %d", __FUNCTION__, 
                nat->rDevActivateParam.rRfTechParam.u1Mode, nat->rDevActivateParam.u1Protocol);
            nfc_jni_notify_host_emu_deactivated(nat);
        }    
    }
    #endif

    // to update the device inoformation
    if (0 == u1DeactivateType) // idle
    {
        ALOGD("JNI-CB: nfc_jni_dev_deactivate_ntf_callback, nat->fgPollingEnabled,%d(set to false)",
            nat->fgPollingEnabled);
        nat->fgPollingEnabled = FALSE;
        nat->fgChangeToFrameRfInterface = FALSE;
        nat->fgHostRequestDeactivate = FALSE;
        nat->u1DevActivated = 0;
        nat->u4NumDicoverDev = 0;
        memset(&nat->rDiscoverDev[0],0,DISCOVERY_DEV_MAX_NUM*sizeof(s_mtk_discover_device_ntf_t));
        memset(&nat->rDevActivateParam,0,sizeof(s_mtk_nfc_service_activate_param_t));
        memset(&nat->au4TechList[0],0,NUM_MAX_TECH*sizeof(unsigned int));
        memset(&nat->au4TechIndex[0],0,NUM_MAX_TECH*sizeof(unsigned int));
        memset(&nat->apTechHandles[0],0,NUM_MAX_TECH*sizeof(MTK_NFC_HANDLER));
        memset(&nat->au4DevId[0],0,NUM_MAX_TECH*sizeof(unsigned int));
        memset(&nat->au4TechProtocols[0],0,NUM_MAX_TECH*sizeof(unsigned int));
    }
    else if (3 == u1DeactivateType) // discovery
    {
        nat->fgChangeToFrameRfInterface = FALSE;
        nat->fgHostRequestDeactivate = FALSE;
        nat->u1DevActivated = 0;
        nat->u4NumDicoverDev = 0;
        memset(&nat->rDiscoverDev[0],0,DISCOVERY_DEV_MAX_NUM*sizeof(s_mtk_discover_device_ntf_t));
        memset(&nat->rDevActivateParam,0,sizeof(s_mtk_nfc_service_activate_param_t));
        memset(&nat->au4TechList[0],0,NUM_MAX_TECH*sizeof(unsigned int));
        memset(&nat->au4TechIndex[0],0,NUM_MAX_TECH*sizeof(unsigned int));
        memset(&nat->apTechHandles[0],0,NUM_MAX_TECH*sizeof(MTK_NFC_HANDLER));
        memset(&nat->au4DevId[0],0,NUM_MAX_TECH*sizeof(unsigned int));
        memset(&nat->au4TechProtocols[0],0,NUM_MAX_TECH*sizeof(unsigned int));
    }
    else
    {
        nat->fgChangeToFrameRfInterface = FALSE;
        nat->fgHostRequestDeactivate = FALSE;
        nat->u1DevActivated = 0;
        memset(&nat->rDevActivateParam,0,sizeof(s_mtk_nfc_service_activate_param_t));
    }
    if (0 == u1DeactivateReason ) // DH request
    {
        if (pContextData != NULL)
        {
            pContextData->status = (NFCSTATUS)u4Result;
            sem_post(&pContextData->sem);     
        }
        else
        {
            ALOGE("JNI-CB: nfc_jni_dev_deactivate_ntf_callback,pContextData");
        }
    }
}

#ifdef SUPPORT_EVT_TRANSACTION
static void nfc_jni_dev_event_ntf_callback(
    nfc_jni_native_data *nat,
    s_mtk_nfc_service_dev_event_ntf_t *ntf
)
{
    JNIEnv* e = NULL;
    jobject tmp_array = NULL;
    jobject tmp_array_param = NULL;
    int cnt1;

    ALOGD("JNI-CB: nfc_jni_dev_event_ntf_callback, %p, %p", nat, ntf);

    //e = nfc_get_env();
    nat->vm->AttachCurrentThread (&e, NULL);
    
    if (ntf->u1EventType == 0x10) // EVT_CONNECTIVITY
    {       
        //
    }    
    else if (ntf->u1EventType == 0x12) // EVT_TRANSACTION 
    {
        UINT8 u1AidTag;
        UINT8 u1AidLen;
        UINT8 *pAidData;

        // -------------------------------------------------------------
        // Refer to ETSI TS 102 622 V10.2.0 - 11.2.2.4 "EVT_TRANSACTION"
        // -------------------------------------------------------------
        // --------------------------------
        // | Description | Tag  | Length  |
        // --------------------------------
        // | AID         | 0x81 | 5 ~ 16  |
        // --------------------------------
        // | PARAMETERS  | 0x82 | 0 ~ 255 |
        // --------------------------------
        for(cnt1 = 0 ;cnt1 < ntf->u1NumOfTags; cnt1++)
        {
            u1AidTag = ntf->tag_tlv[cnt1].tag;
            u1AidLen = ntf->tag_tlv[cnt1].len;
            pAidData = ntf->tag_tlv[cnt1].value;
            if (u1AidTag == 0x81) // AID
            {            
                // transfer the byte array from C style to Java style
                tmp_array = e->NewByteArray(u1AidLen);
                android_nfc_print_debuglog_array(pAidData,u1AidLen);
                e->SetByteArrayRegion((jbyteArray)tmp_array, 0, u1AidLen, (jbyte *)pAidData);
                if(e->ExceptionCheck()) {
                    goto __my_finally;
                }
                ALOGD("JNI-CB: nfc_jni_dev_event_ntf_callback,AID");

            }
            else if(u1AidTag == 0x82) // parameter
            {
                tmp_array_param = e->NewByteArray(u1AidLen);
                android_nfc_print_debuglog_array(pAidData,u1AidLen);
                e->SetByteArrayRegion((jbyteArray)tmp_array_param, 0, u1AidLen, (jbyte *)pAidData);
                if(e->ExceptionCheck()) {
                    goto __my_finally;
                }
                ALOGD("JNI-CB: nfc_jni_dev_event_ntf_callback,parameter");
                
            }
            else
            {
                ALOGD("JNI-CB: aid tag is wrong (%d)", u1AidTag);            
            }        
        }

        #if 1 // L Migration , TODO : add para 
        // notify Java layer, the NativeNfcManager has already been cached in genericManager, use it directly
        if((tmp_array )&&(tmp_array_param))
        {
            e->CallVoidMethod(nat->manager, 
                          cached_NfcManager_notifyTransactionListeners, 
                          tmp_array,tmp_array_param);
            if(e->ExceptionCheck()) {
                    goto __my_finally;
                }
        }
        #endif   
    }
    else // error cases
    {
        ALOGD("JNI-CB: nfc_jni_dev_event_ntf_callback,unsupported,event,%d", ntf->u1EventType);
    }

__my_finally:
    
    if(tmp_array != NULL) {
        e->DeleteLocalRef(tmp_array);
    }

    if(tmp_array_param != NULL) {
        e->DeleteLocalRef(tmp_array_param);
    }

    ALOGD("JNI-CB: nfc_jni_dev_event_ntf_callback() - EXIT");
}
#endif

/* P2P - LLCP callbacks */
static void nfc_jni_llcp_linkStatus_callback(struct nfc_jni_native_data *Natdata, int status, unsigned char LinkStatus)
{
    JNIEnv *e;
    nfc_jni_listen_data_t * pListenData = NULL;   
    struct nfc_jni_native_data *nat = Natdata;

    s_mtk_nfc_service_dev_deactivate_req_t rDevDeactivate;
    uint16_t PayloadSize = sizeof(s_mtk_nfc_service_dev_deactivate_req_t);
    struct nfc_jni_callback_data cb_data;
    int ret = -1;

    nfc_jni_native_monitor_t* pMonitor = nfc_jni_get_monitor();

    ALOGD("Callback: nfc_jni_llcp_linkStatus_callback(),%p", nat);
    if (nat != NULL)
    {
        nat->vm->AttachCurrentThread (&e, NULL);       
        /* Update link status */
        //  MTK_NFC_P2P_LINK_NTF CMD

        if(LinkStatus == 1) // Link Up
        {
            if(status != 0) 
            {
                ALOGW("GetRemote Info failded - Status = %02x",status);
            }
            else
            {
                device_connected_flag = 1;
            }
        }
        else if(LinkStatus == 2) //Link Down
        {
            ALOGD("LLCP Link deactivated");   
            /* Reset device connected flag */
            device_connected_flag = 0;


            /* Reset incoming socket list */
            while (!LIST_EMPTY(&pMonitor->incoming_socket_head))
            {
                pListenData = LIST_FIRST(&pMonitor->incoming_socket_head);
                if (pListenData != NULL)
                {
                    ALOGD("LLCP Link deactivated,T,%p", pListenData);
                    LIST_REMOVE(pListenData, entries);
                    free(pListenData);
                }
            }

            /* Notify manager that the LLCP is lost or deactivated */
            //   ALOGD("LLCP Link deactivated,%p,%p,%p,%p", nat->manager, nat->tag, e, cached_NfcManager_notifyLlcpLinkDeactivated);
            if ((nat->tag != NULL) && (nat->manager != NULL) && (e != NULL) && (cached_NfcManager_notifyLlcpLinkDeactivated != NULL))
            {
                e->CallVoidMethod(nat->manager, cached_NfcManager_notifyLlcpLinkDeactivated, nat->tag);
                ALOGD("LLCP Link deactivated,C");
                if(e->ExceptionCheck())
                {
                    e->ExceptionClear();
                    ALOGE ("vm got a pending exception");
                }               
            }
        }
    }
}

static void nfc_jni_se_set_mode_callback(void *pContext,
   unsigned char handle, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_se_set_mode_callback", status);

    pContextData->status = status;
    
    sem_post(&pContextData->sem);
}

static void nfc_jni_se_get_list_callback(void *pContext, NFCSTATUS status, 
    s_mtk_nfc_jni_se_get_list_rsp_t *prSeGetList)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_se_get_list_callback", status);

    pContextData->status = status;
    
    pContextData->pContext = (void*)prSeGetList; 
    
    sem_post(&pContextData->sem);
}

static void nfc_jni_se_get_cur_se_callback(void *pContext,
   unsigned char handle, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_se_get_cur_se_callback", status);

    pContextData->status = status;
    
    sem_post(&pContextData->sem);
}

#ifdef DTA_SUPPORT
static void nfc_jni_set_PNFC_callback(void *pContext, NFCSTATUS status)
{
    struct nfc_jni_callback_data * pContextData =  (struct nfc_jni_callback_data*)pContext;

    LOG_CALLBACK("nfc_jni_set_PNFC_callback", status);

    pContextData->status = status;
    
    sem_post(&pContextData->sem);
}
#endif

static void com_android_nfc_NfcManager_disableDiscovery(JNIEnv *e, jobject o){

    struct nfc_jni_callback_data cb_data;
    struct nfc_jni_native_data *nat = NULL;
    int result = -1;
    
    CONCURRENCY_LOCK();

    ALOGD("com_android_nfc_NfcManager_disableDiscovery()");

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);
  
    if (!nat)
    {
        ALOGE("Invalid Nat");
        return;
    }
    
    //error handler for enable discovery fail case
    if (enableDiscoveryErrorFlag == TRUE)
    {
        result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
        enableDiscoveryErrorFlag = FALSE;
        ALOGD("error handler for enable discovery fail case ,result,%d, enableDiscoveryErrorFlag,%d",
            result, enableDiscoveryErrorFlag);
    }    

    if (!nat->fgPollingEnabled)
    {
        device_connected_flag = 0;
        ALOGD("Discovery is not enabled");
        result = 0;        
    }
    else  
    {
        result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
    }
         
    CONCURRENCY_UNLOCK();

    ALOGD("com_android_nfc_NfcManager_disableDiscovery result is %d", result);

    return;
}

#if 0 // L  Migration    
static void com_android_nfc_NfcManager_enableDiscovery(JNIEnv *e, jobject o) {

    struct nfc_jni_callback_data cb_data;
    struct nfc_jni_native_data *nat = NULL;
    int result = -1;
    
    CONCURRENCY_LOCK();

    ALOGD("com_android_nfc_NfcManager_enableDiscovery()");
 
    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);

    if (!nat)
    {
        ALOGE("Invalid NAT");
    }
    else
    {
        result = nfc_jni_enable_discovery(nat);
    }
    ALOGD("com_android_nfc_NfcManager_enableDiscovery(), done, result %d",result);   

    CONCURRENCY_UNLOCK();
    return;
}
#endif


// static final int NFC_POLL_DEFAULT = -1;
// static final int NFC_POLL_A = 0x01;
// static final int NFC_POLL_B = 0x02;
// static final int NFC_POLL_F = 0x04;
// static final int NFC_POLL_ISO15693 = 0x08;
// static final int NFC_POLL_B_PRIME = 0x10;
// static final int NFC_POLL_KOVIO = 0x20; 
/* Definitions for tNFA_TECHNOLOGY_MASK */
static void com_android_nfc_NfcManager_enableDiscovery(JNIEnv *e, jobject o,
    jint technologies_mask, jboolean enable_lptd, jboolean reader_mode,
    jboolean enable_host_routing, jboolean enable_p2p, jboolean restart ) {

    //struct nfc_jni_callback_data cb_data;    
    //int result = -1;
    //int tech_mask = DEFAULT_TECH_MASK;
    
    struct nfc_jni_native_data *nat = NULL;
    bool response = false;
    
    ALOGD("%s: TechMask,0x%02x,LowPowerPolling,%d,ReaderMode,%d,EnableHostRouting,%d,EnableP2P,%d,restart,%d",
        __FUNCTION__ ,technologies_mask, enable_lptd, reader_mode, enable_host_routing,
        enable_p2p, restart);
    
    CONCURRENCY_LOCK();

    // 1. Retrieve native structure address 
    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
        ALOGE("%s: Invalid NAT", __FUNCTION__);
        goto clean_and_return;
    }

    // 2. Update discovery info.
    gDiscoveryInfo.technologies_mask    = technologies_mask;
    gDiscoveryInfo.enable_lptd          = enable_lptd;
    gDiscoveryInfo.reader_mode          = reader_mode;
    gDiscoveryInfo.enable_host_routing  = enable_host_routing;
    gDiscoveryInfo.enable_p2p           = enable_p2p;
    gDiscoveryInfo.restart              = restart;

    // 3. enable discovery with info
    response = nfc_jni_enable_discovery_with_info(nat, gDiscoveryInfo);

#if 0
    
    // 2. Check technologies mask    
    if (technologies_mask == -1 )  //default value
    {
        tech_mask = nat->tech_mask;
    }    
    else
    {
        tech_mask = technologies_mask;
    }    
    ALOGD ("%s: tech_mask = 0x%02x", __FUNCTION__, tech_mask);

    if (nat->fgPollingEnabled && !restart)
    {
        ALOGE ("%s: already discovering", __FUNCTION__);
        goto clean_and_return;
    }    
       
    // 3.  If RF-Discover is  enable, stop RF-discover.
    result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
    ALOGD("%s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
    
    // 4. Check polling configuration    
    if (tech_mask != 0)
    {
        ALOGD("%s Before:A,%d,B,%d,F,%d,V,%d,BP,%d,K,%d", __FUNCTION__, 
            nat->rDiscoverConfig.reader_setting.fgEnTypeA,
            nat->rDiscoverConfig.reader_setting.fgEnTypeB,
            nat->rDiscoverConfig.reader_setting.fgEnTypeF,
            nat->rDiscoverConfig.reader_setting.fgEnTypeV,
            nat->rDiscoverConfig.reader_setting.fgEnTypeBP,
            nat->rDiscoverConfig.reader_setting.fgEnTypeK);  
        
        if (reader_mode)
        {
            nat->rDiscoverConfig.u1OpMode = MODE_READER;
        }
        else
        {
            //nat->rDiscoverConfig.u1OpMode = (MODE_READER|MODE_CARD|MODE_P2P);
            ALOGD("%s u1OpMode, %d", __FUNCTION__,nat->rDiscoverConfig.u1OpMode);
        }
        nat->rDiscoverConfig.reader_setting.fgEnTypeA = ((tech_mask & TECHNOLOGY_MASK_A ) != 0);
        nat->rDiscoverConfig.reader_setting.fgEnTypeB = ((tech_mask & TECHNOLOGY_MASK_B ) != 0); 
        nat->rDiscoverConfig.reader_setting.fgEnTypeF = ((tech_mask & TECHNOLOGY_MASK_F ) != 0);
        nat->rDiscoverConfig.reader_setting.fgEnTypeV = ((tech_mask & TECHNOLOGY_MASK_ISO15693 ) != 0);
        nat->rDiscoverConfig.reader_setting.fgEnTypeBP= ((tech_mask & TECHNOLOGY_MASK_B_PRIME ) != 0);
        nat->rDiscoverConfig.reader_setting.fgEnTypeK = ((tech_mask & TECHNOLOGY_MASK_KOVIO ) != 0);

        ALOGD("%s After:A,%d,B,%d,F,%d,V,%d,BP,%d,K,%d", __FUNCTION__, 
            nat->rDiscoverConfig.reader_setting.fgEnTypeA,
            nat->rDiscoverConfig.reader_setting.fgEnTypeB,
            nat->rDiscoverConfig.reader_setting.fgEnTypeF,
            nat->rDiscoverConfig.reader_setting.fgEnTypeV,
            nat->rDiscoverConfig.reader_setting.fgEnTypeBP,
            nat->rDiscoverConfig.reader_setting.fgEnTypeK);
    }
    else
    {
        ALOGD("%s: No technologies configured, stop polling", __FUNCTION__);  
    }

    // 5. Check listen configuration
    if (enable_host_routing)
    {       
        // Update routing
        result = nfc_jni_update_routing(true); // true : enable , false : disable
        ALOGD("%s: nfc_jni_update_routing done, result %d", __FUNCTION__, result);
    }
    else
    {
        result = nfc_jni_update_routing(false); // true : enable , false : disable
        ALOGD("%s: nfc_jni_update_routing done, result %d", __FUNCTION__, result);
    }

    // 6. Check LowPowerPolling
    if (gEnableLowPowerPolling)
    {
        result = nfc_jni_low_power_polling(enable_lptd);
        ALOGD("%s: nfc_jni_low_power_polling done, result %d", __FUNCTION__, result);  
    }
    else
    {
        ALOGD("%s: not support low power polling", __FUNCTION__);
    }

    //7. low power card mode
    // -- SWP test  in normal flow
    if (TRUE == gSwpTestInNormalMode )
    {
        // TODO : need verify 
        ALOGD("%s : SWP test in normal flow. Do not send PNFC",__FUNCTION__);
        nat->rDiscoverConfig.u1OpMode = 0x02; // card mode
        nat->rDiscoverConfig.u1DtaTestMode = 0x04; //SWP Test
        nat->rDiscoverConfig.i4DtaPatternNum = gSwpTestPatternNum; 
        ALOGD("opMode,%d,DtaTestMode,%d,DtaPatternNum,%d",
            nat->rDiscoverConfig.u1OpMode,nat->rDiscoverConfig.u1DtaTestMode,nat->rDiscoverConfig.i4DtaPatternNum);
    }
    else 
    {
        result = nfc_jni_low_power_card_mode(nat->rDiscoverConfig.u1OpMode);
        ALOGD("%s: nfc_jni_low_power_card_mode done, result %d", __FUNCTION__, result); 
    }
    
    // 8. Actually start discovery
    result = nfc_jni_enable_discovery(nat);
    ALOGD("%s: nfc_jni_enable_discovery done, result %d", __FUNCTION__, result);  

#endif     

clean_and_return:
    
    ALOGD("com_android_nfc_NfcManager_enableDiscovery(), done, response %d", response);      
      
    CONCURRENCY_UNLOCK();    
    
    return;
}


static void com_android_nfc_NfcManager_doResetTimeouts( JNIEnv *e, jobject o) {
    
    ALOGD("com_android_nfc_NfcManager_doResetTimeouts()");
    
    return;
}
static bool com_android_nfc_NfcManager_doSetTimeout( JNIEnv *e, jobject o,
        jint tech, jint timeout) {
        
    ALOGD("com_android_nfc_NfcManager_doSetTimeout(%d)",timeout);

    if (timeout <= 0)
    {
        ALOGE("%s: Timeout must be positive.",__FUNCTION__);
        return false;
    }
    gTimeout = timeout;

    ALOGD ("%s: timeout=%d", __FUNCTION__, timeout);
    
    return true;
}

static jint com_android_nfc_NfcManager_doGetTimeout( JNIEnv *e, jobject o,
        jint tech) {

    ALOGD("com_android_nfc_NfcManager_doGetTimeout(%d)",gTimeout);

#if 1 // workaround for user load NE issue
    /* *****************************************************
        if persist.mtk.aee.dal exist
          if persist.mtk.aee.dal == 1
            red screen
          else
            no red screen
        else
          if persist.mtk.aee.mode exist
            if persist.mtk.aee.mode == 1
              red screen
            else
              no red screen
          else
            if eng load
              red screen
            else
              no red screen
    ****************************************************** */
    if (tech == 6605) 
    {
        char value[PROPERTY_VALUE_MAX];
        bool red_screen = FALSE;
        int  timeout = 10000;
            
        if (property_get("persist.mtk.aee.dal", value, NULL)>0)
        {
            ALOGD("persist.mtk.aee.dal(%s)",value);
            
            if (!strncmp("1", value, PROPERTY_VALUE_MAX)) 
            {
                red_screen = TRUE;
            } 
        }
        else // persist.mtk.aee.dal is not exist
        {
            ALOGD("persist.mtk.aee.dal is not exist");
            
            if (property_get("persist.mtk.aee.mode", value, NULL)>0)
            {
                ALOGD("persist.mtk.aee.mode(%s)",value);
                
                if (!strncmp("1", value, PROPERTY_VALUE_MAX)) 
                {
                    red_screen = TRUE;
                } 
            }
            else // persist.mtk.aee.mode is not exist
            {
                ALOGD("persist.mtk.aee.mode is not exist");    

                if (!is_user_build()) 
                {
                    red_screen = TRUE;
                } 
            }        
        }

        if (red_screen) {
            timeout = 20000; // 20s for red screen 
        } else {
            timeout = 10000; // 10s for user load recovery mechanism
        }
        
        ALOGD("Timeout(%d)", timeout);
        
        return timeout;
        
    }
    
#endif 
        
    return (gTimeout);
}

static jboolean com_android_nfc_NfcManager_sendRawFrame (JNIEnv* e, jobject o, jbyteArray j_data)
{
    bool      result  = false;
    
#ifdef MTK_NFC_HCE_SUPPORT 

    uint8_t   *data = NULL;
    uint32_t  length  = 0;
    struct nfc_jni_native_data *nat = NULL;

    CONCURRENCY_LOCK();
    ALOGD ("[HCE] %s: enter", __FUNCTION__);

    // 1. Retrieve native structure address 
    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
        ALOGE("[HCE] %s: Invalid NAT", __FUNCTION__);
        goto clean_and_return;
    }
    
    // 2. Validate Parameters
    if(NULL == j_data)
    {
        ALOGE("[HCE] %s: data is NULL", __FUNCTION__);
        goto clean_and_return;
    }
    
    // 3. Init data
    length = (uint32_t) e->GetArrayLength ( j_data);
    data   = (uint8_t *)e->GetByteArrayElements ( j_data, NULL);

    // 4. Send raw frame
    result = nfc_jni_send_raw_frame( data, length);
    ALOGE("[HCE] %s: nfc_jni_send_raw_frame done, result, %d", __FUNCTION__, result);

clean_and_return:

    // 5. De-Init data
    if (data != NULL)
    {
        e->ReleaseByteArrayElements(j_data, (jbyte *)data, JNI_ABORT);
    }
    
    CONCURRENCY_UNLOCK();

    ALOGD ("[HCE] %s: exit", __FUNCTION__);
    
#endif

    return result;
}

static jboolean com_android_nfc_NfcManager_routeAid (JNIEnv* e, jobject o, jbyteArray aid, jint route)
{
    bool result = false;

#ifdef MTK_NFC_HCE_SUPPORT 

    uint8_t *data = NULL;
    uint8_t  length = 0;
    struct nfc_jni_native_data *nat = NULL;
    
    CONCURRENCY_LOCK();
    ALOGD ("[HCE] %s: enter", __FUNCTION__);
    
    // 1. Retrieve native structure address
    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
        ALOGE("[HCE] %s: Invalid NAT", __FUNCTION__);
        //error case , return.
        goto clean_and_return;
    }
    
    // 2. Check command is set default route or set aid
    if (NULL == aid)
    {
        //If aid is null , it is set default route command
        result = nfc_jni_set_default_route((uint32_t)route);
        ALOGE("[HCE] %s: nfc_jni_set_default_route done, result, %d, default_route, %d", __FUNCTION__, result, route);
    }
    else
    {
        //set aid
        // Init data
        length = (uint8_t)  e->GetArrayLength( aid);
        data   = (uint8_t *)e->GetByteArrayElements( aid, NULL);        
        result = nfc_jni_add_aid_routing(data, length, (uint32_t) route);
        ALOGE("[HCE] %s: nfc_jni_add_aid_routing done, result, %d", __FUNCTION__, result);
    }
clean_and_return:

    // 3. De-Init data    
    if (data != NULL)
    {
        e->ReleaseByteArrayElements(aid, (jbyte *)data, JNI_ABORT);
    }

    CONCURRENCY_UNLOCK();

    ALOGD ("[HCE] %s: exit", __FUNCTION__);

#endif 

    return result;
}

static jboolean com_android_nfc_NfcManager_unrouteAid (JNIEnv* e, jobject o, jbyteArray aid)
{
    bool    result = false;

#ifdef MTK_NFC_HCE_SUPPORT 

    uint8_t   *data  = NULL;
    uint32_t  length = 0;    
    struct nfc_jni_native_data *nat = NULL;
    
    CONCURRENCY_LOCK();
    ALOGD ("[HCE] %s: enter", __FUNCTION__);    

    // 1. Retrieve native structure address
    nat = nfc_jni_get_nat(e, o);
    if ( !nat)
    {
        ALOGE("[HCE] %s: Invalid NAT", __FUNCTION__);
        //error case , return.
        goto clean_and_return;
    }
    // 2. Validate Parameters
    if ( NULL == aid)
    {
        ALOGE("[HCE] %s: aid is NULL", __FUNCTION__);
        goto clean_and_return;
    }

    // 3. Init data
    length = (uint32_t) e->GetArrayLength( aid);    
    data   = (uint8_t *)e->GetByteArrayElements( aid, NULL);
    
    // 4. Remove aid  
    result = nfc_jni_remove_aid_routing( data, length);
    ALOGE("[HCE] %s: nfc_jni_remove_aid_routing done, result, %d", __FUNCTION__, result);
            
clean_and_return:

    // 5. De-Init data    
    if (data != NULL)
    {
        e->ReleaseByteArrayElements(aid, (jbyte *)data, JNI_ABORT);
    }

    CONCURRENCY_UNLOCK();

    ALOGD ("[HCE] %s: exit", __FUNCTION__);

#endif
    
    return result;

}


static jboolean com_android_nfc_NfcManager_commitRouting(JNIEnv* e, jobject o)
{

    bool ret = true;
    
#ifdef MTK_NFC_HCE_SUPPORT

    int result = FALSE;
    struct nfc_jni_native_data *nat;
    bool fgPollingWasEnabled = false;

    CONCURRENCY_LOCK();

    // Retrieve native structure address 
    nat = nfc_jni_get_nat(e, o);

    // --- [Deactivate]--- If RF-Discover is  enable, stop RF-discover ---
    if (!nat->fgPollingEnabled)
    {
        fgPollingWasEnabled = false;
        ALOGD("%s: Discovery is not enabled", __FUNCTION__);
    }
    else
    {
        fgPollingWasEnabled = true; 
        result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
        ALOGD("%s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
    }
    //-------------------------------------------------------


    //---[EnableDiscovery]--- If fgPolling was enabled , start RF-discover.---
    if (fgPollingWasEnabled)
    {
        result = nfc_jni_enable_discovery_with_info(nat, gDiscoveryInfo);
        ALOGD("%s: nfc_jni_enable_discovery done, result %d", __FUNCTION__, result);  
        if (result == FALSE) {ret = false;}
    }
    //--------------------------------------------------------------

    CONCURRENCY_UNLOCK();

#endif

    return ret;
}


#if 0 // L migration
static void com_android_nfc_NfcManager_enableRoutingToHost(JNIEnv* e, jobject o)
{
    int  result = -1;

#ifdef MTK_NFC_HCE_SUPPORT 
    
    bool fgPollingWasEnabled = false;
    struct nfc_jni_native_data *nat = NULL;
    
    CONCURRENCY_LOCK();
    ALOGD ("[HCE] %s: enter", __FUNCTION__);    

    // 1. Retrieve native structure address
    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
        ALOGE("[HCE] %s: Invalid NAT", __FUNCTION__);
        //error case , return.
        goto clean_and_return;
    }

    // 2. If RF-Discover is  enable, stop RF-discover.
    if (!nat->fgPollingEnabled)
    {
        fgPollingWasEnabled = false;
        ALOGD("[HCE] %s: Discovery is not enabled", __FUNCTION__);
    }
    else
    {
        fgPollingWasEnabled = true; 
        result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
        ALOGD("[HCE] %s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
    }
    
    // 3. Update routing
    result = nfc_jni_update_routing(true); // true : enable , false : disable
    ALOGD("[HCE] %s: nfc_jni_update_routing done, result %d", __FUNCTION__, result);
  
    // 4. If fgPolling was enabled , start RF discovery.
    if (fgPollingWasEnabled)
    {
        result = nfc_jni_enable_discovery(nat);
        ALOGD("[HCE] %s: nfc_jni_enable_discovery done, result %d", __FUNCTION__, result);  
    }

clean_and_return:

    CONCURRENCY_UNLOCK();
    ALOGD ("[HCE] %s: exit", __FUNCTION__);

#endif
    
    return;
}

static void com_android_nfc_NfcManager_disableRoutingToHost(JNIEnv* e, jobject o)
{
    int  result = -1;

#ifdef MTK_NFC_HCE_SUPPORT 

    bool fgPollingWasEnabled = false;
    struct nfc_jni_native_data *nat = NULL;
    
    CONCURRENCY_LOCK();
    ALOGD ("[HCE] %s: enter", __FUNCTION__);    

    // 1. Retrieve native structure address
    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
        ALOGE("[HCE] %s: Invalid NAT", __FUNCTION__);
        //error case , return.
        goto clean_and_return;
    }

    // 2. If RF-Discover is  enable, stop RF-discover.
    if ( !nat->fgPollingEnabled)
    {
        fgPollingWasEnabled = false; 
        ALOGD("[HCE] %s: Discovery is not enabled", __FUNCTION__);
    }
    else
    {
        fgPollingWasEnabled = true; 
        result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
        ALOGD("[HCE] %s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
    }
    
    // 3. Update routing
    result = nfc_jni_update_routing(false); // true : enable , false : disable
    ALOGD("[HCE] %s: nfc_jni_update_routing done, result %d", __FUNCTION__, result);

    // 4. If fgPolling was enabled , start RF discovery.
    if ( fgPollingWasEnabled)
    {
        result = nfc_jni_enable_discovery(nat);
        ALOGD("[HCE] %s: nfc_jni_enable_discovery done, result %d", __FUNCTION__, result);  
    }
    
clean_and_return:
    
    CONCURRENCY_UNLOCK();
    ALOGD ("[HCE] %s: exit", __FUNCTION__);
    
#endif

    return;
}

#endif

static jboolean com_android_nfc_NfcManager_init_native_struc(JNIEnv *e, jobject o)
{
    NFCSTATUS status;
    struct nfc_jni_native_data *nat = NULL;
    jclass cls;
    jobject obj;
    jfieldID f;

    TRACE("******  Init Native Structure ******"); 

    /* Initialize native structure */
    nat = (nfc_jni_native_data*)malloc(sizeof(struct nfc_jni_native_data));
    if(nat == NULL)
    {
        ALOGD("malloc of nfc_jni_native_data failed");
        return FALSE;   
    }
    memset(nat, 0, sizeof(*nat));
    e->GetJavaVM(&(nat->vm));
    nat->env_version = e->GetVersion();
    nat->manager = e->NewGlobalRef(o);
      
    cls = e->GetObjectClass(o);
    f = e->GetFieldID(cls, "mNative", "J");
    e->SetLongField(o, f, (jlong)nat);
                 
    /* Initialize native cached references */
    cached_NfcManager_notifyNdefMessageListeners = e->GetMethodID(cls,
      "notifyNdefMessageListeners","(Lcom/android/nfc/dhimpl/NativeNfcTag;)V");

    cached_NfcManager_notifyTransactionListeners = e->GetMethodID(cls,
      "notifyTransactionListeners", "([B[B)V");
         
    cached_NfcManager_notifyLlcpLinkActivation = e->GetMethodID(cls,
      "notifyLlcpLinkActivation","(Lcom/android/nfc/dhimpl/NativeP2pDevice;)V");
         
    cached_NfcManager_notifyLlcpLinkDeactivated = e->GetMethodID(cls,
      "notifyLlcpLinkDeactivated","(Lcom/android/nfc/dhimpl/NativeP2pDevice;)V");

    cached_NfcManager_notifySeFieldActivated = e->GetMethodID(cls,
      "notifySeFieldActivated", "()V");

    cached_NfcManager_notifySeFieldDeactivated = e->GetMethodID(cls,
      "notifySeFieldDeactivated", "()V");

#if 0  // L migration  
    cached_NfcManager_notifyTargetDeselected = e->GetMethodID(cls,
      "notifyTargetDeselected","()V");

    cached_NfcManager_notifySeApduReceived= e->GetMethodID(cls,
      "notifySeApduReceived", "([B)V");

    cached_NfcManager_notifySeMifareAccess = e->GetMethodID(cls,
      "notifySeMifareAccess", "([B)V");

    cached_NfcManager_notifySeEmvCardRemoval =  e->GetMethodID(cls,
      "notifySeEmvCardRemoval", "()V");
#endif

#ifdef MTK_NFC_HCE_SUPPORT 
    cached_NfcManager_notifyHostEmuActivated = e->GetMethodID(cls,
           "notifyHostEmuActivated", "()V");

    cached_NfcManager_notifyHostEmuData = e->GetMethodID(cls,
           "notifyHostEmuData", "([B)V");

    cached_NfcManager_notifyHostEmuDeactivated = e->GetMethodID(cls,
           "notifyHostEmuDeactivated", "()V");
#endif

    if(nfc_jni_cache_object(e,"com/android/nfc/dhimpl/NativeNfcTag",&(nat->cached_NfcTag)) == -1)
    {
        ALOGD("Native Structure initialization failed");
        return FALSE;
    }
         
    if(nfc_jni_cache_object(e,"com/android/nfc/dhimpl/NativeP2pDevice",&(nat->cached_P2pDevice)) == -1)
    {
        ALOGD("Native Structure initialization failed");
        return FALSE;   
    }
    TRACE("****** Init Native Structure OK ******");

    return TRUE;

}

/* Init/Deinit method */
static jboolean com_android_nfc_NfcManager_initialize(JNIEnv *e, jobject o)
{
    struct nfc_jni_native_data *nat = NULL;
    int init_result = JNI_FALSE;
    int i=0;
    jboolean result;

    memset(g_llcp_cb_data, 0x00, (sizeof(struct nfc_jni_callback_data*)*NFC_LLCP_SERVICE_NUM*NFC_LLCP_ACT_END));
    memset(g_llcp_service, 0x00, (sizeof(unsigned int)*NFC_LLCP_SERVICE_NUM));
    memset(g_llcp_sertype, 0x00, (sizeof(unsigned char)*NFC_LLCP_SERVICE_NUM));
    memset(g_idx_service , 0x00, (sizeof(unsigned char)*NFC_LLCP_ACT_END));
    memset(g_llcp_remiu  , 0x00, (sizeof(unsigned int)*NFC_LLCP_SERVICE_NUM));
    memset(g_llcp_remrw  , 0x00, (sizeof(unsigned char)*NFC_LLCP_SERVICE_NUM));
    memset(sReceiveLength, 0x00, (sizeof(unsigned int)*NFC_LLCP_SERVICE_NUM));
    memset(sReceiveBuffer, 0x00, (sizeof(unsigned char*)*NFC_LLCP_SERVICE_NUM));

    CONCURRENCY_LOCK();

    ALOGD("%s:call",__FUNCTION__);

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);
    
    nat->seId = SMX_SECURE_ELEMENT_ID;
    
    // L Migration
    nat->tech_mask = DEFAULT_TECH_MASK; //init value
    
    nat->lto = 150;  // LLCP_LTO
    nat->miu = 128; // LLCP_MIU
    // WKS indicates well-known services; 1 << sap for each supported SAP.
    // We support Link mgmt (SAP 0), SDP (SAP 1) and SNEP (SAP 4)
    nat->wks = 0x13;  // LLCP_WKS
    nat->opt = 0;  // LLCP_OPT

    memset(&nat->rDiscoverConfig,0,sizeof(s_mtk_nfc_service_set_discover_req_t));
    nat->fgHostRequestDeactivate = FALSE;

    // ****** Please modify here to config polling loop*******
   
    // ---Reader mode -------------------------------------------
    nat->rDiscoverConfig.u2TotalDuration = 0xAA; // for CT Test 150401 Leo.Kuo
    nat->rDiscoverConfig.fgEnListen = TRUE;
    //nat->rDiscoverConfig.u1OpMode = (NFC_OPMODE_READER); // reader mode
    //nat->rDiscoverConfig.u1OpMode = (NFC_OPMODE_P2P | NFC_OPMODE_READER); // reader + p2p mode
    nat->rDiscoverConfig.reader_setting.fgEnTypeA = TRUE;
    nat->rDiscoverConfig.reader_setting.u1BitRateA = 1; // 106
    nat->rDiscoverConfig.reader_setting.fgEnTypeB = TRUE;
    nat->rDiscoverConfig.reader_setting.u1BitRateB = 1; // 106
    nat->rDiscoverConfig.reader_setting.fgEnTypeF = TRUE;
    nat->rDiscoverConfig.reader_setting.u1BitRateF = 2; // 212
    nat->rDiscoverConfig.reader_setting.fgEnTypeK = TRUE;
    nat->rDiscoverConfig.reader_setting.fgEnTypeV = TRUE;
    nat->rDiscoverConfig.reader_setting.u1BitRateV = 2; // 26.48
    nat->rDiscoverConfig.reader_setting.fgEnDualSubCarrier = FALSE;
    nat->u1DevActivated = 0;
    nat->CurrentRfInterface = NFC_DEV_INTERFACE_NFC_UNKNOWN;
    //----------------------------------------------------------

    //--- P2P mode----------------------------------------------
    nat->rDiscoverConfig.p2p_setting.fgDisableCardMode = TRUE;
    nat->rDiscoverConfig.p2p_setting.fgEnPassiveMode = TRUE;
    nat->rDiscoverConfig.p2p_setting.fgEnActiveMode = FALSE;
    nat->rDiscoverConfig.p2p_setting.fgEnInitiatorMode= TRUE;
    nat->rDiscoverConfig.p2p_setting.fgEnTargetMode = TRUE;
    nat->rDiscoverConfig.p2p_setting.fgEnTypeA  = TRUE;
    nat->rDiscoverConfig.p2p_setting.fgEnTypeF  = TRUE;
    nat->rDiscoverConfig.p2p_setting.u1BitRateA = 0x01;
    nat->rDiscoverConfig.p2p_setting.u1BitRateF = 2; // 212
    //----------------------------------------------------------

    // ---Card mode----------------------------------------------
    nat->rDiscoverConfig.card_setting.u1Swio = 0x07; // bit-0: SWIO-1; bit-1:SWIO-2; bit-2: SWIO-3    
    nat->rDiscoverConfig.card_setting.fgEnTypeA = TRUE;
    nat->rDiscoverConfig.card_setting.u1BitRateA = 0x01;
    nat->rDiscoverConfig.card_setting.fgEnTypeB = TRUE;
    nat->rDiscoverConfig.card_setting.u1BitRateB = 0x01;
    nat->rDiscoverConfig.card_setting.fgEnTypeBP = TRUE;
    nat->rDiscoverConfig.card_setting.u1BitRateBP = 0x01;
    nat->rDiscoverConfig.card_setting.fgEnTypeF = TRUE; // turn on card mode type F for GSMA Felica CLT task
    nat->rDiscoverConfig.card_setting.u1BitRateF = 0x06; // 212 & 424
    //----------------------------------------------------------

    nat->fgPollingEnabled = FALSE;
    nat->rDiscoverConfig.i4DtaPatternNum = 0xFF;
    nat->rDiscoverConfig.u1DtaTestMode = 0xFF;

    exported_nat = nat;

    /* Perform the initialization */
    for (i=0 ;i<NFC_BOOTUP_RTY ;i++) {

        init_result = nfc_jni_initialize(nat);
        ALOGD("%s: retry_cnt:%d init_result :%d",__FUNCTION__,i,init_result);
        if(init_result == TRUE)
        {
            break;
        }
    }
    CONCURRENCY_UNLOCK();

    ALOGD("com_android_nfc_NfcManager_initialize result is %d", init_result);

    /* Convert the result and return */
    return (init_result == TRUE) ? JNI_TRUE : JNI_FALSE;
}

static jboolean com_android_nfc_NfcManager_deinitialize(JNIEnv *e, jobject o)
{
    int deinit_result = FALSE;
    struct nfc_jni_native_data *nat;

    CONCURRENCY_LOCK();

    ALOGD("com_android_nfc_NfcManager_deinitialize()");

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);

    /* Perform the de-initialization */
    deinit_result = nfc_jni_deinitialize(nat);

    if (TRUE == deinit_result)
    {
        nat->fgPollingEnabled = FALSE;
    }    

    CONCURRENCY_UNLOCK();

    ALOGD("com_android_nfc_NfcManager_deinitialize result is %d", deinit_result);

    /* Convert the result and return */
    return (deinit_result == TRUE) ? JNI_TRUE : JNI_FALSE;
}

/* Secure Element methods */
static jintArray com_android_nfc_NfcManager_doGetSecureElementList(JNIEnv *e, jobject o) {
    NFCSTATUS ret;
    struct nfc_jni_callback_data cb_data;
    jintArray list = NULL;
    s_mtk_nfc_jni_se_get_list_rsp_t *rSeGetListRsp;
    //jint SEInfor[MTK_NFC_MAXNO_OF_SE*2];
    struct timespec ts;
    struct nfc_jni_native_data *nat = NULL;
    uint8_t i;
    bool fgPollingWasEnabled = false;
    int  result = -1;

    CONCURRENCY_LOCK();
    
    TRACE("******  Get Secure Element List ******");    
    TRACE("com_android_nfc_NfcManager_doGetSecureElementList()");

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
        TRACE("Retrieve native structure address fail");
        goto clean_and_return;
    }

    // --- [Deactivate]--- If RF-Discover is  enable, stop RF-discover ---
    if (!nat->fgPollingEnabled)
    {
        fgPollingWasEnabled = false;
        ALOGD("%s: Discovery is not enabled", __FUNCTION__);
    }
    else
    {
        fgPollingWasEnabled = true; 
        result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
        ALOGD("%s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
    }
    //-------------------------------------------------------    


    // Create the local semaphore 
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        goto clean_and_return;
    }

    g_working_cb_data = &cb_data;

    ///SWP conformance in normal mode
    {
        FILE *fp;
        int data = 0;
        int patternNum = 0;
        TRACE("%s: check SWP in Normal Mode file (mtknfcdtaInProgress.txt)", __FUNCTION__);        
        fp = fopen("/sdcard/mtknfcdtaInProgress.txt","r+b");
        
        if (fp == NULL)
        {
            ALOGE("%s: can not open mtknfcdtaInProgress.txt", __FUNCTION__);
        }
        else
        {
            fscanf(fp,"%d",&data);
            ALOGE("%s: data: %d", __FUNCTION__, data);
            if (1 == data)
            {
                gSwpTestInNormalMode = TRUE;
                fscanf(fp, ",%d",&patternNum);
                ALOGE("%s: patternNum: %d", __FUNCTION__, patternNum);
                gSwpTestPatternNum = (patternNum);
            }
            else
            {
                gSwpTestInNormalMode = FALSE;
            }
            fclose(fp);
        }
        TRACE("%s: SwpTestInNormalMode,%d,SwpTestPatternNum,%d", __FUNCTION__,gSwpTestInNormalMode,gSwpTestPatternNum);         
    }    

    if (TRUE == gSwpTestInNormalMode)
    {        
        TRACE("SWP Test in Normal mode");
        //PNFC
        JniDtaSetPNFC(gSwpTestPatternNum);
        //return JNI
        g_SeCount = 1;
        list = e->NewIntArray(g_SeCount * 2);
        SEInfor[0] = 1;
        SEInfor[1] = 1;
        e->SetIntArrayRegion(list, 0, g_SeCount * 2, (jint*)SEInfor);
    }
    else
    {
        TRACE("Normal flow , not SWP");
        ret = android_nfc_jni_send_msg(MTK_NFC_GET_SELIST_REQ, 0, NULL);

        if (ret == FALSE) {            
            TRACE("android_nfc_jni_send_msg fail");
            goto clean_and_return;
        }

        /* Wait for callback response */
        if (sem_wait(&cb_data.sem)) {
            ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
            goto clean_and_return;
        }
        
        if (cb_data.status != 0)
        {
           ALOGE("Failed to doGetSecureElementList\n");
           goto clean_and_return;
        }

        rSeGetListRsp = (s_mtk_nfc_jni_se_get_list_rsp_t*)cb_data.pContext;
        
        TRACE("Nb SE: %d", rSeGetListRsp->SeCount);

        list = e->NewIntArray(rSeGetListRsp->SeCount*2);
        g_SeCount = rSeGetListRsp->SeCount;
        
        if ((rSeGetListRsp->SeCount == 0) || (rSeGetListRsp->SeCount > NFC_JNI_MAXNO_OF_SE))
        {
           goto clean_and_return;
        }

        for (i = 0; i < rSeGetListRsp->SeCount; i++) {

            SEInfor[i*2] = (uint32_t)rSeGetListRsp->SeInfor[i].type;
            SEInfor[i*2+1] = (uint32_t)rSeGetListRsp->SeInfor[i].seid;
            TRACE("SE_type %d, %d ; SE_seid %d, %d ; SeCount,%d ",i*2,SEInfor[i*2],i*2+1,SEInfor[i*2+1],rSeGetListRsp->SeCount);
        }
        e->SetIntArrayRegion(list, 0, rSeGetListRsp->SeCount*2, (jint*)SEInfor);    
    }
        
clean_and_return :
    /* clear working callback data pointer */
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    //---[EnableDiscovery]--- If fgPolling was enabled , start RF discovery.---
    if (fgPollingWasEnabled)
    {
        result = nfc_jni_enable_discovery(nat);
        ALOGD("%s: nfc_jni_enable_discovery done, result %d", __FUNCTION__, result);  
    }
    //--------------------------------------------------------------

    CONCURRENCY_UNLOCK();

    return list;
}

#if 0 // L Migration
static void com_android_nfc_NfcManager_doSelectSecureElement(JNIEnv *e, jobject o) {
    #if 1
    NFCSTATUS ret;
    int result = FALSE;
    struct nfc_jni_native_data *nat;
    struct nfc_jni_callback_data cb_data;
    s_mtk_nfc_jni_se_set_mode_req_t rSeSetMode;

    CONCURRENCY_LOCK();

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);

    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        goto clean_and_return;
    }

    g_working_cb_data = &cb_data;

    TRACE("******  Select Secure Element ******");

    //TRACE("com_android_nfc_NfcManager_doSelectSecureElement()");

    /* Set SE mode */
    /* send req msg to nfc daemon */
    memset(&rSeSetMode,0,sizeof(rSeSetMode));
    if(TRUE == gSwpTestInNormalMode)
    {
        rSeSetMode.seid = 1; //SWP1
    }
    else
    {
        rSeSetMode.seid = 0; //SWP1
    }
    rSeSetMode.enable = TRUE;
    rSeSetMode.pContext = (void *)&cb_data;
    TRACE("com_android_nfc_NfcManager_doSelectSecureElement()");
    ret = android_nfc_jni_send_msg(MTK_NFC_JNI_SE_MODE_SET_REQ, sizeof(rSeSetMode), &rSeSetMode);

    if (ret == FALSE)
    {
       ALOGE("send MTK_NFC_JNI_SE_MODE_SET_REQ fail\n");
       goto clean_and_return;
    }

    /* Wait for callback response */
    if (sem_wait(&cb_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }

    result = TRUE;
    
    clean_and_return:

    if (result != TRUE)
    {
       #if 0 // don't kill nfcstackp
       if(nat)
       {
          kill_mtk_client(nat);
       }
       #endif
    }

    /* clear working callback data pointer */
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    CONCURRENCY_UNLOCK();
    #endif
}
#endif

static void com_android_nfc_NfcManager_doSelectSecureElementById(JNIEnv *e, jobject o, jint seid) {
    NFCSTATUS ret;
    int result = FALSE;
    bool response = false;
    struct nfc_jni_native_data *nat;
    struct nfc_jni_callback_data cb_data;
    s_mtk_nfc_jni_se_set_mode_req_t rSeSetMode;
    bool fgPollingWasEnabled = false;

    CONCURRENCY_LOCK();

    // Retrieve native structure address 
    nat = nfc_jni_get_nat(e, o);

    // --- [Deactivate]--- If RF-Discover is  enable, stop RF-discover ---
    if (!nat->fgPollingEnabled)
    {
        fgPollingWasEnabled = false;
        ALOGD("%s: Discovery is not enabled", __FUNCTION__);
    }
    else
    {
        fgPollingWasEnabled = true; 
        result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
        ALOGD("%s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
    }
    //-------------------------------------------------------

    // Create the local semaphore 
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        goto clean_and_return;
    }

    g_working_cb_data = &cb_data;

    TRACE("******  Select Secure Element ******");

    //TRACE("com_android_nfc_NfcManager_doSelect1of3SecureElement(),seid,%d",seid);

    // 1. Set SE mode
    //     send req msg to nfcstackp 
    memset(&rSeSetMode,0,sizeof(rSeSetMode));
    rSeSetMode.seid = (unsigned char)seid;
    rSeSetMode.enable = TRUE;
    rSeSetMode.pContext = (void *)&cb_data;
    TRACE("com_android_nfc_NfcManager_doSelectSecureElementById(),seid,%d", rSeSetMode.seid);
    
    ret = android_nfc_jni_send_msg(MTK_NFC_JNI_SE_MODE_SET_REQ, sizeof(rSeSetMode), &rSeSetMode);

    if (ret == FALSE)
    {
        ALOGE("send MTK_NFC_JNI_SE_MODE_SET_REQ fail\n");
        goto clean_and_return;
    }

    /* Wait for callback response */
    if (sem_wait(&cb_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }

    g_current_seid = seid;

    // 2. Low power card mode : enable (403,1)
    // -- SWP test  in normal flow
    if (TRUE == gSwpTestInNormalMode )
    {
        // TODO : need verify 
        ALOGD("%s : SWP test in normal flow. Do not send PNFC",__FUNCTION__);
        nat->rDiscoverConfig.u1OpMode = 0x02; // card mode
        nat->rDiscoverConfig.u1DtaTestMode = 0x04; //SWP Test
        nat->rDiscoverConfig.i4DtaPatternNum = gSwpTestPatternNum; 
        ALOGD("opMode,%d,DtaTestMode,%d,DtaPatternNum,%d",
            nat->rDiscoverConfig.u1OpMode,nat->rDiscoverConfig.u1DtaTestMode,nat->rDiscoverConfig.i4DtaPatternNum);
    }
//    else
//    {

    //3. Normal and DTA swp test mode always do low power card mode
        response = nfc_jni_low_power_card_mode(true);
        ALOGD("%s: nfc_jni_low_power_card_mode done, result %d", __FUNCTION__, response); 
//    }
    
    result = TRUE;
    
    clean_and_return:
    
    if (result != TRUE)
    {
       #if 0 // don't kill nfcstackp
       if(nat)
       {
          kill_mtk_client(nat);
       }
       #endif
    }
    
    /* clear working callback data pointer */
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    //---[EnableDiscovery]--- If fgPolling was enabled , start RF discovery.---
    if (fgPollingWasEnabled)
    {
        //result = nfc_jni_enable_discovery(nat);
        response = nfc_jni_enable_discovery_with_info(nat, gDiscoveryInfo);
        ALOGD("%s: nfc_jni_enable_discovery done, response %d", __FUNCTION__, response);  
    }
    //--------------------------------------------------------------

    CONCURRENCY_UNLOCK();
    
}


static void com_android_nfc_NfcManager_doDeselectSecureElement(JNIEnv *e, jobject o) {

    NFCSTATUS ret;
    int result = FALSE;
    bool response = false;
    struct nfc_jni_native_data *nat;
    struct nfc_jni_callback_data cb_data;
    s_mtk_nfc_jni_se_set_mode_req_t rSeSetMode;
    bool fgPollingWasEnabled = false;

    CONCURRENCY_LOCK();

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);

    // --- [Deactivate]--- If RF-Discover is  enable, stop RF-discover ---
    if (!nat->fgPollingEnabled)
    {
        fgPollingWasEnabled = false;
        ALOGD("%s: Discovery is not enabled", __FUNCTION__);
    }
    else
    {
        fgPollingWasEnabled = true; 
        result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
        ALOGD("%s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
    }
    //-------------------------------------------------------

    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        goto clean_and_return;
    }

    g_working_cb_data = &cb_data;

    TRACE("******  Deselect Secure Element ******");

    //TRACE("com_android_nfc_NfcManager_doDeselectSecureElement(),seid,%d",seid);

    // 1. Set SE mode
    // send req msg to nfc daemon
    memset(&rSeSetMode,0,sizeof(rSeSetMode));
    rSeSetMode.seid = (unsigned char)g_current_seid; //SWP1
    rSeSetMode.enable = FALSE;
    rSeSetMode.pContext = (void *)&cb_data;
    TRACE("com_android_nfc_NfcManager_doDeselectSecureElement()");
    ret = android_nfc_jni_send_msg(MTK_NFC_JNI_SE_MODE_SET_REQ, sizeof(rSeSetMode), &rSeSetMode);

    if (ret == FALSE)
    {
       ALOGE("send MTK_NFC_JNI_SE_MODE_SET_REQ fail\n");
       goto clean_and_return;
    }

    /* Wait for callback response */
    if (sem_wait(&cb_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }
    g_current_seid = 0;

    // 2. Low power card mode : disable (403,0)
    // -- SWP test  in normal flow
    if (TRUE == gSwpTestInNormalMode )
    {
        // TODO : need verify 
        ALOGD("%s : SWP test in normal flow. Do not send PNFC",__FUNCTION__);
        nat->rDiscoverConfig.u1OpMode = 0x02; // card mode
        nat->rDiscoverConfig.u1DtaTestMode = 0x04; //SWP Test
        nat->rDiscoverConfig.i4DtaPatternNum = gSwpTestPatternNum; 
        ALOGD("opMode,%d,DtaTestMode,%d,DtaPatternNum,%d",
            nat->rDiscoverConfig.u1OpMode,nat->rDiscoverConfig.u1DtaTestMode,nat->rDiscoverConfig.i4DtaPatternNum);
    }
//    else
//    {
     //3. turn off low power card mode
        response = nfc_jni_low_power_card_mode(false);
        ALOGD("%s: nfc_jni_low_power_card_mode done, result %d", __FUNCTION__, response); 

//    }

    result = TRUE;
    
    clean_and_return:
    
    if (result != TRUE)
    {
       #if 0 // don't kill nfcstackp
       if(nat)
       {
          kill_mtk_client(nat);
       }
       #endif
    }
    
    /* clear working callback data pointer */
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    //---[EnableDiscovery]--- If fgPolling was enabled , start RF discovery.---
    if (fgPollingWasEnabled)
    {
        //result = nfc_jni_enable_discovery(nat);
        response = nfc_jni_enable_discovery_with_info(nat, gDiscoveryInfo);
        ALOGD("%s: nfc_jni_enable_discovery done, response %d", __FUNCTION__, response);  
    }
    //--------------------------------------------------------------

    CONCURRENCY_UNLOCK();
    
}
#if 1
static jboolean com_android_nfc_NfcManager_doActivateSecureElementInterfaceById(JNIEnv *e, jobject o, jint seid) {

    jboolean retVal = JNI_TRUE; // true for success
    NFCSTATUS ret;
    int result = FALSE;
    bool response = false;
    struct nfc_jni_native_data *nat;
    struct nfc_jni_callback_data cb_data;
    mtk_nfc_activate_se_interface_req_t rActSetReq;
    mtk_nfc_activate_se_interface_rsp_t* prActSeRsp;
    bool fgPollingWasEnabled = false;

    CONCURRENCY_LOCK();

    // Retrieve native structure address 
    nat = nfc_jni_get_nat(e, o);

    // --- [Deactivate]--- If RF-Discover is  enable, stop RF-discover ---
    if (!nat->fgPollingEnabled)
    {
        fgPollingWasEnabled = false;
        ALOGD("%s: Discovery is not enabled", __FUNCTION__);
    }
    else
    {
        fgPollingWasEnabled = true; 
        result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
        ALOGD("%s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
    }
    //-------------------------------------------------------

    // Create the local semaphore 
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        goto clean_and_return;
    }

    g_working_cb_data = &cb_data;

    TRACE("******  Activate SWP interface ******");

    //TRACE("com_android_nfc_NfcManager_doSelect1of3SecureElement(),seid,%d",seid);

    /* Set SE mode */
    /* send req msg to nfc daemon */
    memset(&rActSetReq,0,sizeof(rActSetReq));
    rActSetReq.u1NfceeId = (unsigned char)seid;

    #if 1
    ret = android_nfc_jni_send_msg(MTK_NFC_JNI_ACTIVATE_SE_INTERFACE_REQ, sizeof(rActSetReq), &rActSetReq);

    if (ret == FALSE)
    {
        ALOGE("send MTK_NFC_JNI_ACTIVATE_SE_INTERFACE_REQ fail\n");
        goto clean_and_return;
    }

    // wait nfc_jni_ActivateSeInterface_callback
    /* Wait for callback response */
    if (sem_wait(&cb_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }
    #endif

    //g_current_seid = seid;
    result = TRUE;
    
    clean_and_return:
    if (result != TRUE)
    {
       #if 0 // don't kill nfcstackp
       if(nat)
       {
          kill_mtk_client(nat);
       }
       #endif
    }

    prActSeRsp = (mtk_nfc_activate_se_interface_rsp_t*)g_working_cb_data->pContext;

    if(prActSeRsp->u4Result == 0)// success
    {
        retVal = JNI_TRUE;
    }
    else
    {
        retVal = JNI_FALSE;
    }
    retVal = JNI_FALSE;
        
    /* clear working callback data pointer */
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    //---[EnableDiscovery]--- If fgPolling was enabled , start RF discovery.---
    if (fgPollingWasEnabled)
    {
        //result = nfc_jni_enable_discovery(nat);
        response = nfc_jni_enable_discovery_with_info(nat, gDiscoveryInfo);
        ALOGD("%s: nfc_jni_enable_discovery done, response %d", __FUNCTION__, response);  
    }
    //--------------------------------------------------------------

    CONCURRENCY_UNLOCK();

    TRACE("com_android_nfc_NfcManager_doActivateSecureElementInterfaceById returned 0x%x , haha", retVal);
    
    return retVal;
    
}
#endif

#if 0
static void com_android_nfc_NfcManager_doGetCurrentActivateSecureElement(JNIEnv *e, jobject o, jint *pseid) {
    NFCSTATUS ret;
    int result = FALSE;
    struct nfc_jni_native_data *nat;
    struct nfc_jni_callback_data cb_data;

    CONCURRENCY_LOCK();

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);

    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        goto clean_and_return;
    }

    g_working_cb_data = &cb_data;

    TRACE("com_android_nfc_NfcManager_doGetCurrentActivateSecureElement()");
    ret = android_nfc_jni_send_msg(MTK_NFC_JNI_SE_GET_CUR_SE_REQ, 0, NULL);

    if (ret == FALSE)
    {
       ALOGE("send MTK_NFC_JNI_SE_GET_CUR_SE_REQ fail\n");
       goto clean_and_return;
    }

    /* Wait for callback response */
    if (sem_wait(&cb_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        goto clean_and_return;
    }

    result = TRUE;
    
    clean_and_return:
    if (result != TRUE)
    {
       if(nat)
       {
          kill_mtk_client(nat);
       }
    }
    
    /* clear working callback data pointer */
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    CONCURRENCY_UNLOCK();
}

#endif

/* Llcp methods */

static jboolean com_android_nfc_NfcManager_doCheckLlcp(JNIEnv *e, jobject o)
{
    jboolean result = JNI_TRUE; 

    if (device_connected_flag != 1)
    {
        result = JNI_FALSE;
    }
    
    TRACE("doCheckLlcp returned 0x%x", result);
    
    return result;
}

static jboolean com_android_nfc_NfcManager_doActivateLlcp(JNIEnv *e, jobject o)
{
    jboolean result = JNI_TRUE;
    
    TRACE("com_android_nfc_NfcManager_doActivateLlcp");
    
    return result;
}

static jobject com_android_nfc_NfcManager_doCreateLlcpConnectionlessSocket(JNIEnv *e, jobject o,
        jint nSap, jstring sn)
{
   int ret ;
   unsigned int SnLength = 0 , TotalLength = 0;
   jobject connectionlessSocket = NULL;
   unsigned int hLlcpSocket = 0;
   struct nfc_jni_native_data *nat;
   unsigned char* pSnBuffer = NULL;    
   MTK_NFC_LLCP_CREATE_SERVICE*  pSocket = NULL; // Setup by nSap,sn
   jclass clsNativeConnectionlessSocket;
   jfieldID f;

   CONCURRENCY_LOCK();   
   /* Retrieve native structure address */
   nat = nfc_jni_get_nat(e, o); 

   if (sn != NULL)
   {
      pSnBuffer = (unsigned char *)e->GetStringUTFChars(sn, NULL);
      SnLength = (unsigned int)e->GetStringUTFLength(sn);
   }
   TotalLength = (sizeof(MTK_NFC_LLCP_CREATE_SERVICE) + SnLength);
   pSocket = (MTK_NFC_LLCP_CREATE_SERVICE*)malloc(TotalLength);
   memset(pSocket, 0x00, TotalLength);

   pSocket->buffer_options.miu = LLCP_DEFAULT_LENGTH;
   pSocket->buffer_options.rw = 1;
   pSocket->connection_type = MTK_NFC_LLCP_CONNECTION_LESS;
   if ((pSnBuffer != NULL) && (SnLength != 0))
   {
       memcpy(pSocket->llcp_sn.buffer, pSnBuffer, SnLength);
       pSocket->llcp_sn.length = SnLength;
   }
   pSocket->sap = (unsigned char)nSap;
   
   /* Create socket */
   TRACE("com_android_nfc_NfcManager_doCreateLlcpConnectionlessSocket,sap,%d,%d,type,%d,rw,%d,miu,%d",
   pSocket->sap, pSocket->llcp_sn.length, pSocket->connection_type, pSocket->buffer_options.rw , pSocket->buffer_options.miu);

   //Send  MTK_NFC_P2P_CREATE_CLIENT_REQ message   
   ret = android_nfc_jni_send_msg(MTK_NFC_P2P_CREATE_CLIENT_REQ,TotalLength, (void *)pSocket);
   if (pSocket != NULL) 
   {
      free(pSocket);
   }   

   if (ret == FALSE)
   {
      ALOGE("send doCreateLlcpConnectionlessSocket cmd fail\n");
      connectionlessSocket = NULL;      
      goto error;
   }

   
   // Wait MTK_NFC_P2P_CREATE_CLIENT_RSP mesage and update hLlcpSocket and ret
   if (sem_wait(&g_cb_p2p_data.sem)) 
   {
       ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
       connectionlessSocket = NULL;
       goto error;
   }
   ret = g_cb_p2p_data.status;
   hLlcpSocket = hLlcpWorkingHandle;
   if(ret != 0)
   {
      ALOGE("MTK_NFC_P2P_CREATE_CLIENT_RSP returned 0x%04x", ret);
      connectionlessSocket = NULL;
      goto error;
   }
   TRACE("MTK_NFC_P2P_CREATE_CLIENT_RSP returned 0x%04x", ret);

   TRACE("MTK_NFC_P2P_CREATE_CLIENT_RSP (hSocket=0x%08x, nSap=0x%02x)", hLlcpSocket, nSap);

   /* Create new NativeLlcpConnectionlessSocket object */
   if(nfc_jni_cache_object(e,"com/android/nfc/dhimpl/NativeLlcpConnectionlessSocket",&(connectionlessSocket)) == -1)
   {
      connectionlessSocket = NULL;
      goto error;
   } 
   
   /* Get NativeConnectionless class object */
   clsNativeConnectionlessSocket = e->GetObjectClass(connectionlessSocket);
   if(e->ExceptionCheck())
   {
      connectionlessSocket = NULL;
      goto error;
   }
   
   /* Set socket handle */
   f = e->GetFieldID(clsNativeConnectionlessSocket, "mHandle", "I");
   e->SetIntField(connectionlessSocket, f,(jint)hLlcpSocket);
   TRACE("Connectionless socket Handle = %02x\n",hLlcpSocket);    
   
   /* Set the miu link of the connectionless socket */
   f = e->GetFieldID(clsNativeConnectionlessSocket, "mLinkMiu", "I");
   e->SetIntField(connectionlessSocket, f,(jint)LLCP_DEFAULT_LENGTH);
   TRACE("Connectionless socket Link MIU = %d\n",LLCP_DEFAULT_LENGTH);    
   
   /* Set socket SAP */
   f = e->GetFieldID(clsNativeConnectionlessSocket, "mSap", "I");
   e->SetIntField(connectionlessSocket, f,(jint)nSap);
   TRACE("Connectionless socket SAP = %d\n",nSap);    
   
error:
    
   if ((pSnBuffer != NULL) && (e != NULL)) 
   {
       e->ReleaseStringUTFChars(sn, (const char *)pSnBuffer);
   }    
   
   CONCURRENCY_UNLOCK();
   
   return connectionlessSocket;
   
}


static jobject com_android_nfc_NfcManager_doCreateLlcpServiceSocket(JNIEnv *e, jobject o, jint nSap, jstring sn, jint miu, jint rw, jint linearBufferLength)
{
    int ret = 0; 
    unsigned int SnLength = 0 , TotalLength = 0;   
    unsigned int hLlcpSocket = 0;
    struct nfc_jni_native_data *nat;
    jobject serviceSocket = NULL;
    jclass clsNativeLlcpServiceSocket;
    jfieldID f;    
    unsigned char* pSnBuffer = NULL;    
    MTK_NFC_LLCP_CREATE_SERVICE* pSocket = NULL; // Setup by nSap,miu,rw,sn


    CONCURRENCY_LOCK();
    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o); 


    if (sn != NULL)
    {
        pSnBuffer = (unsigned char *)e->GetStringUTFChars(sn, NULL);
        SnLength = (unsigned int)e->GetStringUTFLength(sn);
    }
    TotalLength = (sizeof(MTK_NFC_LLCP_CREATE_SERVICE) + SnLength);
    pSocket = (MTK_NFC_LLCP_CREATE_SERVICE*)malloc(TotalLength);
    memset(pSocket, 0x00, TotalLength);

    pSocket->buffer_options.miu = miu;
    pSocket->buffer_options.rw = rw;
    pSocket->connection_type = MTK_NFC_LLCP_CONNECTION_ORIENTED;
    if ((pSnBuffer != NULL) && (SnLength != 0))
    {
        memcpy(pSocket->llcp_sn.buffer, pSnBuffer, SnLength);
        pSocket->llcp_sn.length = SnLength;
    }
    pSocket->sap = (unsigned char)nSap;

    /* Create socket */
    TRACE("com_android_nfc_NfcManager_doCreateLlcpServiceSocket,sap,%d,%d,type,%d,rw,%d,miu,%d",
    pSocket->sap, pSocket->llcp_sn.length, pSocket->connection_type, pSocket->buffer_options.rw , pSocket->buffer_options.miu);

    //Send  MTK_NFC_P2P_CREATE_CLIENT_REQ message
    ret = android_nfc_jni_send_msg(MTK_NFC_P2P_CREATE_SERVER_REQ,TotalLength, (void *)pSocket);
    if (pSocket != NULL) 
    {
        free(pSocket);
    }   

    if (ret == FALSE)
    {
        ALOGE("send doCreateLlcpServiceSocket cmd fail\n");
        serviceSocket = NULL;
        goto error;
    }

    // Wait MTK_NFC_P2P_CREATE_CLIENT_RSP mesage and update hLlcpSocket and ret
    if (sem_wait(&g_cb_p2p_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        serviceSocket = NULL;
        goto error;
    }
    ret = g_cb_p2p_data.status;
    hLlcpSocket = hLlcpWorkingHandle;

    if(ret != 0)
    {
        ALOGE("SERVER_RSP returned 0x%04x", ret);
        serviceSocket = NULL;
        goto error;
    }
    TRACE("SERVER_RSP returned 0x%08x 0x%04x", hLlcpSocket, ret);

    /* Create new NativeLlcpServiceSocket object */
    if(nfc_jni_cache_object(e,"com/android/nfc/dhimpl/NativeLlcpServiceSocket",&(serviceSocket)) == -1)
    {
        ALOGE("Llcp Socket object creation error");
        serviceSocket = NULL;
        goto error;
    } 

    /* Get NativeLlcpServiceSocket class object */
    clsNativeLlcpServiceSocket = e->GetObjectClass(serviceSocket);
    if(e->ExceptionCheck())
    {
        ALOGE("Llcp Socket get object class error");
        serviceSocket = NULL;
        goto error;
    } 

    /* Set socket handle */
    f = e->GetFieldID(clsNativeLlcpServiceSocket, "mHandle", "I");
    e->SetIntField(serviceSocket, f,(jint)hLlcpSocket);
    TRACE("Service socket Handle = 0x%02x\n",hLlcpSocket);  

    /* Set socket linear buffer length */
    f = e->GetFieldID(clsNativeLlcpServiceSocket, "mLocalLinearBufferLength", "I");
    e->SetIntField(serviceSocket, f,(jint)linearBufferLength);
    TRACE("Service socket Linear buffer length = %d\n",linearBufferLength);  

    /* Set socket MIU */
    f = e->GetFieldID(clsNativeLlcpServiceSocket, "mLocalMiu", "I");
    e->SetIntField(serviceSocket, f,(jint)miu);
    TRACE("Service socket MIU = %d\n",miu);    

    /* Set socket RW */
    f = e->GetFieldID(clsNativeLlcpServiceSocket, "mLocalRw", "I");
    e->SetIntField(serviceSocket, f,(jint)rw);
    TRACE("Service socket RW = %d\n",rw); 

error:

    if ((pSnBuffer != NULL) && (e != NULL)) 
    {
        e->ReleaseStringUTFChars(sn, (const char *)pSnBuffer);
    }    

    CONCURRENCY_UNLOCK();

    return serviceSocket;

}


static jobject com_android_nfc_NfcManager_doCreateLlcpSocket(JNIEnv *e, jobject o, jint nSap, jint miu, jint rw, jint linearBufferLength)
{
    jobject clientSocket = NULL;

    int ret = 0; 
    unsigned int SnLength = 0 , TotalLength = 0;
    unsigned int hLlcpSocket = 0;
    struct nfc_jni_native_data *nat;
    jclass clsNativeLlcpSocket;
    jfieldID f;
    unsigned char* pSnBuffer = NULL;    
    MTK_NFC_LLCP_CREATE_SERVICE* pSocket = NULL; // Setup by nSap,miu,rw
    // MTK_NFC_LLCP_RSP_SERVICE* pSerRsp = NULL;

    CONCURRENCY_LOCK();

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o); 

    TotalLength = (sizeof(MTK_NFC_LLCP_CREATE_SERVICE));
    pSocket = (MTK_NFC_LLCP_CREATE_SERVICE*)malloc(TotalLength);
    memset(pSocket, 0x00, TotalLength);

    pSocket->buffer_options.miu = miu;
    pSocket->buffer_options.rw = rw;
    pSocket->connection_type = MTK_NFC_LLCP_CONNECTION_ORIENTED;
    pSocket->llcp_sn.length = 0;

    pSocket->sap = (unsigned char)nSap;

    /* Create socket */
    TRACE("com_android_nfc_NfcManager_doCreateLlcpSocket,sap,%d,%d,type,%d,rw,%d,miu,%d",
    pSocket->sap, pSocket->llcp_sn.length, pSocket->connection_type, pSocket->buffer_options.rw , pSocket->buffer_options.miu);

    //Send  MTK_NFC_P2P_CREATE_CLIENT_REQ message
    ret = android_nfc_jni_send_msg(MTK_NFC_P2P_CREATE_CLIENT_REQ,TotalLength, (void *)pSocket);
    if (pSocket != NULL) 
    {
        free(pSocket);
    }   

    if (ret == FALSE)
    {
        ALOGE("send doCreateLlcpSocket cmd fail\n");
        goto error;
    }

    // Wait MTK_NFC_P2P_CREATE_CLIENT_RSP mesage and update hLlcpSocket and ret
    if (sem_wait(&g_cb_p2p_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        clientSocket =  NULL;
        goto error;
    }
    ret = g_cb_p2p_data.status;
    hLlcpSocket = hLlcpWorkingHandle;

    if(ret != 0)
    {
        ALOGE("CLIENT RSP returned 0x%04x", ret);
        clientSocket = NULL;
        goto error;
    }
    TRACE("CLIENT_RSP returned 0x%04x", ret);

    /* Create new NativeLlcpSocket object */
    if(nfc_jni_cache_object(e,"com/android/nfc/dhimpl/NativeLlcpSocket",&(clientSocket)) == -1)
    {
        ALOGE("Llcp socket object creation error");  
        clientSocket = NULL;
        goto error;
    } 

    /* Get NativeConnectionless class object */
    clsNativeLlcpSocket = e->GetObjectClass(clientSocket);
    if(e->ExceptionCheck())
    {
        ALOGE("Get class object error");      
        clientSocket =  NULL;
        goto error;
    }

    /* Test if an SAP number is present */
    if(nSap != 0)
    {
        /* Set socket SAP */
        f = e->GetFieldID(clsNativeLlcpSocket, "mSap", "I");
        e->SetIntField(clientSocket, f,(jint)nSap);
        TRACE("socket SAP = %d\n",nSap);      
    }  
    /* Set socket handle */
    f = e->GetFieldID(clsNativeLlcpSocket, "mHandle", "I");
    e->SetIntField(clientSocket, f,(jint)hLlcpSocket);
    TRACE("socket Handle = %02x\n",hLlcpSocket);  

    /* Set socket MIU */
    f = e->GetFieldID(clsNativeLlcpSocket, "mLocalMiu", "I");
    e->SetIntField(clientSocket, f,(jint)miu);
    TRACE("socket MIU = %d\n",miu);    

    /* Set socket RW */
    f = e->GetFieldID(clsNativeLlcpSocket, "mLocalRw", "I");
    e->SetIntField(clientSocket, f,(jint)rw);
    TRACE("socket RW = %d\n",rw);   

error:

    CONCURRENCY_UNLOCK();  

    return clientSocket;
   
}


static jint com_android_nfc_NfcManager_doGetLastError(JNIEnv *e, jobject o)
{
    ALOGD("com_android_nfc_NfcManager_doGetLastError()");
    
    return 0;
}

static void com_android_nfc_NfcManager_doAbort(JNIEnv *e, jobject o)
{
    struct nfc_jni_native_data *nat;

    ALOGD("com_android_nfc_NfcManager_doAbort()");

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);

    emergency_recovery(nat);
}

static void com_android_nfc_NfcManager_doSetP2pInitiatorModes(JNIEnv *e, jobject o,
        jint modes)
{
    ALOGD("com_android_nfc_NfcManager_doSetP2pInitiatorModes()");
}

static void com_android_nfc_NfcManager_doSetP2pTargetModes(JNIEnv *e, jobject o,
        jint modes)
{
    ALOGD("com_android_nfc_NfcManager_doSetP2pTargetModes()");
}
    /*******************************************************************************
    ** 2015/05/25 Leo.Kuo <<NFC Quick Enable>>
    ** Function:        com_android_nfc_NfcManager_doDownload
    **
    ** Description:    1. exec NFC Middleware nfcstackp.
    **                    2. Download firmware patch files.
    **                    3. Do not turn on NFC,NFC Enter Sleep mode
    **
    ** Returns:         True if ok.
    **
    *******************************************************************************/
static jboolean com_android_nfc_NfcManager_doDownload(JNIEnv *e, jobject o)
{
    ALOGD("com_android_nfc_NfcManager_doDownload()");
#ifdef DTA_SUPPORT
        dta_quick_mode = check_dta_quick_mode();
        if(dta_quick_mode == TRUE)
        {
            //clear dta sync flag
            ALOGD("[QE]%s: DTA/EM Mode Enter doDownload() ... do nothing",__FUNCTION__);
            dta_nfc_jni_set_dta_quick_mode(FALSE);
            return JNI_TRUE;
        }

#endif
#ifdef MTK_NFC_QUICK_SUPPORT
        struct nfc_jni_native_data *nat;
        bool response = false;
        /* Retrieve native structure address */
        nat = nfc_jni_get_nat(e, o);

        quick_enable_mode = quickEnable_cfg_check();
        if(quick_enable_mode == TRUE) {
            // init nfcstackp , start download firmware
            com_android_nfc_NfcManager_initialize(e,o);
            // NFC Enter Sleep mode
            // 1. Set mode :IDLE
            // 2. enter mtc mos mode
            ALOGD("[QE]%s: Enter MTC_mos_mode()",__FUNCTION__);

            nat->rDiscoverConfig.u1OpMode = MODE_IDLE;
            nat->fgPollingEnabled = FALSE;

            com_android_nfc_NfcManager_deinitialize(e,o);

            return response;
        }
#endif
            // DTA/EM flow
            //Do nothing
    return JNI_TRUE;
}

static jstring com_android_nfc_NfcManager_doDump(JNIEnv *e, jobject o)
{
    ALOGD("com_android_nfc_NfcManager_doDump()");
    return NULL;
}

static void com_android_nfc_NfcManager_doEnableScreenOffSuspend(JNIEnv* e, jobject o)
{
    //PowerSwitch::getInstance().setScreenOffPowerState(PowerSwitch::POWER_STATE_FULL);
    ALOGD("com_android_nfc_NfcManager_doEnableScreenOffSuspend()");
    return;
}

static void com_android_nfc_NfcManager_doDisableScreenOffSuspend(JNIEnv* e, jobject o)
{
    //PowerSwitch::getInstance().setScreenOffPowerState(PowerSwitch::POWER_STATE_OFF);
    ALOGD("com_android_nfc_NfcManager_doDisableScreenOffSuspend()");
    return;    
}

static void com_android_nfc_NfcManager_doSetNfcModePolling(JNIEnv *e, jobject o, 
    jint mode, jboolean actNow)
{
    struct nfc_jni_native_data *nat = NULL;
    int result = FALSE;

    CONCURRENCY_LOCK();
    
    ALOGD("%s : mode, 0x%02x, actNow, %d", __FUNCTION__, mode, actNow);

    // 1. Retrieve native structure address
    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
        ALOGE("%s: Invalid NAT", __FUNCTION__);
        goto clean_and_return;
    }

    #if 0
    // 2. Update discovery info
    //reader_mode == true is reader mode only
    //gDiscoveryInfo.reader_mode = (( mode & MODE_READER ) == MODE_READER) ? true : false ; 
    gDiscoveryInfo.enable_p2p  = (( mode & MODE_P2P    ) == MODE_P2P)    ? true : false ;
    #endif
    
    // 3. Update polling mode
    nat->rDiscoverConfig.u1OpMode = mode ;


    ALOGD("%s : u1OpMode = %d, reader_mode = %d, enable_p2p = %d",
        __FUNCTION__, nat->rDiscoverConfig.u1OpMode, gDiscoveryInfo.reader_mode, gDiscoveryInfo.enable_p2p);
    // 4. Check polling mode
    if (nat->rDiscoverConfig.u1OpMode == MODE_IDLE)
    {
        nfc_jni_mtc_mos_mode(TRUE);
    }
    else
    {
        nfc_jni_mtc_mos_mode(FALSE);
    }
#if 0  //  TODO, move to enableDiscovery
    //keep mode use in doDisableReaderMode()
    temp_mode = nat->rDiscoverConfig.u1OpMode;


    // -- SWP in normal flow
    if (TRUE == gSwpTestInNormalMode )
    {
        ALOGD("%s : SWP Test in normal flow. Do not send PNFC",__FUNCTION__);
        nat->rDiscoverConfig.u1OpMode = 0x02; // card mode
        nat->rDiscoverConfig.u1DtaTestMode = 0x04; //SWP Test
        nat->rDiscoverConfig.i4DtaPatternNum = gSwpTestPatternNum; 
        ALOGD("opMode,%d,DtaTestMode,%d,DtaPatternNum,%d",
            nat->rDiscoverConfig.u1OpMode,nat->rDiscoverConfig.u1DtaTestMode,nat->rDiscoverConfig.i4DtaPatternNum);
        goto clean_and_return;
    }
    //
#endif

    // Check start polling
    if ( actNow )
    {   
        //If RF-Discover is  enable, stop RF-discover.
        if (!nat->fgPollingEnabled)
        {
            ALOGD("%s: Discovery is not enabled", __FUNCTION__);
        }
        else
        {
            result = nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
            ALOGD("%s: nfc_jni_deactivate done, result %d", __FUNCTION__, result);  
        }    
        
        result = nfc_jni_enable_discovery_with_info(nat, gDiscoveryInfo);
        ALOGD("%s: nfc_jni_enable_discovery_with_info done, result %d", __FUNCTION__, result);
#if 0         
        // low power card mode
        result = nfc_jni_low_power_card_mode(mode);
        ALOGD("%s: nfc_jni_low_power_card_mode done, result %d", __FUNCTION__, result); 
        
        result = nfc_jni_enable_discovery(nat);
        ALOGD("%s: nfc_jni_enable_discovery done, result %d", __FUNCTION__, result);          
#endif
    }
    else 
    {
        ALOGD("%s : set mode only.", __FUNCTION__);
    }    

clean_and_return:    

    CONCURRENCY_UNLOCK();

    ALOGD ("%s: exit", __FUNCTION__);
    
    return;
}

#if 0 // L Migration
static void com_android_nfc_NfcManager_doSetNfcMode(JNIEnv *e, jobject o, jint mode)
{
    struct nfc_jni_callback_data cb_data;
    struct nfc_jni_native_data *nat = NULL;
    int result = FALSE;
    int ret = -1;
    s_mtk_nfc_em_pnfc_req rSetPnfc;
    uint16_t PayloadSize = sizeof(s_mtk_nfc_em_pnfc_req);
    
    CONCURRENCY_LOCK();
    
    ALOGD("com_android_nfc_NfcManager_doSetNfcMode()");

    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL))
    {
       ALOGD("com_android_nfc_NfcManager_enableDiscovery(), CB INIT FAIL");
       goto clean_and_return;
    }
    /* assign working callback data pointer */
    g_working_cb_data = &cb_data;

    /* Retrieve native structure address */
    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
       ALOGD("com_android_nfc_NfcManager_enableDiscovery(), INVALID NAT");
       goto clean_and_return;
    }
    nat->rDiscoverConfig.u1OpMode = 0; // disable all
    if ((mode & MODE_READER) != 0) {
        nat->rDiscoverConfig.u1OpMode |= 0x01; // bit-0
    } 
    if ((mode & MODE_P2P) != 0) {
        nat->rDiscoverConfig.u1OpMode |= 0x04; // bit-2
    } 
    if ((mode & MODE_CARD) != 0) {
        nat->rDiscoverConfig.u1OpMode |= 0x02; // bit-1
    }
    if (mode == 0)
    {
        nat->rDiscoverConfig.u1OpMode |= 0x02; // bit-1
        ALOGD("no nfc function, set card mode only");
    }

    //keep mode use in doDisableReaderMode()
    temp_mode = nat->rDiscoverConfig.u1OpMode;

    ///SWP in normal flow
    if (TRUE == gSwpTestInNormalMode )
    {
        ALOGD("%s : SWP Test in normal flow. Do not send PNFC",__FUNCTION__);
        nat->rDiscoverConfig.u1OpMode = 0x02; // card mode
        nat->rDiscoverConfig.u1DtaTestMode = 0x04; //SWP Test
        nat->rDiscoverConfig.i4DtaPatternNum = gSwpTestPatternNum; 
        ALOGD("opMode,%d,DtaTestMode,%d,DtaPatternNum,%d",
            nat->rDiscoverConfig.u1OpMode,nat->rDiscoverConfig.u1DtaTestMode,nat->rDiscoverConfig.i4DtaPatternNum);
        goto clean_and_return;
    }
    ///
    
    memset(&rSetPnfc,0,sizeof(rSetPnfc));
    if ((mode & MODE_CARD) != 0)
    {
       unsigned char pnfc[] = "$PNFC403,1*"; 
       
       rSetPnfc.action = 1;
       rSetPnfc.datalen = sizeof(pnfc);
          memcpy(&rSetPnfc.data[0],pnfc,sizeof(pnfc));
       ALOGD("Send PNFC 403,1");
    }
    else
    {
       unsigned char pnfc[] = "$PNFC403,0*"; 
       
       rSetPnfc.action = 1;
       rSetPnfc.datalen = sizeof(pnfc);
          memcpy(&rSetPnfc.data[0],pnfc,sizeof(pnfc));
       ALOGD("Send PNFC 403,0");
    }
    
        
    ret = android_nfc_jni_send_msg(MTK_NFC_TEST_PNFC, PayloadSize, &rSetPnfc);

    if (ret == FALSE)
    {
       ALOGE("send MTK_NFC_TEST_PNFC fail\n");
       goto clean_and_return;
    }
    
    // Wait for callback response
    if(sem_wait(&cb_data.sem))
    {
       ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
       goto clean_and_return;
    }
    ALOGD("com_android_nfc_NfcManager_doSetNfcMode(), done");
    // Initialization Status (0: success, 1: fail)
    if(cb_data.status != 0)
    {
       ALOGE("pnfc in set mode fail, stauts: %d\n", cb_data.status);
       goto clean_and_return;
    }

    
    result = TRUE;
    // TODO: if the config is card mode only, to config DSP and config polling loop again
    
clean_and_return:
    /* clear working callback data pointer */
    g_working_cb_data = NULL;
 
    nfc_cb_data_deinit(&cb_data);
    
    if (result != TRUE)
    {
       #if 0 // don't kill nfcstackp
       if(nat)
       {
          kill_mtk_client(nat);
       }
       #endif
    }    

    CONCURRENCY_UNLOCK();
}

static void com_android_nfc_NfcManager_doSetNfc(JNIEnv *e, jobject o, jint on_off)
{
    //dummy, replaced by doSetNfcMode
}

static void com_android_nfc_NfcManager_doSetNfcReaderP2p(JNIEnv *e, jobject o, jint on_off)
{
    //dummy, replaced by doSetNfcMode
}

static void com_android_nfc_NfcManager_doEnableReaderMode (JNIEnv *e, jobject o, jint technologies)
{

    struct nfc_jni_native_data *nat = NULL;
    int result = FALSE;

    ALOGD("com_android_nfc_NfcManager_doEnableReaderMode()");
    CONCURRENCY_LOCK();

    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
        ALOGD("com_android_nfc_NfcManager_doEnableReaderMode(), INVALID NAT");
        goto clean_and_return;
    }

    nfc_jni_deactivate(nat, 0); // 0: idle, 1/2: sleep, 3:discovery
    temp_mode = nat->rDiscoverConfig.u1OpMode;
    nat->rDiscoverConfig.u1OpMode = 0x01; // reader only
    
    if (technologies & 0x01)
       nat->rDiscoverConfig.reader_setting.fgEnTypeA = true;
    if (technologies & 0x02)
       nat->rDiscoverConfig.reader_setting.fgEnTypeB = true;
    if (technologies & 0x04)
       nat->rDiscoverConfig.reader_setting.fgEnTypeF = true;
    if (technologies & 0x08)
       nat->rDiscoverConfig.reader_setting.fgEnTypeV = true;
    if (technologies & 0x10)
       nat->rDiscoverConfig.reader_setting.fgEnTypeK = false;

    //nat->rDiscoverConfig.reader_setting.fgEnTypeBP = true;

    result = nfc_jni_enable_discovery(nat);
    
clean_and_return:
    
    ALOGD("com_android_nfc_NfcManager_doEnableReaderMode(), done, result %d",result); 
    CONCURRENCY_UNLOCK();

    return;
}

static void com_android_nfc_NfcManager_doDisableReaderMode (JNIEnv* e, jobject o)
{

    struct nfc_jni_native_data *nat = NULL;
    int result = -1;
    int nfc_mode = 0;
    
    CONCURRENCY_LOCK();

    ALOGD("com_android_nfc_NfcManager_doDisableReaderMode()");
 
    nat = nfc_jni_get_nat(e, o);
    if (!nat)
    {
        ALOGE("com_android_nfc_NfcManager_doDisableReaderMode(), Invalid NAT");
    }
    else
    {
        nfc_mode = temp_mode;

        nfc_jni_deactivate(nat, 0);
        nat->rDiscoverConfig.u1OpMode = 0; // disable all
        if ((nfc_mode & NFC_OPMODE_READER) != 0) {
            nat->rDiscoverConfig.u1OpMode |= 0x01; // bit-0
        } 
        if ((nfc_mode & NFC_OPMODE_P2P) != 0) {
            nat->rDiscoverConfig.u1OpMode |= 0x04; // bit-2
        } 
        if ((nfc_mode & NFC_OPMODE_CARD) != 0) {
            nat->rDiscoverConfig.u1OpMode |= 0x02; // bit-1
        }
        if (nfc_mode == 0)
        {
            nat->rDiscoverConfig.u1OpMode |= 0x02; // bit-1
            ALOGD("no nfc function, set card mode only");
        }
        
        result = nfc_jni_enable_discovery(nat);
    }
    ALOGD("com_android_nfc_NfcManager_doDisableReaderMode(), done, result %d",result);   

    CONCURRENCY_UNLOCK();
}
#endif 

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] =
{
    {"doDownload", "()Z",
        (void *)com_android_nfc_NfcManager_doDownload},

    {"initializeNativeStructure", "()Z",
        (void *)com_android_nfc_NfcManager_init_native_struc},

    {"doInitialize", "()Z",
        (void *)com_android_nfc_NfcManager_initialize},
 
    {"doDeinitialize", "()Z",
        (void *)com_android_nfc_NfcManager_deinitialize},
      
    {"doEnableDiscovery", "(IZZZZZ)V",
        (void *)com_android_nfc_NfcManager_enableDiscovery},
      
#ifdef MTK_NFC_HCE_SUPPORT 
    {"sendRawFrame", "([B)Z",
        (void*) com_android_nfc_NfcManager_sendRawFrame},
   
    {"routeAid", "([BI)Z",
        (void*) com_android_nfc_NfcManager_routeAid},
           
    {"unrouteAid", "([B)Z",
        (void*) com_android_nfc_NfcManager_unrouteAid},

    {"commitRouting", "()Z",
        (void*) com_android_nfc_NfcManager_commitRouting},

   #if 0 // L midration
   {"enableRoutingToHost", "()V",
           (void*) com_android_nfc_NfcManager_enableRoutingToHost},
   
   {"disableRoutingToHost", "()V",
           (void*) com_android_nfc_NfcManager_disableRoutingToHost},
   #endif
   
#endif
      
    {"doCheckLlcp", "()Z",
      (void *)com_android_nfc_NfcManager_doCheckLlcp},
      
    {"doActivateLlcp", "()Z",
      (void *)com_android_nfc_NfcManager_doActivateLlcp},
            
    {"doCreateLlcpConnectionlessSocket", "(ILjava/lang/String;)Lcom/android/nfc/dhimpl/NativeLlcpConnectionlessSocket;",
      (void *)com_android_nfc_NfcManager_doCreateLlcpConnectionlessSocket},
        
    {"doCreateLlcpServiceSocket", "(ILjava/lang/String;III)Lcom/android/nfc/dhimpl/NativeLlcpServiceSocket;",
      (void *)com_android_nfc_NfcManager_doCreateLlcpServiceSocket},
      
    {"doCreateLlcpSocket", "(IIII)Lcom/android/nfc/dhimpl/NativeLlcpSocket;",
      (void *)com_android_nfc_NfcManager_doCreateLlcpSocket},
      
    {"doGetLastError", "()I",
      (void *)com_android_nfc_NfcManager_doGetLastError},

    {"doDisableDiscovery", "()V",
      (void *)com_android_nfc_NfcManager_disableDiscovery},

    {"doSetTimeout", "(II)Z",
      (void *)com_android_nfc_NfcManager_doSetTimeout},

    {"doGetTimeout", "(I)I",
      (void *)com_android_nfc_NfcManager_doGetTimeout},

    {"doResetTimeouts", "()V",
      (void *)com_android_nfc_NfcManager_doResetTimeouts},

    {"doAbort", "()V",
      (void *)com_android_nfc_NfcManager_doAbort},

    {"doSetP2pInitiatorModes","(I)V",
      (void *)com_android_nfc_NfcManager_doSetP2pInitiatorModes},

    {"doSetP2pTargetModes","(I)V",
      (void *)com_android_nfc_NfcManager_doSetP2pTargetModes},

    {"doEnableScreenOffSuspend", "()V",
            (void *)com_android_nfc_NfcManager_doEnableScreenOffSuspend},

    {"doDisableScreenOffSuspend", "()V",
            (void *)com_android_nfc_NfcManager_doDisableScreenOffSuspend},

    {"doDump", "()Ljava/lang/String;",
      (void *)com_android_nfc_NfcManager_doDump},

   //********** MTK Customized - start **********//
    {"doGetSecureElementList", "()[I",
       (void *)com_android_nfc_NfcManager_doGetSecureElementList},
    
    #if 0 // L migration
    {"doSelectSecureElement", "()V",
       (void *)com_android_nfc_NfcManager_doSelectSecureElement},
    #endif
     
    {"doSelectSecureElementById", "(I)V",
       (void *)com_android_nfc_NfcManager_doSelectSecureElementById},      
    
    {"doDeselectSecureElement", "()V",
       (void *)com_android_nfc_NfcManager_doDeselectSecureElement},

    {"doSetNfcModePolling","(IZ)V",
      (void *)com_android_nfc_NfcManager_doSetNfcModePolling},
       
#if 1    
    {"doActivateSecureElementInterfaceById", "(I)Z",
       (void *)com_android_nfc_NfcManager_doActivateSecureElementInterfaceById}, 
#endif

    #if 0 // L  Migration
    {"doEnableReaderMode", "(I)V",
            (void *)com_android_nfc_NfcManager_doEnableReaderMode},

    {"doDisableReaderMode", "()V",
            (void *)com_android_nfc_NfcManager_doDisableReaderMode},

    {"doSetNfc","(I)V",
        (void *)com_android_nfc_NfcManager_doSetNfc},            //dummy, replaced by doSetNfcMode

    {"doSetNfcReaderP2p","(I)V",
        (void *)com_android_nfc_NfcManager_doSetNfcReaderP2p},    //dummy, replaced by doSetNfcMode
    
    {"doSetNfcMode","(I)V",
      (void *)com_android_nfc_NfcManager_doSetNfcMode},
    #endif
    
   //********** MTK Customized - end **********//
   
};   
        
int register_com_android_nfc_NativeNfcManager(JNIEnv *e)
{
    nfc_jni_native_monitor_t *nfc_jni_native_monitor;

    nfc_jni_native_monitor = nfc_jni_init_monitor();
    if(nfc_jni_native_monitor == NULL)
    {
        ALOGE("NFC Manager cannot recover native monitor %x\n", errno);
        return -1;
    }

    return jniRegisterNativeMethods(e,
        "com/android/nfc/dhimpl/NativeNfcManager",
        gMethods, NELEM(gMethods));
}

#ifdef DTA_SUPPORT
static void dta_detection_handler(unsigned long type, unsigned long nError)
{
    ALOGD("[DTA] dta_detection_handler, type: %d, nError: %d\n", (int)type, (int)nError);
    detection_type = type;
    detection_nError = nError;

    ALOGD("[DTA] unlock sem for DTA client\n");
    sem_post(&dta_client_sem);
}

static void *nfc_jni_dta_client_thread(void *arg)
{
    ALOGD("[DTA] MTK NFC client started\n");

    dta_client_is_running = TRUE;
    while(dta_client_is_running)
    {
        // wait semaphore
        ALOGD("[DTA] wait for semaphore...\n");
        if (sem_wait(&dta_client_sem))
        {
            ALOGE("[DTA] Failed to wait for semaphore (errno=0x%08x)\n", errno);
            break;
        }
        ALOGD("[DTA] wait for semaphore ok\n");

        // process notification
        if (NULL != pDetectionHandler)
        {
            ALOGD("[DTA] detection_type: %d, detection_nError: %d\n", (int)detection_type, (int)detection_nError);
            (*pDetectionHandler)(detection_type, detection_nError);
        }
        else
        {
            ALOGE("[DTA] pDetectionHandler is NULL\n");
        }
    }   

    ALOGD("[DTA] MTK NFC client stopped\n");
   
    return NULL;
}

void dta_nfc_jni_set_dta_mode(bool flag)
{
    dta_mode = flag;
    // set dta file for framework usage
    if (flag == TRUE)
    {
        dta_file_write();
    }
    else
    {
        dta_file_clear();
    }
}


void dta_NormalFlow_Set_PatternNum(unsigned int Num)
{
    ALOGD ("%s: gDtaTestNum=%d", __FUNCTION__, Num);
#ifdef DTA_SUPPORT
    if(Num < 0xF)
    {
    //gDtaTestNum = Num;
    //ALOGD ("%s: gDtaTestNum=%d", __FUNCTION__, gDtaTestNum);
        {
            FILE *fp;
            int idx = 0;
            char pData[] = DTA_MODE_IDENTITY;
            
            ALOGD("%s: start...", __FUNCTION__);
            
            fp=fopen("/sdcard/mtknfcdtaPatternNum.txt","w+t");
            if (fp == NULL)
            {
                ALOGE("%s: can not open dta file", __FUNCTION__);
                return;// FALSE;
            }
            
            // write dta mode identify
            fprintf(fp, "%d", Num);
            fprintf(fp, "\n");
            
            fclose(fp);
        }    
    }
#endif
}


bool dta_nfc_jni_get_dta_mode(void)
{
    return dta_mode;
}

//set config path for operation test @mingyen
int dta_nfc_jni_set_config_path(unsigned char * path, unsigned int length)
{
    ALOGD ("%s: config path = %s, len, %d", __FUNCTION__, path, length);
#ifdef DTA_SUPPORT
    memcpy(dta_nat.rDiscoverConfig.au1ConfigPath, path, length );

    ALOGD ("%s: config path = %s", __FUNCTION__, dta_nat.rDiscoverConfig.au1ConfigPath);
    //for dta operation test
    {
        FILE *fp; 
        ALOGD("%s: start... write path in /sdcard/mtknfcdtaConfigPath.txt", __FUNCTION__);        
        fp = fopen("/sdcard/mtknfcdtaConfigPath.txt","w+t");
        if (fp == NULL)
        {
            ALOGE("%s: can not open dta file", __FUNCTION__);
            return 0; //
        }        
        // write dta mode identify
        fprintf(fp, "%s", path);
        fprintf(fp, "\n");        
        fclose(fp);
    }    
#endif
    return length;
}

void dta_fill_native_struct(struct nfc_jni_native_data *nat)
{   
   TRACE("******  DTA Fill Native Structure ******"); 
   
   memset(nat,0,sizeof(nfc_jni_native_data));
   nat->seId = SMX_SECURE_ELEMENT_ID;
   
   nat->lto = 150;  // LLCP_LTO
   nat->miu = 128; // LLCP_MIU
   // WKS indicates well-known services; 1 << sap for each supported SAP.
   // We support Link mgmt (SAP 0), SDP (SAP 1) and SNEP (SAP 4)
   nat->wks = 0x13;  // LLCP_WKS
   nat->opt = 0;  // LLCP_OPT
   
   memset(&nat->rDiscoverConfig,0,sizeof(s_mtk_nfc_service_set_discover_req_t));
   nat->fgHostRequestDeactivate = FALSE;
   
   // Please modify here to config polling loop
   #if 1 // Reader mode 
   nat->rDiscoverConfig.u2TotalDuration = 0x44;
   nat->rDiscoverConfig.fgEnListen = TRUE;
   //nat->rDiscoverConfig.u1OpMode = (NFC_OPMODE_READER); // reader mode
   nat->rDiscoverConfig.u1OpMode = (NFC_OPMODE_P2P | NFC_OPMODE_READER); // reader + p2p mode
   nat->rDiscoverConfig.reader_setting.fgEnTypeA = TRUE;
   nat->rDiscoverConfig.reader_setting.u1BitRateA = 1; // 106
   nat->rDiscoverConfig.reader_setting.fgEnTypeB = TRUE;
   nat->rDiscoverConfig.reader_setting.u1BitRateB = 1; // 106
   nat->rDiscoverConfig.reader_setting.fgEnTypeF = TRUE;
   nat->rDiscoverConfig.reader_setting.u1BitRateF = 2; // 212
   nat->fgPollingEnabled = FALSE;
   
   #if 1   
   nat->rDiscoverConfig.reader_setting.fgEnTypeV = TRUE;
   nat->rDiscoverConfig.reader_setting.u1BitRateV = 1; // 6.62
   nat->rDiscoverConfig.reader_setting.fgEnDualSubCarrier = FALSE;
   #endif
   nat->u1DevActivated = 0;
   nat->CurrentRfInterface = NFC_DEV_INTERFACE_NFC_UNKNOWN;
   #endif
       
   #if 1
   // P2P mode
   nat->rDiscoverConfig.p2p_setting.fgDisableCardMode = TRUE;
   nat->rDiscoverConfig.p2p_setting.fgEnPassiveMode = TRUE;
   nat->rDiscoverConfig.p2p_setting.fgEnActiveMode = FALSE;
   nat->rDiscoverConfig.p2p_setting.fgEnInitiatorMode= TRUE;
   nat->rDiscoverConfig.p2p_setting.fgEnTargetMode = TRUE;
   nat->rDiscoverConfig.p2p_setting.fgEnTypeA  = FALSE;
   nat->rDiscoverConfig.p2p_setting.fgEnTypeF  = TRUE;
   nat->rDiscoverConfig.p2p_setting.u1BitRateA = 0x00;
   nat->rDiscoverConfig.p2p_setting.u1BitRateF = 0x02; // 212
   #endif
   
   nat->rDiscoverConfig.i4DtaPatternNum = 0xFF;
   nat->rDiscoverConfig.u1DtaTestMode = 0xFF;
   
   TRACE("******  DTA Fill Native Structure OK ******"); 
}

//int dta_nfc_jni_initialize(void)
int dta_nfc_jni_initialize(void *handler)
{
   int result = FALSE;

   nfc_jni_native_monitor_t *nfc_jni_native_monitor;

   nfc_jni_native_monitor = nfc_jni_init_monitor();
   if(nfc_jni_native_monitor == NULL)
   {
      ALOGE("NFC Manager cannot recover native monitor %x\n", errno);
      return -1;
   }
   
   CONCURRENCY_LOCK();

   ALOGD("[DTA] dta_nfc_jni_initialize()...");

   /* Clear DTA native structure */
   memset(&dta_nat, 0, sizeof(struct nfc_jni_native_data));
      
   /* Fill DTA native structure */   
   dta_fill_native_struct(&dta_nat);
           
   /* Perform the initialization */
   result = nfc_jni_initialize(&dta_nat);      
   if (TRUE == result)
   {      
      /* Register Detection Handler & Create DTA Client Thread */
      pDetectionHandler = (pDetectionHandler_t)handler;
      if (pthread_create(&(dta_client_thread), NULL, 
          nfc_jni_dta_client_thread, NULL) != 0)
      {
         ALOGE("[DTA] pthread_create failed\n");
      }
      else
      {
         /* Create semaphore for DTA client */
         ALOGD("[DTA] Create semaphore for DTA client\n");
         if(sem_init(&dta_client_sem, 0, 0) == -1)
         {
            ALOGE("[DTA] Semaphore creation failed (errno=0x%08x)\n", errno);
         }

         // set dta mode
         dta_mode = TRUE;
      }      
   }

   CONCURRENCY_UNLOCK();

   ALOGD("[DTA] dta_nfc_jni_initialize() result,%d", result);
   
   return result;
}

int dta_nfc_jni_deinitialize(void)
{
   int result = FALSE;
    
   CONCURRENCY_LOCK();

   ALOGD("[DTA] dta_nfc_jni_deinitialize()...");

   /* Un-Register Detection Handler */
   pDetectionHandler = (pDetectionHandler_t)NULL;
   
   /* Perform the de-initialization */   
   result = nfc_jni_deinitialize(&dta_nat);      
   if (TRUE == result)
   {
      dta_mode = FALSE;
   }

   /* Kill DTA Client Thread */
   dta_client_is_running = FALSE;
   sem_post(&dta_client_sem);
   usleep(100000); // wait 100ms for DTA client exit

   /* Destroy semaphore from DTA client */
   if (sem_destroy(&dta_client_sem))
   {
      ALOGE("[DTA] Failed to destroy semaphore (errno=0x%08x)", errno);
   }

   /* Clear DTA native structure */
   memset(&dta_nat, 0, sizeof(struct nfc_jni_native_data)); 
   
   CONCURRENCY_UNLOCK();

   ALOGD("[DTA] dta_nfc_jni_deinitialize() result: %d", result);
   
   return result;
}

bool JniDtaSetPNFC(int pattern_num)
{
    int result = FALSE;
    struct nfc_jni_callback_data cb_data;
    
    s_mtk_nfc_em_pnfc_req rPnfcData;
    
    ALOGD("JniDtaSetPNFC()");

    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        return result;
    }

      g_working_cb_data = &cb_data;

    /* send req msg to nfc daemon */
    memset(&rPnfcData, 0, sizeof(rPnfcData));
    rPnfcData.action = NFC_EM_ACT_START;
    rPnfcData.datalen = 9;
    rPnfcData.data[0] = '$';
    rPnfcData.data[1] = 'P';
    rPnfcData.data[2] = 'N';
    rPnfcData.data[3] = 'F';
    rPnfcData.data[4] = 'C';
    rPnfcData.data[5] = '8';  
    rPnfcData.data[6] = '1';
    if (pattern_num == 1)
    {
       rPnfcData.data[7] = '0';
    }
    else
    {
       rPnfcData.data[7] = '1';
    }
    rPnfcData.data[8] = '*';

    result = android_nfc_jni_send_msg(MTK_NFC_EM_PNFC_CMD_REQ, sizeof(rPnfcData), &rPnfcData);

    if (result == FALSE)
    {
        ALOGE("send MTK_NFC_EM_PNFC_CMD_REQ fail\n");
        goto clean_and_return;
    }

    /* Wait for callback response */
    if (sem_wait(&cb_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        result = FALSE;
        goto clean_and_return;
    }
    
clean_and_return:
    
    /* clear working callback data pointer */
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);
    
    return result;
}

bool JniDtaEnableSecureElement(unsigned char seid)
{
    int result = FALSE;
    struct nfc_jni_callback_data cb_data;
    s_mtk_nfc_jni_se_set_mode_req_t rSeSetMode;
    
    ALOGD("JniDtaEnableSecureElement()");
    
    //CONCURRENCY_LOCK();

    /* Create the local semaphore */
    if (!nfc_cb_data_init(&cb_data, NULL)) {
        return result;
    }

    g_working_cb_data = &cb_data;

    TRACE("******  DTA Enable Secure Element ******");

    /* send req msg to nfc daemon */
    memset(&rSeSetMode,0,sizeof(rSeSetMode));
    rSeSetMode.seid = seid;
    rSeSetMode.enable = TRUE;
    rSeSetMode.pContext = (void *)&cb_data;

    result = android_nfc_jni_send_msg(MTK_NFC_JNI_SE_MODE_SET_REQ, sizeof(rSeSetMode), &rSeSetMode);

    if (result == FALSE)
    {
        ALOGE("send MTK_NFC_JNI_SE_MODE_SET_REQ fail\n");
        goto clean_and_return;
    }

    /* Wait for callback response */
    if (sem_wait(&cb_data.sem)) {
        ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
        result = FALSE;
        goto clean_and_return;
    }
    
clean_and_return:
    
    /* clear working callback data pointer */
    g_working_cb_data = NULL;

    nfc_cb_data_deinit(&cb_data);

    //CONCURRENCY_UNLOCK();
    
    return result;
}

bool JniDtaEnableDiscovery(unsigned char test_type, int pattern_num){

    bool result = false;
    unsigned char SeId = 1;
    
    CONCURRENCY_LOCK();

    ALOGD("JniDtaEnableDiscovery()");
    
    if (4 == test_type)
    {
        ALOGD("PNFC810/811,%d",pattern_num);
        if (!JniDtaSetPNFC(pattern_num))
        {
            CONCURRENCY_UNLOCK();
            return result;                  
        }  
        
        ALOGD("enable se1");
        if (!JniDtaEnableSecureElement(SeId))
        {
            CONCURRENCY_UNLOCK();
            return result;                  
        }    
    }
    
    dta_nat.rDiscoverConfig.u1DtaTestMode = (unsigned char)test_type;
    dta_nat.rDiscoverConfig.i4DtaPatternNum = pattern_num;
    if (TRUE == nfc_jni_enable_discovery(&dta_nat))
    {
        result = true;
    }
    ALOGD("JniDtaEnableDiscovery(),done, result: %d", result);

    CONCURRENCY_UNLOCK();
    return result;
}

bool JniDtaDisableDiscovery(void){

    bool result = false;
    
    CONCURRENCY_LOCK();

    ALOGD("JniDtaDisableDiscovery()");

    if (dta_rf_cmd_rsp)
    {
        // this action is taken because the last action in DTA doesn't free the buffer
        ALOGE("dta rf cmd response buffer already used, free the buffer");
        free(dta_rf_cmd_rsp); // a buffer malloc in 6605 JNI client callback MTK_NFC_JNI_TAG_TRANSCEIVE_RSP
        dta_rf_cmd_rsp = NULL;
        dta_rf_cmd_rsp_len = 0;
    }

    if (TRUE == nfc_jni_deactivate(&dta_nat, 0)) // back to idle
    {
        result = true;
    }
        
    CONCURRENCY_UNLOCK();

    ALOGD("JniDtaDisableDiscovery(), done, result: %d",result);
    
    return result;
}

int JniDtaIsoDslCmd(void){

    struct nfc_jni_callback_data cb_data;
    struct nfc_jni_native_data *nat = NULL;
    int result = FALSE;
    
    CONCURRENCY_LOCK();

    ALOGD("JniDtaIsoDslCmd()");

    result = nfc_jni_deactivate(&dta_nat, 0); // 0: idle, 1/2: sleep, 3:discovery
         
    CONCURRENCY_UNLOCK();

    ALOGD("JniDtaIsoDslCmd result is %d", result);
    
    return result;
}

int JniDtaNfcDslCmd(void){

    struct nfc_jni_callback_data cb_data;
    struct nfc_jni_native_data *nat = NULL;
    int result = FALSE;
    
    CONCURRENCY_LOCK();

    ALOGD("JniDtaNfcDslCmd()");

    result = nfc_jni_deactivate(&dta_nat, 2); // 0: idle, 1: sleep,2: sleep_AF, 3:discovery
         
    CONCURRENCY_UNLOCK();

    ALOGD("JniDtaNfcDslCmd result is %d", result);
    
    return result;
}

int JniDtaNfcRlsCmd(void){

    struct nfc_jni_callback_data cb_data;
    struct nfc_jni_native_data *nat = NULL;
    int result = FALSE;
    
    CONCURRENCY_LOCK();

    ALOGD("JniDtaNfcRlsCmd()");

    result = nfc_jni_deactivate(&dta_nat, 0); // 0: idle, 1: sleep,2: sleep_AF, 3:discovery         
    
    CONCURRENCY_UNLOCK();

    ALOGD("JniDtaNfcRlsCmd result is %d", result);    
    
    return result;
}

int JniDtaRfCmd(const unsigned char *cmd, unsigned char cmd_len, unsigned char** rsp_cmd, unsigned long *rsp_cmd_len){

    unsigned int len=0;
    int result = -1;
    struct nfc_jni_callback_data cb_data;
    int data_raw=0;
    int data_result=0;
    int data_length=0;
    UINT8 *tempbuf=NULL;

    ALOGD("JniDtaRfCmd()");
    if (nfc_cb_data_init(&cb_data, NULL))
    {
        // assign working callback data pointer


        g_working_cb_data_tag = &cb_data;
        len = cmd_len + sizeof(data_raw)+sizeof(data_result)+sizeof(data_length);

        ALOGD("JniDtaRfCmd,len=%d,cmdlen =%d",len,cmd_len);

        data_raw = 0;
        data_result = 0x00;
        data_length = cmd_len;
        /*
        *  avoid buffer overflow issue by klocwork tool, when cmd unkonw length
        *  leo,kuo 2015.01.19
        */

        tempbuf=(UINT8*)malloc(len);
        memcpy(tempbuf,&data_raw,sizeof(UINT32));
        memcpy(tempbuf+sizeof(UINT32),&data_result,sizeof(UINT32));
        memcpy(tempbuf+sizeof(UINT32)*2,&data_length,sizeof(UINT32));
        memcpy(tempbuf+sizeof(UINT32)*3,cmd,cmd_len);
        {
              int i;
              unsigned char *p = tempbuf+sizeof(UINT32)*3;
              for( i=0 ; i < data_length; i++)
              {
                  ALOGD("JniDtaRfCmd,%d,[%02x][%c]\n",i,*p,*p);
                  p++;
              }
        }
        /////////////////////////////////////////////////////////////
        if (dta_rf_cmd_rsp)
        {
            ALOGE("dta rf cmd response buffer already used, free the buffer");
            free(dta_rf_cmd_rsp); // a buffer malloc in 6605 JNI client callback MTK_NFC_JNI_TAG_TRANSCEIVE_RSP
            dta_rf_cmd_rsp = NULL;
        }
        dta_rf_cmd_rsp_len = 0;
        result = android_nfc_jni_send_msg(MTK_NFC_JNI_TAG_TRANSCEIVE_REQ, len, tempbuf);
      
        // Wait for callback response
        if(sem_wait(&cb_data.sem))
        {
            ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
            nfc_cb_data_deinit(&cb_data);
            return FALSE;
        }
      
        if (result == TRUE)
        {
            /* doWrite Status (0: success, 1: fail) */
            if(cb_data.status != 0)
            {
                ALOGE("Failed to doTransceive the stack,%d\n",cb_data.status);
                result = false;
            }      
        }
        else
        {
            ALOGE("JniDtaRfCmd fail\n");
        }

        if(tempbuf) free(tempbuf);
        nfc_cb_data_deinit(&cb_data);
        g_working_cb_data_tag = NULL;
        
        if( result == TRUE)
        {
            
            ALOGD("p_dta_rf_cmd_cb,len(%d)", (int)dta_rf_cmd_rsp_len);
            {
                int i;
                unsigned char *p = dta_rf_cmd_rsp;
                for(i=0; i < (int)dta_rf_cmd_rsp_len;i++)
                {
                  ALOGD("p_dta_rf_cmd_cb,%d,[%02x][%c]\n",i,*p,*p);
                  p++;
                }
            }
        }
        *rsp_cmd = dta_rf_cmd_rsp;
        *rsp_cmd_len = dta_rf_cmd_rsp_len;
    // the response buffer will be freed next time this API is called.
    }
    else
    {
        ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
    }

    if (result == TRUE)
    {
        return 0; // success
    }
    else
    {
        return -1;
    }
    
}

int JniDtaDepExchange(const unsigned char *cmd, unsigned char cmd_len, unsigned char* rsp_cmd, unsigned long *rsp_cmd_len)
{
    unsigned int len=0;
    int result = -1;
    struct nfc_jni_callback_data cb_data;
    MTK_NFC_P2P_TRANSCEIVE_DATA* p_data=NULL;   
 
    ALOGD("JniDtaDepExchange()");

    CONCURRENCY_LOCK();
    if (nfc_cb_data_init(&cb_data, NULL))
    {
        // assign working callback data pointer
        g_working_cb_data = &cb_data;
        len = cmd_len + sizeof(MTK_NFC_P2P_TRANSCEIVE_DATA);

        ALOGD("JniDtaDepExchange,len,%d,%d",len, cmd_len);
        p_data = (MTK_NFC_P2P_TRANSCEIVE_DATA*)malloc(len);
        memset(p_data, 0x00, len);
        p_data->data_buf.length = cmd_len;
        if (cmd_len != 0)
        {
            memcpy(p_data->data_buf.buffer, cmd, cmd_len);
        }

        #if 0 // Disable Debug-Messages 
        {
            int i = 0, DebugBufLen =0;
            char* DebugBuf = NULL;

            DebugBuf = (char*)malloc(cmd_len*6);
            memset(DebugBuf, 0x00, (cmd_len*6));
            
            for(i=0; (i<cmd_len) && ((DebugBuf+DebugBufLen) != NULL) ;i++)
            {
                sprintf(DebugBuf+DebugBufLen, "0x%x ", p_data->data_buf.buffer[i]);
                DebugBufLen = strlen(DebugBuf);
            }
            ALOGD("%s",DebugBuf);
            if (DebugBuf != NULL)
            {
               free(DebugBuf);
               DebugBuf = NULL;
            }
        }
        #endif // Disable Debug-Messages         
        
        result = android_nfc_jni_send_msg(MTK_NFC_JNI_P2P_TRANSCEIVE_REQ, len, p_data);
      
        // Wait for callback response
        if(sem_wait(&cb_data.sem))
        {
            ALOGE("Failed to wait for semaphore (errno=0x%08x)", errno);
            nfc_cb_data_deinit(&cb_data);
            result = FALSE;
            goto clean_and_return;
        }
              
        if (result == TRUE)
        {
            /* doWrite Status (0: success, 1: fail) */
            if(cb_data.status != 0)
            {
                ALOGE("Failed to DtaDepExchange,%d\n",cb_data.status);
                result = false;
                goto clean_and_return;                
            } 
            else
            {
                (*rsp_cmd_len) = dta_dep_rsp_len;                
                if ((dta_dep_rsp_len != 0) && (rsp_cmd != NULL))
                {
                   memcpy(rsp_cmd, dta_dep_rsp, dta_dep_rsp_len);
                }
                ALOGD("Received DtaDepExchange,len,%d\n", dta_dep_rsp_len);

                #if 0 // Disable Debug-Messages 
                {
                    unsigned int i = 0, DebugBufLen =0;
                    char* DebugBuf = NULL;
        
                    DebugBuf = (char*)malloc(dta_dep_rsp_len*6);
                    memset(DebugBuf, 0x00, (dta_dep_rsp_len*6));
                    
                    for(i=0; (i<dta_dep_rsp_len) && ((DebugBuf+DebugBufLen) != NULL) ;i++)
                    {
                        sprintf(DebugBuf+DebugBufLen, "0x%x ", dta_dep_rsp[i]);
                        DebugBufLen = strlen(DebugBuf);
                    }
                    ALOGD("%s",DebugBuf);
                    if (DebugBuf != NULL)
                    {
                       free(DebugBuf);
                       DebugBuf = NULL;
                    }
                }   
                #endif // Disable Debug-Messages 
            }
        }
        else
        {
            ALOGE("DtaDepExchange REQ fail\n");
        }

clean_and_return:   
    
        g_working_cb_data = NULL;
        nfc_cb_data_deinit(&cb_data);
        if(p_data != NULL)
        {
            free(p_data);
            p_data = NULL;
        }
        if (dta_dep_rsp != NULL)
        {
            free(dta_dep_rsp);
            dta_dep_rsp = NULL;
        }
        dta_dep_rsp_len = 0;
    }
    else
    {
        ALOGE("Failed to create semaphore (errno=0x%08x)\n", errno);
    }

    CONCURRENCY_UNLOCK();

    return result;
}

static bool dta_file_check()
{
    FILE *fp;
    int idx = 0;
    unsigned int fileRead = 0;
    unsigned char data = 0;
    const char* txt = DTA_MODE_IDENTITY;

    ALOGD("%s: start...", __FUNCTION__);
    
    fp=fopen("/sdcard/mtknfcdta.txt","r+b");
    if (fp == NULL)
    {
        ALOGD("%s: Can't open dta file...", __FUNCTION__);
        return FALSE;
    }

    // check data mode
    for(idx=0; idx<9; idx++)
    {                  
        fscanf(fp,"%c",&data);
        if (data != (unsigned char)*(txt + idx))
        {
            ALOGD("%s: dta value incorrect", __FUNCTION__);
            fclose(fp);
            return FALSE;
        }
    }

    fclose(fp);
    ALOGD("%s: end...", __FUNCTION__);
    return TRUE;
}

static bool dta_file_write()
{
    FILE *fp;
    int idx = 0;
    char pData[] = DTA_MODE_IDENTITY;

    ALOGD("%s: start...", __FUNCTION__);

    fp=fopen("/sdcard/mtknfcdta.txt","w+t");
    if (fp == NULL)
    {
        ALOGE("%s: can not open dta file", __FUNCTION__);
        return FALSE;
    }

    // write dta mode identify
    for (idx=0; idx<9; idx++)
    {
        fprintf(fp, "%c", pData[idx]);
    }
    fprintf(fp, "\n");

    fclose(fp);
    ALOGD("%s: end...", __FUNCTION__);
    return TRUE;
}

static bool dta_file_clear()
{
    FILE *fp;
    int idx = 0;

    ALOGD("%s: start...", __FUNCTION__);

    fp=fopen("/sdcard/mtknfcdta.txt","w+t");

    if (fp == NULL)
    {
        ALOGE("%s: can not open dta file", __FUNCTION__);
        return FALSE;
    }

    // clear
    fprintf(fp, " ");

    fclose(fp);
    
    ALOGD("%s: end...", __FUNCTION__);

    return TRUE;
}
#endif

/*******************************************************************************
** Author : Thomas-CH
**
** Function:        ACFD_cfg_check
**
** Arguments:        null
**
** Description:        read ACFD_CFG_NAME
**
** Returns:         bool 0:Stop , 1:Star
**
*******************************************************************************/
static bool ACFD_cfg_check()
{
    FILE *fp;
    bool cfg=FALSE;
    int idx = 0;
    int buffersize=128;
    char buf[buffersize];
    char name[buffersize];
    char value[buffersize];
    char *ptr;
    ALOGD("%s: start...", __FUNCTION__);

    fp=fopen(QUICK_ENABLE_CFG,"r");

    if (fp == NULL)
    {
        ALOGE("%s: can not open %s file", __FUNCTION__,QUICK_ENABLE_CFG);

        return FALSE;
    }
    else
    {
        while (fgets(buf, buffersize,fp) != NULL)
        {
            // bypass comment line
            //ALOGE("%s",buf);
            if (buf[0] == '#') {
                continue;
            }

            // parsing - name
            ptr = strtok(buf, " :#\t\xd\xa");
            if (ptr == NULL) {
                continue;
            }
            strncpy(name, ptr, buffersize);

             // parsing - value
            ptr = strtok(NULL, " #\t\xd\xa");
            if (ptr == NULL) {
                continue;
            }
            strncpy(value, ptr, buffersize);
            //ALOGE("%s = %s", name,value);
            if((strncmp(name, E2_ACFD_CFG_NAME, buffersize) == 0) || (strncmp(name, E3_ACFD_CFG_NAME, buffersize) == 0))
            {
                if (strcmp(value,"ACFD_POLLING") == 0){
                    cfg=TRUE;
                }
            }
        }
    }
    fclose(fp);

    ALOGD("%s: end...%d", __FUNCTION__,cfg);

    return cfg;

}
#ifdef MTK_NFC_QUICK_SUPPORT
/*******************************************************************************
** Author : Leo.Kuo
**
** Function:        quickEnable_cfg_check
**
** Arguments:        null
**
** Description:        read QUICK_ENABLE_CFG_NAME
**
** Returns:         bool 0:Stop , 1:Star
**
*******************************************************************************/
static bool quickEnable_cfg_check()
{
    FILE *fp;
    bool cfg=FALSE;
    int idx = 0;
    int buffersize=128;
    char buf[buffersize];
    char name[buffersize];
    char value[buffersize];
    char *ptr;
    //ALOGD("%s: start...", __FUNCTION__);

    fp=fopen(QUICK_ENABLE_CFG,"r");

    if (fp == NULL)
    {
        ALOGE("%s: can not open %s file", __FUNCTION__,QUICK_ENABLE_CFG);

        return FALSE;
    }
    else
    {
        while (fgets(buf, buffersize,fp) != NULL)
        {
            // bypass comment line
            //ALOGE("%s",buf);
            if (buf[0] == '#') {
                continue;
            }

            // parsing - name
            ptr = strtok(buf, " :#\t\xd\xa");
            if (ptr == NULL) {
                continue;
            }
            strncpy(name, ptr, buffersize);

             // parsing - value
            ptr = strtok(NULL, " #\t\xd\xa");
            if (ptr == NULL) {
                continue;
            }
            strncpy(value, ptr, buffersize);
            //ALOGE("%s = %d", name,atoi(value));
            if(strncmp(name, QUICK_ENABLE_CFG_NAME, buffersize) == 0)
            {
                if (atoi(value) == 1){
                    cfg=TRUE;
                }
            }
        }
    }
    fclose(fp);

    //ALOGD("%s: end...", __FUNCTION__);

    return cfg;

}
#endif
} /* namespace android */
