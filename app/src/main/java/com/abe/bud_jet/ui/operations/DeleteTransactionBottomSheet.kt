package com.abe.bud_jet.ui.operations

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.abe.bud_jet.R
import com.abe.bud_jet.database.FinanceRepositoryProvider
import com.abe.bud_jet.databinding.BottomSheetDeleteTransactionBinding
import com.abe.bud_jet.ui.common.BaseBottomSheetDialogFragment
import kotlinx.coroutines.launch

class DeleteTransactionBottomSheet : BaseBottomSheetDialogFragment() {

    private var _binding: BottomSheetDeleteTransactionBinding? = null
    private val binding get() = _binding!!

    private val repository by lazy { FinanceRepositoryProvider.get(requireContext()) }

    override fun getTheme(): Int = R.style.ThemeOverlay_BudJet_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDeleteTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val id = requireArguments().getLong(ARG_ID)
        val message = requireArguments().getString(ARG_MESSAGE).orEmpty()

        binding.tvDeleteMessage.text = message

        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnDelete.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val deleted = repository.deleteTransaction(id)
                Toast.makeText(
                    requireContext(),
                    if (deleted) getString(R.string.edit_transaction_transaction_deleted)
                    else getString(R.string.edit_transaction_unable_to_delete),
                    Toast.LENGTH_SHORT
                ).show()
                if (deleted) dismissAllowingStateLoss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "arg_id"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(
            id: Long,
            message: String
        ): DeleteTransactionBottomSheet {
            return DeleteTransactionBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ID, id)
                    putString(ARG_MESSAGE, message)
                }
            }
        }
    }
}

