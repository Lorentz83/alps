#include <CRC32.h>
#include <Adafruit_NeoPixel.h>
#include <FastLED.h>



// Switch this to Serial to test over USB instead of bluetoot
#define channel Serial3

#define debug(x) Serial.println(x)

const int led_pin = 6; 
const int led_pin_dma = 8; // Serial 2 on Teensy 4.0
const int numled = 144;


Adafruit_NeoPixel adafruit(numled, led_pin, NEO_GRB + NEO_KHZ800);

CRGB leds[numled]; // FastLED

#ifdef ARDUINO_TEENSY40

#include "WS2812Serial.h" // https://www.pjrc.com/non-blocking-ws2812-led-library/
byte drawingMemory[numled*3];         //  3 bytes per LED
DMAMEM byte displayMemory[numled*12]; // 12 bytes per LED
WS2812Serial dma_leds(numled, displayMemory, drawingMemory, led_pin_dma, WS2812_GRB);

#endif




struct DummyLibrary {
  void show() {
    // debug("show");
  };
  void setPixelColor(uint16_t, uint8_t, uint8_t, uint8_t) {
  }
} dummy;

struct FastLEDInterface {
  void show() {
    FastLED.show();
  };
  void setPixelColor(uint16_t n, uint8_t r, uint8_t g, uint8_t b) {
    leds[n].setRGB(r, g, b);
  }
} fastLEDWrapper;


void setup() {

  Serial.begin(115200);
  
#ifdef ARDUINO_TEENSY40
  Serial3.begin(1382400); // teensy
#else
  pinMode(15, INPUT_PULLUP);
  Serial3.begin(115200); // arduino
#endif

  adafruit.begin();
  
  FastLED.addLeds<WS2812, led_pin, GRB>(leds, numled);  // WS2812B?

  #if defined(ARDUINO_TEENSY40)
  dma_leds.begin();
  #endif
}


void loop() {
  // NOTE Only one of the following should be uncommented.
  
  // readOnly();
  // readOnlyBy3();

  // These requires the terminator to be aligned at the column.
  simulate(dummy);
  // simulate(adafruit);
  // simulate(fastLEDWrapper);
  // simulate(dma_leds); // Only with tennsy
  // simulateMultiCol(dma_leds); // Only with tennsy
}


CRC32 crc;
uint32_t num = 0;
byte buf3[3];
size_t bufIdx = 0;

#if defined(ARDUINO_TEENSY40)
void simulateMultiCol(WS2812Serial& library) {
  if ( channel.available() > 0 ) {
    byte b = channel.read();
    crc.update(b);
    num++;
    if (b == '\n' ) {
      uint32_t checksum = crc.finalize();
      crc.reset();
      // No show, here we assume we always get full columns, so we don't show twice.
      writeUInt32(num);
      writeUInt32(checksum);
      num = 0;
      bufIdx = 0;
    } else {
      buf3[bufIdx++] = b;
      if ( bufIdx == 3  ) { // we have a full pixel
        bufIdx = 0;
        int px = num/3 - 1; // zero index.
        px = px % numled;
        library.setPixelColor(px, buf3[0], buf3[1], buf3[2]);
        if (px == numled - 1) {
          library.show(); // with DMA this should be close to no time.
        }
      }
    }
  } 
}
#endif

template<class T>
void simulate(T& library) {
  if ( channel.available() > 0 ) {
    byte b = channel.read();
    crc.update(b);
    num++;
    if (b == '\n' ) {
      uint32_t checksum = crc.finalize();
      crc.reset();

      library.show(); // must be called AFTER reading the escabe but BEFORE sending the ack.
      
      writeUInt32(num);
      writeUInt32(checksum);
      num = 0;
      bufIdx = 0;
    } else {
      buf3[bufIdx++] = b;
      if ( bufIdx == 3  ) { // we have a full pixel
        bufIdx = 0;
        int px = num/3 - 1; // zero index.
        if ( px > numled ) {
          debug("ERROR pixel");
          debug(px);
        } else {
          library.setPixelColor(px, buf3[0], buf3[1], buf3[2]);
        }
      }
    }
  }
}

void readOnlyBy3() {
  size_t r = channel.readBytes(buf3, 3);
  // Here if r == 1 we have the terminator and we wait 1 full second before moving on.
  for ( size_t i = 0 ; i<r ; i++ ) {
    num++;
    crc.update(buf3[i]);
    if (buf3[i] == '\n' ) {
      uint32_t checksum = crc.finalize();
      crc.reset();
      writeUInt32(num);
      writeUInt32(checksum);
      num = 0;
    }
  }
}

void readOnly() {
  if ( channel.available() > 0 ) {
    uint8_t b = channel.read();
    crc.update(b);
    num++;
    if (b == '\n' ) {
      uint32_t checksum = crc.finalize();
      crc.reset();
      writeUInt32(num);
      writeUInt32(checksum);
      num = 0;
    }
  }
}

void writeUInt32(uint32_t val) {
    for ( int i = 0 ; i<4 ; i++) {
      channel.write(val & 0xFF);
      val = val >> 8;
    }
}
