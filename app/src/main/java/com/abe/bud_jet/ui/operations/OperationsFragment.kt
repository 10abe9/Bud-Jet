package com.abe.bud_jet.ui.operations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.abe.bud_jet.databinding.FragmentOperationsBinding

class OperationsFragment : Fragment() {

    private var _binding: FragmentOperationsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val OperationsViewModel =
            ViewModelProvider(this).get(OperationsViewModel::class.java)

        _binding = FragmentOperationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textOperations
        OperationsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}