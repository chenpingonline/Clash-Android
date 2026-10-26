#!/usr/bin/env python3
"""Loopback TCP/UDP echo and DNS for emulator connection-owner attribution QA."""
import socketserver
import struct
import threading


class TCP(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(60)
        try:
            while data := self.request.recv(4096):
                self.request.sendall(data)
        except OSError:
            pass


class UDP(socketserver.BaseRequestHandler):
    def handle(self):
        data, sock = self.request
        sock.sendto(data, self.client_address)


class DNS(socketserver.BaseRequestHandler):
    def handle(self):
        data, sock = self.request
        if len(data) < 12:
            return
        end = 12
        while end < len(data) and data[end]:
            end += data[end] + 1
        end += 5
        if end > len(data):
            return
        qtype = struct.unpack('!H', data[end - 4:end - 2])[0]
        answer = b'\xc0\x0c\x00\x01\x00\x01\x00\x00\x00\x3c\x00\x04\x0a\x00\x02\x02' if qtype == 1 else b''
        response = data[:2] + b'\x81\x80\x00\x01' + (b'\x00\x01' if answer else b'\x00\x00') + b'\x00\x00\x00\x00' + data[12:end] + answer
        sock.sendto(response, self.client_address)


class TCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


servers = [TCPServer(('127.0.0.1', 18086), TCP),
           socketserver.ThreadingUDPServer(('127.0.0.1', 18087), UDP),
           socketserver.ThreadingUDPServer(('127.0.0.1', 18088), DNS)]
for server in servers:
    threading.Thread(target=server.serve_forever, daemon=True).start()
print('Ready: owner TCP 18086, UDP 18087, DNS 18088', flush=True)
try:
    threading.Event().wait()
except KeyboardInterrupt:
    for server in servers:
        server.shutdown()
        server.server_close()
