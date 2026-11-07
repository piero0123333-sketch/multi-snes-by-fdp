#ifndef ROLLBACK_RING_BUFFER_H__
#define ROLLBACK_RING_BUFFER_H__

#include <cstdint>
#include <cstddef>
#include <vector>
#include <mutex>

namespace multisnes {

/**
 * Buffer circular determinista en memoria nativa C++ para Netplay Hotspot (<10cm) sin lag.
 * Almacena los últimos K estados serializados de la SNES (aprox. 180 KB c/u) para
 * permitir Rollback + Replay en menos de 0.8 ms si un paquete remoto llega con 1-7 frames de diferencia.
 */
class RollbackRingBuffer {
public:
    static constexpr size_t kHistoryCapacity = 12;

    struct FrameSnapshot {
        int32_t frameNumber = -1;
        uint16_t p1Mask = 0;
        uint16_t p2Mask = 0;
        uint32_t stateCrc32 = 0;
        size_t validSize = 0;
        std::vector<uint8_t> stateData;
    };

    RollbackRingBuffer();
    void configureStateSize(size_t maxSerializeBytes);
    void clear();

    void saveSnapshot(
        int32_t frameNumber,
        uint16_t p1Mask,
        uint16_t p2Mask,
        const uint8_t* stateBytes,
        size_t stateLen
    );

    const FrameSnapshot* findSnapshot(int32_t frameNumber) const;
    uint32_t computeFastCrc32(const uint8_t* data, size_t length) const;

private:
    std::vector<FrameSnapshot> ring_;
    size_t configuredSize_ = 0;
};

} // namespace multisnes

#endif // ROLLBACK_RING_BUFFER_H__
