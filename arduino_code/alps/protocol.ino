
class Callbacks {
  public:
    virtual void show() = 0;
    virtual void off() = 0;
    virtual bool isButtonPressed() = 0;
    virtual void setPixelColor(int pos, byte r, byte g, byte b) = 0;
};

class Protocol {

    enum message {
      off   = 'o',
      show  = 's',
      pixel = 'p',
      ready = 'r',
    };

    Callbacks *callbacks;
    Stream *io;
    Stream *ds;

    static const int blen = 64;
    byte buf[blen];
    static const int olen = 5;
    char obuf[olen+1]; // for the \0 in snprint

    void errorf(int line, String msg);
    void debugf(int line, String msg);
    void ackf(int line);

    int readByte();

    void handleOff();
    void handleShow();
    void handlePixel();
    void handleReady();

  public:
    Protocol(Callbacks *callbacks, Stream* io, Stream* debug);
    void checkChannel();
};

void Protocol::debugf(int line, String msg){
  if (!ds) {
    return;  
  }
  ds->print(line, DEC);
  ds->write(' ');
  ds->println(msg);
}

void Protocol::errorf(int line, String msg) {
  snprintf(obuf, olen+1, "k%04d", line);
  io->write(obuf, olen);
  
  debugf(line, msg);
  
  // flush input
  int b = io->available();
  while (b-- > 0)
    io->read();
}

void Protocol::ackf(int line) {
  snprintf(obuf, olen+1, "o%04d", line);
  io->write(obuf, olen);
}


#define debug(msg) debugf(__LINE__, msg)
#define error(msg) errorf(__LINE__, msg)
#define ack() ackf(__LINE__)

Protocol::Protocol(Callbacks *callbacks, Stream* io, Stream* ds): callbacks(callbacks), io(io), ds(ds) {
  // NOTE: here streams are not initialized yet.
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

void Protocol::handleShow() {
  if (readByte() != 2) {
    error("unexpected show message");
    return;
  }
  callbacks->show();
  ack();
}

void Protocol::handlePixel() {
  int msglen = readByte();
  if ( msglen < 3 ) {
    error("unexpected pixel message");
    return;
  }
  int pixelPos = readByte();
  if ( pixelPos < 0 ) {
    error("unexpected pixel pos");
    return;
  }

  msglen -= 3;
  while (msglen > 0) {
    int r = io->readBytes(buf, 3);
    if (r != 3) {
      error("incomplete pixel");
      break;
    }
    callbacks->setPixelColor(pixelPos++, buf[0], buf[1], buf[2]);
    msglen -= r;
  }
  //ack();
}

void Protocol::checkChannel() {
  char buf[20];
  
  switch (int b = io->read()) {
    case ready:
      handleReady();
      break;
    case off:
      handleOff();
      break;
    case show:
      handleShow();
      break;
    case pixel:
      handlePixel();
      break;
    case -1:
      // nothing to read.
      break;
    default:
    
      snprintf(buf, 20, "got %d '%d'", b,io->available());
      debug(buf);
      error("unexpected message");
  }
}

#undef error
#undef ack
#undef debug
