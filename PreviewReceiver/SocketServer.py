import struct
import Queue
import socket
import select

__author__ = 'anthony'

class SocketServer:

    def __init__(self):
        return

    def startListening(self, ip, port, data_queue):
        print("start listening")
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(None)
        s.bind((ip, port))
        s.listen(1)
        try:
            conn, addr = s.accept()
        except socket.timeout:
            print("Socket accept timeout")
        print("Connected by:" + str(addr))
        while True:
            r = self.recv_msg(conn)
            if r:
                width, height, framedata = r
                print("received framedata size=", framedata.__sizeof__(), "width=", width, "height=", height)
                data_queue.put([width, height, framedata])

    def recv_msg(self, conn):
        raw_msglen = self.recvall(conn, 4)
        if not raw_msglen:
            return None
        msglen = struct.unpack('>I', raw_msglen)[0]
        # print("received packetSize from header=", msglen)
        raw_width = self.recvall(conn, 4)
        if not raw_width:
            return None
        width = struct.unpack('>I', raw_width)[0]
        raw_height = self.recvall(conn, 4)
        if not raw_height:
            return None
        height = struct.unpack('>I', raw_height)[0]
        return width, height, self.recvall(conn, msglen-8)

    def recvall(self, conn, n):
        data = ''
        while len(data) < n:
            rdy = [[], [], []]
            try:
                rdy = select.select([conn], [], [], 0)
            except:
                pass
            else:
                if len(rdy[0]) != 0 and rdy[0][0] == conn:
                    packet = conn.recv(min(n-len(data), 1024))
                    data += packet
            # packet = conn.recv(min(n-len(data), 1024))
            # if not packet:
            #     return None
        return data