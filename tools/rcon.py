#!/usr/bin/env python3
"""Minimal RCON client for CI: rcon.py PORT PASSWORD CMD... ; prints each reply, exits 1 if any is empty/unknown."""
import socket, struct, sys

def pkt(s, rid, typ, body):
    d = struct.pack('<ii', rid, typ) + body.encode() + b'\0\0'
    s.sendall(struct.pack('<i', len(d)) + d)

def rd(s):
    n = struct.unpack('<i', s.recv(4))[0]
    d = b''
    while len(d) < n:
        d += s.recv(n - len(d))
    return struct.unpack('<ii', d[:8]), d[8:-2].decode(errors='replace')

port, pw, cmds = int(sys.argv[1]), sys.argv[2], sys.argv[3:]
s = socket.create_connection(('127.0.0.1', port), timeout=30)
pkt(s, 1, 3, pw)
if rd(s)[0][0] == -1:
    sys.exit('rcon auth failed')
bad = False
for c in cmds:
    pkt(s, 2, 2, c)
    _, r = rd(s)
    print(f'> {c}\n{r}', flush=True)
    if 'Unknown' in r or 'Incorrect' in r:
        bad = True
sys.exit(1 if bad else 0)
