#!/usr/bin/env python3
"""Loopback-only deterministic HTTP, UDP and DNS servers for emulator integration tests."""
import http.server, socketserver, threading, struct, socket, select
socks_count=0
class HTTP(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        data=str(socks_count).encode() if self.path=='/socks-count' else b'CLASH_ANDROID_FIXTURE_OK'
        self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
    def log_message(self,*args):pass
class UDP(socketserver.BaseRequestHandler):
    def handle(self):
        data,sock=self.request;sock.sendto(b'ECHO:'+data,self.client_address)
class DNS(socketserver.BaseRequestHandler):
    def handle(self):
        data,sock=self.request
        if len(data)<12:return
        end=12
        while end<len(data) and data[end]:end+=data[end]+1
        end+=5
        if end>len(data):return
        qtype=struct.unpack('!H',data[end-4:end-2])[0]
        answer=b'\xc0\x0c\x00\x01\x00\x01\x00\x00\x00\x3c\x00\x04\xcb\x00\x71\x07' if qtype==1 else b''
        response=data[:2]+b'\x81\x80\x00\x01'+(b'\x00\x01' if answer else b'\x00\x00')+b'\x00\x00\x00\x00'+data[12:end]+answer
        sock.sendto(response,self.client_address)
class SOCKS(socketserver.BaseRequestHandler):
    def handle(self):
        global socks_count
        client=self.request;client.settimeout(5)
        def read(n):
            data=b''
            while len(data)<n:
                part=client.recv(n-len(data))
                if not part:raise EOFError()
                data+=part
            return data
        try:
            version,count=read(2);read(count);client.sendall(b'\x05\x00')
            version,command,reserved,kind=read(4)
            if command!=1:return
            if kind==1:host=socket.inet_ntoa(read(4))
            elif kind==3:host=read(read(1)[0]).decode()
            elif kind==4:host=socket.inet_ntop(socket.AF_INET6,read(16))
            else:return
            port=struct.unpack('!H',read(2))[0]
            # Restrict the fixture to its own HTTP listener, never an open proxy.
            if host not in ('10.0.2.2','127.0.0.1') or port!=18080:return
            with socket.create_connection(('127.0.0.1',18080),5) as upstream:
                socks_count+=1;client.sendall(b'\x05\x00\x00\x01\x7f\x00\x00\x01\x00\x00')
                readers=[client,upstream]
                while readers:
                    ready,_,_=select.select(readers,[],[],5)
                    if not ready:return
                    for source in ready:
                        data=source.recv(65536)
                        destination=upstream if source is client else client
                        if not data:
                            readers.remove(source);destination.shutdown(socket.SHUT_WR)
                        else:destination.sendall(data)
        except (OSError,EOFError):pass
class TCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address=True
    daemon_threads=True
servers=[http.server.ThreadingHTTPServer(('127.0.0.1',18080),HTTP),socketserver.ThreadingUDPServer(('127.0.0.1',18081),UDP),socketserver.ThreadingUDPServer(('127.0.0.1',18053),DNS),TCPServer(('127.0.0.1',18082),SOCKS)]
for server in servers:threading.Thread(target=server.serve_forever,daemon=True).start()
print('Ready: HTTP 18080, UDP 18081, DNS 18053',flush=True)
try:threading.Event().wait()
except KeyboardInterrupt:
    for server in servers:server.shutdown();server.server_close()
