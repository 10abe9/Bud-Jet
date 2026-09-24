package com.abe.bud_jet.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiChatPolicyTest {

    @Test
    fun normalizesWhitespace() {
        assertEquals("How much on food?", AiChatPolicy.normalize("  How much\n on   food? "))
    }

    @Test
    fun rejectsEmptyAndTooLong() {
        assertNull(AiChatPolicy.normalize("   "))
        assertNull(AiChatPolicy.normalize("a".repeat(AiChatPolicy.MAX_MESSAGE_CHARS + 1)))
        assertEquals(AiChatPolicy.MAX_MESSAGE_CHARS, AiChatPolicy.normalize("a".repeat(AiChatPolicy.MAX_MESSAGE_CHARS))!!.length)
    }

    @Test
    fun keepsOnlyRecentHistory() {
        val turns = (1..10).map { ChatTurn(it % 2 == 1, "m$it") }
        val trimmed = AiChatPolicy.trimHistory(turns)
        assertEquals(AiChatPolicy.MAX_HISTORY_TURNS, trimmed.size)
        assertEquals("m5", trimmed.first().text)
        assertEquals("m10", trimmed.last().text)
    }
}
