
LedControl ledControl(NUMPIXELS, LED_STRIP_PIN);
Protocol protocol(&ledControl, cmdSerial, dbgSerial);

// We want to have integers which can contain the image width (>255 pixels).
static_assert((sizeof(unsigned int) >= 2), "need at least 16 bit ints");

void setup() {
  
  // button setup.
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  // Serial initialization.
  
  if (dbgSerial != NULL) {
    dbgSerial->begin(115200);
    dbgSerial->println("debug enabled");
  }

  cmdSerial->begin(115200);
#ifdef BT_PULLUP
  pinMode(15, INPUT_PULLUP); 
#endif

  ledControl.init();

  if (!SD.begin(SD_CS_PIN)) {
    ledControl.flashError();
  } else {
    ledControl.flashInit();
  }
}



void loop() {
  if ( ledControl.isButtonPressed() ) {
    protocol.doReshow();
  }
  protocol.checkChannel();
}
