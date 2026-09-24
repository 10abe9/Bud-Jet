package com.abe.bud_jet.ui.ai

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.abe.bud_jet.R
import com.abe.bud_jet.api.AiChatPolicy
import com.abe.bud_jet.api.BudJetApi
import com.abe.bud_jet.api.ChatTurn
import com.abe.bud_jet.database.preferences.PreferenceManager
import com.abe.bud_jet.databinding.FragmentAiAssistantBinding
import com.abe.bud_jet.utils.VibrationManager
import com.abe.bud_jet.utils.collectWithLifecycle
import com.google.android.material.chip.Chip

/**
 * AI assistant (Pro plan, after consent): tips for the budget and a short chat about it.
 * Profile opens this screen only when the assistant is active.
 */
class AiAssistantFragment : Fragment(R.layout.fragment_ai_assistant) {

    private var _binding: FragmentAiAssistantBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AiAssistantViewModel by viewModels()

    private var renderedMessages = -1
    private var renderedTips: List<BudJetApi.AiInsight>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAiAssistantBinding.bind(view)
        renderedMessages = -1
        renderedTips = null

        // Plan ended or consent withdrawn while this screen was in the back stack.
        if (!PreferenceManager.getInstance(requireContext()).isAiAssistantActive()) {
            findNavController().popBackStack()
            return
        }

        binding.buttonBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnGetTips.setOnClickListener { viewModel.loadTips() }
        binding.btnRefreshTips.setOnClickListener { viewModel.loadTips() }
        binding.btnSend.setOnClickListener { sendInput() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendInput()
                true
            } else {
                false
            }
        }
        setupSuggestions()

        viewModel.state.collectWithLifecycle(viewLifecycleOwner) { render(it) }
    }

    private fun setupSuggestions() {
        listOf(
            R.string.ai_suggest_spend_less,
            R.string.ai_suggest_growth,
            R.string.ai_suggest_limits,
            R.string.ai_suggest_goal
        ).forEach { res ->
            val question = getString(res)
            val chip = Chip(requireContext()).apply {
                text = question
                isCheckable = false
                setOnClickListener {
                    VibrationManager.get().tap()
                    viewModel.send(question)
                }
            }
            binding.chipSuggestions.addView(chip)
        }
    }

    private fun sendInput() {
        val text = binding.etMessage.text?.toString().orEmpty()
        if (text.isBlank()) return
        if (AiChatPolicy.normalize(text) == null) {
            binding.inputLayout.error = getString(R.string.ai_chat_too_long, AiChatPolicy.MAX_MESSAGE_CHARS)
            return
        }
        if (viewModel.send(text)) {
            binding.inputLayout.error = null
            binding.etMessage.setText("")
        }
    }

    private fun render(state: AiAssistantViewModel.UiState) {
        // Tips
        binding.progressTips.visibility = if (state.loadingTips) View.VISIBLE else View.GONE
        binding.btnGetTips.visibility = if (state.tipsLoaded || state.loadingTips) View.GONE else View.VISIBLE
        binding.btnRefreshTips.visibility = if (state.tipsLoaded && !state.loadingTips) View.VISIBLE else View.GONE
        binding.tvTipsEmpty.visibility = if (state.tipsLoaded) View.GONE else View.VISIBLE
        if (state.tips != renderedTips) {
            renderedTips = state.tips
            binding.layoutTips.removeAllViews()
            if (state.tipsLoaded && state.tips.isEmpty()) {
                binding.layoutTips.addView(tipView(null, getString(R.string.ai_tips_none)))
            }
            state.tips.forEach { binding.layoutTips.addView(tipView(it.title, it.text)) }
        }

        // Chat: messages only grow, so append the new ones.
        if (renderedMessages < 0) {
            binding.layoutMessages.removeAllViews()
            renderedMessages = 0
        }
        state.messages.drop(renderedMessages).forEach { binding.layoutMessages.addView(bubble(it)) }
        if (state.messages.size > renderedMessages) {
            renderedMessages = state.messages.size
            binding.scroll.post { _binding?.scroll?.fullScroll(View.FOCUS_DOWN) }
        }
        binding.tvChatHint.visibility = if (state.messages.isEmpty()) View.VISIBLE else View.GONE
        binding.tvTyping.visibility = if (state.sending) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !state.sending
        binding.chipSuggestions.isEnabled = !state.sending
        for (i in 0 until binding.chipSuggestions.childCount) {
            binding.chipSuggestions.getChildAt(i).isEnabled = !state.sending
        }

        // Problem
        val problem = state.problem
        binding.tvStatus.visibility = if (problem == null) View.GONE else View.VISIBLE
        binding.tvStatus.text = problem?.let { getString(problemText(it)) }.orEmpty()
    }

    private fun problemText(problem: AiAssistantViewModel.Problem): Int = when (problem) {
        AiAssistantViewModel.Problem.DISABLED -> R.string.ai_problem_disabled
        AiAssistantViewModel.Problem.NOT_CONFIGURED -> R.string.ai_problem_not_configured
        AiAssistantViewModel.Problem.NOT_ENTITLED -> R.string.ai_problem_not_entitled
        AiAssistantViewModel.Problem.RATE_LIMITED -> R.string.ai_problem_rate_limited
        AiAssistantViewModel.Problem.FAILED -> R.string.ai_problem_failed
    }

    private fun tipView(title: String?, text: String): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            if (!title.isNullOrBlank()) {
                addView(TextView(context).apply {
                    this.text = title
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
            }
            addView(TextView(context).apply {
                this.text = text
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 14f
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun bubble(turn: ChatTurn): View {
        val context = requireContext()
        return TextView(context).apply {
            text = turn.text
            textSize = 15f
            setTextIsSelectable(true)
            setTextColor(
                ContextCompat.getColor(context, if (turn.fromUser) R.color.on_primary_container else R.color.text_primary)
            )
            setBackgroundResource(if (turn.fromUser) R.drawable.bg_bubble_user else R.drawable.bg_bubble_ai)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                gravity = if (turn.fromUser) Gravity.END else Gravity.START
                if (turn.fromUser) marginStart = dp(48) else marginEnd = dp(48)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
