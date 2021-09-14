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


#include <SPI.h>
#include <SD.h>

// Files are concatenated by filename.
// This file contains arduino specific configuration and globals.


// cmdSerial is the serial line to use to receive commands.
// dbgSerial is the serial line to send debug messages (can be null).
// Depending on the arduino model, the type can be either HardwareSerial or Serial_

HardwareSerial *cmdSerial = &Serial3;
HardwareSerial *dbgSerial = NULL;

//Serial_ *cmdSerial = &Serial;
//Serial_ *dbgSerial = NULL;

// Should enable the pullup resistor for the bluetooth device?
// See https://forum.arduino.cc/index.php?topic=280928.0
// probably only required for JY-MCU v1.06 board.
// without this you can transmit but not receive.
#define BT_PULLUP

// Where the led strip is connected.
#define LED_STRIP_PIN 6

// How long is the led strip.
#define NUMPIXELS 144


// Which pis is the SD connected?
// TODO make this optional?

#define SD_CS_PIN 46
// #define SD_CS_PIN 10


// Which pin is the HW button connected
#define BUTTON_PIN 9
