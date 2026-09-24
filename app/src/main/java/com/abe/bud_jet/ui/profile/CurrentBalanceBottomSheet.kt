package com.abe.bud_jet.ui.profile

import com.abe.bud_jet.utils.AmountParser
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.abe.bud_jet.R
import com.abe.bud_jet.databinding.BottomSheetCurrentBalanceBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment

class CurrentBalanceBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetCurrentBalanceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCurrentBalanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val current = requireArguments().getDouble(ARG_CURRENT, 0.0)
        if (current != 0.0) {
            binding.etCurrentBalance.setText(AmountParser.toEditable(current))
            binding.etCurrentBalance.setSelection(binding.etCurrentBalance.text?.length ?: 0)
        }

        binding.btnCancelBalance.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnSaveBalance.setOnClickListener {
            val parsed = AmountParser.parse(binding.etCurrentBalance.text?.toString())
            if (parsed == null) {
                Toast.makeText(requireContext(), getString(R.string.common_enter_valid_amount), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply { putDouble(RESULT_BALANCE, parsed) }
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "current_balance_result"
        const val RESULT_BALANCE = "current_balance_value"
        private const val ARG_CURRENT = "arg_current_balance"

        fun newInstance(currentBalance: Double): CurrentBalanceBottomSheet {
            return CurrentBalanceBottomSheet().apply {
                arguments = Bundle().apply { putDouble(ARG_CURRENT, currentBalance) }
            }
        }
    }
}
