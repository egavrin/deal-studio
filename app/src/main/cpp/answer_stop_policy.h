#pragma once

#include <chrono>
#include <cstddef>
#include <string_view>

namespace offlineassistant {

constexpr std::size_t MIN_ANSWER_CHARACTERS_FOR_SENTENCE_LIMIT = 80;
constexpr std::size_t TARGET_ANSWER_CHARACTERS = 600;
constexpr std::size_t HARD_ANSWER_CHARACTERS = 900;
constexpr std::size_t TARGET_ANSWER_SENTENCES = 4;
constexpr std::chrono::seconds MAX_GENERATION_DURATION{90};

inline std::size_t utf8_character_count(std::string_view text) {
    std::size_t count = 0;
    for (const unsigned char byte : text) {
        if ((byte & 0xC0U) != 0x80U) {
            count++;
        }
    }
    return count;
}

inline bool is_sentence_boundary(char value) {
    return value == '.' || value == '!' || value == '?';
}

inline bool ends_at_sentence_boundary(std::string_view text) {
    for (auto iterator = text.rbegin(); iterator != text.rend(); ++iterator) {
        const char value = *iterator;
        if (value == ' ' || value == '\n' || value == '\r' || value == '\t') {
            continue;
        }
        return is_sentence_boundary(value);
    }
    return false;
}

inline std::size_t sentence_boundary_count(std::string_view text) {
    std::size_t count = 0;
    bool previous_was_boundary = false;
    for (const char value : text) {
        const bool boundary = is_sentence_boundary(value);
        if (boundary && !previous_was_boundary) {
            count++;
        }
        previous_was_boundary = boundary;
    }
    return count;
}

inline bool should_stop_for_answer_shape(std::string_view text) {
    const std::size_t characters = utf8_character_count(text);
    if (characters >= HARD_ANSWER_CHARACTERS) {
        return true;
    }
    if (!ends_at_sentence_boundary(text)) {
        return false;
    }
    if (characters >= TARGET_ANSWER_CHARACTERS) {
        return true;
    }
    return characters >= MIN_ANSWER_CHARACTERS_FOR_SENTENCE_LIMIT &&
           sentence_boundary_count(text) >= TARGET_ANSWER_SENTENCES;
}

inline bool generation_timed_out(
    std::chrono::steady_clock::time_point started,
    std::chrono::steady_clock::time_point now
) {
    return now - started >= MAX_GENERATION_DURATION;
}

}  // namespace offlineassistant
