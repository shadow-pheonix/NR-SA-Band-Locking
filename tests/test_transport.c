/* Boundary checks for the vendored framing and blocking-read timeout. */
#include "../native/vendor/qcom-efs/diag.c"
#include <assert.h>

int main(void)
{
    const uint8_t digits[]="123456789";
    assert(crc16_x25(digits,9)==0x906e);
    const uint8_t payload[]={0x4b,0x13,0x27,0x00,0x7d,0x7e};
    uint8_t encoded[64],decoded[64];size_t used=0;
    int n=hdlc_encode(payload,sizeof payload,encoded,sizeof encoded);
    assert(n>0);
    assert(hdlc_decode(encoded,n,&used,decoded,sizeof decoded)==sizeof payload);
    assert(!memcmp(decoded,payload,sizeof payload));
    assert(hdlc_decode(encoded,n-1,&used,decoded,sizeof decoded)<0);
    encoded[1]^=1;
    assert(hdlc_decode(encoded,n,&used,decoded,sizeof decoded)<0);

    const uint8_t wrapper[]={0x7e,1,4,0,0x4b,0x13,0x27,0,0x7e};
    const uint8_t *p=wrapper;
    assert(unwrap(&p,sizeof wrapper)==4&&p==wrapper+4);
    p=wrapper;
    assert(unwrap(&p,sizeof wrapper-1)==0);
    const uint8_t foreign_error[]={0x13,0x26};
    assert(!matches(foreign_error,2,0x4b,0x13,39));
    const uint8_t our_error[]={0x13,0x4b};
    assert(matches(our_error,2,0x4b,0x13,39));
    const uint8_t foreign_efs_error[]={0x15,0x4b,0x13,38,0};
    assert(!matches(foreign_efs_error,5,0x4b,0x13,39));
    const uint8_t own_efs_error[]={0x15,0x4b,0x13,39,0};
    assert(matches(own_efs_error,5,0x4b,0x13,39));

    int pipes[2];assert(pipe(pipes)==0);
    int64_t start=now_ms();errno=0;
    assert(bounded_read(pipes[0],decoded,sizeof decoded,100)==-1);
    assert(errno==EINTR);
    assert(now_ms()-start>=50&&now_ms()-start<2000);
    close(pipes[0]);close(pipes[1]);
    puts("Transport boundary checks passed, including interrupted blocking read.");
    return 0;
}
