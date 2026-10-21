package com.abe.bud_jet.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abe.bud_jet.api.AiAssistantRepository
import com.abe.bud_jet.api.AiAssistantRepository.Outcome
import com.abe.bud_jet.api.AiChatPolicy
import com.abe.bud_jet.api.BudJetApi
import com.abe.bud_jet.api.ChatTurn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tips and chat state; survives rotation. Tips and the conversation are saved on the device
 * (until "New conversation" or turning the assistant off), never on the server.
 */
class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    /** Why the last request did not produce an answer. */
    enum class Problem { DISABLED, NOT_CONFIGURED, NOT_ENTITLED, RATE_LIMITED, FAILED }

    data class UiState(
        val tips: List<BudJetApi.AiInsight> = emptyList(),
        val tipsLoaded: Boolean = false,
        /** When the shown tips were received (they are cached between visits), 0 = never. */
        val tipsUpdatedAt: Long = 0L,
        val loadingTips: Boolean = false,
        val messages: List<ChatTurn> = emptyList(),
        val sending: Boolean = false,
        val problem: Problem? = null,
        /** Requests left today as reported by the server; null until the first answer. */
        val tipsRemaining: Int? = null,
        val chatRemaining: Int? = null,
        /** A question that got no answer, to put back into the input once. */
        val unsentMessage: String? = null
    )

    private val repository = AiAssistantRepository(application)

    private val _state = MutableStateFlow(
        (repository.cachedTips()?.let { (tips, savedAt) ->
            UiState(tips = tips, tipsLoaded = true, tipsUpdatedAt = savedAt)
        } ?: UiState()).copy(messages = repository.savedChat())
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadTips() {
        if (_state.value.loadingTips) return
        _state.update { it.copy(loadingTips = true, problem = null) }
        viewModelScope.launch {
            val outcome = repository.insights()
            _state.update { current ->
                when (outcome) {
                    is Outcome.Success -> current.copy(
                        tips = outcome.value.insights,
                        tipsLoaded = true,
                        tipsUpdatedAt = System.currentTimeMillis(),
                        loadingTips = false,
                        tipsRemaining = outcome.value.remaining ?: current.tipsRemaining
                    )
                    Outcome.RateLimited -> current.copy(
                        loadingTips = false,
                        problem = Problem.RATE_LIMITED,
                        tipsRemaining = 0
                    )
                    else -> current.copy(loadingTips = false, problem = problemOf(outcome))
                }
            }
        }
    }

    /** Returns false when the message is empty or too long (the input shows the limit). */
    fun send(rawMessage: String): Boolean {
        val message = AiChatPolicy.normalize(rawMessage) ?: return false
        if (_state.value.sending) return false
        val history = _state.value.messages
        _state.update {
            it.copy(
                messages = history + ChatTurn(fromUser = true, text = message),
                sending = true,
                problem = null,
                unsentMessage = null
            )
        }
        viewModelScope.launch {
            val outcome = repository.ask(message, history)
            _state.update { current ->
                when (outcome) {
                    is Outcome.Success -> current.copy(
                        messages = current.messages + ChatTurn(fromUser = false, text = outcome.value.reply),
                        sending = false,
                        chatRemaining = outcome.value.remaining ?: current.chatRemaining
                    )
                    // No answer: take the question back out of the chat and return it to the input.
                    else -> current.copy(
                        messages = history,
                        sending = false,
                        problem = problemOf(outcome),
                        unsentMessage = message,
                        chatRemaining = if (outcome == Outcome.RateLimited) 0 else current.chatRemaining
                    )
                }
            }
            repository.saveChat(_state.value.messages)
        }
        return true
    }

    fun newConversation() {
        if (_state.value.sending) return
        _state.update { it.copy(messages = emptyList(), problem = null) }
        repository.saveChat(emptyList())
    }

    fun unsentMessageShown() {
        _state.update { it.copy(unsentMessage = null) }
    }

    private fun problemOf(outcome: Outcome<*>): Problem = when (outcome) {
        Outcome.Disabled -> Problem.DISABLED
        Outcome.NotConfigured -> Problem.NOT_CONFIGURED
        Outcome.NotEntitled -> Problem.NOT_ENTITLED
        Outcome.RateLimited -> Problem.RATE_LIMITED
        is Outcome.Failed, is Outcome.Success -> Problem.FAILED
    }
}
