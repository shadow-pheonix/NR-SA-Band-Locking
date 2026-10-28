"""Exercise the C QMI backend against independent NAS and DMS mock services."""
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import tempfile
import threading
import unittest

ROOT=Path(__file__).resolve().parents[1]
HELPER=Path(os.environ.get('SA_QMI_HELPER_TEST_BINARY',ROOT/'build/sa-helper-host'))

def mask(*bands):
    return sum(1<<(n-1) for n in bands).to_bytes(64,'little')

def tlv(kind,value):
    return struct.pack('<BH',kind,len(value))+value

def parse(data):
    result={}
    while data:
        assert len(data)>=3
        kind,size=struct.unpack_from('<BH',data)
        assert size<=len(data)-3 and kind not in result
        result[kind]=data[3:3+size];data=data[3+size:]
    return result

class Modem:
    def __init__(self,fault=None):
        self.tmp=tempfile.TemporaryDirectory(prefix='qsa-');self.prefix=self.tmp.name+'/m'
        self.fault=fault;self.stop=threading.Event();self.errors=[];self.sets=[];self.binds=[];self.gets=0;self.frequency_gets=0
        self.original=[mask(1,3,28,78),mask(28,78)];self.sa=self.original.copy();self.nsa=[mask(1,78),mask(28,78)]
        self.lte=b'\xff'*8;self.sockets=[];self.threads=[];self.lag=0
        self.modes=[92,92];self.mode_sets=[];self.mode_lag=0;self.combined_sets=[]
        for service in [2,3]:
            s=socket.socket(socket.AF_UNIX,socket.SOCK_SEQPACKET);s.bind(self.prefix+'-'+str(service));s.listen(3);s.settimeout(.1)
            self.sockets.append(s);t=threading.Thread(target=self.run,args=(s,service),daemon=True);t.start();self.threads.append(t)

    def run(self,server,service):
        try:
            while not self.stop.is_set():
                try:conn,_=server.accept()
                except socket.timeout:continue
                with conn:
                    slot=None
                    while not self.stop.is_set():
                        request=conn.recv(4096)
                        if not request:break
                        flag,txn,msg,size=struct.unpack_from('<BHHH',request)
                        assert flag==0 and len(request)==7+size
                        fields=parse(request[7:]);code=0
                        if service==3 and msg==0x45:
                            assert fields.keys()=={1} and fields[1] in [b'\x00',b'\x01']
                            slot=fields[1][0];self.binds.append(slot)
                            code=0x48 if self.fault=='bind_error' else 0;payload=b''
                        elif service==3 and msg==0x34:
                            assert slot is not None and not fields
                            self.gets+=1
                            if self.fault=='silent_get':continue
                            selected=self.sa[slot]
                            if self.fault=='lag' and self.lag:
                                selected=self.original[slot];self.lag-=1
                            mode=self.modes[slot]
                            if self.mode_lag:mode=92;self.mode_lag-=1
                            payload=tlv(0x12,b'\x44'*8)+tlv(0x15,self.lte)+tlv(0x23,b'\x11'*32)+tlv(0x2b,b'\x00'*4)
                            if self.fault!='missing_mode':payload=tlv(0x11,struct.pack('<H',mode) if self.fault!='short_mode' else b'\x40')+payload
                            if self.fault!='missing_sa':payload+=tlv(0x2c,selected[:-1] if self.fault=='short_sa' else selected)
                            if self.fault!='missing_nsa':payload+=tlv(0x2d,self.nsa[slot])
                            if self.fault=='duplicate_sa':payload+=tlv(0x2c,selected)
                            if self.fault=='truncated_tlv':payload+=b'\x80\xff\xffx'
                        elif service==3 and msg==0xd2:
                            assert slot is not None and not fields
                            self.frequency_gets+=1
                            payload=tlv(0x1c,b'\x01'+struct.pack('<I',634080))
                            if self.fault!='missing_neighbors':payload+=tlv(0x1d,b'\x00' if self.fault=='empty_neighbors' else b'\x02'+struct.pack('<II',155000,634080))
                            if self.fault=='missing_frequency_lists':payload=b''
                            if self.fault=='short_frequency_list':payload=tlv(0x1d,b'\x02'+struct.pack('<I',155000))
                            if self.fault=='bad_frequency_count':payload=tlv(0x1d,b'\x00'+struct.pack('<I',155000))
                            if self.fault=='zero_length_frequency_list':payload=tlv(0x1d,b'')
                            if self.fault=='invalid_frequency':payload=tlv(0x1d,b'\x01'+struct.pack('<I',0xffffffff))
                            if self.fault=='zero_frequency':payload=tlv(0x1d,b'\x01'+bytes(4))
                            if self.fault=='duplicate_frequency_tlv':payload+=tlv(0x1c,b'\x00')
                            if self.fault=='frequency_error':code=0x47
                            if self.fault=='frequency_device_unsupported':code=25;payload=b''
                        elif service==2 and msg==0x45:
                            assert not fields
                            supported=[1,3,78]
                            if self.fault=='no_hardware':code=0x47
                            payload=tlv(0x13,struct.pack('<H',len(supported))+struct.pack('<HHH',*supported))
                            if self.fault=='bad_hardware':payload=tlv(0x13,b'\x03\x00\x01\x00')
                        elif service==3 and msg==0x33:
                            assert slot is not None
                            if 0x11 in fields and 0x2f in fields:
                                assert fields.keys()=={0x17,0x11,0x2f},fields.keys()
                                assert fields[0x17]==b'\x00' and len(fields[0x11])==2 and len(fields[0x2f])==64
                                target=struct.unpack('<H',fields[0x11])[0]
                                self.combined_sets.append((slot,request,fields[0x2f],target));first=len(self.combined_sets)==1
                                if self.fault=='all_combined_errors' or self.fault=='combined_error' and first:code=1
                                if not code:
                                    self.sa[slot]=mask(3) if self.fault=='combined_bad_sa' and first else fields[0x2f]
                                    self.modes[slot]=92 if self.fault=='combined_bad_mode' and first else target
                                if self.fault=='combined_nsa_changed' and first:self.nsa[slot]=mask(3)
                                if self.fault=='combined_lag' and first:self.lag=2;self.mode_lag=2
                                payload=tlv(2,struct.pack('<HH',int(bool(code)),code))
                                conn.send(struct.pack('<BHHH',2,txn,msg,len(payload))+payload)
                                continue
                            if 0x11 in fields:
                                assert fields.keys()=={0x17,0x11},fields.keys()
                                assert fields[0x17]==b'\x00' and len(fields[0x11])==2
                                target=struct.unpack('<H',fields[0x11])[0]
                                self.mode_sets.append((slot,request,target));first=len(self.mode_sets)==1
                                if self.fault=='all_mode_errors' or self.fault=='mode_error' and first:code=1
                                if not code:self.modes[slot]=92 if self.fault=='mode_bad_readback' and first else target
                                if self.fault=='mode_sa_changed' and first:self.sa[slot]=mask(3)
                                if self.fault=='mode_lte_changed' and first:self.lte=b'\x33'*8
                                if self.fault=='mode_lag' and first:self.mode_lag=2
                                payload=tlv(2,struct.pack('<HH',int(bool(code)),code))
                                conn.send(struct.pack('<BHHH',2,txn,msg,len(payload))+payload)
                                continue
                            # A hard assertion against unrelated preference writes.
                            assert fields.keys()=={0x17,0x2f},fields.keys()
                            assert fields[0x17]==b'\x00' and len(fields[0x2f])==64
                            self.sets.append((slot,request,fields[0x2f]));first=len(self.sets)==1
                            if self.fault=='all_set_errors' or self.fault=='set_error' and first:code=0x01
                            if not code:
                                self.sa[slot]=mask(3) if self.fault=='bad_readback' and first else fields[0x2f]
                            if self.fault=='nsa_changed' and first:self.nsa[slot]=mask(3)
                            if self.fault=='lte_changed' and first:self.lte=b'\x33'*8
                            if self.fault=='lag' and first:self.lag=2
                            payload=b''
                        else:raise AssertionError((service,msg))
                        payload=tlv(2,struct.pack('<HH',int(bool(code)),code))+payload
                        if self.fault=='no_result' and service==3 and msg==0x34:payload=payload[7:]
                        response=struct.pack('<BHHH',2,txn,msg,len(payload))+payload
                        if self.fault=='wrong_transaction':conn.send(struct.pack('<BHHH',2,txn^0x8000,msg,len(payload))+payload)
                        if self.fault=='wrong_message':conn.send(struct.pack('<BHHH',2,txn,msg^0x0100,len(payload))+payload)
                        if self.fault=='wrong_header_length' and service==3 and msg==0x34:response=response[:5]+struct.pack('<H',len(payload)+1)+response[7:]
                        conn.send(response)
        except Exception as exc:
            if not self.stop.is_set():self.errors.append(exc)

    def call(self,*args,env=None):
        p=subprocess.run([str(HELPER),'--qmi-mock',self.prefix,*map(str,args)],capture_output=True,text=True,timeout=20,env={**os.environ,**(env or {})})
        return json.loads(p.stdout)

    def close(self):
        self.stop.set()
        for t in self.threads:t.join(1)
        for s in self.sockets:s.close()
        self.tmp.cleanup()
        if self.errors:raise self.errors[0]

class QmiTests(unittest.TestCase):
    def modem(self,fault=None):
        m=Modem(fault);self.addCleanup(m.close);return m

    def test_combined_apply_sets_band_and_nr_mode_in_one_request(self):
        for slot in [0,1]:
            m=self.modem();original=m.sa[slot].hex();nsa=m.nsa[slot].hex();target=mask(78).hex()
            r=m.call('qmi-apply-plan',slot,original,original,nsa,target,92,64)
            self.assertEqual(r['state'],'applied_verified',r);self.assertEqual(r['rat_mode_preference'],64);self.assertEqual(r['sa_hex'],target)
            self.assertEqual(len(m.combined_sets),1);self.assertEqual(m.mode_sets,[]);self.assertEqual(m.sets,[])
            fields=parse(m.combined_sets[0][1][7:]);self.assertEqual(fields,{0x17:b'\x00',0x2f:mask(78),0x11:b'\x40\x00'})
            self.assertEqual(m.nsa[slot].hex(),nsa);self.assertEqual(m.lte,b'\xff'*8);self.assertEqual(m.modes[1-slot],92)
            r=m.call('qmi-apply-plan',slot,original,target,nsa,mask(78).hex(),64,92)
            self.assertEqual(r['state'],'applied_verified',r);self.assertEqual(r['rat_mode_preference'],92);self.assertEqual(m.sa[slot],mask(78))
            self.assertEqual(len(m.combined_sets),2)

    def test_combined_band_change_preserves_non_nr_only_mode(self):
        m=self.modem();r=m.call('qmi-apply-plan',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(78).hex(),92,92)
        self.assertEqual(r['state'],'applied_verified',r);self.assertEqual(m.modes[0],92);self.assertEqual(len(m.combined_sets),1)

    def test_combined_mode_change_runs_even_when_bands_already_match(self):
        m=self.modem();m.sa[0]=mask(78);original=m.original[0].hex();nsa=m.nsa[0].hex()
        r=m.call('qmi-apply-plan',0,original,mask(78).hex(),nsa,mask(78).hex(),92,64)
        self.assertEqual(r['state'],'applied_verified',r);self.assertEqual(len(m.combined_sets),1)
        r=m.call('qmi-apply-plan',0,original,mask(78).hex(),nsa,mask(78).hex(),64,64)
        self.assertEqual(r['state'],'already_matches',r);self.assertFalse(r['write_attempted']);self.assertEqual(len(m.combined_sets),1)

    def test_combined_stale_modes_and_invalid_selection_never_set(self):
        for fault in [None,'missing_mode']:
            m=self.modem(fault);r=m.call('qmi-apply-plan',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(78).hex(),64,92)
            self.assertEqual(r['state'],'stale_mode',r);self.assertEqual(m.combined_sets,[])
        m=self.modem()
        for target,mode in [(mask().hex(),64),(mask(28).hex(),64),(mask(78).hex(),128)]:
            r=m.call('qmi-apply-plan',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),target,92,mode)
            self.assertFalse(r['ok'],r);self.assertFalse(r['write_attempted']);self.assertEqual(m.combined_sets,[])

    def test_combined_failure_rolls_back_both_band_and_radio_mode(self):
        for fault in ['combined_error','combined_bad_sa','combined_bad_mode']:
            m=self.modem(fault);r=m.call('qmi-apply-plan',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(78).hex(),92,64)
            self.assertEqual(r['state'],'failed_rolled_back',r);self.assertTrue(r['rollback_verified'])
            self.assertEqual(m.sa,m.original);self.assertEqual(m.modes,[92,92]);self.assertEqual(len(m.combined_sets),2)

    def test_combined_unrelated_change_or_failed_rollback_requires_recovery(self):
        for fault in ['combined_nsa_changed','all_combined_errors']:
            m=self.modem(fault);r=m.call('qmi-apply-plan',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(78).hex(),92,64)
            self.assertEqual(r['state'],'recovery_required',r);self.assertFalse(r['rollback_verified']);self.assertEqual(m.mode_sets,[]);self.assertEqual(m.sets,[])

    def test_combined_readback_can_converge(self):
        m=self.modem('combined_lag');r=m.call('qmi-apply-plan',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(78).hex(),92,64)
        self.assertEqual(r['state'],'applied_verified',r);self.assertEqual(len(m.combined_sets),1)

    def test_nr_only_then_restore_preserves_all_band_preferences(self):
        for slot in [0,1]:
            m=self.modem();sa=m.sa.copy();nsa=m.nsa.copy();lte=m.lte
            r=m.call('qmi-mode',slot,92,64)
            self.assertEqual(r['state'],'mode_applied_verified',r);self.assertEqual(r['rat_mode_preference'],64);self.assertTrue(r['write_attempted'])
            self.assertEqual(m.mode_sets[0][0],slot);self.assertEqual(m.mode_sets[0][1][7:].hex(),'170100001102004000')
            self.assertEqual(m.modes[1-slot],92)
            r=m.call('qmi-mode',slot,64,92)
            self.assertEqual(r['state'],'mode_applied_verified',r);self.assertEqual(m.modes,[92,92])
            self.assertEqual(m.sa,sa);self.assertEqual(m.nsa,nsa);self.assertEqual(m.lte,lte);self.assertEqual(m.sets,[])

    def test_mode_stale_missing_invalid_or_noop_does_not_set(self):
        m=self.modem();r=m.call('qmi-mode',0,64,92)
        self.assertEqual(r['state'],'stale_mode');self.assertEqual(m.mode_sets,[])
        r=m.call('qmi-mode',0,92,92);self.assertEqual(r['state'],'mode_already_matches');self.assertEqual(m.mode_sets,[])
        for value in ['0','128','-1','abc','64junk']:
            r=m.call('qmi-mode',0,92,value);self.assertEqual(r['state'],'invalid_request');self.assertFalse(r['write_attempted'])
        for fault in ['missing_mode','short_mode']:
            m=self.modem(fault);r=m.call('qmi-mode',0,92,64)
            self.assertEqual(r['state'],'mode_unavailable',r);self.assertEqual(m.mode_sets,[])

    def test_mode_failure_rolls_back_only_mode(self):
        for fault in ['mode_error','mode_bad_readback']:
            m=self.modem(fault);r=m.call('qmi-mode',0,92,64)
            self.assertEqual(r['state'],'mode_failed_rolled_back',r);self.assertTrue(r['rollback_verified']);self.assertEqual(m.modes,[92,92]);self.assertEqual(m.sets,[])
            self.assertEqual(m.sa,m.original)

    def test_mode_unrelated_changes_or_rollback_failure_require_recovery(self):
        for fault in ['mode_sa_changed','mode_lte_changed','all_mode_errors']:
            m=self.modem(fault);r=m.call('qmi-mode',0,92,64)
            self.assertEqual(r['state'],'mode_recovery_required',r);self.assertFalse(r['rollback_verified']);self.assertEqual(m.sets,[])

    def test_mode_readback_can_converge(self):
        m=self.modem('mode_lag');r=m.call('qmi-mode',0,92,64)
        self.assertEqual(r['state'],'mode_applied_verified',r);self.assertEqual(len(m.mode_sets),1)

    def test_actual_phone_frequency_rejection_is_explicit(self):
        m=self.modem('frequency_device_unsupported');r=m.call('qmi-neighbors',0)
        self.assertFalse(r['ok']);self.assertTrue(r['frequency_query_unsupported']);self.assertIn('DeviceUnsupported',r['error'])
        self.assertEqual(r['exchanges'][1]['response_hex'],'020200d200070002040001001900')
        self.assertEqual(m.sets,[]);self.assertEqual(m.mode_sets,[]);self.assertFalse(r['write_attempted'])

    def test_direct_neighbor_read_is_separate_and_never_sets(self):
        for slot in [0,1]:
            m=self.modem();r=m.call('qmi-neighbors',slot)
            self.assertTrue(r['ok'],r);self.assertEqual(r['read_profile'],'qmi-nas-nr-frequencies-v1')
            self.assertEqual(r['nr_serving_arfcns'],[634080]);self.assertEqual(r['nr_neighbor_arfcns'],[155000,634080])
            self.assertFalse(r['write_ready']);self.assertFalse(r['write_attempted']);self.assertFalse(r['complete_band_scan']);self.assertFalse(r['signal_measurements_available'])
            self.assertEqual(m.binds,[slot]);self.assertEqual(m.gets,0);self.assertEqual(m.frequency_gets,1);self.assertEqual(m.sets,[])

    def test_missing_and_empty_neighbor_lists_are_distinct(self):
        for fault,expected in [('missing_neighbors',None),('empty_neighbors',[]),('missing_frequency_lists',None)]:
            m=self.modem(fault);r=m.call('qmi-neighbors',0)
            self.assertTrue(r['ok'],r);self.assertEqual(r['nr_neighbor_arfcns'],expected);self.assertEqual(m.sets,[])
            if fault=='missing_frequency_lists':self.assertIsNone(r['nr_serving_arfcns'])

    def test_malformed_frequency_lists_and_refusal_never_publish_frequencies(self):
        for fault in ['short_frequency_list','bad_frequency_count','zero_length_frequency_list','invalid_frequency','zero_frequency','duplicate_frequency_tlv','frequency_error']:
            m=self.modem(fault);r=m.call('qmi-neighbors',0)
            self.assertFalse(r['ok'],(fault,r));self.assertFalse(r['write_attempted']);self.assertIsNone(r['nr_neighbor_arfcns']);self.assertIsNone(r['nr_serving_arfcns']);self.assertEqual(m.sets,[])

    def test_neighbor_read_without_capabilities_preserves_raw_frequencies(self):
        m=self.modem('no_hardware');r=m.call('qmi-neighbors',0)
        self.assertTrue(r['ok'],r);self.assertNotIn('supported_nr_hex',r);self.assertTrue(r['capability_error']);self.assertEqual(r['nr_neighbor_arfcns'],[155000,634080]);self.assertFalse(r['write_ready'])

    def test_read_live_preferences_and_hardware_without_set(self):
        m=self.modem();r=m.call('qmi-probe',0)
        self.assertTrue(r['ok'],r);self.assertTrue(r['write_ready']);self.assertFalse(r['write_attempted'])
        self.assertEqual(r['sa_hex'],m.original[0].hex());self.assertEqual(r['supported_nr_hex'],mask(1,3,78).hex())
        self.assertEqual(r['read_profile'],'qmi-nas-sa-v1');self.assertEqual(m.sets,[]);self.assertEqual(m.binds,[0])
        for exchange in r['exchanges']:
            self.assertEqual(exchange['send_flags'],0)
            self.assertEqual(exchange['send_return'],exchange['request_length'])
            self.assertEqual(exchange['send_errno'],0);self.assertEqual(exchange['send_error'],'')

    @unittest.skipUnless(os.environ.get('SA_QMI_CONTRACT_TEST'),'requires test-only send wrapper')
    def test_send_errno_is_reported_before_any_modem_request(self):
        for fault,code in [('invalid',22),('access',13)]:
            m=self.modem();r=m.call('qmi-probe',0,env={'SA_QMI_SEND_FAULT':fault})
            self.assertFalse(r['ok']);self.assertFalse(r['write_attempted'])
            self.assertEqual(r['state'],'transport_or_bind_failed')
            self.assertIn('bind_subscription',r['error']);self.assertIn(f'errno {code}',r['error'])
            self.assertEqual(m.binds,[]);self.assertEqual(m.gets,0);self.assertEqual(m.sets,[])
            self.assertEqual(len(r['exchanges']),1);exchange=r['exchanges'][0]
            self.assertEqual(exchange['send_return'],-1);self.assertEqual(exchange['send_errno'],code)
            self.assertTrue(exchange['send_error']);self.assertEqual(exchange['response_length'],-1)

    @unittest.skipUnless(os.environ.get('SA_QMI_CONTRACT_TEST'),'requires test-only send wrapper')
    def test_short_send_is_distinct_from_system_error(self):
        m=self.modem();r=m.call('qmi-probe',0,env={'SA_QMI_SEND_FAULT':'short'})
        self.assertFalse(r['ok']);self.assertFalse(r['write_attempted']);self.assertIn('short send',r['error'])
        self.assertEqual(m.sets,[]);self.assertEqual(m.binds,[])
        exchange=r['exchanges'][0]
        self.assertEqual(exchange['send_return'],exchange['request_length']-1)
        self.assertEqual(exchange['send_errno'],0);self.assertEqual(exchange['send_error'],'')

    def test_apply_then_restore_only_sa_until_reboot(self):
        m=self.modem();original=m.original[0].hex();nsa=m.nsa[0].hex();target=mask(78).hex()
        r=m.call('qmi-apply',0,original,original,nsa,target);self.assertEqual(r['state'],'applied_verified',r)
        self.assertEqual(m.nsa[0].hex(),nsa);self.assertEqual(m.lte,b'\xff'*8)
        request=m.sets[0][1];self.assertEqual(request[7:14].hex(),'170100002f4000')
        self.assertEqual(request[14:],mask(78))
        r=m.call('qmi-restore',0,original,target,nsa);self.assertEqual(r['state'],'restored_verified',r);self.assertEqual(m.sa[0],m.original[0])

    def test_second_slot_is_explicitly_bound(self):
        m=self.modem();original=m.original[1].hex();ns=m.nsa[1].hex()
        r=m.call('qmi-apply',1,original,original,ns,mask(78).hex());self.assertTrue(r['ok'],r)
        self.assertEqual(m.binds,[1]);self.assertEqual(m.sets[0][0],1);self.assertEqual(m.sa[0],m.original[0])

    def test_stale_preferences_never_set(self):
        m=self.modem();r=m.call('qmi-apply',0,m.original[0].hex(),mask(1).hex(),m.nsa[0].hex(),mask(78).hex())
        self.assertEqual(r['state'],'stale_snapshot');self.assertEqual(m.sets,[])

    def test_hardware_limits_are_enforced(self):
        m=self.modem();r=m.call('qmi-apply',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(28).hex())
        self.assertEqual(r['state'],'unsupported_band');self.assertEqual(m.sets,[])

    def test_missing_hardware_allows_read_but_blocks_apply(self):
        for fault in ['no_hardware','bad_hardware']:
            m=self.modem(fault);r=m.call('qmi-probe',0)
            self.assertTrue(r['ok'],r);self.assertFalse(r['write_ready']);self.assertTrue(r['capability_error'])
            r=m.call('qmi-apply',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(78).hex())
            self.assertEqual(r['state'],'capabilities_required');self.assertEqual(m.sets,[])

    def test_missing_and_malformed_masks_or_results_never_set(self):
        for fault in ['missing_sa','missing_nsa','short_sa','duplicate_sa','truncated_tlv','wrong_header_length','no_result']:
            m=self.modem(fault);r=m.call('qmi-probe',0)
            self.assertFalse(r['ok'],(fault,r));self.assertFalse(r['write_attempted']);self.assertEqual(m.sets,[])

    def test_unrelated_responses_are_ignored(self):
        for fault in ['wrong_transaction','wrong_message']:
            m=self.modem(fault);self.assertTrue(m.call('qmi-probe',0)['ok'])

    def test_bind_refusal_blocks_preferences(self):
        m=self.modem('bind_error');r=m.call('qmi-probe',1)
        self.assertEqual(r['state'],'transport_or_bind_failed');self.assertEqual(m.gets,0);self.assertEqual(m.sets,[])

    def test_failed_ack_or_readback_rolls_back(self):
        for fault in ['set_error','bad_readback']:
            m=self.modem(fault);r=m.call('qmi-apply',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(78).hex())
            self.assertEqual(r['state'],'failed_rolled_back',r);self.assertTrue(r['rollback_verified']);self.assertEqual(m.sa[0],m.original[0])

    def test_unrelated_change_or_failed_rollback_requires_recovery(self):
        for fault in ['nsa_changed','lte_changed','all_set_errors']:
            m=self.modem(fault);r=m.call('qmi-apply',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(78).hex())
            self.assertEqual(r['state'],'recovery_required',r);self.assertFalse(r['rollback_verified'])

    def test_readback_can_converge_after_ack(self):
        m=self.modem('lag');r=m.call('qmi-apply',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex(),mask(78).hex())
        self.assertEqual(r['state'],'applied_verified',r);self.assertEqual(len(m.sets),1)

    def test_probe_timeout_does_not_set(self):
        m=self.modem('silent_get');r=m.call('qmi-probe',0)
        self.assertFalse(r['ok']);self.assertIn('timed out',r['error']);self.assertFalse(r['write_attempted']);self.assertEqual(m.sets,[])

    def test_restore_noop_has_no_set(self):
        m=self.modem();r=m.call('qmi-restore',0,m.original[0].hex(),m.original[0].hex(),m.nsa[0].hex())
        self.assertEqual(r['state'],'already_matches');self.assertEqual(m.sets,[])

if __name__=='__main__':unittest.main(verbosity=2)
