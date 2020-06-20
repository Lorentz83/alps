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


#include <Adafruit_NeoPixel.h>

class LedControl {
    int lastButtonStatus = HIGH;
    Adafruit_NeoPixel pixels;
    
  public:

    LedControl(uint16_t numPixels, uint16_t pin) : pixels(numPixels, pin, NEO_GRB + NEO_KHZ800) {
    }

    void init() {
      pixels.begin();
    }

    void show() {
      pixels.show();
    }

    void off() {
      pixels.fill(pixels.Color(0, 0, 0));

//      // TODO remove this
//      setPixelColor(1, 255, 0, 0);
//      setPixelColor(2, 0, 255, 0);
//      setPixelColor(3, 0, 0, 255);

      pixels.show();
    }

    bool isButtonPressed() {
      // TODO add deghosting.
      int now = digitalRead(BUTTON_PIN);
      int ret = (now == LOW) && (lastButtonStatus == HIGH);
      lastButtonStatus = now;
      return ret;
    }

    void setPixelColor(int pos, byte r, byte g, byte b) {
      pixels.setPixelColor(pos, pixels.Color(r, g, b));
    }

    void flashError() {
      pixels.fill(pixels.Color(100, 0, 0));
      pixels.show();
      delay(500);
      pixels.fill(pixels.Color(2, 0, 0));
      pixels.show();
      delay(500);
      pixels.fill(pixels.Color(100, 0, 0));
      pixels.show();
      delay(500);
      pixels.fill(pixels.Color(2, 0, 0));
      pixels.show();
      delay(500);
      pixels.fill(pixels.Color(100, 0, 0));
      pixels.show();
      delay(500);
      off();
    }

    void flashInit() {
      pixels.fill(pixels.Color(2, 0, 0));
      pixels.show();
      delay(200);
      pixels.fill(pixels.Color(0, 2, 0));
      pixels.show();
      delay(200);
      pixels.fill(pixels.Color(0, 0, 2));
      pixels.show();
      delay(200);
      off();
    }

    uint16_t numPixels() const {
      return pixels.numPixels();
    }
};
