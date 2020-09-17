# Another Light Painting Stick (ALPS)

This is just another programmable light painting stick.

But, compared to the other projects I've seen so far, instead of
storing all the settings on an SD card you can program the stick from
the phone.


WARNING: this is a work in progress, it is currently tested on a
         single Arduino model (Mega ADK board) and on a single
         Android phone (running Android 9).


## TODO

An unordered list of stuff I'd like to work on:

- documentation:
  - write a list of the harwdware required
  - add some schematics for the connections
  - add some example photos
- on the Arduino side:
  - benchmark the fastled library and decide if it is worth a migration
  - better use of the SD card supporting pre-loaded images
  - check if I can push bluetooth faster using a Teensy board
- on the Android side:
  - re-design the whole app UI/UX (any help is appreciated)
  - add gradient/pattern generators
  - write some unit test

And most important:
- take many photos, enjoy the time spent, and find new features to implement :)
