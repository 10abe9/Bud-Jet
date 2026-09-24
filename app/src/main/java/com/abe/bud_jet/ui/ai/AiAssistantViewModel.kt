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

/** Tips and chat state; survives rotation. Nothing is stored after the screen is closed. */
class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    /** Why the last request did not produce an answer. */
    enum class Problem { DISABLED, NOT_CONFIGURED, NOT_ENTITLED, RATE_LIMITED, FAILED }

    data class UiState(
        val tips: List<BudJetApi.AiInsight> = emptyList(),
        val tipsLoaded: Boolean = false,
        val loadingTips: Boolean = false,
        val messages: List<ChatTurn> = emptyList(),
        val sending: Boolean = false,
        val problem: Problem? = null
    )

    private val repository = AiAssistantRepository(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadTips() {
        if (_state.value.loadingTips) return
        _state.update { it.copy(loadingTips = true, problem = null) }
        viewModelScope.launch {
            val outcome = repository.insights()
            _state.update { current ->
                when (outcome) {
                    is Outcome.Success -> current.copy(tips = outcome.value, tipsLoaded = true, loadingTips = false)
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
            it.copy(messages = it.messages + ChatTurn(fromUser = true, text = message), sending = true, problem = null)
        }
        viewModelScope.launch {
            val outcome = repository.ask(message, history)
            _state.update { current ->
                when (outcome) {
                    is Outcome.Success -> current.copy(
                        messages = current.messages + ChatTurn(fromUser = false, text = outcome.value),
                        sending = false
                    )
                    else -> current.copy(sending = false, problem = problemOf(outcome))
                }
            }
        }
        return true
    }

    private fun problemOf(outcome: Outcome<*>): Problem = when (outcome) {
        Outcome.Disabled -> Problem.DISABLED
        Outcome.NotConfigured -> Problem.NOT_CONFIGURED
        Outcome.NotEntitled -> Problem.NOT_ENTITLED
        Outcome.RateLimited -> Problem.RATE_LIMITED
        is Outcome.Failed, is Outcome.Success -> Problem.FAILED
    }
}
