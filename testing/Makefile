arduino_dir=/opt/arduino
arduino_board=arduino:avr:mega
teensy_board=teensy:avr:teensy40

all: tester_1 tester_3

tester_1: protocol_tester.cpp CRC32.h ../arduino_code/alps/protocol.ino
	g++ -DTESTING_COLS=1 -g -I . -std=c++20 -pthread -o $@ $< -lz

tester_3: protocol_tester.cpp CRC32.h ../arduino_code/alps/protocol.ino
	g++ -DTESTING_COLS=3 -g -I . -std=c++20 -pthread -o $@ $< -lz

clean:
	rm -f tester_1 tester_3

format:
	clang-format -i protocol_tester.cpp

validate: validate_arduino validate_teensy

validate_arduino:
	$(arduino_dir)/arduino-builder -compile -fqbn $(arduino_board) \
		-hardware $(arduino_dir)/hardware \
		-tools $(arduino_dir)/tools-builder \
		-tools $(arduino_dir)/hardware/tools \
		-libraries $(arduino_dir)/libraries \
		-libraries $(shell grep sketchbook.path $(HOME)/.arduino15/preferences.txt | cut -d= -f 2)/libraries/ \
		../arduino_code/alps/alps.ino

validate_teensy:
	$(arduino_dir)/arduino-builder -compile -fqbn $(teensy_board) \
		-hardware $(arduino_dir)/hardware \
		-tools $(arduino_dir)/tools-builder \
		-tools $(arduino_dir)/hardware/tools \
		-libraries $(arduino_dir)/libraries \
		-libraries $(shell grep sketchbook.path $(HOME)/.arduino15/preferences.txt | cut -d= -f 2)/libraries/ \
		../arduino_code/alps/alps.ino
