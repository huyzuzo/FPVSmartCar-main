import socket
import struct
import fcntl
import  sys
from time import sleep, time
import serial
import subprocess
from threading import Thread

from Buzzer import *

class Server():
    def __init__(self):
        self.tcp_Flag = False
        self.buzzer = Buzzer()
        self.ser = serial.Serial('/dev/ttyACM0', 9600, timeout=1)

    def getInterfaceIP(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        return socket.inet_ntoa(fcntl.ioctl(s.fileno(),
                                            0x8915,
                                            struct.pack('256s',b'wlan0'[:15])
                                            )[20:24])

    def startTcpServer(self):
        HOST = str(self.getInterfaceIP())

        #VideoStream
        self.cmd = ("python3","VideoStream.py")

        #Reader
        self.server_socket1 = socket.socket()
        self.server_socket1.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
        self.server_socket1.bind((HOST, 3000))
        self.server_socket1.listen(1)

        #Sender
        self.server_socket2 = socket.socket()
        self.server_socket2.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
        self.server_socket2.bind((HOST, 5000))
        self.server_socket2.listen(1)

        #Serial
        self.ser.flush()

        print(f"Server runing on {HOST}!")

    def stopTcpServer(self):
        try:
            self.proc.kill()
            self.connection1.close()
            self.connection2.close()
        except Exception as e:
            print('\n'+"No client connection")

    def reset(self):
        self.stopTcpServer()
        self.startTcpServer()
        self.tcp_Flag = False
        self.video_stream = Thread(target=self.videoStream)
        self.read_data_2 = Thread(target=self.readData2)
        self.read_data_1 = Thread(target=self.readData1)
        self.video_stream.start()
        self.read_data_2.start()
        self.read_data_1.start()

    def videoStream(self):
        try:
            self.proc = subprocess.Popen(self.cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        except Exception as e: 
            print(e)

    def readData2(self):
        try:
            try:
                self.connection2,self.client_address2 = self.server_socket2.accept()
                print("Sender2 connection successful!")
            except:
                print("Sender2 connect failed!")
            self.server_socket2.close()
            while True:
                try:
                    AllData=self.connection2.recv(1024).decode('utf-8')
                except:
                    break
                if(AllData):
                    AllData = AllData[2:]
                    if(AllData == "CLIENT#0"):
                        self.reset()
                    else:
                        print(AllData)
                        if(not self.tcp_Flag):
                            self.ser.write(str(AllData + '\n').encode('utf-8'))
        except Exception as e: 
            print(e)
        print("Stop Server")
        self.stopTcpServer()

    def readData1(self):
        try:
            try:
                self.connection1,self.client_address1 = self.server_socket1.accept()
                print("Sender1 connection successful!")
            except:
                print("Sender1 connect failed!")
            self.server_socket1.close()
            while True:
                try:
                    AllData=self.connection1.recv(1024).decode('utf-8')
                    self.alert()
                except:
                    break
                if(AllData):
                    AllData = AllData[2:]
                    if(AllData == "CLIENT#0"):
                        self.reset()
                    else:
                        self.tcp_Flag = True
                        print(AllData)
                        self.ser.write(str(AllData + '\n').encode('utf-8'))
                        self.tcp_Flag = False

        except Exception as e: 
            print(e)
        print("Stop Server")
        self.stopTcpServer()

    def alert(self):
        try:
            if self.ser.in_waiting > 0:
                message = self.ser.readline().decode('utf-8').rstrip()
                if(message == "0" or message == "1"):
                    self.buzzer.run(True)
                    sleep(0.1)
                    self.buzzer.run(False)
        except Exception as e:
            print(e)

    def signalHandler(self, signal, frame):
        print()
        sys.exit(0)

if __name__ == "__main__":
    TCP_Server = Server()
    TCP_Server.startTcpServer()
    video_stream=Thread(target=TCP_Server.videoStream)
    read_data_2=Thread(target=TCP_Server.readData2)
    read_data_1=Thread(target=TCP_Server.readData1)
    video_stream.start()
    read_data_2.start()
    read_data_1.start()
