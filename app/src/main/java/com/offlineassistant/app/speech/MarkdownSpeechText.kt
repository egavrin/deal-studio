package com.offlineassistant.app.speech

/**
 * Converts visible Markdown into natural speech while leaving the displayed source
 * text untouched. This is intentionally a speech projection, not a Markdown parser.
 */
internal fun markdownToSpeechText(value: String): String = value
    .replace(FENCED_CODE, " Code fragment. ")
    .replace(MARKDOWN_IMAGE) { it.groupValues[1] }
    .replace(MARKDOWN_LINK) { it.groupValues[1] }
    .replace(RAW_URL, "")
    .replace(HEADING_OR_QUOTE, "")
    .replace(LIST_MARKER, "")
    .replace(EMPHASIS_MARKER, "")
    .replace(Regex("""[ \t]+"""), " ")
    .replace(Regex("""[ \t]*\n[ \t]*"""), "\n")
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()

private val FENCED_CODE = Regex("""(?s)```.*?```""")
private val MARKDOWN_IMAGE = Regex("""!\[([^]]*)]\([^)]+\)""")
private val MARKDOWN_LINK = Regex("""\[([^]]+)]\([^)]+\)""")
private val RAW_URL = Regex("""https?://\S+""")
private val HEADING_OR_QUOTE = Regex("""(?m)^\s{0,3}(?:#{1,6}|>)\s*""")
private val LIST_MARKER = Regex("""(?m)^\s*(?:[-+*]|\d+[.)])\s+""")
private val EMPHASIS_MARKER = Regex("""[*_~`]""")
