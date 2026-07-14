#include <cassert>
#include <chrono>
#include <string>

#include "answer_stop_policy.h"
#include "utf8_stream_decoder.h"

int main() {
    using namespace offlineassistant;

    assert(utf8_character_count("plain") == 5);
    assert(utf8_character_count("\xD0\x90\xD0\x91\xD0\x92") == 3);
    assert(sentence_boundary_count("One... Two! Three?") == 3);

    const std::string short_four_sentences =
        "First useful sentence here. Second useful sentence here. "
        "Third useful sentence here. Fourth useful sentence here.";
    assert(short_four_sentences.size() >= MIN_ANSWER_CHARACTERS_FOR_SENTENCE_LIMIT);
    assert(should_stop_for_answer_shape(short_four_sentences));
    assert(!should_stop_for_answer_shape("One sentence. Two sentences. Three sentences."));

    assert(should_stop_for_answer_shape(std::string(TARGET_ANSWER_CHARACTERS, 'a') + "."));
    assert(!should_stop_for_answer_shape(std::string(TARGET_ANSWER_CHARACTERS, 'a')));
    assert(should_stop_for_answer_shape(std::string(HARD_ANSWER_CHARACTERS, 'a')));

    Utf8StreamDecoder decoder;
    assert(decoder.append(std::string(" \xD0", 2)) == u" ");
    assert(decoder.append(std::string("\x90", 1)) == u"\u0410");
    assert(decoder.append(std::string("\xF0\x9F", 2)).empty());
    assert(decoder.append(std::string("\x98\x80", 2)) == u"\U0001F600");
    assert(decoder.flush().empty());

    Utf8StreamDecoder incomplete;
    assert(incomplete.append(std::string("\xD0", 1)).empty());
    assert(incomplete.flush() == u"\uFFFD");

    const auto started = std::chrono::steady_clock::time_point{};
    assert(!generation_timed_out(started, started + MAX_GENERATION_DURATION - std::chrono::seconds(1)));
    assert(generation_timed_out(started, started + MAX_GENERATION_DURATION));
    return 0;
}
