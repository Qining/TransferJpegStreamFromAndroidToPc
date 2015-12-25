import StringIO
from copy import deepcopy
import io
from PIL import Image, ImageFile, ImageQt
import sys
from PyQt4 import QtGui, QtCore

__author__ = 'anthony'

class ImageProcessor(QtCore.QObject):

    frame_ready = QtCore.pyqtSignal(QtGui.QImage)

    def __init__(self):
        super(ImageProcessor, self).__init__()

    def processFrameData(self, data_queue):
        # super(ImageProcessor, self).__init__()
        print("process framed data")
        while True:
            r = data_queue.get()
            width = r[0]
            height = r[1]
            rawdata = r[2]
            parser = ImageFile.Parser()
            parser.feed(rawdata)
            im = parser.close()
            image = ImageQt.ImageQt(im)
            print image.width(), image.height()
            self.frame_ready.emit(image)

    def convertRGBAtoARGB(self, data):
        i = 0
        r = ''
        while i<len(data):
            r += data[i+1]
            r += data[i+2]
            r += data[i+3]
            r += data[i]
            i += 4
        return r