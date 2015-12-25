from time import sleep
import sys
import SocketServer
import ImageProcessor
import multiprocessing
from threading import Thread
from PyQt4 import QtGui, QtCore

__author__ = 'anthony'

class PreviewReciver(QtGui.QWidget):
    def __init__(self):
        super(PreviewReciver, self).__init__()
        self.server = SocketServer.SocketServer()
        self.processor = ImageProcessor.ImageProcessor()
        self.q = multiprocessing.Manager().Queue()
        self.ip = '192.168.0.3'
        self.port = 9889
        self.server_process = None
        self.imageprocessor_process = None
        self.timeout = 50
        self.lbl = None
        self.initUI()

    def initUI(self):
        hbox = QtGui.QHBoxLayout(self)
        pixmap = QtGui.QPixmap("test.jpg")
        self.lbl = QtGui.QLabel(self)
        self.lbl.setPixmap(pixmap)
        hbox.addWidget(self.lbl)
        self.setLayout(hbox)
        self.move(0,0)
        self.setWindowTitle('preview')
        self.show()
        self.processor.frame_ready.connect(self.refreshImage)

    def refreshImage(self, qimage):
        print("refreshImage() called")
        # qimage = self.pixel_queue.get()
        if qimage:
            print qimage
            print qimage.width(), qimage.height()
            pix = QtGui.QPixmap.fromImage(qimage)
            self.lbl.setPixmap(pix)
            self.show()

    def startServer(self):
        self.server_process = Thread(target=self.server.startListening, args=(self.ip, self.port, self.q))
        self.server_process.daemon = True
        self.server_process.start()


    def startProcessor(self):
        # self.imageprocessor_process = multiprocessing.Process(target=self.processor.processFrameData,
        #                                                       args=(self.q, self.pixel_queue))
        self.imageprocessor_process = Thread(target=self.processor.processFrameData, args=(self.q,))
        self.imageprocessor_process.daemon = True
        self.imageprocessor_process.start()

    def stopServer(self):
        if self.server_process != None:
            self.server_process.terminate()

    def stopProcessor(self):
        if self.imageprocessor_process != None:
            self.imageprocessor_process.terminate()

    def start(self):
        self.startServer()
        self.startProcessor()

    def terminate(self):
        self.stopServer()
        self.stopProcessor()
        self.q.task_done()

    def setIp(self, ip):
        self.ip = str(ip)

    def setPort(self, port):
        self.port = int(port)

    def setTimeout(self, t):
        self.timeout = t

def main():
    app = QtGui.QApplication(sys.argv)
    r = PreviewReciver()
    r.start()
    sys.exit(app.exec_())

if __name__ == "__main__":
   main()