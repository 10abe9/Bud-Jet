package com.abe.bud_jet.ui.operations

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class OperationsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is Operations Fragment"
    }
    val text: LiveData<String> = _text
}