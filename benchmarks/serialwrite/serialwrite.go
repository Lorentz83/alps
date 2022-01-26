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
	dev = flag.String("dev", "/dev/rfcomm0", "serial device to use")

	// Apparently this is not required for rfcomm connections because they can negotiate the speed.
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
		// 10 columns only
		// {3, 144 * 10 * 3, "10 columns in pixels"},
		// {144 * 3, 144 * 10 * 3, "10 columns in 1 column"},
		// {144 * 3 * 2, 144 * 10 * 3, "10 columns in 2 columns"},
		// {144 * 3 * 3, 144 * 10 * 3, "10 columns in 3 columns"},
		// {144 * 3 * 4, 144 * 10 * 3, "10 columns in 4 columns"},
		// {144 * 3 * 5, 144 * 10 * 3, "10 columns in 5 columns"},
		// {144 * 3 * 6, 144 * 10 * 3, "10 columns in 6 columns"},
		// {144 * 3 * 7, 144 * 10 * 3, "10 columns in 7 columns"},
		// {144 * 3 * 8, 144 * 10 * 3, "10 columns in 8 columns"},
		// {144 * 3 * 9, 144 * 10 * 3, "10 columns in 9 columns"},
		// {144 * 3 * 10, 144 * 10 * 3, "10 columns in full"},

		// Images
		// {144 * 3, 144 * 44 * 3, "44 columns"},

		// {144 * 3, 144 * 36 * 3, "quarter image"},
		// {144 * 3, 144 * 72 * 3, "half image"},
		{144 * 3, 144 * 144 * 3, "square image"},
		// {144 * 3, 144 * 144 * 3 * 2, "wide image in single columns"},
		// Multiple columns
		{144 * 3 * 2, 144 * 144 * 3, "square image in 2 columns"},
		{144 * 3 * 3, 144 * 144 * 3, "square image in 3 columns"},
		{144 * 3 * 4, 144 * 144 * 3, "square image in 4 columns"},
		{144 * 3 * 5, 144 * 144 * 3, "square image in 5 columns"},
		{144 * 3 * 6, 144 * 144 * 3, "square image in 6 columns"},
	}

	for _, tt := range tests {
		fmt.Printf("%s:\n", tt.desc)
		// time, err := sendTestData(connection, tt.chunkSize, tt.totalSize)
		time, err := sendPicture(connection, tt.chunkSize, tt.totalSize)
		if err != nil {
			fmt.Printf(" ERROR: %v\n", err)
		} else {
			throughput := float64(tt.totalSize*8) / float64(time.Seconds())
			fmt.Printf(" time %.2f seconds, throughput %.2f bit/s", time.Seconds(), throughput)
		}
		fmt.Println("")
	}
}

// Similar to sendTestData but this sends a pattern easier to validate.
func sendPicture(s io.ReadWriter, chunkSize int, totalSize int) (time.Duration, error) {
	if chunkSize == 3 {
		return sendTestData(s, chunkSize, totalSize) // for the test pixel.
	}
	if chunkSize%(144*3) != 0 {
		panic(fmt.Sprintf("chunkSize must be a multiple of a column in sendPicture, got %d", chunkSize))
	}
	if totalSize%(144*3) != 0 {
		panic("totalSize must be a multiple of a column in sendPicture")
	}

	fullImage := make([]byte, totalSize)

	const on = '\n' - 1

	w := totalSize / 3 / 144
	for x := 0; x < w; x++ {
		for y := 0; y < 144; y++ {
			pos := (x*144 + y) * 3
			switch (x + y) % 144 {
			case 0:
				fullImage[pos] = on
				fullImage[pos+1] = 0
				fullImage[pos+2] = 0
			case 1:
				fullImage[pos] = 0
				fullImage[pos+1] = on
				fullImage[pos+2] = 0
			case 2:
				fullImage[pos] = 0
				fullImage[pos+1] = 0
				fullImage[pos+2] = on
			default:
				fullImage[pos] = 0
				fullImage[pos+1] = 0
				fullImage[pos+2] = 0
			}
		}
	}

	start := time.Now()

	it := -1
	for remain := len(fullImage); remain > 0; remain = len(fullImage) {
		it++
		// Send.

		send := chunkSize
		if remain < send {
			send = remain
		}
		data := make([]byte, send+1)
		copy(data, fullImage[:send])
		data[send] = '\n'
		fullImage = fullImage[send:]

		var crc uint32
		crc = crc32.Update(crc, crc32.IEEETable, data)

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
		data := nlTerminatedRandomData(size, it)

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

func nlTerminatedRandomData(size, _ int) []byte {
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
