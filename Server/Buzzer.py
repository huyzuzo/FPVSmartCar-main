import RPi.GPIO as GPIO

GPIO.setwarnings(False)
Buzzer_Pin = 17
GPIO.setmode(GPIO.BCM)
GPIO.setup(Buzzer_Pin,GPIO.OUT)

class Buzzer:
    def run(self,command):
        if command:
            GPIO.output(Buzzer_Pin,True)
        else:
            GPIO.output(Buzzer_Pin,False)

if __name__=='__main__':
    pass
