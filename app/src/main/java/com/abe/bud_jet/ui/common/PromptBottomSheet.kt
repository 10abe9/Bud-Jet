package com.abe.bud_jet.ui.common

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.abe.bud_jet.databinding.BottomSheetPromptBinding

/**
 * Friendly two-button prompt as a bottom sheet. The answer is delivered as a fragment result
 * under the request key (see [listen]), so it survives screen recreation.
 */
class PromptBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetPromptBinding? = null
    private val binding get() = _binding!!
    private var answered = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPromptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        binding.ivIcon.setImageResource(args.getInt(ARG_ICON))
        binding.tvTitle.setText(args.getInt(ARG_TITLE))
        binding.tvMessage.setText(args.getInt(ARG_MESSAGE))
        args.getInt(ARG_NOTE).takeIf { it != 0 }?.let {
            binding.tvNote.setText(it)
            binding.tvNote.visibility = View.VISIBLE
        }
        binding.btnPositive.setText(args.getInt(ARG_POSITIVE))
        binding.btnNegative.setText(args.getInt(ARG_NEGATIVE))
        binding.btnPositive.setOnClickListener { answer(true) }
        binding.btnNegative.setOnClickListener { answer(false) }
    }

    /** Swiping the sheet away counts as "not now". */
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        answer(false)
    }

    private fun answer(accepted: Boolean) {
        if (answered) return
        answered = true
        val requestKey = requireArguments().getString(ARG_REQUEST_KEY) ?: return
        parentFragmentManager.setFragmentResult(requestKey, Bundle().apply { putBoolean(RESULT_ACCEPTED, accepted) })
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val RESULT_ACCEPTED = "accepted"
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_ICON = "icon"
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_NOTE = "note"
        private const val ARG_POSITIVE = "positive"
        private const val ARG_NEGATIVE = "negative"

        fun show(
            fragmentManager: FragmentManager,
            requestKey: String,
            @DrawableRes icon: Int,
            @StringRes title: Int,
            @StringRes message: Int,
            @StringRes positive: Int,
            @StringRes negative: Int,
            @StringRes note: Int = 0
        ) {
            if (fragmentManager.findFragmentByTag(requestKey) != null) return
            PromptBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_REQUEST_KEY, requestKey)
                    putInt(ARG_ICON, icon)
                    putInt(ARG_TITLE, title)
                    putInt(ARG_MESSAGE, message)
                    putInt(ARG_NOTE, note)
                    putInt(ARG_POSITIVE, positive)
                    putInt(ARG_NEGATIVE, negative)
                }
            }.show(fragmentManager, requestKey)
        }

        /** Register in onViewCreated of the fragment whose parentFragmentManager shows the sheet. */
        fun listen(fragment: Fragment, requestKey: String, onAnswer: (accepted: Boolean) -> Unit) {
            fragment.parentFragmentManager.setFragmentResultListener(requestKey, fragment.viewLifecycleOwner) { _, bundle ->
                onAnswer(bundle.getBoolean(RESULT_ACCEPTED))
            }
        }
    }
}
