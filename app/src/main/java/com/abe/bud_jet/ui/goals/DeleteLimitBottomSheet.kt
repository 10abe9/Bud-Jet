package com.abe.bud_jet.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.abe.bud_jet.databinding.BottomSheetDeleteLimitBinding
import com.abe.bud_jet.R
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment

class DeleteLimitBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetDeleteLimitBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDeleteLimitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val goalId = requireArguments().getLong(ARG_GOAL_ID)
        val categoryName = requireArguments().getString(ARG_CATEGORY_NAME).orEmpty()
        binding.tvDeleteLimitMessage.text =
            getString(R.string.goals_delete_limit_message, categoryName)

        binding.btnCancelDeleteLimit.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.btnConfirmDeleteLimit.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply {
                    putLong(RESULT_GOAL_ID, goalId)
                }
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "delete_limit_result"
        const val RESULT_GOAL_ID = "result_goal_id"

        private const val ARG_GOAL_ID = "arg_goal_id"
        private const val ARG_CATEGORY_NAME = "arg_category_name"

        fun newInstance(goalId: Long, categoryName: String): DeleteLimitBottomSheet {
            return DeleteLimitBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_GOAL_ID, goalId)
                    putString(ARG_CATEGORY_NAME, categoryName)
                }
            }
        }
    }
}
