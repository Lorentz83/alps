/**
    Copyright 2020 Lorenzo Bossi

    This file is part of ALPS (Another Light Painting Stick).

    ALPS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    ALPS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with ALPS.  If not, see <https://www.gnu.org/licenses/>.
*/

// Files are concatenated by filename.
// This file contains arduino specific configuration and globals.


// cmdSerial is the serial line to use to receive commands.
// dbgSerial is the serial line to send debug messages (can be null).
// Depending on the arduino model, the type can be either HardwareSerial or Serial_

auto &cmdSerial = Serial3;

// Uncomment to enable debug over Serial
// #define DEBUG_SERIAL Serial

//Serial_ *cmdSerial = &Serial;


// Should enable the pullup resistor for the bluetooth device?
// See https://forum.arduino.cc/index.php?topic=280928.0
// probably only required for JY-MCU v1.06 board.
// without this you can transmit but not receive.
//#define BT_PULLUP

// Where the led strip is connected.
const uint16_t LED_STRIP_PIN = 8;

// How long is the led strip.
const uint16_t NUMPIXELS = 144;


#ifdef DEBUG_SERIAL
#include <stdarg.h>

void debugf(int line, const char* msg, ...) {
  static const size_t debugBufLen = 40;
  static char debugbuf[debugBufLen];

  va_list ap;
  va_start( ap, msg );
  vsnprintf(debugbuf, debugBufLen, msg, ap);
  va_end(ap);

  DEBUG_SERIAL.print(line, DEC);
  DEBUG_SERIAL.write(' ');
  DEBUG_SERIAL.println(debugbuf);
}

#define debug(...) debugf(__LINE__, __VA_ARGS__)

#else

#define debug(...)

#endif
