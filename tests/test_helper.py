"""Real C helper against an independent mock modem; never opens /dev/diag."""
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import tempfile
import threading
import unittest

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'build/sa-helper-host'
SA = '/nv/item_files/modem/mmode/nr_band_pref'
NSA = '/nv/item_files/modem/mmode/nr_nsa_band_pref'


def mask(*bands):
    return sum(1 << (n-1) for n in bands).to_bytes(64, 'little')


def crc(data):
    value = 65535
    for b in data:
        value ^= b
        for _ in range(8):
            value = (value >> 1) ^ (0x8408 if value & 1 else 0)
    return value ^ 65535


def hdlc(data):
    data += struct.pack('<H', crc(data))
    return data.replace(b'\x7d', b'\x7d\x5d').replace(b'\x7e', b'\x7d\x5e') + b'\x7e'


def unhdlc(data):
    assert data[-1] == 0x7e
    out, escaped = bytearray(), False
    for b in data[:-1]:
        if escaped:
            out.append(b ^ 32)
            escaped = False
        elif b == 0x7d:
            escaped = True
        else:
            out.append(b)
    assert struct.unpack('<H', out[-2:])[0] == crc(out[:-2])
    return bytes(out[:-2])


class Modem:
    def __init__(self, framing='qrtr', fault=None):
        self.directory = tempfile.TemporaryDirectory(prefix='salab-')
        self.path = self.directory.name + '/m.sock'
        self.framing, self.fault = framing, fault
        self.original, self.nsa = mask(1, 3, 78), mask(1, 78)
        self.files = {SA: self.original, NSA: self.nsa,
                      SA+'_Subscription01': mask(28, 78), NSA+'_Subscription01': mask(78)}
        self.puts, self.errors, self.gets = [], [], []
        self.stopped = threading.Event()
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_SEQPACKET)
        self.sock.bind(self.path)
        self.sock.listen(3)
        self.sock.settimeout(0.1)
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def run(self):
        try:
            while not self.stopped.is_set():
                try:
                    conn, _ = self.sock.accept()
                except socket.timeout:
                    continue
                with conn:
                    while not self.stopped.is_set():
                        data = conn.recv(4096)
                        if not data:
                            break
                        if self.framing != 'qrtr':
                            off = 8 if self.framing == 'mdm' else 4
                            data = unhdlc(data[off:])
                        assert data[:2] == b'\x4b\x13'
                        op = struct.unpack_from('<H', data, 2)[0]
                        if op == 39:
                            self.gets.append(data)
                            # Protocol independently described by bkerler/edl qc_diag.efs_get:
                            # header + u32 max data + u32 path bytes + u16 seq + C string.
                            valid = len(data) >= 15 and struct.unpack_from('<I',data,4)[0] == 64 and struct.unpack_from('<I',data,8)[0] == len(data)-14 and data[-1] == 0
                            seq = struct.unpack_from('<H',data,12)[0] if valid else 0
                            path = data[14:-1].decode() if valid else ''
                            if not valid or self.fault == 'bad_length':
                                response = b'\x15' + data
                            elif self.fault == 'get_error':
                                response = data[:4] + struct.pack('<IIH', 0, 13, seq)
                            elif self.fault == 'missing_sa_file':
                                response = data[:4] + struct.pack('<IIH', 0xffffffff, 2, seq)
                            elif self.fault == 'short_get':
                                response = data[:4] + struct.pack('<IIH', 64, 0, seq) + b'\x00'
                            elif self.fault == 'unsupported_get':
                                response = b'\x13' + data
                            elif self.fault == 'silent_get':
                                continue
                            elif self.fault == 'old_response':
                                response = data[:4] + struct.pack('<II', 64, 0) + self.files[path]
                            elif self.fault == 'wrong_sequence':
                                response = data[:4] + struct.pack('<IIH', 64, 0, seq ^ 1) + self.files[path]
                            elif self.fault == 'extra_response_byte':
                                response = data[:4] + struct.pack('<IIH', 64, 0, seq) + self.files[path] + b'\x00'
                            else:
                                response = data[:4] + struct.pack('<IIH', 64, 0, seq) + self.files[path]
                        elif op == 38:
                            size, flags, sequence = struct.unpack_from('<IIH', data, 4)
                            assert size == 64 and flags == 0x80040
                            path = data[78:-1].decode()
                            assert path in (SA, SA+'_Subscription01'), 'NSA write attempted!'
                            self.puts.append((path, data[14:78]))
                            error = 13 if self.fault == 'rollback_error' or self.fault == 'put_error' and len(self.puts) == 1 else 0
                            if not error:
                                self.files[path] = mask(3) if self.fault == 'bad_readback' and len(self.puts) == 1 else data[14:78]
                            if self.fault == 'nsa_changed' and len(self.puts) == 1:
                                self.files[NSA] = mask(28)
                            response = data[:4] + struct.pack('<HHH', sequence, error, 64)
                        else:
                            raise AssertionError('Unexpected modem operation '+str(op))
                        # Foreign subsystem packet must be discarded, followed by the answer.
                        foreign = b'\x4b\x30\x01\x00junk'
                        if self.framing == 'qrtr':
                            for payload in (foreign, response):
                                wrapped = b'\x7e\x01' + struct.pack('<H', len(payload)) + payload + b'\x7e'
                                conn.send(wrapped)
                        else:
                            token = struct.pack('<i', -1) if self.framing == 'mdm' else b''
                            frame1, frame2 = hdlc(foreign), hdlc(response)
                            conn.send(struct.pack('<II', 0x20, 2) + token + struct.pack('<I',len(frame1)) + frame1 + token + struct.pack('<I',len(frame2)) + frame2)
        except Exception as exc:
            if not self.stopped.is_set():
                self.errors.append(exc)

    def call(self, *args):
        p = subprocess.run([str(HELPER), '--mock', self.path, '--framing', self.framing, *map(str,args)], text=True, capture_output=True, timeout=15)
        return json.loads(p.stdout)

    def close(self):
        self.stopped.set()
        self.thread.join(1)
        self.sock.close()
        self.directory.cleanup()
        if self.errors:
            raise self.errors[0]


class HelperTests(unittest.TestCase):
    def modem(self, *args, **kw):
        m = Modem(*args, **kw)
        self.addCleanup(m.close)
        return m

    def test_probe_is_read_only(self):
        m=self.modem(); r=m.call('probe',0)
        self.assertTrue(r['ok']); self.assertEqual(r['sa_hex'],m.original.hex()); self.assertFalse(r['write_attempted']); self.assertEqual(m.puts,[])

    def test_get_has_full_modern_prefix_and_payload_offset(self):
        m=self.modem();r=m.call('probe',0)
        self.assertTrue(r['ok'],r)
        # Fixed golden header for the 39-character SA pathname plus NUL.
        self.assertEqual(m.gets[0][:14].hex(),'4b13270040000000280000000000')
        self.assertEqual(m.gets[0][14:],SA.encode()+b'\x00')
        self.assertEqual(len(m.gets[0]),54)
        self.assertEqual(r['sa_hex'],m.original.hex())
        self.assertEqual(r['read_profile'],'efs-get39-v2')
        reply=bytes.fromhex(r['exchanges'][0]['response_hex'])
        self.assertEqual(len(reply),78)
        self.assertEqual(reply[14:],m.original)

    def test_old_short_request_reproduces_bad_length(self):
        m=self.modem()
        with socket.socket(socket.AF_UNIX,socket.SOCK_SEQPACKET) as s:
            s.settimeout(2);s.connect(m.path)
            request=b'\x4b\x13\x27\x00'+SA.encode()+b'\x00'
            s.send(request);s.recv(1024)  # unrelated subsystem packet
            response=s.recv(1024)
        self.assertEqual(response[4],0x15)
        self.assertEqual(response[5:-1],request)
        self.assertEqual(m.puts,[])

    def test_apply_restore_across_three_transports(self):
        for framing in ['qrtr','chardev','mdm']:
            with self.subTest(framing=framing):
                m=self.modem(framing); target=mask(78).hex()
                r=m.call('apply',0,m.original.hex(),m.original.hex(),m.nsa.hex(),target)
                self.assertEqual(r['state'],'applied_verified',r); self.assertEqual(m.files[NSA],m.nsa)
                r=m.call('restore',0,m.original.hex(),target,m.nsa.hex())
                self.assertEqual(r['state'],'restored_verified',r); self.assertEqual(m.files[SA],m.original)
                self.assertTrue(all(p==SA for p,_ in m.puts))

    def test_second_slot(self):
        m=self.modem(); orig=m.files[SA+'_Subscription01']; ns=m.files[NSA+'_Subscription01']
        r=m.call('apply',1,orig.hex(),orig.hex(),ns.hex(),mask(78).hex())
        self.assertTrue(r['ok'],r); self.assertEqual(m.puts[0][0],SA+'_Subscription01'); self.assertEqual(m.files[SA],m.original)

    def test_stale_sa_or_nsa_never_writes(self):
        for sa,ns in [(mask(1),mask(1,78)),(mask(1,3,78),mask(28))]:
            m=self.modem(); r=m.call('apply',0,m.original.hex(),sa.hex(),ns.hex(),mask(78).hex())
            self.assertEqual(r['state'],'stale_snapshot'); self.assertEqual(m.puts,[])

    def test_invalid_or_unsupported_mask_never_writes(self):
        for selection in ['00'*64,mask(77).hex(),'ff','zz'*64]:
            m=self.modem();r=m.call('apply',0,m.original.hex(),m.original.hex(),m.nsa.hex(),selection)
            self.assertFalse(r['ok']); self.assertFalse(r['write_attempted']); self.assertEqual(m.puts,[])

    def test_get_errors_never_write(self):
        for fault in ['get_error','short_get','old_response','wrong_sequence','extra_response_byte']:
            m=self.modem(fault=fault); r=m.call('apply',0,m.original.hex(),m.original.hex(),m.nsa.hex(),mask(78).hex())
            self.assertEqual(r['state'],'read_failed'); self.assertEqual(m.puts,[])

    def test_unsupported_get_does_not_try_unverified_legacy_layout(self):
        m=self.modem(fault='unsupported_get');r=m.call('probe',0)
        self.assertFalse(r['ok']);self.assertIn('bad command',r['error']);self.assertEqual(len(m.gets),1);self.assertEqual(m.puts,[])

    def test_rejection_keeps_packet_diagnostics(self):
        m=self.modem(fault='bad_length');r=m.call('probe',0)
        self.assertEqual(r['state'],'read_failed');self.assertIn('get_sa: bad length (0x15)',r['error'])
        x=r['exchanges'][0];self.assertEqual(x['request_length'],54)
        self.assertEqual(x['response_length'],55);self.assertTrue(x['response_hex'].startswith('154b132700'))
        self.assertEqual(m.puts,[])

    def test_phone_missing_override_is_not_created(self):
        m=self.modem(fault='missing_sa_file');r=m.call('probe',0)
        self.assertEqual(r['state'],'read_failed');self.assertIn('errno 2',r['error'])
        self.assertEqual(r['exchanges'][0]['response_hex'],'4b132700ffffffff020000000000')
        self.assertFalse(r['write_attempted']);self.assertEqual(m.puts,[])

    def test_silent_get_times_out_without_mutation(self):
        m=self.modem(fault='silent_get');r=m.call('probe',0)
        self.assertFalse(r['ok']);self.assertIn('timed out',r['error']);self.assertFalse(r['write_attempted'])
        self.assertEqual(r['exchanges'][0]['response_length'],-1);self.assertEqual(m.puts,[])

    def test_failed_ack_or_readback_rolls_back(self):
        for fault in ['put_error','bad_readback']:
            m=self.modem(fault=fault);r=m.call('apply',0,m.original.hex(),m.original.hex(),m.nsa.hex(),mask(78).hex())
            self.assertEqual(r['state'],'failed_rolled_back',r); self.assertTrue(r['rollback_verified']); self.assertEqual(m.files[SA],m.original); self.assertEqual(m.files[NSA],m.nsa)

    def test_noop_does_not_write(self):
        m=self.modem();r=m.call('restore',0,m.original.hex(),m.original.hex(),m.nsa.hex())
        self.assertEqual(r['state'],'already_matches'); self.assertEqual(m.puts,[])

    def test_unverified_rollback_is_recovery_required(self):
        for fault in ['rollback_error','nsa_changed']:
            m=self.modem(fault=fault);r=m.call('apply',0,m.original.hex(),m.original.hex(),m.nsa.hex(),mask(78).hex())
            self.assertEqual(r['state'],'recovery_required',r); self.assertFalse(r['ok']); self.assertFalse(r['rollback_verified'])
            self.assertTrue(all(path==SA for path,_ in m.puts))


if __name__ == '__main__':
    unittest.main(verbosity=2)
