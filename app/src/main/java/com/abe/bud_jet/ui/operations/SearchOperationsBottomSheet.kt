package com.abe.bud_jet.ui.operations

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.BottomSheetSearchOperationsBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment

class SearchOperationsBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetSearchOperationsBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_BudJet_BottomSheet

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSearchOperationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialQuery = requireArguments().getString(ARG_QUERY).orEmpty()
        binding.etSearch.setText(initialQuery)
        binding.etSearch.setSelection(initialQuery.length)

        binding.btnCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.btnClear.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf(ARG_QUERY to "")
            )
            dismissAllowingStateLoss()
        }

        binding.btnApply.setOnClickListener {
            val query = binding.etSearch.text?.toString().orEmpty()
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf(ARG_QUERY to query)
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "operations_search_result"
        const val ARG_QUERY = "arg_query"

        fun newInstance(initialQuery: String): SearchOperationsBottomSheet {
            return SearchOperationsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_QUERY, initialQuery)
                }
            }
        }
    }
}

