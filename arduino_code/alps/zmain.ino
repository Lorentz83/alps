#include <Adafruit_NeoPixel.h>

// Which pin on the Arduino is connected to the NeoPixels?
#define PIN        6


#define NUMPIXELS 150
Adafruit_NeoPixel pixels(NUMPIXELS, PIN, NEO_GRB + NEO_KHZ800);

#define BUTTON 12

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(BUTTON, INPUT_PULLUP);

  Serial.begin(115200);

  Serial3.begin(115200);
  // https://forum.arduino.cc/index.php?topic=280928.0
  pinMode(15, INPUT_PULLUP); // only needed for  JY-MCU v1.06?

  pixels.begin(); // INITIALIZE NeoPixel strip object (REQUIRED)

  pixels.fill(pixels.Color(2, 0, 0));
  pixels.show();
  delay(100);
  pixels.fill(pixels.Color(0, 2, 0));
  pixels.show();
  delay(100);
  pixels.fill(pixels.Color(0, 0, 2));
  pixels.show();
  delay(100);
  pixels.fill(pixels.Color(0, 0, 0));
  pixels.show();
}


class LedCallbacks: public Callbacks {
  public:
    virtual void show() {
      pixels.show();
    }
    virtual void off() {
      pixels.fill(pixels.Color(0, 0, 0));
      pixels.show();
    }
    virtual bool isButtonPressed() {
      return digitalRead(BUTTON) == HIGH;
    }
    virtual void setPixelColor(int pos, byte r, byte g, byte b) {
      pixels.setPixelColor(pos, pixels.Color(r, g, b));
    }
} callbacks;

//Protocol protocol(&callbacks, &Serial, &Serial3);
//Protocol protocol(&callbacks, &Serial, NULL);

//Protocol protocol(&callbacks, &Serial3, &Serial);
Protocol protocol(&callbacks, &Serial3, NULL);


void loop() {
  protocol.checkChannel();
}
