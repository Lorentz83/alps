/**
    Copyright 2020-2021 Lorenzo Bossi

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

#include <CRC32.h>

// Status is used together the [[nodiscard]] attribute to generate warnings when an error is ignored.
class Status {};

template<int maxPixels, int maxCols>
class ColumnBuffer {
    byte buf[maxCols][maxPixels*3];
    size_t currWCol;
    size_t currRCol;
    bool isEmpty;

  public:
    ColumnBuffer() {
      reset();
    }

    void reset() {
      currWCol = 0;
      currRCol = 0;
      isEmpty = true;
    }

    bool canWrite() {
      return isEmpty || currWCol != currRCol;
    }

    bool canRead() {
      return !isEmpty;
    }

    byte* readNextColumn() {
      byte* r = buf[currRCol];
      currRCol = ( currRCol + 1 ) % maxCols;
      isEmpty = currRCol == currWCol;
      return r;
    }

    byte* writeBuffer() {
      return buf[currWCol];
    }

    void advanceColumn() {
      currWCol = ( currWCol + 1 ) % maxCols;
      isEmpty = false;
    }
};

template<int maxPixels, int maxCols>
class Protocol {

    enum message {
      noCommand = 0, // Never sent on the wire, just represents we are currently handling nothing.
      info = '?',
      off   = 'o',
      newImage = 'n',
      continueImage = 'c',
    };

    CRC32 crc;

    LedControl<maxPixels> *callbacks;
    Stream &io;

    // Buffers.
    static const int blen = 5; // This is the maximum size of a header.
    byte buf[blen];
    ColumnBuffer<maxPixels, maxCols> colBuf;

    // Protocol related
    byte currentMessage = noCommand;

    uint8_t pxPerCol = 0;
    uint8_t delayBetweenCols = 0;
    uint8_t numCols = 0;
    bool lastBatchOfCols = false;


    [[nodiscard]] Status error(const char code[4]) { // TODO it would be nice to validate the size at compile time
      buf[0] = 'k';
      for ( int i = 0 ; i < 4 ; i++ ) {
        buf[i + 1] = code [i];
      }
      io.write(buf, 5);

      // flush input
      int b = io.available();
      while (b-- > 0)
        io.read();

      // reset processing message.
      currentMessage = noCommand;

      return Status{};
    }

    [[nodiscard]] Status ack() {
      buf[0] = 'o';
      
      uint32_t v = crc.finalize();
      buf[1] = v & 0xFF;
      v >>= 8;
      buf[2] = v & 0xFF;
      v >>= 8;
      buf[3] = v & 0xFF;
      v >>= 8;
      buf[4] = v & 0xFF;
      
      io.write(buf, 5);
      return Status{};
    }

    size_t waitAndCRCBytes(byte *buf, size_t len) {
      size_t r = io.readBytes(buf, len);
      crc.update(buf, r);
      return r;
    }

    Status handleInfo() {
      buf[0] = '!';
      buf[1] = 0; // no extensions.
      buf[2] = 0; // not used.
      buf[3] = maxPixels;
      buf[4] = maxCols;

      io.write(buf, 5);
      currentMessage = noCommand;
      return Status{};
    }

    Status handleOff() {
      if ( waitAndCRCBytes(buf, 4) != 4 ) {
        return error("noof");
      }
      callbacks->off();
      currentMessage = noCommand;
      return ack();
    }

    Status handleNewImage() {
      if ( waitAndCRCBytes(buf, 3) != 3 ) {
        return error("nada");
      }

      pxPerCol = buf[0];
      delayBetweenCols = buf[1];
      numCols = buf[2];
      lastBatchOfCols = false;

      return handleColumn();
    }

    Status handleContinueImage() {
      if ( waitAndCRCBytes(buf, 2) != 2 ) {
        return error("noci");
      }

      lastBatchOfCols = buf[0];
      numCols = buf[1];

      return handleColumn();
    }

    // handleColumn reads and writes columns, depeding on what's available now.
    Status handleColumn() {
      // Do I have data in the stream to read and buffer to put it into?
      if ( numCols > 0 && colBuf.canWrite() ) {
        byte* buf = colBuf.writeBuffer();
        // For now let's read it in full. I'm not sure that reading it in pieces can increase performances.
        size_t want = pxPerCol * 3;
        if ( waitAndCRCBytes(buf, want) != want ) {
          numCols = 0;
          colBuf.reset();
          return error("noco");
        }
        numCols--;
        colBuf.advanceColumn();
      }

      // Do I have data in the buffer to show on the stick?
      if ( !callbacks->busy() && colBuf.canRead() ) { // TODO use delayBetweenCols.
        byte* buf = colBuf.readNextColumn();
        for ( int n = 0 ; n < pxPerCol ; n++ ) {
          byte r = *buf;
          buf++;
          byte g = *buf;
          buf++;
          byte b = *buf;
          buf++;
          callbacks->setPixelColor(n, r, g, b);
        }

        // We assume that the last image called off correctly, hence the rest of pixels should be already black.
        callbacks->show();
      }

      // If nothing left on the wire and all the columns has been sent to the stick.
      if ( numCols == 0 && !colBuf.canRead() ) {
        if ( lastBatchOfCols ) {
          // TODO add delay before turning off.
          callbacks->off();
        }
        currentMessage = noCommand; // We can wait for the new command.
        return ack();
      }
      // Otherwise let's wait the next cycle.
      return Status{};
    }

  public:

    Protocol(LedControl<maxPixels> *callbacks, Stream& io): callbacks(callbacks), io(io), colBuf() {
      // NOTE: here streams are not initialized yet.

      static_assert(maxPixels > 0, "maxPixels must be positive");
      static_assert(maxPixels < 255, "maxPixels must fit in a byte");
      static_assert(maxCols > 0, "maxCols must be positive");
      static_assert(maxCols < 255, "maxCols must fit in a byte");
    }

    void checkChannel() {

      if ( currentMessage == noCommand ) { // We are waiting for something do do.
        if ( io.available() == 0 ) {
          return; // nothing to do.
        }
        currentMessage = io.read();
        
        crc.reset();
        crc.update(currentMessage);

        debug("CMD_%c", currentMessage);
        switch (currentMessage) {
          case off:
            handleOff();
            break;
          case info:
            handleInfo();
            break;
          case newImage:
            handleNewImage();
            break;
          case continueImage:
            handleContinueImage();
            break;
          default:
            currentMessage = noCommand;
            (void) error("cmdE"); // Ignore [[nodiscard]]
        }
      } else { // Let's check if we were doing something.
        switch (currentMessage) {
          case noCommand:
            return; // Nothing to do.
          case newImage:
          // fallthrough;
          case continueImage:
            handleColumn();
            break;
          default:
            debug("NO_CONTINUE_%c", currentMessage);
            (void) error("cntE"); // Ignore [[nodiscard]]
        }
      }
    };
};
