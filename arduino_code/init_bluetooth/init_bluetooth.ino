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

#define PULLUP_PIN 15

void setup()
{
  // Open serial communications and wait for port to open:
  Serial.begin(115200);
  
#ifdef PULLUP_PIN
  pinMode(PULLUP_PIN, INPUT_PULLUP); // only needed for  JY-MCU v1.06?
#endif

  btSerial.begin(btBaud);
  
  // 1 set to 1200bps
  // 2 set to 2400bps
  // 3 set to 4800bps
  // 4 set to 9600bps (Default)
  // 5 set to 19200bps
  // 6 set to 38400bps
  // 7 set to 57600bps
  // 8 set to 115200bps

  // TODO: other boards require different terminators. Make this configurable
  
  btSerial.print("AT+BAUD8");

  // TODO: make pin configurable
  btSerial.print("AT+PIN1234"); // Set the pin to 1234

  // TODO: make the name suffix configurable
  btSerial.print("AT+NAMEALPS-01"); // Set the name to ALPS-01
  
  // 
  btSerial.print("AT+VERSION");

  // Useful info at https://www.instructables.com/id/AT-command-mode-of-HC-05-Bluetooth-module/
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
