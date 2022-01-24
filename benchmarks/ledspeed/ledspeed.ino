#include <Adafruit_NeoPixel.h>
#include <FastLED.h>


#define testMillis // comment this to measure time from PC. Use `cd timer && go run timer.go`


const int led_pin = 6; 
const int led_pin_dma = 8; // Serial 2 on Teensy 4.0
const int numled = 144;


Adafruit_NeoPixel pixels(numled, led_pin, NEO_GRB + NEO_KHZ800);

CRGB leds[numled];


#ifdef ARDUINO_TEENSY40

#include "WS2812Serial.h" // https://www.pjrc.com/non-blocking-ws2812-led-library/
byte drawingMemory[numled*3];         //  3 bytes per LED
DMAMEM byte displayMemory[numled*12]; // 12 bytes per LED
WS2812Serial dma_leds(numled, displayMemory, drawingMemory, led_pin_dma, WS2812_GRB);

#endif


void setup() {
  // Serial.begin(9600);
  Serial.begin(115200);

  FastLED.addLeds<WS2812, led_pin, GRB>(leds, 144);  // WS2812B?

  pixels.begin();

  #if defined(ARDUINO_TEENSY40)
  dma_leds.begin();
  #endif
}

#ifdef testMillis
#define printMillis() Serial.println(time)
#else
#define printMillis()
#endif

unsigned long time;
void loop() {
  
  Serial.print('f');
  time = millis();
  for (int i = 0; i<10; i++) slideFastLED();
  time = millis() - time;
  Serial.print('.');
  printMillis();

  Serial.print('a');
  time = millis();
  for (int i = 0; i<10; i++) slideAdafruit();
  time = millis() - time;
  Serial.print('.');
  printMillis();

  #if defined(ARDUINO_TEENSY40)

  Serial.print('d');
  time = millis();
  for (int i = 0; i<10; i++) slideDMA();
  time = millis() - time;
  Serial.print('.');
  printMillis();

  #endif
}

/*

WARNING: millis() is not incremented during the interrupt functions (https://www.arduino.cc/reference/en/language/functions/external-interrupts/attachinterrupt/).

Testing on Arduino MEGA ADK

with millis
f.8542
a.2470
f.8541
a.2469

Wall clock:
fastLED 8.405086106s
adafruit 7.213265824s
fastLED 8.392739761s
adafruit 7.212967165s


Testing it with Teensy, a logic level shifter is required otherwise the LED strip sometime behaves weirdly. 

Testing with millis let us know the time spent when intterrupts are enabled,
which should be the time spent while NOT using CPU to send data ~ time to process data + time to wait between columns.

f.7724
a.1920
d.6649

f.7724
a.1920
d.6649

f.7724
a.1920
d.6649


Checking the wall clock monitoring the serial port from the computer:

fastLED 7.728041386s
adafruit 6.715214155s
DMA 6.649549581s

fastLED 7.727930343s
adafruit 6.715443377s
DMA 6.649588857s

fastLED 7.728037941s
adafruit 6.715460754s
DMA 6.649607675s

fastLED 7.727986571s
adafruit 6.715514328s
DMA 6.649302187s

fastLED is the slowest option on Teensy, which is probably because we have to add the delay(1) in the loop.
Which means that in a real case scenario where we have to spend time between reading columns it may be faster.
But it cannot be faster than DMA where we see that time millis is pretty much the same wall clock we measure,
meaning that we don't use CPU at all and we can spend all that CPU time doing other stuff.

After this, the only further improvement is to split the LED strip in 2 and try 
1. Parallel output https://github.com/FastLED/FastLED/wiki/Parallel-Output

Theoretically, according to https://github.com/FastLED/FastLED/wiki/Parallel-Output:
> WS2812 strips are slow for writing data, with a data rate of just 800khz, it takes 30µs to write out a single led's worth of data. 
and from https://github.com/PaulStoffregen/WS2812Serial/issues/4
> wait 300us WS2812 reset time

Therefore these are hard limits:
 - 4.62 milliseconds for a column (144 pixels)
 - 665 milliseconds for a square (144x144)
 - 1.3 seconds for a wide image (288x144)

The test sends 10 144x144 images, therefore the best we could get from this benchmark is 6.65 seconds,
which is pretty much what we see with adafruit and DMA using tennsy 4.

 */


#if defined(ARDUINO_TEENSY40)
void slideDMA() {
  for ( int x = 0 ; x < numled ; x++ ) {
    for ( int y = 0 ; y < numled ; y++ ) {
        int pos = (x - y) % numled;
        dma_leds.setPixelColor(y, pos == 0 ? 50 : 0, pos == 5 ? 50 : 0, pos == 10 ? 50 : 0);
    }
    dma_leds.show();
  }  
}
#endif

void slideFastLED() {
  for ( int x = 0 ; x < numled ; x++ ) {
    for ( int y = 0 ; y < numled ; y++ ) {
        int pos = (x - y) % numled;
        leds[y].setRGB(pos == 0 ? 50 : 0, pos == 5 ? 50 : 0, pos == 10 ? 50 : 0);
    }
    FastLED.show();
    #if defined(ARDUINO_TEENSY40)
    // Required on Teensy https://www.reddit.com/r/FastLED/comments/r9pq6o/odd_behaviors_on_teensy4_using_fastled/
    delay(1);
    #endif
  }  
}

void slideAdafruit() {
  for ( int x = 0 ; x < numled ; x++ ) {
    for ( int y = 0 ; y < numled ; y++ ) {
        int pos = (x - y) % numled;
        pixels.setPixelColor(y, pos == 0 ? 50 : 0, pos == 5 ? 50 : 0, pos == 10 ? 50 : 0);
    }
    pixels.show();
  }  
}
