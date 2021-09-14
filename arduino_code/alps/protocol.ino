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


#include <stdarg.h>

class Protocol {

    enum message {
      off   = 'o',
      column = 'c',
      ready = 'r',
      //
      upload = 'u',
      reshow = 'w',
      settings = 's',
    };

    LedControl *callbacks;
    Stream *io;
    Stream *ds;

    const unsigned int maxPixels;
    const size_t blen;
    byte *buf;

    static const int olen = 5;
    char obuf[olen + 1]; // for the \0 in snprint

    // settings
    unsigned long sDelay = 0;
    uint8_t sBrightness = 0;
    byte sLoop = 0;

    void errorf(int line, String msg);
    void debugf(int line, const char* msg, ...);
    void ackf(int line);

    int readByte();

    void handleOff();
    void handleReady();
    void handleColumn();
    void handleUpload();
    void handleReshow();
    void handleSettings();
    
  public:
    Protocol(LedControl *callbacks, Stream* io, Stream* debug);
    ~Protocol();
    void checkChannel();
    int doReshow();
};

void Protocol::debugf(int line, const char* msg, ...) {
  static const size_t debugBufLen = 40;
  static char debugbuf[debugBufLen];
  
  if (!ds) {
    return;
  }

  va_list ap;
  va_start( ap, msg );
  vsnprintf(debugbuf, debugBufLen, msg, ap);
  va_end(ap);

  ds->print(line, DEC);
  ds->write(' ');
  ds->println(debugbuf);
}

void Protocol::errorf(int line, String msg) {
  snprintf(obuf, olen + 1, "k%04d", line);
  io->write(obuf, olen);

  debugf(line, msg.c_str());

  // flush input
  int b = io->available();
  while (b-- > 0)
    io->read();
}

void Protocol::ackf(int line) {
  snprintf(obuf, olen + 1, "o%04d", line);
  io->write(obuf, olen);
}


#define debug(...) debugf(__LINE__, __VA_ARGS__)
#define error(msg) errorf(__LINE__, msg)
#define ack() ackf(__LINE__)

Protocol::Protocol(LedControl *callbacks, Stream* io, Stream* ds): callbacks(callbacks), io(io), ds(ds), maxPixels(callbacks->numPixels()), blen(maxPixels * 3) {
  // NOTE: here streams are not initialized yet.
  
  buf = (byte*) malloc( sizeof(*buf) * blen );
}

Protocol::~Protocol() {
  free(buf);  
}

int Protocol::readByte() {
  int r = io->readBytes(buf, 1);
  if (r != 1)
    return -1;
  return buf[0];
}

void Protocol::handleReady() {
  if (readByte() != 2) {
    error("unexpected ready message");
    return;
  }
  if (callbacks->isButtonPressed()) {
    error("not ready");
  } else {
    ack();
  }
}

void Protocol::handleOff() {
  if (readByte() != 2) {
    error("unexpected off message");
    return;
  }
  callbacks->off();
  ack();
}

void Protocol::handleColumn() {
  int pixels = readByte();
    
  if ( (unsigned int)pixels > maxPixels ) {
    error("column too long");
  }

  for (int pixelPos = 0; pixelPos < pixels; pixelPos ++) {
    int r = io->readBytes(buf, 3);
    if (r != 3) {
      debug("incomplete pixel %d: %d", pixelPos, r);
      error("incomplete pixel");
      break;
    }
    callbacks->setPixelColor(pixelPos, buf[0], buf[1], buf[2]);
  }
  callbacks->show();
  ack();
}

void Protocol::handleUpload() {
  File myFile = SD.open("ledcode.txt", O_READ | O_WRITE | O_CREAT | O_TRUNC);
  if (!myFile) {
    error("cannot open file");
    return;
  }

  size_t r = io->readBytes(buf, 3);
  if ( r != 3 ) {
    error("wrong upload header");
  }
  
  byte h = buf[0];
  if ( h > maxPixels ) {
    error("image too high");
  }

  unsigned int w = ( ((unsigned int)buf[1]) << 8) + buf[2];

  if ( myFile.write(buf, 3) != 3 ) {
    error("cannot write file");
  }

  ack();

  size_t toRead = h * 3;

  for ( byte x = 0 ; x < w ; x++ ) {
    r = io->readBytes(buf, toRead);
    if (r != toRead) {
      debug("incomplete col %d read %d bytes", x, r);
      error("incomplete pixel");
      myFile.close();
      return;
    }
    if ( myFile.write(buf, toRead) != toRead) {
      error("cannot write file");
      myFile.close();
      return;
    }

    myFile.flush();
    debug("up ack %d col", x);
    ack();
  }

  myFile.close();
}

void Protocol::handleReshow() {
  if ( !doReshow() ) {
    error("error reading file");
  } else {
    ack();
  }
}

void Protocol::handleSettings() {
  size_t r = io->readBytes(buf, 3);
  if ( r != 3 ) {
    error("cannot read settings");
    return;
  }

  sDelay = buf[0] * 10;
  sBrightness = buf[1] + 1;
  sLoop = buf[2];

  ack();
}

// This function can be triggered from a button, so it shouldn't be part of the communication protocol.
int Protocol::doReshow() {
  File myFile = SD.open("ledcode.txt");
  if (!myFile) {
    debug("cannot open file");
    callbacks->flashError();
    return 0;
  }

  unsigned int h = myFile.read();
  unsigned int wHi = myFile.read();
  unsigned int wLow = myFile.read();
  unsigned int w = (wHi << 8) + wLow;

  do {
    myFile.seek(3);
    for ( unsigned int x = 0 ; x < w ; x++ ) {
      if (callbacks->isButtonPressed()) {
        goto exitLoop;
      }
      for ( unsigned int y = 0 ; y < h ; y++ ) {
        int r = myFile.readBytes(buf, 3);
        if (r != 3) {
          debug("incomplete file %dx%d: %d", x, y, r);
          myFile.close();
          callbacks->flashError();
          return 0;
        }

        uint8_t rr = buf[0];
        uint8_t gg = buf[1];
        uint8_t bb = buf[2];

        if ( sBrightness ) {
          rr = (rr * sBrightness) >> 8;
          gg = (gg * sBrightness) >> 8;
          bb = (bb * sBrightness) >> 8;
        }
        
        callbacks->setPixelColor(y, rr, gg, bb);
      }
      callbacks->show();
      
      delay(sDelay);
    }
  } while( sLoop );

exitLoop:

  myFile.close();
  callbacks->off();

  return 1;
}

void Protocol::checkChannel() {
  static int last = -2;

  int b = io->read();
  if (b != last) {
    debug("COOMMAND '%c' (%d) avail=%d", b, b, io->available());
    last = b;
  }
  switch (b) {
    case ready:
      handleReady();
      break;
    case off:
      handleOff();
      break;
    case column:
      handleColumn();
      break;
    case upload:
      handleUpload();
      break;
    case reshow:
      handleReshow();
      break;
    case settings:
      handleSettings();
      break;
    case -1:
      // nothing to read.
      break;
    default:
      error("unexpected message");
  }
}

#undef error
#undef ack
#undef debug
