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


#ifdef DMA_LED
  #include "WS2812Serial.h" 
#else 
  #include <Adafruit_NeoPixel.h>
#endif

template<int maxPixels>
class LedControl {

#ifdef DMA_LED
  byte displayMemory[maxPixels*12]; // 12 bytes per LED
  byte drawingMemory[maxPixels*3];  //  3 bytes per LED
  WS2812Serial pixels;

public: 
  LedControl(uint16_t pin) : pixels(maxPixels, displayMemory, drawingMemory, pin, WS2812_GRB) {
  }
  
  bool busy() {
    return pixels.busy();
  }

#else 
  Adafruit_NeoPixel pixels;

public:
  LedControl(uint16_t pin) : pixels(maxPixels, pin, NEO_GRB + NEO_KHZ800) {
  }
  
  bool busy() {
    return !pixels.canShow();
  }

#endif

  void init() {
    pixels.begin();
  }

  void show() {
    pixels.show();
  }

  void fill(uint8_t r, uint8_t g, uint8_t b) {
    for ( int i = 0 ; i < maxPixels ; i++ ) {
        pixels.setPixelColor(i, r, g, b);
    }
  }

  void off() {
    fill(0, 0, 0);
    pixels.show();
  }

  void setPixelColor(uint16_t pos, uint8_t r, uint8_t g, uint8_t b) {
    pixels.setPixelColor(pos, r, g, b);
  }

  void flashError() {
    fill(100, 0, 0);
    pixels.show();
    delay(500);
    fill(2, 0, 0);
    pixels.show();
    delay(500);
    fill(100, 0, 0);
    pixels.show();
    delay(500);
    fill(2, 0, 0);
    pixels.show();
    delay(500);
    fill(100, 0, 0);
    pixels.show();
    delay(500);
    off();
  }

  void flashInit() {
    fill(2, 0, 0);
    pixels.show();
    delay(200);
    fill(0, 2, 0);
    pixels.show();
    delay(200);
    fill(0, 0, 2);
    show();
    delay(200);
    off();
  }

  uint16_t numPixels() const {
    return maxPixels;
  }

};
