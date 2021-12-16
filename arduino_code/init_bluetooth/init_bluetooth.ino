/**
 *  Copyright 2020 Lorenzo Bossi
 *
 *  This file is part of ALPS (Another Light Painting Stick).
 *
 *  ALPS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ALPS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with ALPS.  If not, see <https://www.gnu.org/licenses/>.
 */



// WARNING: SoftwareSerial seems unreliable.

#define btSerial Serial3

// TODO: fix this for arduino with only 1 serial.

#define btBaud 9600

// Some bluetooth modules require the pullup resistor on the arduino RX pin.
// This is probably required for the JY-MCU v1.06 bluetooth board.
// More information at https://forum.arduino.cc/index.php?topic=280928.0

//#define PULLUP_PIN 15

void setup()
{
  // Open serial communications and wait for port to open:
  Serial.begin(115200);
  
#ifdef PULLUP_PIN
  pinMode(PULLUP_PIN, INPUT_PULLUP); // only needed for  JY-MCU v1.06?
#endif

  btSerial.begin(btBaud);
  Serial.println("ready");
}

void loop()
{
  if (btSerial.available())
  {
    Serial.write(btSerial.read());
  }
  if (Serial.available())
  {
      btSerial.write(Serial.read());
  }
}

/**
To run manually in the serial console.

HC-06 JY-MCU v1.06, slow connection tested with Arduino:
no line terminator
- set name
AT+NAMEALPS-01
- set pin
AT+PIN1234
- set speed at 115200bps
AT+BAUD8

HC-05, faster connection with Teensy
keeping the button pressed and using the line terminator \r\n
- set name
AT+NAME:ALPS-02
- set pin
AT+PIN:1234
- set speed at 1382400bps http://www.techbitar.com/uploads/2/0/3/1/20316977/hc-05_at_commands.pdf
AT+UART=1382400,0,0


Both to check version:
AT+VERSION

more info at https://www.instructables.com/id/AT-command-mode-of-HC-05-Bluetooth-module/
*/
