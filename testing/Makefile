arduino_dir=/opt/arduino
arduino_board=arduino:avr:mega

all: protocol_tester

protocol_tester: protocol_tester.cpp CRC32.h ../arduino_code/alps/protocol.ino
	g++ -g -I . -std=c++20 -pthread -o $@ $< -lz

clean:
	rm -f protocol_tester

validate:
	$(arduino_dir)/arduino-builder -compile -fqbn $(arduino_board) \
		-hardware $(arduino_dir)/hardware \
		-tools $(arduino_dir)/tools-builder \
		-tools $(arduino_dir)/hardware/tools \
		-libraries $(arduino_dir)/libraries \
		-libraries $(shell grep sketchbook.path $(HOME)/.arduino15/preferences.txt | cut -d= -f 2)/libraries/ \
		../arduino_code/alps/alps.ino
