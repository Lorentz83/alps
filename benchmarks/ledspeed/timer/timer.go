package main

import (
	"flag"
	"fmt"
	"log"
	"time"

	"github.com/tarm/serial"
)

var (
	dev  = flag.String("dev", "/dev/ttyACM0", "serial device to use")
	baud = flag.Int("baud", 115200, "serial speed")
)

func main() {
	s, err := serial.OpenPort(&serial.Config{
		Name: *dev,
		Baud: *baud,
	})
	if err != nil {
		log.Fatalf("cannot open serial: %v", err)
	}

	var (
		lastTime time.Time
		library  string
		buf      = make([]byte, 1)
	)
	for {
		n, err := s.Read(buf)
		if err != nil {
			log.Printf("ERROR: %v", err)
		}
		if n == 0 {
			continue
		}

		switch c := buf[0]; c {
		case '.':
			if lastTime.IsZero() {
				continue // it is the 1st run, we don't have data.
			}
			fmt.Printf("%s %v\n", library, time.Now().Sub(lastTime))
		case 'f':
			lastTime = time.Now()
			library = "fastLED"
		case 'a':
			lastTime = time.Now()
			library = "adafruit"
		case 'd':
			lastTime = time.Now()
			library = "DMA"
		default:
			log.Printf("unknown byte %c", c)
		}
	}
}
