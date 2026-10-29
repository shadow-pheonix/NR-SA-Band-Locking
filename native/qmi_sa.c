#define _GNU_SOURCE
/* Independent, narrow QMI NAS SA controller. Protocol facts: libqmi NAS/DMS
 * schemas; client subscription binding as documented by qcom-band-menu.
 * No QMI library, vendor executable, or reference application's code is linked. */
#include "qmi_sa.h"
#include "vendor/qcom-efs/util.h"
#include <errno.h>
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define FAMILY_QRTR 42
#define CTRL_PORT 0xfffffffeu
#define MAX_PACKET 4096
#define QTRACE_MAX 16
struct qrtr_address { unsigned short family; uint32_t node,port; };
struct endpoint { uint32_t service,instance,node,port; };
struct link { int fd; struct endpoint peer; };
struct view { const uint8_t *v[256]; uint16_t len[256]; };
struct preferences { uint8_t sa[64],nsa[64],guard[256];size_t guard_len;int mode; };
struct context {
    struct link nas;
    int slot,hardware_known,frequency_query,mode_command,plan_command;
    int protocol_error;
    struct { int present;unsigned count;uint32_t values[255]; } frequencies[2];
    uint16_t transaction;
    uint8_t supported[64];
    char error[512],hardware_error[256];
    struct endpoint endpoints[24];unsigned endpoint_count;
    struct { const char *stage;uint32_t service;int tx_len,rx_len,send_flags,send_return,send_errno;char tx[257],rx[1025]; } trace[QTRACE_MAX];
    unsigned trace_count;
};
#ifdef SA_TESTING
static char mock_prefix[256];
void qmi_sa_set_mock(const char *prefix) { snprintf(mock_prefix,sizeof mock_prefix,"%s",prefix); }
#endif
static unsigned rd16(const uint8_t *p) { return p[0]|((unsigned)p[1]<<8); }
static uint32_t rd32(const uint8_t *p) { return (uint32_t)p[0]|((uint32_t)p[1]<<8)|((uint32_t)p[2]<<16)|((uint32_t)p[3]<<24); }
static void wr16(uint8_t *p,unsigned v) { p[0]=(uint8_t)v;p[1]=(uint8_t)(v>>8); }
static void wr32(uint8_t *p,uint32_t v) { for(int i=0;i<4;i++)p[i]=(uint8_t)(v>>(i*8)); }
static int error(struct context *c,const char *s) { snprintf(c->error,sizeof c->error,"%s",s);return -1; }
static int system_error(struct context *c,const char *stage,int code) {
    snprintf(c->error,sizeof c->error,"%s: %s (errno %d).",stage,strerror(code),code);return -1;
}
static int64_t milliseconds(void) { struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (int64_t)t.tv_sec*1000+t.tv_nsec/1000000; }
static int wait_input(int fd,int64_t deadline) {
    for(;;) {int left=(int)(deadline-milliseconds());if(left<=0)return 0;
        struct pollfd p={.fd=fd,.events=POLLIN};int n=poll(&p,1,left);
        if(n<0&&errno==EINTR)continue;
        if(n<=0)return n;
        return (p.revents&POLLIN)?1:-1;
    }
}
static int open_link(struct context *c,struct link *l,uint32_t service) {
    l->fd=-1;
#ifdef SA_TESTING
    if(mock_prefix[0]) {
        struct sockaddr_un sa={.sun_family=AF_UNIX};
        if(snprintf(sa.sun_path,sizeof sa.sun_path,"%s-%u",mock_prefix,service)>=(int)sizeof sa.sun_path)return error(c,"Mock path too long.");
        int fd=socket(AF_UNIX,SOCK_SEQPACKET|SOCK_CLOEXEC,0);
        if(fd<0)return error(c,"Mock socket failed.");
        if(connect(fd,(struct sockaddr*)&sa,sizeof sa)<0) {close(fd);return error(c,"Mock connection failed.");}
        l->fd=fd;l->peer=(struct endpoint){service,1,0,service};return 0;
    }
#endif
    int fd=socket(FAMILY_QRTR,SOCK_DGRAM|SOCK_CLOEXEC,0);
    if(fd<0)return system_error(c,"QMI QRTR socket",errno);
    struct qrtr_address me={0};socklen_t sl=sizeof me;
    if(getsockname(fd,(struct sockaddr*)&me,&sl)<0) {int code=errno;close(fd);return system_error(c,"QMI QRTR local address",code);}
    uint8_t request[20]={0};wr32(request,10);wr32(request+4,service);
    struct qrtr_address control={FAMILY_QRTR,me.node,CTRL_PORT};
    ssize_t sent=sendto(fd,request,sizeof request,0,(struct sockaddr*)&control,sizeof control);
    if(sent!=(ssize_t)sizeof request) {int code=sent<0?errno:EIO;close(fd);return system_error(c,"QMI service lookup send",code);}
    int found=0;struct endpoint chosen={0};int64_t end=milliseconds()+2000;
    while(wait_input(fd,end)>0) {
        uint8_t response[64];ssize_t n=recv(fd,response,sizeof response,MSG_DONTWAIT);
        if(n<20||rd32(response)!=4)continue;
        struct endpoint e={rd32(response+4),rd32(response+8),rd32(response+12),rd32(response+16)};
        if(!e.service&&!e.instance&&!e.node&&!e.port)break;
        if(e.service!=service||e.node==me.node||!e.port)continue;
        if(c->endpoint_count<24)c->endpoints[c->endpoint_count++]=e;
        /* Standard QMI version 1 / instance 0. Never bind a random service instance. */
        if(e.instance==1) {if(found&&(e.node!=chosen.node||e.port!=chosen.port)) {close(fd);return error(c,"Ambiguous QMI service endpoints. No setting was changed.");}chosen=e;found=1;}
    }
    if(!found) {close(fd);snprintf(c->error,sizeof c->error,"No QMI service %u version 1 instance 0 found. Copy the report.",service);return -1;}
    struct qrtr_address peer={FAMILY_QRTR,chosen.node,chosen.port};
    if(connect(fd,(struct sockaddr*)&peer,sizeof peer)<0) {int code=errno;close(fd);return system_error(c,"QMI endpoint connect",code);}
    l->fd=fd;l->peer=chosen;return 0;
}
static int parse_tlvs(struct context *c,const uint8_t *r,int n,struct view *v) {
    memset(v,0,sizeof *v);
    if(n<7||n!=7+(int)rd16(r+5))return error(c,"QMI reply length mismatch.");
    for(int p=7;p<n;) {
        if(n-p<3)return error(c,"Truncated QMI TLV header.");
        unsigned id=r[p],len=rd16(r+p+1);p+=3;
        if(len>(unsigned)(n-p)||v->v[id])return error(c,"Truncated or duplicate QMI TLV.");
        v->v[id]=r+p;v->len[id]=(uint16_t)len;p+=(int)len;
    }
    if(!v->v[2]||v->len[2]!=4)return error(c,"QMI reply has no exact result TLV.");
    if(rd16(v->v[2])||rd16(v->v[2]+2)) {c->protocol_error=(int)rd16(v->v[2]+2);snprintf(c->error,sizeof c->error,"QMI rejected request: result 0x%04x, error 0x%04x%s",rd16(v->v[2]),rd16(v->v[2]+2),c->protocol_error==25?" (DeviceUnsupported)":"");return -1;}
    return 0;
}
static int transfer(struct context *c,struct link *l,const char *stage,unsigned message,const uint8_t *payload,size_t size,uint8_t out[MAX_PACKET],struct view *v) {
    uint8_t req[128]={0};if(size>121)return error(c,"QMI request too large.");
    uint16_t txn=++c->transaction;if(!txn)txn=++c->transaction;
    wr16(req+1,txn);wr16(req+3,message);wr16(req+5,(unsigned)size);if(size)memcpy(req+7,payload,size);
    unsigned t=c->trace_count<QTRACE_MAX?c->trace_count++:QTRACE_MAX;
    if(t<QTRACE_MAX) {c->trace[t].stage=stage;c->trace[t].service=l->peer.service;c->trace[t].tx_len=(int)(7+size);c->trace[t].rx_len=-1;hex_encode(req,7+size,c->trace[t].tx);}
    /* QRTR rejects all send flags except MSG_DONTWAIT. In particular,
     * MSG_NOSIGNAL is not accepted by qrtr_sendmsg (EINVAL). */
    ssize_t sent=send(l->fd,req,7+size,0);int code=sent<0?errno:0;
    if(t<QTRACE_MAX) {c->trace[t].send_flags=0;c->trace[t].send_return=(int)sent;c->trace[t].send_errno=code;}
    if(sent!=(ssize_t)(7+size)) {
        if(sent<0)snprintf(c->error,sizeof c->error,"%s: QMI send failed: %s (errno %d); sent %zd of %zu bytes.",stage,strerror(code),code,sent,7+size);
        else snprintf(c->error,sizeof c->error,"%s: QMI short send: sent %zd of %zu bytes.",stage,sent,7+size);
        return -1;
    }
    int64_t deadline=milliseconds()+4000;
    while(wait_input(l->fd,deadline)>0) {
        ssize_t n=recv(l->fd,out,MAX_PACKET,MSG_DONTWAIT|MSG_TRUNC);
        if(n<7)continue;
        if(n>MAX_PACKET)return error(c,"Oversized QMI response.");
        if(out[0]!=2||rd16(out+1)!=txn||rd16(out+3)!=message)continue;
        if(t<QTRACE_MAX) {c->trace[t].rx_len=(int)n;hex_encode(out,n>512?512:(size_t)n,c->trace[t].rx);}
        return parse_tlvs(c,out,(int)n,v);
    }
    snprintf(c->error,sizeof c->error,"%s: QMI response timed out.",stage);return -1;
}
static int open_nas(struct context *c) {
    if(open_link(c,&c->nas,3)<0)return -1;
    uint8_t bind[4]={1,1,0,(uint8_t)c->slot},r[MAX_PACKET];struct view v;
    if(transfer(c,&c->nas,"bind_subscription",0x45,bind,sizeof bind,r,&v)<0) {close(c->nas.fd);c->nas.fd=-1;return -1;}
    return 0;
}
static int read_state(struct context *c,struct preferences *s) {
    uint8_t r[MAX_PACKET];struct view v;
    if(transfer(c,&c->nas,"get_preferences",0x34,NULL,0,r,&v)<0)return -1;
    if(!v.v[0x2c]||v.len[0x2c]!=64||!v.v[0x2d]||v.len[0x2d]!=64)return error(c,"QMI did not return separate exact 64-byte SA and NSA masks. No defaults are inferred.");
    memset(s,0,sizeof *s);s->mode=-1;memcpy(s->sa,v.v[0x2c],64);memcpy(s->nsa,v.v[0x2d],64);
    if(v.v[0x11]&&v.len[0x11]==2)s->mode=(int)rd16(v.v[0x11]);
    const unsigned ids[]={0x11,0x12,0x15,0x23,0x2b,0x2d};
    for(unsigned i=0;i<sizeof ids/sizeof ids[0];i++) {
        unsigned id=ids[i],len=v.len[id];
        /* 0x2b's four-byte NR mode report is separate from a combined band mask. */
        if(id==0x2b&&len!=4)continue;
        if(!v.v[id])continue;
        if(s->guard_len+3+len>sizeof s->guard)return error(c,"Unexpectedly large preference guard.");
        size_t p=s->guard_len;s->guard[p]=(uint8_t)id;wr16(s->guard+p+1,len);memcpy(s->guard+p+3,v.v[id],len);s->guard_len+=3+len;
    }
    return 0;
}
static int hardware(struct context *c) {
    struct link l={.fd=-1};uint8_t r[MAX_PACKET];struct view v;int rc=-1;
    if(open_link(c,&l,2)<0)goto done;
    if(transfer(c,&l,"get_nr_capabilities",0x45,NULL,0,r,&v)<0)goto done;
    if(!v.v[0x13]||v.len[0x13]<2) {error(c,"DMS omitted the NR capability list.");goto done;}
    unsigned count=rd16(v.v[0x13]);
    if(!count||count>512||v.len[0x13]!=2+2*count) {error(c,"DMS NR capability array is malformed.");goto done;}
    memset(c->supported,0,64);
    for(unsigned i=0;i<count;i++) {unsigned b=rd16(v.v[0x13]+2+2*i);if(!b||b>512) {error(c,"DMS NR band is outside the supported mask range.");goto done;}c->supported[(b-1)/8]|=(uint8_t)(1u<<((b-1)%8));}
    c->hardware_known=1;rc=0;
done:
    if(l.fd>=0)close(l.fd);
    if(rc<0) {snprintf(c->hardware_error,sizeof c->hardware_error,"%.250s",c->error);c->error[0]=0;}
    return rc;
}
static int read_frequencies(struct context *c) {
    uint8_t r[MAX_PACKET];struct view v;
    if(transfer(c,&c->nas,"get_nr_frequency_lists",0xd2,NULL,0,r,&v)<0)return -1;
    /* GET_ARFCN_LIST: optional serving 0x1c and neighbor 0x1d, each
     * u8 count followed by count little-endian u32 NR-ARFCNs.
     * An absent list is unknown, distinct from an explicitly empty list. */
    for(unsigned i=0;i<2;i++) {
        unsigned id=0x1c+i;if(!v.v[id])continue;
        if(v.len[id]<1||v.len[id]!=1+4u*v.v[id][0])return error(c,"Malformed NR frequency list; no bands are inferred.");
        for(unsigned j=0;j<v.v[id][0];j++) {
            uint32_t arfcn=rd32(v.v[id]+1+4*j);
            if(!arfcn||arfcn>3279165)return error(c,"NR frequency list contains an invalid ARFCN; no bands are inferred.");
        }
    }
    for(unsigned i=0;i<2;i++) {
        unsigned id=0x1c+i;if(!v.v[id])continue;
        c->frequencies[i].present=1;c->frequencies[i].count=v.v[id][0];
        for(unsigned j=0;j<c->frequencies[i].count;j++)c->frequencies[i].values[j]=rd32(v.v[id]+1+4*j);
    }
    return 0;
}
static int set_sa(struct context *c,const uint8_t target[64]) {
    /* Until next power cycle; only Change Duration and the SA write TLV. */
    uint8_t request[71]={0x17,1,0,0,0x2f,64,0},r[MAX_PACKET];struct view v;
    memcpy(request+7,target,64);
    return transfer(c,&c->nas,"set_sa_until_reboot",0x33,request,sizeof request,r,&v);
}
static int set_mode(struct context *c,unsigned mode) {
    uint8_t request[9]={0x17,1,0,0,0x11,2,0,0,0},r[MAX_PACKET];struct view v;
    wr16(request+7,mode);
    return transfer(c,&c->nas,"set_radio_mode_until_reboot",0x33,request,sizeof request,r,&v);
}
static int set_selection(struct context *c,const uint8_t sa[64],unsigned mode) {
    uint8_t request[76]={0x17,1,0,0,0x2f,64,0},r[MAX_PACKET];struct view v;
    memcpy(request+7,sa,64);request[71]=0x11;wr16(request+72,2);wr16(request+74,mode);
    return transfer(c,&c->nas,"set_sa_and_radio_mode_until_reboot",0x33,request,sizeof request,r,&v);
}
static int verify_selection(struct context *c,const struct preferences *before,const uint8_t target[64],unsigned mode) {
    for(int i=0;i<5;i++) {
        struct preferences now,guard=*before;
        if(read_state(c,&now)<0)return -1;
        if(now.mode<0)return error(c,"Radio mode missing from combined verification readback.");
        wr16(guard.guard+3,(unsigned)now.mode);
        if(now.guard_len!=guard.guard_len||memcmp(now.guard,guard.guard,guard.guard_len))return error(c,"NSA, LTE or other guarded preferences changed during combined verification.");
        if(!memcmp(now.sa,target,64)&&now.mode==(int)mode)return 0;
        if(i<4) {struct timespec t={0,150000000};while(nanosleep(&t,&t)<0&&errno==EINTR){}}
    }
    return error(c,"SA bands or radio mode did not match combined readback within the verification window.");
}
static int verify_mode(struct context *c,const struct preferences *before,unsigned target) {
    for(int i=0;i<5;i++) {
        struct preferences now,guard=*before;
        if(read_state(c,&now)<0)return -1;
        if(now.mode<0)return error(c,"Radio mode missing from verification readback.");
        /* read_state places the exact two-byte RAT field first when present. */
        wr16(guard.guard+3,(unsigned)now.mode);
        if(memcmp(now.sa,before->sa,64)||memcmp(now.nsa,before->nsa,64)||now.guard_len!=guard.guard_len||memcmp(now.guard,guard.guard,guard.guard_len))return error(c,"Band or other guarded preferences changed during radio-mode verification.");
        if(now.mode==(int)target)return 0;
        if(i<4) {struct timespec t={0,150000000};while(nanosleep(&t,&t)<0&&errno==EINTR){}}
    }
    return error(c,"Radio mode did not match readback within the verification window.");
}
static int mode_input(const char *text,int *out) {
    char *end;errno=0;long mode=strtol(text,&end,10);
    if(errno||!*text||*end||mode<=0||mode>127)return -1;
    *out=(int)mode;return 0;
}
static int verify(struct context *c,const struct preferences *before,const uint8_t target[64]) {
    struct preferences now;
    for(int i=0;i<5;i++) {
        if(read_state(c,&now)<0)return -1;
        if(now.guard_len!=before->guard_len||memcmp(now.guard,before->guard,now.guard_len))return error(c,"NSA, RAT, LTE or legacy band preferences changed. SA verification refused.");
        if(!memcmp(now.sa,target,64))return 0;
        if(i<4) {struct timespec t={0,150000000};while(nanosleep(&t,&t)<0&&errno==EINTR){}}
    }
    return error(c,"SA preference did not match readback within the verification window.");
}
static int mask_input(struct context *c,const char *s,uint8_t out[64]) {
    if(strlen(s)!=128)return error(c,"Expected a 64-byte hex mask.");
    for(int i=0;i<128;i++)if(!((s[i]>='0'&&s[i]<='9')||(s[i]>='a'&&s[i]<='f')||(s[i]>='A'&&s[i]<='F')))return error(c,"Invalid mask hex.");
    return hex_decode(s,out,64)==64?0:error(c,"Invalid mask.");
}
static void hex_field(sbuf *s,const char *key,const uint8_t *data,size_t n) {
    char hex[513];if(n>256)n=256;hex_encode(data,n,hex);sb_str(s,",\"");sb_str(s,key);sb_str(s,"\":");sb_json_str(s,hex);
}
static void result(struct context *c,int ok,const char *state,const struct preferences *p,int attempted,int rolled) {
    sbuf s;sb_init(&s);sb_fmt(&s,"{\"helper_version\":\"0.10\",\"backend\":\"qmi_nas\",\"read_profile\":\"%s\",\"ok\":%s,\"state\":",c->frequency_query?"qmi-nas-nr-frequencies-v1":"qmi-nas-sa-v1",ok?"true":"false");sb_json_str(&s,state);
    sb_fmt(&s,",\"slot\":%d,\"write_attempted\":%s,\"rollback_verified\":%s,\"write_ready\":%s,\"duration\":\"until_power_cycle\",\"transport\":",c->slot,attempted?"true":"false",rolled?"true":"false",ok&&c->hardware_known&&!c->frequency_query?"true":"false");
    char desc[96];snprintf(desc,sizeof desc,"QMI NAS qrtr %u:%u instance %u",c->nas.peer.node,c->nas.peer.port,c->nas.peer.instance);sb_json_str(&s,desc);
    sb_str(&s,",\"error\":");sb_json_str(&s,ok?"":c->error);sb_str(&s,",\"capability_error\":");sb_json_str(&s,c->hardware_error);
    if(p) {hex_field(&s,"sa_hex",p->sa,64);hex_field(&s,"nsa_hex",p->nsa,64);hex_field(&s,"guard_hex",p->guard,p->guard_len);sb_fmt(&s,",\"rat_mode_preference\":%d",p->mode);}
    if(c->hardware_known)hex_field(&s,"supported_nr_hex",c->supported,64);
    if(c->frequency_query) {
        sb_fmt(&s,",\"frequency_query_unsupported\":%s",c->protocol_error==25?"true":"false");
        const char *keys[]={"nr_serving_arfcns","nr_neighbor_arfcns"};
        for(unsigned i=0;i<2;i++) {
            sb_fmt(&s,",\"%s\":",keys[i]);
            if(!ok||!c->frequencies[i].present) {sb_str(&s,"null");continue;}
            sb_str(&s,"[");for(unsigned j=0;j<c->frequencies[i].count;j++) {if(j)sb_str(&s,",");sb_fmt(&s,"%u",c->frequencies[i].values[j]);}sb_str(&s,"]");
        }
        sb_str(&s,",\"complete_band_scan\":false,\"signal_measurements_available\":false");
    }
    sb_str(&s,",\"endpoints\":[");
    for(unsigned i=0;i<c->endpoint_count;i++) {struct endpoint *e=&c->endpoints[i];if(i)sb_str(&s,",");sb_fmt(&s,"{\"service\":%u,\"instance\":%u,\"node\":%u,\"port\":%u}",e->service,e->instance,e->node,e->port);}
    sb_str(&s,"],\"exchanges\":[");
    for(unsigned i=0;i<c->trace_count;i++) {
        if(i)sb_str(&s,",");
        sb_str(&s,"{\"stage\":");sb_json_str(&s,c->trace[i].stage);sb_fmt(&s,",\"service\":%u,\"request_length\":%d,\"response_length\":%d,\"request_hex\":",c->trace[i].service,c->trace[i].tx_len,c->trace[i].rx_len);
        /* Keep send diagnostics separate from QMI result/error TLVs. */
        sb_json_str(&s,c->trace[i].tx);sb_fmt(&s,",\"send_flags\":%d,\"send_return\":%d,\"send_errno\":%d,\"send_error\":",c->trace[i].send_flags,c->trace[i].send_return,c->trace[i].send_errno);sb_json_str(&s,c->trace[i].send_errno?strerror(c->trace[i].send_errno):"");
        sb_str(&s,",\"response_hex\":");sb_json_str(&s,c->trace[i].rx);sb_str(&s,"}");
    }
    sb_str(&s,"]}");puts(s.p);sb_free(&s);
}
int qmi_sa_run(int argc,char **argv) {
    struct context c={.nas={.fd=-1},.slot=-1};struct preferences before;
    uint8_t original[64],expected[64],nsa[64],target[64];int apply=0,restore=0,rc=1,expected_mode=-1,target_mode=-1;
    c.frequency_query=argc>1&&!strcmp(argv[1],"qmi-neighbors");
    c.mode_command=argc>1&&!strcmp(argv[1],"qmi-mode");
    c.plan_command=argc>1&&!strcmp(argv[1],"qmi-apply-plan");
    if(argc<3||(strcmp(argv[2],"0")&&strcmp(argv[2],"1"))) {error(&c,"QMI command requires SIM slot 0 or 1.");goto invalid;}
    c.slot=argv[2][0]-'0';apply=!strcmp(argv[1],"qmi-apply")||c.plan_command;restore=!strcmp(argv[1],"qmi-restore");
    if((!strcmp(argv[1],"qmi-probe")||c.frequency_query)&&argc==3) { /* read only */ }
    else if(c.mode_command&&argc==5) {
        if(mode_input(argv[3],&expected_mode)<0||mode_input(argv[4],&target_mode)<0) {error(&c,"Radio mode must be a nonzero seven-bit RAT mask.");goto invalid;}
    }
    else if((apply&&!c.plan_command&&argc==7)||(c.plan_command&&argc==9)||(restore&&argc==6)) {
        if(mask_input(&c,argv[3],original)<0||mask_input(&c,argv[4],expected)<0||mask_input(&c,argv[5],nsa)<0)goto invalid;
        if(restore)memcpy(target,original,64);
        else {if(mask_input(&c,argv[6],target)<0)goto invalid;unsigned any=0;for(int i=0;i<64;i++) {any|=target[i];if(target[i]&(uint8_t)~original[i]) {error(&c,"Selection exceeds the captured SA preference.");goto invalid;}}if(!any) {error(&c,"Select at least one SA band.");goto invalid;}}
        if(c.plan_command&&(mode_input(argv[7],&expected_mode)<0||mode_input(argv[8],&target_mode)<0)) {error(&c,"Combined selection requires valid expected and target RAT masks.");goto invalid;}
    } else {error(&c,"Invalid QMI command or argument count.");goto invalid;}
    if(open_nas(&c)<0) {result(&c,0,"transport_or_bind_failed",NULL,0,0);goto done;}
    if(c.frequency_query) {
        if(read_frequencies(&c)<0) {result(&c,0,"frequency_read_failed",NULL,0,0);goto done;}
        hardware(&c);result(&c,1,"read_only_frequencies",NULL,0,0);rc=0;goto done;
    }
    if(read_state(&c,&before)<0) {result(&c,0,"read_failed",NULL,0,0);goto done;}
    hardware(&c);
    if(c.mode_command) {
        if(before.mode<0) {error(&c,"Modem omitted the exact radio-mode preference; no mode SET sent.");result(&c,0,"mode_unavailable",&before,0,0);goto done;}
        if(before.mode!=expected_mode) {error(&c,"Radio mode changed since the snapshot; no mode SET sent.");result(&c,0,"stale_mode",&before,0,0);goto done;}
        if(before.mode==target_mode) {result(&c,1,"mode_already_matches",&before,0,0);rc=0;goto done;}
        if(set_mode(&c,(unsigned)target_mode)==0&&verify_mode(&c,&before,(unsigned)target_mode)==0) {
            before.mode=target_mode;wr16(before.guard+3,(unsigned)target_mode);result(&c,1,"mode_applied_verified",&before,1,0);rc=0;goto done;
        }
        char cause[512];snprintf(cause,sizeof cause,"%s",c.error);close(c.nas.fd);c.nas.fd=-1;
        int rolled=open_nas(&c)==0&&set_mode(&c,(unsigned)before.mode)==0&&verify_mode(&c,&before,(unsigned)before.mode)==0;
        snprintf(c.error,sizeof c.error,"%.350s; %s",cause,rolled?"previous radio mode restored and verified":"radio-mode outcome uncertain; keep mode backup and restore");
        result(&c,0,rolled?"mode_failed_rolled_back":"mode_recovery_required",rolled?&before:NULL,1,rolled);goto done;
    }
    if(!apply&&!restore) {result(&c,1,"read_only",&before,0,0);rc=0;goto done;}
    if(memcmp(before.sa,expected,64)||memcmp(before.nsa,nsa,64)) {error(&c,"Live SA or NSA differs from the expected snapshot. No SET was sent.");result(&c,0,"stale_snapshot",&before,0,0);goto done;}
    if(c.plan_command&&(before.mode<0||before.mode!=expected_mode)) {error(&c,"Radio mode is missing or differs from the expected snapshot. No SET was sent.");result(&c,0,"stale_mode",&before,0,0);goto done;}
    if(apply) {
        if(!c.hardware_known) {error(&c,"NR hardware capabilities unavailable. Apply is disabled.");result(&c,0,"capabilities_required",&before,0,0);goto done;}
        for(int i=0;i<64;i++)if(target[i]&(uint8_t)~c.supported[i]) {error(&c,"Selected NR band is not in the modem capability list.");result(&c,0,"unsupported_band",&before,0,0);goto done;}
    }
    if(!memcmp(target,before.sa,64)&&(!c.plan_command||before.mode==target_mode)) {result(&c,1,"already_matches",&before,0,0);rc=0;goto done;}
    int set_ok=c.plan_command?set_selection(&c,target,(unsigned)target_mode):set_sa(&c,target);
    int verified=set_ok==0&&(c.plan_command?verify_selection(&c,&before,target,(unsigned)target_mode):verify(&c,&before,target))==0;
    if(verified) {memcpy(before.sa,target,64);if(c.plan_command) {before.mode=target_mode;wr16(before.guard+3,(unsigned)target_mode);}result(&c,1,restore?"restored_verified":"applied_verified",&before,1,0);rc=0;goto done;}
    char cause[512];snprintf(cause,sizeof cause,"%s",c.error);close(c.nas.fd);c.nas.fd=-1;
    int rolled=0;
    if(open_nas(&c)==0) {
        int restored=c.plan_command?set_selection(&c,before.sa,(unsigned)before.mode):set_sa(&c,before.sa);
        rolled=restored==0&&(c.plan_command?verify_selection(&c,&before,before.sa,(unsigned)before.mode):verify(&c,&before,before.sa))==0;
    }
    snprintf(c.error,sizeof c.error,"%.350s; %s",cause,rolled?(c.plan_command?"previous SA and radio mode restored and verified":"previous SA restored and verified"):"outcome uncertain; keep backup and restore");
    result(&c,0,rolled?"failed_rolled_back":"recovery_required",rolled?&before:NULL,1,rolled);
    goto done;
invalid:
    result(&c,0,"invalid_request",NULL,0,0);
done:
    if(c.nas.fd>=0)close(c.nas.fd);
    return rc;
}
