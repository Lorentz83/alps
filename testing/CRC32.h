#ifndef CRC32_H
#define CRC32_H


class CRC32 {
    uLong crc;
    
public:
    CRC32() {
        reset();
    }
    
    uint32_t finalize() {
        return crc;
    }
    void reset() {
        crc = crc32(0L, Z_NULL, 0);
    }
    void update(byte *buf, size_t l) {
        auto b = (unsigned char*)buf;
        crc = crc32(crc, b, l);
    }
    void update(byte b) {
        update(&b, 1);
    }
}; 

#endif
