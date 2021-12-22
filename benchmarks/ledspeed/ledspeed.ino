#include <Adafruit_NeoPixel.h>
#include <FastLED.h>


Adafruit_NeoPixel pixels(144, 6, NEO_GRB + NEO_KHZ800);

CRGB leds[144];


void setup() {
  pinMode(15, INPUT_PULLUP);

  Serial.begin(9600);
  Serial3.begin(1382400);

  FastLED.addLeds<WS2812, 6, GRB>(leds, 144);  // WS2812B?

  pixels.begin();
}

unsigned long time;
void loop() {
  time = millis();
  for (int i = 0; i<10; i++) slideFastLED();
  time = millis() - time;
  Serial.print("fastLED ");
  Serial.println(time);

  time = millis();
  for (int i = 0; i<10; i++) slideAdafruit();
  time = millis() - time;
  Serial.print("Adafruit ");
  Serial.println(time);
}

/*
Arduino mega ADK
fastLED 9962
Adafruit 2472
fastLED 9955
Adafruit 2471
fastLED 9965
Adafruit 2474
fastLED 9965
Adafruit 2472

Testing it with Teensy looks that the difference in speed is even wider, but controlling the LEDs 
with 3V is unreliable (sometimes I get random colors). So I don't trust too much these results.
I need to buy a logic level shifter before I can get meaningful results for the Teensy out of this benchmark.

But there are a couple of optimizations that can be done, I should investigate more in:
1. Parallel output https://github.com/FastLED/FastLED/wiki/Parallel-Output
2. Non blocking LED library https://www.pjrc.com/non-blocking-ws2812-led-library/

In the meantime, according to the 1st link:
> WS2812 strips are slow for writing data, with a data rate of just 800khz, it takes 30µs to write out a single led's worth of data. 
and from https://github.com/PaulStoffregen/WS2812Serial/issues/4
> wait 300us WS2812 reset time

Therefore these are hard limits:
 - 4.62 milliseconds for a column (144 pixels)
 - 665 milliseconds for a square (144x144)
 - 1.3 seconds for a wide image (288x144)

 */

void slideFastLED() {
  for ( int x = 0 ; x < 144 ; x++ ) {
    for ( int y = 0 ; y < 144 ; y++ ) {
        int pos = (x + y) % 144;
        leds[y].setRGB(pos == 10 ? 50 : 0, pos == 5 ? 50 : 0, pos == 0 ? 50 : 0);
    }
    FastLED.show();
  }  
}

void slideAdafruit() {
  for ( int x = 0 ; x < 144 ; x++ ) {
    for ( int y = 0 ; y < 144 ; y++ ) {
        int pos = (x - y) % 144;
        pixels.setPixelColor(y, pos == 0 ? 50 : 0, pos == 5 ? 50 : 0, pos == 10 ? 50 : 0);
    }
    pixels.show();
  }  
}
