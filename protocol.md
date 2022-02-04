# Protocol

Let's try do document a little the communication protocol between the
host (currently the Android app) and the client (the stick,
implemented by the Arduino or Teensy).
So that I can capture some of the design choiche and avoid reverse
engineering it next time I need to change it.

Requirements:
- Live show, sending a image and display it in real time;
- Info, to validate the software version;
- Reset, to re-synchronize the protocol;

Nice to have:
- Upload, send an image to the internal memory;
- Reshow, display an image from the internal memory;
- Reshow settings, to change brightness, delay, and loop option of the
  reshow;


In V1 the maximum image size I ever experimented with was 868x144;

WARNING: I don't plan to bump version numbers until I feel that the
         software is out of beta.

## V2

Protocol limitation:
- the number of LEDs must fit into a byte (255 max)
- the number of columns we can transfer in a single message must fit
  into a byte too.
- there is no escape sequence, if some bytes are lost the host needs
  to attempt to re-synchronize the communication before suggesting to
  turn off and on the client again.

All messages are initiated by the host. The host must wait for the
answer (or a reasonable timeout) before sending a new message.

The client can either acknowledge the message or return an error.
Both messages are 5 bytes long.

The acknowledgment message (ack in the future) is:
- ascii 'o', as in ok.
- 4 bytes of crc32 in little endian (least significant byte first).

The crc32 includes everything starting from the last message
identifier sent from the host.

The error message (nack in the future) is:
- ascii 'k', as in ko.
- 4 bytes to identify the error.

Before generating the error the stick should discard all the incoming
data over the wire. This is because, without having an escape
sequence, we cannot distinguish a valid message marker by a random
pixel color.

The plan is to document the error codes in the Arduino code in a
machine parsable, and write a script to extract them and generate a
map consumable by java. I didn't reach this point yet.

The client generates an error if it gets an invalid message marker, or
a message that is invalid for the current internal status.

### Host initiated messages

*info*

This is the only message that doesn't get an ack or a nack as answer.
It should be used when the connection is established so that host and
client can agree on the protocol version and exchange information
about the hardware.

The host sends a single byte '?'.

The client answers with 5 bytes:
0. the ascii '!'
1. a byte that should be interpreted as a bitset which describes the
   features enabled (so far nothing is supported, the idea is to use a
   bit to define if the stick has an SD card).
2. reserved for future use, shouldn't be validated by this version of
   the protocol.
3. the number of LEDs in the stick (unsigned int).
4. the maximum number of columns we can send in a single message
   (unsigned int).

The host should disconnect and raise an error message if it doesn't
support this version or doesn't recognize the message.

*new image*

It is used to start sending a new image for a live show.

The host sends the following bytes:
0. the ascii 'n' as in new image.
1. the number of pixels per column (unsigned int).
2. the delay in ms before showing the next column (unsigned int).
3. the number of columns sent in this message (must be at maximum what
   returned by info).
3. the pixels data in RGB (one byte per channel), for a total of
   columns * pixels * 3 bytes.

When a column is received the client takes care of rendering it.  It
should also take care of the delay as best effort (too much precision
is impossible because of delays in bluetooth communication and
slowness in updating the LEDs).

When all the data is received, and all the columns have been drawn on
the stick, the client sends the ack.
The host should verify the crc returned and raise an error if they
don't match.

The client can send a nack in case of validation errors of the header
data (example: too many pixels or columns) or if the data is not
received after a certain amount of time (currently 1 second, the
default timeout of Arduino read).

*continue image*

It is used to send the next batch of columns of an image.

The host sends:
0. the ascii 'c' as in continue.
1. lastMessage, a byte that evaluates to false if this is the last
   message for this image.
2. the number of columns to be sent (0 is a valid value).
3. pixels color in the same format as new image.

The client handles this message as the new image.

The only difference is that, if lastMessage is set to true, after
rendering the last column it waits the delay and turns off the stick.

*off*

Aborts the current operation and turns off the stick.
It can be used to re-sync the protocol in case an error happens.

The host sends:
- the ascii 'o' as in off.
- 4 random bytes.

The client answers with an ack.

If the host receives a nack or the crc returned doesn't match, the
host can assume some old message was on the wire and therefore can
retry sending another off.
Since the client must flush the buffer every time an error is
returned, the 2 should be able to re-sync eventually.
