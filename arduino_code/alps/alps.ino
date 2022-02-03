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

// ============= CONFIGURATION STARTS HERE ===================

// Uncomment to enable debug over Serial
// #define DEBUG_SERIAL Serial

// cmdSerial is the serial line to use to receive commands.
auto &cmdSerial = Serial3;

#ifdef ARDUINO_TEENSY40 // Probably Tennsy 3.x and 4.1 works too, I just don't have any to try.

  #define BT_SERIAL_SPEED 1382400 // Faster speed for Teensy.
  #define MAX_COL_TRANSFER 6      // If more than 1, DMA_LED must be enabled.

#else // fallback to Arduino

  #define BT_SERIAL_SPEED 115200 // Slower speed safe for Arduino.
  #define MAX_COL_TRANSFER 1     // We need DMA for this.

#endif


// Should enable the pullup resistor for the bluetooth device?
// See https://forum.arduino.cc/index.php?topic=280928.0
// probably only required for JY-MCU v1.06 board.
// If your bluetooth can transmit but not receive try enabling this.
// It is the pin number of Serial3 RX. If you changed cmdSerial you should update this too.
// #define BT_PULLUP 15

// Where the led strip is connected.
// If DMA_LED is used, check the supported pin at https://github.com/PaulStoffregen/WS2812Serial
const uint16_t LED_STRIP_PIN = 8;

// How long is the led strip.
const uint16_t NUM_PIXELS = 144;

// ============= SAFE DEFAULTS =================
// This section is still configuration, but it contains safe defaults that can be inferred by the previus config.

#if MAX_COL_TRANSFER > 1
  
  // Use the WS2812Serial library for faster operation (only for Teensy).
  #define DMA_LED

#endif

// ============= CONFIGURATION ENDS HERE ===================

// Debug helper.

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
