package com.abe.bud_jet.api

/** One chat message: the user's question or the assistant's reply. */
data class ChatTurn(val fromUser: Boolean, val text: String)

/**
 * Client-side limits for the AI chat. The server enforces its own (topic, rate, length);
 * these keep requests small and give the user immediate feedback.
 */
object AiChatPolicy {

    /** Budget questions are short; long inputs are mostly attempts to steer the model. */
    const val MAX_MESSAGE_CHARS = 300

    /** Recent messages sent as context: enough for follow-ups, bounded in size and cost. */
    const val MAX_HISTORY_TURNS = 6

    /** Trimmed question with collapsed whitespace, or null when empty or too long. */
    fun normalize(message: String): String? {
        val text = message.trim().replace(Regex("""\s+"""), " ")
        return text.takeIf { it.isNotEmpty() && it.length <= MAX_MESSAGE_CHARS }
    }

    /** Last [MAX_HISTORY_TURNS] messages, oldest first. */
    fun trimHistory(turns: List<ChatTurn>): List<ChatTurn> =
        turns.takeLast(MAX_HISTORY_TURNS)
}
