package com.abe.bud_jet.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.abe.bud_jet.databinding.BottomSheetProfileLanguagePickerBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.abe.bud_jet.utils.LocaleManager
import com.google.android.material.chip.Chip

class LanguagePickerBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetProfileLanguagePickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetProfileLanguagePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val current = requireArguments().getString(ARG_CURRENT).orEmpty()
        LocaleManager.supportedLanguages().forEach { code ->
            val chip = Chip(requireContext()).apply {
                text = LocaleManager.displayNameForLanguage(requireContext(), code)
                isCheckable = true
                isCheckedIconVisible = false
                tag = code
            }
            binding.chipLanguages.addView(chip)
            if (code == current) chip.isChecked = true
        }

        binding.btnCancelLanguage.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnSaveLanguage.setOnClickListener {
            val selected = binding.chipLanguages.checkedChipId
            val code = binding.chipLanguages.findViewById<Chip>(selected)?.tag as? String ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply { putString(RESULT_LANGUAGE, code) }
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "language_picker_result"
        const val RESULT_LANGUAGE = "language_code"
        private const val ARG_CURRENT = "arg_current"

        fun newInstance(currentLanguage: String): LanguagePickerBottomSheet {
            return LanguagePickerBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_CURRENT, currentLanguage) }
            }
        }
    }
}
