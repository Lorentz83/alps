package main

import (
	"flag"
	"fmt"
	"hash/crc32"
	"io"
	"log"
	"math/rand"
	"time"

	"github.com/tarm/serial"
)

/*

# Pair and connect the bluetooth using any UI tool.

sudo rfcomm bind 0 [bt address] # this creates /dev/rfcomm0
go run serialwrite.go

# to release

sudo rfcomm release 0
*/
var (
	dev  = flag.String("dev", "/dev/rfcomm0", "serial device to use")
	baud = flag.Int("baud", 115200, "serial speed")
)

func main() {
	flag.Parse()

	rand.Seed(123) // Let's make the test repeatable.

	s, err := serial.OpenPort(&serial.Config{
		Name:        *dev,
		Baud:        *baud,
		ReadTimeout: 2 * time.Second,
	})
	if err != nil {
		log.Fatalf("cannot open serial: %v", err)
	}

	fmt.Printf("Serial baud rate %d\n\n", *baud)

	// Go benchmark framework is nice, but not meant for such a long
	// running operations. Reading "5117852861 ns" is not very intuitive.
	BenchmarkSend(s)
}

func BenchmarkSend(connection io.ReadWriter) {
	tests := []struct {
		chunkSize int
		totalSize int
		desc      string
	}{
		// The connection gets established on the 1st byte sent.
		// We want to be sure it is on before the real benchmark starts.
		{3, 3, "single pixel to establish the connection"},
		// 144 pixels x 3 bytes/pixel = 432 bytes.
		// wide screen (288 columns) = 124416 bytes.
		{432, 124416, "wide image in single columns"},
		{432 * 2, 124416, "wide image in 2 columns"},
		{432 * 3, 124416, "wide image in 3 columns"},
		{432 * 4, 124416, "wide image in 4 columns"},
		{432 * 5, 124416, "wide image in 5 columns"},
		{432 * 6, 124416, "wide image in 6 columns"},
		{432 * 7, 124416, "wide image in 7 columns"},
		{432 * 8, 124416, "wide image in 8 columns"},
		{432 * 9, 124416, "wide image in 9 columns"},
		{432 * 10, 124416, "wide image in 10 columns"},
		{62208, 124416, "wide image in 144 columns"},
		{124416, 124416, "wide image in full"},
	}

	for _, tt := range tests {
		fmt.Printf("%s:\n", tt.desc)
		time, err := sendTestData(connection, tt.chunkSize, tt.totalSize)
		if err != nil {
			fmt.Printf(" ERROR: %v\n", err)
		} else {
			throughput := float64(tt.totalSize*8) / float64(time.Seconds())
			fmt.Printf(" time %.2f seconds, throughput %.2f bit/s", time.Seconds(), throughput)
		}
		fmt.Println("")
	}
}

func sendTestData(s io.ReadWriter, chunkSize int, totalSize int) (time.Duration, error) {
	start := time.Now()

	for it := 0; ; it++ {
		remain := totalSize - (chunkSize * it)
		size := chunkSize
		if remain <= 0 {
			break
		}
		if size > remain {
			size = 0
		}
		data := nlTerminatedData(size)

		var crc uint32
		crc = crc32.Update(crc, crc32.IEEETable, data)
		// Send.
		n, err := s.Write(data)
		if err != nil {
			return 0, fmt.Errorf("error iteration %d writing %d bytes: %v", it, len(data), err)
		}
		if n != len(data) {
			return 0, fmt.Errorf("error iteration %d writing %d bytes: wrote only %d bytes", it, len(data), n)
		}

		// Read.
		num, err := readUint32(s)
		if err != nil {
			return 0, fmt.Errorf("error iteration %d reading number of bytes received: %v", it, err)
		}
		got, err := readUint32(s)
		if err != nil {
			return 0, fmt.Errorf("error iteration %d reading crc: %v", it, err)
		}

		// Validate.
		if num != uint32(len(data)) {
			return 0, fmt.Errorf("error iteration %d received %d bytes, sent %d bytes", it, num, len(data))
		}
		if got != crc {
			return 0, fmt.Errorf("error iteration %d got CRC %d want %d", it, got, crc)
		}
	}

	return time.Now().Sub(start), nil
}

func nlTerminatedData(size int) []byte {
	data := make([]byte, size+1)
	rand.Read(data)
	for i, v := range data {
		if v == '\n' {
			data[i] = 0
		}
	}
	data[size] = '\n'
	return data
}

func readUint32(r io.Reader) (uint32, error) {
	in := make([]byte, 4)
	var (
		err error
		n   int
	)
	for i := 0; i < 5; i++ {
		// Sometime it gets an EOF, but it can retry and work.
		// I have no idea why.
		n, err = io.ReadFull(r, in)
		if err != io.EOF {
			break
		}
		log.Printf("read error %d: %v", i, err)
	}
	if err != nil {
		return 0, fmt.Errorf("error reading int: %v", err)
	}
	if n != len(in) {
		return 0, fmt.Errorf("error reading int, got %d bytes", n)
	}

	got := uint32(in[0]) + uint32(in[1])<<8 + uint32(in[2])<<16 + uint32(in[3])<<24
	return got, nil
}
