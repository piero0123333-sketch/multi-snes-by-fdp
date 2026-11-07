#include "rollback_ring_buffer.h"
#include <cstring>

namespace multisnes {

RollbackRingBuffer::RollbackRingBuffer() {
    ring_.resize(kHistoryCapacity);
}

void RollbackRingBuffer::configureStateSize(size_t maxSerializeBytes) {
    configuredSize_ = maxSerializeBytes;
    for (auto& slot : ring_) {
        slot.frameNumber = -1;
        slot.validSize = 0;
        slot.stateData.resize(maxSerializeBytes);
    }
}

void RollbackRingBuffer::clear() {
    for (auto& slot : ring_) {
        slot.frameNumber = -1;
        slot.p1Mask = 0;
        slot.p2Mask = 0;
        slot.stateCrc32 = 0;
        slot.validSize = 0;
    }
}

void RollbackRingBuffer::saveSnapshot(
    int32_t frameNumber,
    uint16_t p1Mask,
    uint16_t p2Mask,
    const uint8_t* stateBytes,
    size_t stateLen
) {
    if (frameNumber < 0 || stateBytes == nullptr || stateLen == 0) {
        return;
    }
    FrameSnapshot& slot = ring_[static_cast<size_t>(frameNumber) % kHistoryCapacity];
    if (slot.stateData.size() < stateLen) {
        slot.stateData.resize(stateLen);
    }
    std::memcpy(slot.stateData.data(), stateBytes, stateLen);
    slot.frameNumber = frameNumber;
    slot.p1Mask = p1Mask;
    slot.p2Mask = p2Mask;
    slot.validSize = stateLen;
    slot.stateCrc32 = computeFastCrc32(stateBytes, stateLen);
}

const RollbackRingBuffer::FrameSnapshot* RollbackRingBuffer::findSnapshot(int32_t frameNumber) const {
    if (frameNumber < 0) return nullptr;
    const FrameSnapshot& slot = ring_[static_cast<size_t>(frameNumber) % kHistoryCapacity];
    if (slot.frameNumber == frameNumber && slot.validSize > 0) {
        return &slot;
    }
    return nullptr;
}

uint32_t RollbackRingBuffer::computeFastCrc32(const uint8_t* data, size_t length) const {
    uint32_t hash = 2166136261u; // FNV-1a 32-bit ultrarrápido para verificación determinista
    const size_t step = (length > 4096) ? 4 : 1;
    for (size_t i = 0; i < length; i += step) {
        hash ^= data[i];
        hash *= 16777619u;
    }
    return hash;
}

} // namespace multisnes
