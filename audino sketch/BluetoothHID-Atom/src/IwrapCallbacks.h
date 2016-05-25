#ifndef IWRAPCALLBACKS_H
#define IWRAPCALLBACKS_H

#include "definitions.h"
#include <AltSoftSerial.h>

// define the Platform Specific serial commands
#if defined(AVR_PROMICRO16)
# define UsbSerial Serial
# define IwrapSerial Serial1
#elif defined(ARDUINO_AVR_NANO)

extern AltSoftSerial altSerial; // RX=8, TX=9
# define UsbSerial Serial
# define IwrapSerial altSerial
#else /* if defined(AVR_PROMICRO16) */
# error Select a supported platform, or add support for your own
#endif /* if defined(AVR_PROMICRO16) */

extern void assignIwrapCallbacks();

extern void cbIwrapRxdata(uint8_t        channel,
                          uint16_t       length,
                          const uint8_t *data);
extern void cbIwrapTxdata(uint8_t        channel,
                          uint16_t       length,
                          const uint8_t *data);
extern void cbIwrapRspCall(uint8_t link_id);
extern void cbIwrapRspListCount(uint8_t num_of_connections);
extern void cbIwrapRspListResult(uint8_t                link_id,
                                 const char            *mode,
                                 uint16_t               blocksize,
                                 uint32_t               elapsed_time,
                                 uint16_t               local_msc,
                                 uint16_t               remote_msc,
                                 const iwrap_address_t *addr,
                                 uint16_t               channel,
                                 uint8_t                direction,
                                 uint8_t                powermode,
                                 uint8_t                role,
                                 uint8_t                crypt,
                                 uint16_t               buffer,
                                 uint8_t                eretx);
extern void cbIwrapRspSet(uint8_t     category,
                          const char *option,
                          const char *value);
extern void cbIwrapEvtConnect(uint8_t                link_id,
                              const char            *type,
                              uint16_t               target,
                              const iwrap_address_t *address);
extern void cbIwrapEvtNoCarrier(uint8_t     link_id,
                                uint16_t    error_code,
                                const char *message);
extern void cbIwrapEvtPair(const iwrap_address_t *address,
                           uint8_t                key_type,
                           const uint8_t         *link_key);
extern void cbIwrapEvtReady();
extern void cbIwrapEvtRing(uint8_t                link_id,
                           const iwrap_address_t *address,
                           uint16_t               channel,
                           const char            *profile);


// The below functions are call backs, but they are also called directly
// by the iwrap manager
extern int serialOut(const char *str);
extern int serialOut(const __FlashStringHelper *str);
extern int iwrapOut(int            len,
                    unsigned char *data);

#endif /* ifndef IWRAPCALLBACKS_H */
