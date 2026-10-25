#!/usr/bin/env python3
"""Read-only traffic probes against the loopback network fixture on a dedicated emulator."""
import base64, pathlib, subprocess, sys, urllib.request
adb=str(pathlib.Path.home()/'Library/Android/sdk/platform-tools/adb')
out=pathlib.Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
def shell(command):
 r=subprocess.run([adb,'shell',command],capture_output=True,timeout=12)
 return r.stdout+r.stderr
def count():return int(urllib.request.urlopen('http://127.0.0.1:18080/socks-count').read())
before=count()
tcp=shell("printf 'GET / HTTP/1.0\\r\\nHost: fixture\\r\\n\\r\\n' | toybox timeout 5 toybox nc 10.0.2.2 18080")
after=count();(out/'tcp.log').write_bytes(tcp+f'\nSOCKS count {before} -> {after}\n'.encode())
assert b'CLASH_ANDROID_FIXTURE_OK' in tcp and after>before, tcp
udp=shell("printf 'PING' | toybox timeout 3 toybox nc -u 10.0.2.2 18081")
(out/'udp.log').write_bytes(udp);assert b'ECHO:PING' in udp,udp
query=b'\x12\x34\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00\x07fixture\x04test\x00\x00\x01\x00\x01'
dns=shell("printf '%s' | base64 -d | toybox timeout 3 toybox nc -u 172.19.0.2 53"%base64.b64encode(query).decode())
(out/'dns.hex').write_text(dns.hex());assert bytes([203,0,113,7]) in dns,dns
(out/'system.log').write_bytes(shell('dumpsys vpn_management'))
(out/'settings.log').write_bytes(shell('settings get secure always_on_vpn_app')+shell('settings get secure always_on_vpn_lockdown'))
print('PASS: TCP via SOCKS5, UDP, DNS; SOCKS count',before,'->',after)
