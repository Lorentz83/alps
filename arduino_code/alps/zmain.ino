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



LedControl ledControl(NUMPIXELS, LED_STRIP_PIN);
Protocol<144, 1> protocol(&ledControl, cmdSerial);

// We want to have integers which can contain the image width (>255 pixels).
static_assert((sizeof(unsigned int) >= 2), "need at least 16 bit ints");

void setup() {

  // Serial initialization.
#ifdef DEBUG_SERIAL
  DEBUG_SERIAL.begin(115200);
  DEBUG_SERIAL.println("debug enabled");
#endif


#ifdef BT_PULLUP
  pinMode(15, INPUT_PULLUP);
  // This is my personal setup, should make it more generic
  cmdSerial.begin(115200); // 12 sec 144*x44
#else
  cmdSerial.begin(1382400); // 8.25 sec
#endif

  ledControl.init();
  ledControl.flashInit();
}



void loop() {
  protocol.checkChannel();
}
