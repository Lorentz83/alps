#include <CRC32.h>
#include <Adafruit_NeoPixel.h>

Adafruit_NeoPixel pixels(144, 6, NEO_GRB + NEO_KHZ800);

void setup() {
  pinMode(15, INPUT_PULLUP);

  Serial.begin(9600);
  Serial3.begin(115200);

  pixels.begin();
}

CRC32 crc;
uint32_t num = 0;
byte buf3[3];

#define channel Serial3

void loop() {
  simulateBy3();
}

void simulateBy3() {
  int r = channel.readBytes(buf3, 3);
  for ( int i = 0 ; i<r ; i++ ) {
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
  if (r != 3 && r != 0) {
    Serial.print("error ");
    Serial.println(r);
  }
  if (r == 3) {
    // r == 0 is a valid value when no data is in the buffer.
    int px = num/3;
    pixels.setPixelColor(px, buf3[0], buf3[1], buf3[2]); //
    if ( (px % 144) == 0 ) {
      pixels.show();
    }
  }
}

void readOnlyBy3() {
  int r = channel.readBytes(buf3, 3);
  for ( int i = 0 ; i<r ; i++ ) {
    num++;
    crc.update(buf3[i]);
    if (buf3[i] == '\n' ) {
      uint32_t checksum = crc.finalize();
      crc.reset();
      writeUInt32(num);
      writeUInt32(checksum);
      Serial.println(checksum);
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
