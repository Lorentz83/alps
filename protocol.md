# Protocol

Requirements:
- Live show
- Info
- reset

- Upload
- Reshow
- Reshow settings

Max image size: 144x868



Messages

- new image (n)
  - px x col (1 byte)
  - delay between cols (1 byte)
  - # col (1 byte)
  - data (#col * px * 3)

- continue image (c)
  - last (1 byte)
  - # col (can be 0)
  - data (#col * px * 3)

render each coloumn and wait.
Send ACK when more data can be read or the image is fully rendered)

< ack CRC32 ( 5 bytes )
< nack errcode ( 5 bytes )


- off (o)
  - 4 random bytes

can be used to resync in case of error. Can be sent multiple times in case of nack (or wrong ack)

< ack CRC32 ( 5 bytes )
< nack errcode ( 5 bytes )


- info (?)
< version (5 bytes)
 - !
 - features (xxxx xxxS): x = for future extension. S = has SD
 - 0
 - max px
 - max col



fixed len error codes
- https://stackoverflow.com/questions/52852503/check-length-of-string-literal-at-compile-time