package com.abe.bud_jet.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepository
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.databinding.BottomSheetDeleteCategoryBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import kotlinx.coroutines.launch

class DeleteCategoryBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetDeleteCategoryBinding? = null
    private val binding get() = _binding!!

    private val repository by lazy { FinanceRepositoryProvider.get(requireContext()) }

    override fun getTheme(): Int = R.style.ThemeOverlay_BudJet_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDeleteCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val categoryId = requireArguments().getLong(ARG_CATEGORY_ID)
        val categoryName = requireArguments().getString(ARG_CATEGORY_NAME).orEmpty()

        binding.tvDeleteMessage.text =
            getString(R.string.dashboard_delete_category_message, categoryName)

        binding.btnCancelDelete.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnConfirmDelete.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                when (repository.deleteCategory(categoryId)) {
                    FinanceRepository.DeleteCategoryResult.SUCCESS -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.dashboard_category_deleted),
                            Toast.LENGTH_SHORT
                        ).show()
                        dismissAllowingStateLoss()
                    }
                    FinanceRepository.DeleteCategoryResult.NOT_FOUND -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.dashboard_category_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY_ID = "arg_category_id"
        private const val ARG_CATEGORY_NAME = "arg_category_name"

        fun newInstance(categoryId: Long, categoryName: String): DeleteCategoryBottomSheet {
            return DeleteCategoryBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_CATEGORY_ID, categoryId)
                    putString(ARG_CATEGORY_NAME, categoryName)
                }
            }
        }
    }
}
