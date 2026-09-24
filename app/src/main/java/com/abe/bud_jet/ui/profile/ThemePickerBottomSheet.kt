package com.abe.bud_jet.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.BottomSheetProfileLanguagePickerBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.abe.bud_jet.utils.ThemeManager
import com.google.android.material.chip.Chip

/** Light / dark / system theme picker; reuses the language picker layout. */
class ThemePickerBottomSheet : BaseBottomSheetDialogFragment() {

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
        super.onViewCreated(view, savedInstanceState)
        binding.tvPickerTitle.text = getString(R.string.theme_picker_title)
        val current = requireArguments().getString(ARG_CURRENT).orEmpty()
        ThemeManager.modes.forEach { mode ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = getString(labelFor(mode))
                isCheckable = true
                isCheckedIconVisible = false
                tag = mode
            }
            binding.chipLanguages.addView(chip)
            if (mode == current) binding.chipLanguages.check(chip.id)
        }

        binding.btnCancelLanguage.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnSaveLanguage.setOnClickListener {
            val selected = binding.chipLanguages.checkedChipId
            val mode = binding.chipLanguages.findViewById<Chip>(selected)?.tag as? String
                ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply { putString(RESULT_THEME, mode) })
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "theme_picker_result"
        const val RESULT_THEME = "theme_mode"
        private const val ARG_CURRENT = "arg_current"

        fun labelFor(mode: String): Int = when (mode) {
            ThemeManager.LIGHT -> R.string.theme_light
            ThemeManager.DARK -> R.string.theme_dark
            else -> R.string.theme_system
        }

        fun newInstance(currentMode: String): ThemePickerBottomSheet {
            return ThemePickerBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_CURRENT, currentMode) }
            }
        }
    }
}
