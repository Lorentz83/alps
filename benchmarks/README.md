# Benchmarks

This directory contains a few benchmarks.

1. `ledspeed` measures how fast are different libraries in sending
   pixels.
2. `serialread` and `serialwrite` work in parallel to test the
   bluetooth connection.

NOTE: everything here is more like a scrapbook for me than an actual
product, to run the tests you have likely to touch the code to enable
some specific behaviours.  I tried to leave as many comments as
possible, but I may have failed.

## Findings

### LED libraries

Theoretically

> WS2812 strips are slow for writing data, with a data rate of just
> 800khz, it takes 30µs to write out a single led's worth of data[^1]

and they also need 300us reset time to wait after the whole strip is
written.[^2]

[^1]: https://github.com/FastLED/FastLED/wiki/Parallel-Output
[^2]: https://github.com/PaulStoffregen/WS2812Serial/issues/4

Therefore these are hard limits:
 - 4.32 milliseconds for a column, without wait (144 pixels)
 - 665 milliseconds for a square (144x144)
 - 1.3 seconds for a wide image (288x144)

In the `ledspeed` test, we simulate sending 10 times a 144x144 pixels
image, therefore the best we could get from this benchmark is 6.65
seconds, which is pretty much what we see with adafruit and DMA using
Tennsy 4.

NOTE: in this kind of tests we cannot measure the time on arduino
because `millis()` is not incremented when interrupts are disabled
([ref](https://forum.arduino.cc/t/does-nointerrupts-disable-the-millisecond-timer/433292/9)).

It is still interesting to measure it because it gives us an
estimation of how much time is spent in not sending data, which should
be processing and waiting to reset.

The 3 libraries compared are:
1. [Adafruit_NeoPixel](https://adafruit.github.io/Adafruit_NeoPixel/html/index.html)
2. [FastLED](https://github.com/FastLED/FastLED)
3. [WS2812Serial](https://github.com/PaulStoffregen/WS2812Serial) (Teensy only)

NOTE: in the following tables I report single round of tests. Also,
the millis and the wall time we see on the same line refers to
different runs. But you can check the comments in the source code and
see that there was not too much variance across different runs.

The time recorded here is about rendering 10 times a 144x144px image.

On Arduino Mega ADK

| Library   | Wall time (s) | Millis |
| --------- |--------------:| ------:|
| FastLED   |   8.405086106 |   8542 |
| Adafruit  |   7.213265824 |   2470 |


On Teensy 4.0

| Library      | Wall time (s) | Millis |
| ------------ |--------------:| ------:|
| FastLED      |   7.728041386 |   7724 |
| Adafruit     |   6.715214155 |   1920 |
| WS2812Serial |   6.649549581 |   6649 |

Sending a plain image generated completely on board, the Adafruit
library is the fastest and most portable option.

FastLED is slower, but we see that wall time is very close to
millis. Does it mean that it doesn't disable interrupts?  For sure its
timing is reset timing is buggy on Teensy because we have to add a
[delay](https://www.reddit.com/r/FastLED/comments/r9pq6o/odd_behaviors_on_teensy4_using_fastled/)
to make it work.
Testing in a real case scenario will follow, but so far I fear that
the "fast" in the name refers mostly to the "functions for
high-performing 8bit math for manipulating your RGB values", which is
not very useful in this project because pretty much all the processing
is done in the Android companion app.


### Serial

I really struggle to find the bluetooth boards datasheets, but according to
[this](http://www.techbitar.com/uploads/2/0/3/1/20316977/hc-05_at_commands.pdf) HC-05
can reach 1.382.400 bauds.
While HC-06 can either reach 115.200 bauds
([soruce](https://www.keuwl.com/electronics/rduino/bluet/09-baud-rate/))
or 1.382.400 bauds too
([source](http://www.martyncurrey.com/arduino-and-hc-06-zs-040/)).

It looks that Arduino Mega ADK can handle up to 230.400 bauds on the
USB serial. After data gets totally corrupted. It is a pretty old
board, so probably there are faster Arduino on the market nowadays.
While Teensy 4.0 can handle reasonably well 2.500.000 bauds.

Said so, all the tests with Arduino Mega ADK are done with an HT-06
module configured at 115200 bauds, while the tests with Teensy 4.0 are
done with an HT-05 module at 1382400 bauds.

### Bluetooth

`serialread`, in the basic usage, just reads on Arduino from
bluetooth, computes a crc and sends it back. Optionally, it can use
the a pixel library to send data to the LEDs.

`serialwrite` is a simple go code which writes on a serial device,
checks the returned crc and measures the time. It is written in GO
therefore it doesn't use the Android's Java stack. I'm not sure how
much performance difference it can introduce, but iterating over GO
code is much faster than over Android development.

NOTE: the protocol used here is different than the one used in the
main app, mostly for the sake of simplicity. Instead of sending an
header specifying how much data will follow, '\n' is handled as escape
sequence to trigger the answer from the controller. This is why
`serialwrite` changes every newline in the randomly generated data
stream to something else.

The first interesting finding is that there is a HUGE difference
between the non-blocking `Serial.read()` and the blocking
`Serial.readBytes()` IF the buffer is not always fully read.

This is because `readBytes` waits for 1 second timeout
([ref](https://www.arduino.cc/reference/en/language/functions/communication/serial/settimeout/))
before returning if it doesn't have enough data to fill the buffer.

In this setup, where the terminator byte is sent alone but we read
bytes 3 by 3 to get a full pixel, if we send the column one by one we
end up having an extra second delay on each column! Increasing the
chunk of data we can see the 2 functions converging in performances.

Checking the production code, it looks that every time `readBytes()`
returns a number of bytes different than the one asked it is because
we are on an error path.
But I spent probably half an hour trying to understand why the test
code was so slow before realizing it.
Also I cannot exclude for certain that using the blocking function
doesn't degrade performances even if we have enough data waiting in
the buffer.  It is probably better checking again and try to migrate
as much code as possible to use the non blocking `read()`.

The second investigation focused on finding the optimal amount of data
to send in a single write to maximize the throughput.
Again, I fail to find the optimal size of a bluetooth frame on
Internet, so let's test it.

All the following tests simulate to send images 144x10px in RGB.
It is read using the non-blocking Seria.read().
They just change how frequently the escape sequence is sent to trigger
and wait for the ack and the CRC.
The throughput measured here doesn't take into consideration the
escape sequences nor the acknowledgments.

Arduino Mega ADK, 115.200 baud.

| Chunk of data | time (s) | throughput (bit/s) |
| ------------- |---------:| ------------------:|
| single pixels |    51.16 |             675.59 |
|  1 column     |     0.76 |           45568.72 |
|  2 columns    |     0.57 |           60376.26 |
|  3 columns    |     0.50 |           69462.59 |
|  4 columns    |     0.42 |           82051.10 |
|  5 columns    |     0.45 |           76846.08 |
|  6 columns    |     0.29 |          120223.26 |
|  7 columns    |     0.33 |          104759.74 |
|  8 columns    |     0.37 |           94039.08 |
|  9 columns    |     0.40 |           85359.54 |
| 10 columns    |     0.42 |           83067.11 |

Teensy 4.0, 1.382.400 baud.

| Chunk of data | time (s) | throughput (bit/s) |
| ------------- |---------:| ------------------:|
| single pixels |    52.67 |             656.14 |
|  1 column     |     0.42 |           81320.88 |
|  2 columns    |     0.27 |          125687.88 |
|  3 columns    |     0.20 |          169951.16 |
|  4 columns    |     0.18 |          189385.55 |
|  5 columns    |     0.23 |          150442.04 |
|  6 columns    |     0.16 |          217615.06 |
|  7 columns    |     0.15 |          234332.13 |
|  8 columns    |     0.19 |          185802.02 |
|  9 columns    |     0.19 |          185508.87 |
| 10 columns    |     0.20 |          171838.52 |

We can see in both the cases that sending each pixel is terribly slow
because we incur in a lot of overhead (more acks) and also we likely
have mostly empty bluetooth frames.

We can see that Teensy is a not even twice as faster, despite having
the bluetooth module configured to go 10 time faster.

Again, without a real datasheet it is difficult to know for sure. But,
according to a few stores, HC-05 should implement Bluetooth 2.0 + EDR,
which should offer 2.1 Mbit/s of transfer speed
([Wikipedia](https://en.wikipedia.org/wiki/Bluetooth#Bluetooth_2.0_+_EDR)).
But here we see barely 230Kbit/s.
Which is 230.000 bauds, much lower than the 1.382.400 the module is set.

Honestly, I feel that the bluetooth protocol is terribly complex and
I'm not even sure which bluetooth version my laptop supports. I just
accept this data and make a mental note that it may be interesting to
test with a BLE module in the future.

Anyhow, sending data in columns is more reasonable and in both cases
we get the highest throughput between 6 and 7 columns (around 2800
bytes). The issue is that if we spend time to elaborate the column of
data (like sending it to the LED stick) we likely wait too much and we
drop data from the serial buffer.

### Combining together

The final step is to extend `serialread` to use all the LED libraries
and test how they behave on a real case.

Testing sending fractions of 144x144 image in single columns.
The Dummy library does nothing, it is just used to have a baseline
without LED overhead.

The time information here is expressed in seconds. I register a single
execution for simplicity, but I can say that with the double size
image I can see up to 1 second of variance.

Arduino Mega ADK

| Library      |  1/4 |  1/2 | Full  | Double |
| ------------ |-----:|-----:|------:|-------:|
| Dummy        | 2.58 | 5.36 | 10.46 |  20.63 |
| Neopixel     | 2.67 | 5.12 | 10.54 |  21.16 |
| FastLED      | 2.57 | 5.29 | 10.76 |  20.96 |
| WS2812Serial |  n/a |  n/a |   n/a |    n/a |

Teensy 4.0

| Library      |  1/4 |  1/2 | Full | Double |
| ------------ |-----:|-----:|-----:|-------:|
| Dummy        | 1.50 | 3.18 | 6.29 |  12.56 |
| Neopixel     | 1.51 | 3.05 | 6.25 |  12.55 |
| FastLED      | 1.50 | 3.06 | 6.24 |  12.50 |
| WS2812Serial | 1.57 | 3.17 | 6.35 |  12.66 |

Which looking at this doesn't really make any difference, the slowest
part remains the bluetooth.

Both the Neopixel and the FastLED library are too slow in rendering
the column (this is like the hardware limit of the LED strip), and
therefore if we try to send multiple columns we end up with data
corruption.

But since the `WS2812Serial` uses DMA, we don't use pretty much any
CPU cycle to send the data, therefore we can send as much as 6 columns
before we fill the buffer and we get data loss!
On the other side, the image is not rendered really smoothly anymore
because we add some extra delay every 6 columns.

Specifically, using WS2812Serial to send a 144x144px image in multiple
columns we get these results.

| # of cols | time (s) |
|----------:|---------:|
|         1 |     6.14 |
|         2 |     4.08 |
|         3 |     2.97 |
|         4 |     2.66 |
|         5 |     3.26 |
|         6 |     2.91 |

Coincidentally, 6 columns are also the one that gives a better
bluetooth throughput. Therefore the result is astonishingly fast
taking pretty much half time.

Now, flashing back the current project to both the boards I can see
that it takes 8.30s for Teensy and 12.56s for Arduino to process a
144x144px image.

Again, the Bluetooth stack is different, but I really hope that a 1
year old Android phone has a better Bluetooth support than a 5+ years
old computer running Linux. Therefore I have almost 30% performances I
can increase just fixing the transmission protocol.

After it I can try to write some Teensy specific code which buffers
the image received over bluetooth while rendering it in parallel using
DMA. 
On Teensy rendering a 144x144px image takes 0.66s, and the fastest
transmission (6 columns) of the same image takes 3s.
Meaning that the bottleneck is still the Bluetooth. Therefore with a
reasonably big buffer, and some appropriate delays between columns, we
should be able to send and render the image in parallel.

# Android

Hacking the Android app to generate a 144x144 image like the `serialwrite`, 
the best I can get is 7.30s, which is 1 second more than from the computer.
So I guess my expectation that the phone could handle bluetooth better
were wrong.
I also see a pretty big variance between different runs, I witnessed 1
second delays for no apparent reason.

More in detail:
 - Updating the progress bar doesn't waste any performance;
 - The `sleep(0)` to receive interrupts doesn't waste any time;
 - Also processing the bitmat doesn't seem to incur in any extra overhead;

But logging is terrybly slow. A single log line for each column adds a
good extra second on sending the image!

Also, restoring the original app (with the current protocol) has
similar performance once the logging instruction is removed.

At this point, there is no reason in try micro optimizations.
The only way to improve significantly the performance is to re-design
the protocol to allow sending more data in single writes, which likely
requires using the WS2812Serial library to save time on writing the LEDs.
