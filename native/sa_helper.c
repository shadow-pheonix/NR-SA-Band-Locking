#define _GNU_SOURCE
#include "vendor/qcom-efs/diag.h"
#include "vendor/qcom-efs/util.h"
#include "qmi_sa.h"
#include <errno.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

/* Independent SA-only controller. No proprietary APK code or libraries. */
static char error_text[512];
static uint16_t sequence;
#define TRACE_MAX 16
static struct { const char *stage; size_t tx_len; int rx_len; char tx[385],rx[1025]; } trace[TRACE_MAX];
static unsigned trace_count;
static void le16(uint8_t *p, unsigned v) { p[0] = v; p[1] = v >> 8; }
static void le32(uint8_t *p, uint32_t v) { for (int i=0;i<4;i++) p[i]=(uint8_t)(v>>(8*i)); }
static uint32_t u32(const uint8_t *p) { return (uint32_t)p[0]|((uint32_t)p[1]<<8)|((uint32_t)p[2]<<16)|((uint32_t)p[3]<<24); }
static unsigned u16(const uint8_t *p) { return p[0]|((unsigned)p[1]<<8); }
static int fail(const char *message) { snprintf(error_text,sizeof error_text,"%s",message); return -1; }

/* Keep only our fixed-path preference exchanges, including rejected replies.
 * Packet diagnostics are bounded and contain no arbitrary modem log traffic. */
static int exchange(diag_t *d,const char *stage,const uint8_t *req,size_t len,uint8_t *resp,size_t cap) {
    unsigned index=trace_count<TRACE_MAX?trace_count++:TRACE_MAX;
    if(index<TRACE_MAX) {
        trace[index].stage=stage;trace[index].tx_len=len;trace[index].rx_len=-1;
        hex_encode(req,len>192?192:len,trace[index].tx);
    }
    if(diag_send(d,req,len)<0) return fail(diag_error(d));
    int n=diag_recv(d,resp,cap,4000,req[0],req[1],req[2]|(req[3]<<8));
    if(index<TRACE_MAX) {
        trace[index].rx_len=n;
        if(n>0)hex_encode(resp,(size_t)n>512?512:(size_t)n,trace[index].rx);
    }
    if(n<0) {snprintf(error_text,sizeof error_text,"%s: %.420s",stage,diag_error(d));return -1;}
    if(n<1)return fail("Empty DIAG response.");
    if(resp[0]!=req[0]) {
        const char *reason="target rejected request";
        switch(resp[0]) {
            case 0x13:reason="bad command";break;
            case 0x14:reason="bad parameter";break;
            case 0x15:reason="bad length";break;
            case 0x16:reason="bad device";break;
            case 0x17:reason="bad mode";break;
            case 0x18:reason="bad SPC mode";break;
            case 0x42:reason="security state refused request";break;
        }
        snprintf(error_text,sizeof error_text,"%s: %s (0x%02x); request %zu bytes, response %d bytes. Copy diagnostic report.",stage,reason,resp[0],len,n);
        return -1;
    }
    return n;
}

static int mask_arg(const char *s, uint8_t out[64]) {
    if (strlen(s)!=128) return fail("A mask must contain exactly 128 hex characters.");
    for (int i=0;i<128;i++) if (!((s[i]>='0'&&s[i]<='9')||(s[i]>='a'&&s[i]<='f')||(s[i]>='A'&&s[i]<='F'))) return fail("Invalid mask hex.");
    return hex_decode(s,out,64)==64 ? 0 : fail("Invalid mask.");
}
static int lock_session(void) {
    int fd=socket(AF_UNIX,SOCK_DGRAM|SOCK_CLOEXEC,0);
    if (fd<0) return fail("Cannot create transaction lock.");
    struct sockaddr_un sa={.sun_family=AF_UNIX};
    const char *name="sa_band_lab_modem_lock_v1";
    memcpy(sa.sun_path+1,name,strlen(name));
    if (bind(fd,(struct sockaddr*)&sa,(socklen_t)(offsetof(struct sockaddr_un,sun_path)+1+strlen(name)))<0) {
        close(fd); return fail("Another SA transaction is running. Wait and retry.");
    }
    return fd;
}
static void pref_path(char *out,size_t size,int slot,int nsa) {
    snprintf(out,size,"/nv/item_files/modem/mmode/%s%s",nsa?"nr_nsa_band_pref":"nr_band_pref",slot?"_Subscription01":"");
}
static int get_mask(diag_t *d,int slot,int nsa,uint8_t out[64]) {
    uint8_t req[128]={0x4b,0x13,0x27,0},resp[512];
    char path[96];pref_path(path,sizeof path,slot,nsa);
    size_t path_len=strlen(path)+1;
    uint16_t seq=sequence++;
    /* Modern GET 39: header, u32 requested bytes, u32 C-string length,
       u16 sequence, path + NUL. The path begins at 14, not at 4. */
    le32(req+4,64);le32(req+8,(uint32_t)path_len);le16(req+12,seq);
    memcpy(req+14,path,path_len);
    int n=exchange(d,nsa?"get_nsa":"get_sa",req,14+path_len,resp,sizeof resp);
    if (n<0) return -1;
    if (n<14) return fail("Truncated modern EFS GET response: missing sequence field.");
    if (u32(resp+8)!=0) { snprintf(error_text,sizeof error_text,"EFS GET refused %s: errno %u",path,u32(resp+8)); return -1; }
    if (u16(resp+12)!=seq) return fail("EFS GET sequence mismatch. No preference data was accepted.");
    if (u32(resp+4)!=64 || n!=78) return fail("Preference is not an exact 64-byte modern NR reply. No write is allowed.");
    memcpy(out,resp+14,64);return 0;
}
static int put_sa(diag_t *d,int slot,const uint8_t mask[64]) {
    uint8_t req[192]={0x4b,0x13,0x26,0},resp[128];
    char path[96];pref_path(path,sizeof path,slot,0);
    le32(req+4,64);le32(req+8,0x80040);le16(req+12,sequence++);
    memcpy(req+14,mask,64);memcpy(req+78,path,strlen(path)+1);
    int n=exchange(d,"put_sa",req,78+strlen(path)+1,resp,sizeof resp);
    if (n<0) return -1;
    if (n<10) return fail("Truncated EFS PUT acknowledgement.");
    if (u16(resp+6)!=0) { snprintf(error_text,sizeof error_text,"EFS PUT refused: errno %u",u16(resp+6));return -1; }
    /* Readback is mandatory: a send or zero-error acknowledgement alone is insufficient. */
    return 0;
}
static int read_pair(diag_t *d,int slot,uint8_t sa[64],uint8_t nsa[64]) {
    if (get_mask(d,slot,0,sa)<0) return -1;
    return get_mask(d,slot,1,nsa);
}
static int verify_pair(diag_t *d,int slot,const uint8_t sa[64],const uint8_t nsa[64]) {
    uint8_t a[64],b[64];
    if (read_pair(d,slot,a,b)<0) return -1;
    if (memcmp(a,sa,64)) return fail("SA readback differs from the requested bytes.");
    if (memcmp(b,nsa,64)) return fail("NSA changed during the transaction. It was not written by this helper.");
    return 0;
}
static void print_result(int ok,const char *state,int slot,diag_t *d,const uint8_t *sa,const uint8_t *nsa,int attempted,int rollback) {
    sbuf s;sb_init(&s);sb_fmt(&s,"{\"helper_version\":\"0.2\",\"read_profile\":\"efs-get39-v2\",\"ok\":%s,\"state\":",ok?"true":"false");sb_json_str(&s,state);
    sb_fmt(&s,",\"slot\":%d,\"write_attempted\":%s,\"rollback_verified\":%s,\"transport\":",slot,attempted?"true":"false",rollback?"true":"false");
    sb_json_str(&s,d?diag_transport_desc(d):"unopened");
    sb_str(&s,",\"error\":");sb_json_str(&s,ok?"":error_text);
    sb_str(&s,",\"exchanges\":[");
    for(unsigned i=0;i<trace_count;i++) {
        if(i)sb_str(&s,",");
        sb_str(&s,"{\"stage\":");sb_json_str(&s,trace[i].stage);
        sb_fmt(&s,",\"request_length\":%zu,\"response_length\":%d,\"request_hex\":",trace[i].tx_len,trace[i].rx_len);
        sb_json_str(&s,trace[i].tx);sb_str(&s,",\"response_hex\":");sb_json_str(&s,trace[i].rx);sb_str(&s,"}");
    }
    sb_str(&s,"]");
    if(sa&&nsa) { char a[129],b[129];hex_encode(sa,64,a);hex_encode(nsa,64,b);sb_str(&s,",\"sa_hex\":");sb_json_str(&s,a);sb_str(&s,",\"nsa_hex\":");sb_json_str(&s,b); }
    sb_str(&s,"}");puts(s.p);sb_free(&s);
}
int main(int argc,char **argv) {
    setvbuf(stdout,NULL,_IONBF,0);
#ifdef SA_TESTING
    if (argc>3&&!strcmp(argv[1],"--qmi-mock")) { qmi_sa_set_mock(argv[2]);argc-=2;argv+=2; }
    if (argc>3&&!strcmp(argv[1],"--mock")) { diag_set_mock(argv[2]);argc-=2;argv+=2; }
    if (argc>3&&!strcmp(argv[1],"--framing")) { diag_set_mock_framing(argv[2]);argc-=2;argv+=2; }
#else
    if (geteuid()!=0) { fail("Root is required.");print_result(0,"root_required",-1,NULL,NULL,NULL,0,0);return 1; }
#endif
    if(argc>1&&!strncmp(argv[1],"qmi-",4)) {
        int lockfd=lock_session();
        if(lockfd<0) {print_result(0,"transaction_busy",-1,NULL,NULL,NULL,0,0);return 1;}
        int rc=qmi_sa_run(argc,argv);close(lockfd);return rc;
    }
    int slot=-1, apply=0, restore=0;
    uint8_t original[64],expected[64],nsa_expected[64],target[64],current[64],nsa[64];
    if (argc<3 || (strcmp(argv[2],"0")&&strcmp(argv[2],"1"))) { fail("Usage: probe SLOT | apply SLOT ORIGINAL EXPECTED NSA TARGET | restore SLOT ORIGINAL EXPECTED NSA");goto invalid; }
    slot=argv[2][0]-'0';
    apply=!strcmp(argv[1],"apply");restore=!strcmp(argv[1],"restore");
    if ((!strcmp(argv[1],"probe")&&argc==3)) { /* no mutation */ }
    else if ((apply&&argc==7)||(restore&&argc==6)) {
        if(mask_arg(argv[3],original)<0||mask_arg(argv[4],expected)<0||mask_arg(argv[5],nsa_expected)<0) goto invalid;
        if(restore) memcpy(target,original,64);
        else {
            if(mask_arg(argv[6],target)<0) goto invalid;
            unsigned any=0;
            for(int i=0;i<64;i++) { any|=target[i];if(target[i]&(uint8_t)~original[i]) {fail("Selection exceeds the backed-up SA mask.");goto invalid;} }
            if(!any) {fail("Select at least one SA band. Use restore for the original bytes.");goto invalid;}
        }
    } else {fail("Invalid command or argument count.");goto invalid;}

    int lockfd=lock_session();if(lockfd<0)goto invalid;
    diag_t d;
    if(diag_open(&d)<0) {fail(diag_error(&d));print_result(0,"transport_failed",slot,&d,NULL,NULL,0,0);close(lockfd);return 2;}
    sequence=0;
    if(read_pair(&d,slot,current,nsa)<0) {print_result(0,"read_failed",slot,&d,NULL,NULL,0,0);diag_close(&d);close(lockfd);return 3;}
    if(!apply&&!restore) {print_result(1,"read_only",slot,&d,current,nsa,0,0);diag_close(&d);close(lockfd);return 0;}
    if(memcmp(current,expected,64)||memcmp(nsa,nsa_expected,64)) {
        fail("The modem preferences changed since the snapshot. No write was sent.");
        print_result(0,"stale_snapshot",slot,&d,current,nsa,0,0);diag_close(&d);close(lockfd);return 4;
    }
    if(!memcmp(target,current,64)) {print_result(1,"already_matches",slot,&d,current,nsa,0,0);diag_close(&d);close(lockfd);return 0;}
    if(put_sa(&d,slot,target)==0&&verify_pair(&d,slot,target,nsa_expected)==0) {
        print_result(1,restore?"restored_verified":"applied_verified",slot,&d,target,nsa,1,0);diag_close(&d);close(lockfd);return 0;
    }
    char cause[512];snprintf(cause,sizeof cause,"%s",error_text);
    /* An unanswered write leaves its outcome uncertain. Reopen so a late reply
       cannot be mistaken for the rollback acknowledgement on the old socket. */
    diag_close(&d);
    int rolled=0;
    if(diag_open(&d)==0 && put_sa(&d,slot,current)==0 && verify_pair(&d,slot,current,nsa_expected)==0) rolled=1;
    snprintf(error_text,sizeof error_text,"%.360s; %s",cause,rolled?"previous SA bytes restored and verified":"outcome uncertain; retain backup and retry restore");
    print_result(0,rolled?"failed_rolled_back":"recovery_required",slot,&d,rolled?current:NULL,rolled?nsa:NULL,1,rolled);
    diag_close(&d);close(lockfd);return 5;
invalid:
    print_result(0,"invalid_request",slot,NULL,NULL,NULL,0,0);return 1;
}
