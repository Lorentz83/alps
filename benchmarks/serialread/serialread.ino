#include <CRC32.h>

void setup() {
  pinMode(15, INPUT_PULLUP);

  Serial.begin(9600);
  Serial3.begin(115200);
}

CRC32 crc;

uint32_t num = 0;

#define channel Serial3

void loop() {
  if ( channel.available() > 0 ) {
    uint8_t b = channel.read();
    crc.update(b);
    num++;
    if (b == '\n' ) {
      uint32_t checksum = crc.finalize();
      crc.reset();
      for ( int i = 0 ; i<4 ; i++) {
        channel.write(num & 0xFF);
        num = num >> 8;
      }
      for ( int i = 0 ; i<4 ; i++) {
        channel.write(checksum & 0xFF);
        checksum = checksum >> 8;
      }
      num = 0;
    }
  }
}
