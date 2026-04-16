package com.abe.bud_jet.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.abe.bud_jet.databinding.BottomSheetSavingGoalFormBinding
import com.abe.bud_jet.R
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavingGoalFormBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetSavingGoalFormBinding? = null
    private val binding get() = _binding!!

    private var selectedDeadlineMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedDeadlineMillis = arguments?.getLong(ARG_DEADLINE)?.takeIf { it > 0L }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSavingGoalFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val amount = arguments?.getDouble(ARG_AMOUNT) ?: 0.0
        if (amount > 0) binding.etTargetAmount.setText(amount.toString())
        renderDeadline()

        binding.btnPickDeadline.setOnClickListener { openDatePicker() }
        binding.btnClearDeadline.setOnClickListener {
            selectedDeadlineMillis = null
            renderDeadline()
        }
        binding.btnSaveSavingGoal.setOnClickListener {
            val parsedAmount = binding.etTargetAmount.text?.toString()?.trim()?.toDoubleOrNull()
            if (parsedAmount == null || parsedAmount <= 0) {
                binding.etTargetAmount.error = getString(R.string.common_enter_valid_amount)
                return@setOnClickListener
            }
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply {
                    putDouble(RESULT_AMOUNT, parsedAmount)
                    putLong(RESULT_DEADLINE, selectedDeadlineMillis ?: 0L)
                }
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(selectedDeadlineMillis ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener {
            selectedDeadlineMillis = it
            renderDeadline()
        }
        picker.show(parentFragmentManager, "saving_goal_date_picker")
    }

    private fun renderDeadline() {
        val value = selectedDeadlineMillis?.let {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
        } ?: getString(R.string.bottomsheet_no_deadline_selected)
        binding.tvDeadlineValue.text = value
    }

    companion object {
        const val RESULT_KEY = "saving_goal_form_result"
        const val RESULT_AMOUNT = "result_amount"
        const val RESULT_DEADLINE = "result_deadline"

        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_DEADLINE = "arg_deadline"

        fun newInstance(amount: Double?, deadlineTimestamp: Long?): SavingGoalFormBottomSheet {
            return SavingGoalFormBottomSheet().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_AMOUNT, amount ?: 0.0)
                    putLong(ARG_DEADLINE, deadlineTimestamp ?: 0L)
                }
            }
        }
    }
}
