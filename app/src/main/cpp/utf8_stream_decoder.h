#pragma once

#include <cstdint>
#include <string>
#include <string_view>

namespace offlineassistant {

class Utf8StreamDecoder {
public:
    std::u16string append(std::string_view bytes) {
        pending_.append(bytes.data(), bytes.size());
        return consume(false);
    }

    std::u16string flush() {
        return consume(true);
    }

private:
    static bool is_continuation(uint8_t byte) {
        return (byte & 0xC0U) == 0x80U;
    }

    static void append_code_point(std::u16string & output, uint32_t code_point) {
        if (code_point <= 0xFFFFU) {
            output.push_back(static_cast<char16_t>(code_point));
            return;
        }
        code_point -= 0x10000U;
        output.push_back(static_cast<char16_t>(0xD800U + (code_point >> 10U)));
        output.push_back(static_cast<char16_t>(0xDC00U + (code_point & 0x3FFU)));
    }

    std::u16string consume(bool flush_incomplete) {
        std::u16string output;
        std::size_t index = 0;
        while (index < pending_.size()) {
            const uint8_t first = static_cast<uint8_t>(pending_[index]);
            std::size_t length = 0;
            uint32_t code_point = 0;
            if (first <= 0x7FU) {
                length = 1;
                code_point = first;
            } else if (first >= 0xC2U && first <= 0xDFU) {
                length = 2;
                code_point = first & 0x1FU;
            } else if (first >= 0xE0U && first <= 0xEFU) {
                length = 3;
                code_point = first & 0x0FU;
            } else if (first >= 0xF0U && first <= 0xF4U) {
                length = 4;
                code_point = first & 0x07U;
            } else {
                append_code_point(output, 0xFFFDU);
                index++;
                continue;
            }

            if (index + length > pending_.size()) {
                if (!flush_incomplete) break;
                append_code_point(output, 0xFFFDU);
                index = pending_.size();
                break;
            }

            bool valid = true;
            for (std::size_t offset = 1; offset < length; offset++) {
                const uint8_t next = static_cast<uint8_t>(pending_[index + offset]);
                if (!is_continuation(next)) {
                    valid = false;
                    break;
                }
                code_point = (code_point << 6U) | (next & 0x3FU);
            }
            const uint8_t second = length > 1 ? static_cast<uint8_t>(pending_[index + 1]) : 0;
            valid = valid && !(length == 3 && first == 0xE0U && second < 0xA0U);
            valid = valid && !(length == 3 && first == 0xEDU && second > 0x9FU);
            valid = valid && !(length == 4 && first == 0xF0U && second < 0x90U);
            valid = valid && !(length == 4 && first == 0xF4U && second > 0x8FU);
            if (!valid) {
                append_code_point(output, 0xFFFDU);
                index++;
                continue;
            }

            append_code_point(output, code_point);
            index += length;
        }
        pending_.erase(0, index);
        return output;
    }

    std::string pending_;
};

}  // namespace offlineassistant
