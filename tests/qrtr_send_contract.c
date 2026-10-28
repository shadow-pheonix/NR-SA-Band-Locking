/* Test-only link wrapper: AF_UNIX accepts flags rejected by AF_QIPCRTR.
 * Match qrtr_sendmsg's flag check before forwarding to the mock socket.
 * Fault injection exercises diagnostic output without touching a modem. */
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>

ssize_t __real_send(int fd,const void *buf,size_t len,int flags);
ssize_t __wrap_send(int fd,const void *buf,size_t len,int flags) {
    if(flags&~MSG_DONTWAIT) {errno=EINVAL;return -1;}
    const char *fault=getenv("SA_QMI_SEND_FAULT");
    if(fault&&(!strcmp(fault,"invalid")||!strcmp(fault,"access"))) {
        errno=!strcmp(fault,"invalid")?EINVAL:EACCES;return -1;
    }
    if(fault&&!strcmp(fault,"short"))return len?(ssize_t)len-1:0;
    return __real_send(fd,buf,len,flags);
}
