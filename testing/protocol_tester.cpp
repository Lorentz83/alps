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

#include <bitset>
#include <chrono>
#include <condition_variable>
#include <iostream>
#include <mutex>
#include <queue>
#include <stdarg.h>
#include <string>
#include <thread>
#include <zlib.h>

// Command line flags.
bool debug_to_stderr = false;
bool pixels_to_stderr = false;

// A byte stores an 8-bit unsigned number, from 0 to 255.
// https://www.arduino.cc/reference/en/language/variables/data-types/byte/
using byte = uint8_t;

using String = std::string;

enum PrintFormat { BIN, OCT, DEC, HEX };

void delay(int) {}

long int millis() {
  using namespace std::chrono;
  return duration_cast<milliseconds>(system_clock::now().time_since_epoch())
      .count();
}

class NonBlockingInput {
  std::istream *in;
  std::thread t;

  std::mutex m;
  std::queue<char> q;

  std::condition_variable data_avail;
  std::mutex data_wait;

public:
  NonBlockingInput(std::istream *stream) : in(stream) {
    // It is not the best option, but there is no easy way to do
    // non blocking input using the standard library and this is
    // just a test helper.
    // https://stackoverflow.com/questions/6171132/non-blocking-console-input-c
    t = std::thread([&]() {
      while (1) {
        int c = in->get();
        if (c == std::char_traits<char>::eof()) {
          break;
        }
        std::scoped_lock lock(m, data_wait);
        q.push(c);
        data_avail.notify_all();
      }
    });
    t.detach();
  }

  // Returns how many bytes are available.
  int available() {
    std::lock_guard<std::mutex> l(m);
    return q.size();
  }

  // Reads a single byte or -1 if nothing is available.
  int read() {
    std::lock_guard<std::mutex> l(m);
    if (q.empty())
      return -1;
    int el = q.front();
    q.pop();
    return el;
  }

  // Reads as many bytes are available up to len.
  int readBytes(byte *buf, int len) {
    std::lock_guard<std::mutex> l(m);
    int i;
    for (i = 0; i < len && q.size() > 0; i++) {
      *buf = q.front();
      q.pop();
      buf++;
    }
    return i;
  }

  // Tries to read exactly len bytes waiting at most timeout time.
  int readBytes(byte *buf, int len, std::chrono::milliseconds timeout) {
    auto deadline = std::chrono::system_clock::now() + timeout;

    int read = 0;
    while (1) {
      std::unique_lock<std::mutex> l(data_wait);
      int n = readBytes(buf, len);
      read += n;
      buf += n;
      len -= n;

      if (len == 0) {
        return read;
      }

      if (data_avail.wait_until(l, deadline) == std::cv_status::timeout) {
        return read;
      }
    }
  }
};

class Stream {
  NonBlockingInput *in;
  std::ostream *out;

public:
  Stream() : out(NULL), in(NULL) {}
  Stream(std::ostream *out) : in(NULL), out(out) {}
  Stream(std::istream *in) : out(NULL) { this->in = new NonBlockingInput(in); }
  Stream(std::istream *in, std::ostream *out) : out(out) {
    this->in = new NonBlockingInput(in);
  }
  ~Stream() {
    if (in != NULL)
      delete in;
  }

  // output.
  void print(int n, PrintFormat f) {
    switch (f) {
    case BIN:
      *out << std::bitset<8>(n);
      return;
    case OCT:
      *out << std::oct;
    case HEX:
      *out << std::hex;
    case DEC:
    default:
      *out << std::dec;
    }
    *out << n << std::dec;
  }

  void write(byte c) {
    out->put(c);
    out->flush();
  }

  void write(const byte *buf, int size) {
    out->write((const char *)buf, size);
    out->flush();
  }

  void println(const char *str) { *out << str << '\n'; }

  // input.
  int available() { return in->available(); }

  int read() { return in->read(); }

  int readBytes(byte *buf, int len) {
    // The default timeout in arduino is 1000 milliseconds.
    using namespace std::chrono_literals;
    return in->readBytes(buf, len, 1000ms);
  }
};

struct File : public Stream {

  bool operator!() { return true; }

  int write(const byte *buf, int len) { return len; }

  void close() {}

  void flush() {}

  void seek(int) {}
};

enum FileFormat {
  O_READ = 1 << 0,
  O_WRITE = 1 << 1,
  O_CREAT = 1 << 2,
  O_TRUNC = 1 << 3,
};

struct SDType {
  File open(const char *name, int mode = O_READ) { return File{}; }
} SD;

template <int maxPixels> struct LedControl {

  void init() {}

  void show() {
    if (!pixels_to_stderr)
      return;

    std::cerr << "SHOW";
  }

  void off() {
    if (!pixels_to_stderr)
      return;

    std::cerr << "OFF";
  }

  void setPixelColor(uint16_t pos, uint8_t r, uint8_t g, uint8_t b) {
    if (!pixels_to_stderr)
      return;
    std::cerr.put(r);
    std::cerr.put(g);
    std::cerr.put(b);
  }

  void flashError() {
    if (!pixels_to_stderr)
      return;
    std::cerr << "FLASH_ERROR";
  }

  void flashInit() {}

  uint16_t numPixels() const { return maxPixels; }

  bool busy() { return false; }
};

void debug(const char *msg, ...) {
  if (!debug_to_stderr)
    return;

  static const size_t debugBufLen = 40;
  static char debugbuf[debugBufLen];

  va_list ap;
  va_start(ap, msg);
  vsnprintf(debugbuf, debugBufLen, msg, ap);
  va_end(ap);

  std::cerr << debugbuf << std::endl;
}

#include "../arduino_code/alps/protocol.ino"

using namespace std;

#ifndef TESTING_COLS // Allow override from g++ cli.
#define TESTING_COLS 1
#endif

int main(int argc, char *argv[]) {

  for (int i = 1; i < argc; i++) {
    if (string("--debug_to_stderr") == argv[i]) {
      debug_to_stderr = true;
    }
    if (string("--pixels_to_stderr") == argv[i]) {
      pixels_to_stderr = true;
    }
  }

  LedControl<144> lc;
  Stream io(&std::cin, &std::cout);

  Protocol<144, TESTING_COLS> protocol(&lc, io);
  while (true) {
    protocol.checkChannel();
  }
}
