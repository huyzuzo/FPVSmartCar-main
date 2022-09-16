#include <Arduino_FreeRTOS.h>
#include <semphr.h>
#include <AFMotor.h>
#include <Servo.h>
#include <NewPing.h>

#define SERVO_PIN 10

#define FRONT_TRIGGER_PIN A0
#define FRONT_ECHO_PIN A1
#define BOTTOM_TRIGGER_PIN A4
#define BOTTOM_ECHO_PIN A5

#define MAX_DISTANCE 300
#define MIN_DISTANCE 15
#define MOTOR_SPEED 150
#define DEFAULT_ANGLE 95

enum Direction{
  MIDDLE,
  UP,
  DOWN,
  LEFT,
  RIGHT
};

class MOTOR{
private:
  AF_DCMotor left_motor_1 = AF_DCMotor(2, MOTOR12_64KHZ);
  AF_DCMotor left_motor_2 = AF_DCMotor(1, MOTOR12_64KHZ);
  AF_DCMotor right_motor_1 = AF_DCMotor(4, MOTOR34_64KHZ);
  AF_DCMotor right_motor_2 = AF_DCMotor(3, MOTOR34_64KHZ);
  unsigned int direction;

public:
  
  void setSpeed(uint8_t speed){
    right_motor_1.setSpeed(speed);
    right_motor_2.setSpeed(speed);
    left_motor_1.setSpeed(speed);
    left_motor_2.setSpeed(speed);
  }

  void stop(){
    right_motor_1.run(RELEASE);
    right_motor_2.run(RELEASE);
    left_motor_1.run(RELEASE);
    left_motor_2.run(RELEASE);
  }

  void runUp(){
    right_motor_1.run(FORWARD);
    right_motor_2.run(FORWARD);
    left_motor_1.run(FORWARD);
    left_motor_2.run(FORWARD);
  }

  void runDown(){
    right_motor_1.run(BACKWARD);
    right_motor_2.run(BACKWARD);
    left_motor_1.run(BACKWARD);
    left_motor_2.run(BACKWARD);
  }

  void runLeft(){
    right_motor_1.run(FORWARD);
    right_motor_2.run(FORWARD);
    left_motor_1.run(BACKWARD);
    left_motor_2.run(BACKWARD);
  }

  void runRight(){
    right_motor_1.run(BACKWARD);
    right_motor_2.run(BACKWARD);
    left_motor_1.run(FORWARD);
    left_motor_2.run(FORWARD);
  }

  void setDirection(unsigned int d){
    direction = d;
  }

  unsigned int getDirection(){
    return direction;
  }
};

class SERVO{
protected:
  Servo servo;
  unsigned int pin = SERVO_PIN;
  unsigned int angle = DEFAULT_ANGLE;

public:

  void attach(){
    servo.attach(pin);
  }

  void write(){
    servo.write(angle);
  }

  unsigned int setAngle(unsigned int a){
    angle = a;
  }

  unsigned int getAngle(){
    return angle;
  }
};

class UltrasonicSensor{
protected:
  NewPing front = NewPing(FRONT_TRIGGER_PIN, FRONT_ECHO_PIN, MAX_DISTANCE);
  NewPing bottom = NewPing(BOTTOM_TRIGGER_PIN, BOTTOM_ECHO_PIN, MAX_DISTANCE);

public:
  unsigned int readDistanceFront(){
    return front.ping_cm();
  }

  unsigned int readDistanceBottom(){
    return bottom.ping_cm();
  }

  bool alertFront(){
    unsigned int front_distance = readDistanceFront();
    if(front_distance > 0 && front_distance <= MIN_DISTANCE)
      return true;
    return false;
  }

  bool alertBottom(){
    unsigned int bottom_distance = readDistanceBottom();
    if(bottom_distance > 0 && bottom_distance <= MIN_DISTANCE)
      return true;
    return false;
  }
};

SemaphoreHandle_t motorSemaphore;
SemaphoreHandle_t servoSemaphore;
SemaphoreHandle_t readDataSemaphore;

String data_server = "";
String str_number = "";
int data_number;
int task_delay = 50;

MOTOR motor;
SERVO servo;
UltrasonicSensor sensor;

void setup(){
  Serial.begin(9600);
  
  motor.setSpeed(MOTOR_SPEED);

  servo.attach();

  servo.write();

  motorSemaphore = xSemaphoreCreateBinary();

  servoSemaphore = xSemaphoreCreateBinary();

  readDataSemaphore = xSemaphoreCreateBinary();

  xTaskCreate(readData, "Read Data", configMINIMAL_STACK_SIZE, NULL, 1, NULL);

  xTaskCreate(controlMotor, "Control Motor", configMINIMAL_STACK_SIZE, NULL, 1, NULL);

  xTaskCreate(controlServo, "Control Servo", configMINIMAL_STACK_SIZE, NULL, 1, NULL);
}

void loop(){}

void readData(void* p){
  while(true){
    if(sensor.alertFront() && motor.getDirection() == UP){
      Serial.println("0");
      motor.setDirection(MIDDLE); 
      xSemaphoreGive(motorSemaphore);
      vTaskDelay(pdMS_TO_TICKS(50));
    }else if(sensor.alertBottom() && motor.getDirection() == DOWN){
      Serial.println("1");
      motor.setDirection(MIDDLE); 
      xSemaphoreGive(motorSemaphore);
      vTaskDelay(pdMS_TO_TICKS(50));
    }
    
    if (Serial.available() > 0) {
      data_server = Serial.readStringUntil('\n');
      if(data_server.indexOf("MOTOR") != -1){
        str_number = &data_server[data_server.indexOf("#") + 1];
        data_number = str_number.toInt();
        task_delay = 50;

        if(data_number >= -17 && data_number <=18){
          if(data_number == 0){
            if(sensor.alertFront()){
              motor.setDirection(MIDDLE); 
              Serial.println("0");
            }else{
              motor.setDirection(UP);
            }
          }else if(data_number == 17 || data_number == -17){
            if(sensor.alertBottom()){
              motor.setDirection(MIDDLE); 
              Serial.println("1");
            }else{
              motor.setDirection(DOWN);
            }       
          }else if(data_number == 18){
            motor.setDirection(MIDDLE); 
          }else if(data_number > 0){
            task_delay = (data_number + 1) * 50;
            motor.setDirection(RIGHT);
          }else{
            task_delay = (-data_number + 1) * 50;
            motor.setDirection(LEFT);
          }
        }
        
        xSemaphoreGive(motorSemaphore);
        vTaskDelay(pdMS_TO_TICKS(task_delay));
      }else if(data_server.indexOf("SERVO") != -1){
        str_number = &data_server[data_server.indexOf("#") + 1];
        data_number = str_number.toInt();
        task_delay = 50;

        servo.setAngle(data_number);
        xSemaphoreGive(servoSemaphore);
        vTaskDelay(pdMS_TO_TICKS(task_delay));
      }
    }
  }
}

void controlMotor(void *p){
  while (true){
    xSemaphoreTake(motorSemaphore, portMAX_DELAY);
    switch (motor.getDirection()){
    case MIDDLE:
      motor.stop();
      break;
    case RIGHT:
      motor.runRight();
      break;
    case UP:
      motor.runUp();
      break;
    case LEFT:
      motor.runLeft();
      break;
    case DOWN:
      motor.runDown();
      break;
    }
  }
}

void controlServo(void *p){
  while (true){
    xSemaphoreTake(servoSemaphore, portMAX_DELAY);
    servo.write();
  }
}
