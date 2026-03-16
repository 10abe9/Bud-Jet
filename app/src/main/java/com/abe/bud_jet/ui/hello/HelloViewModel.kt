package com.abe.bud_jet.ui.hello

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HelloViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is Hello Fragment"
    }
    val text: LiveData<String> = _text
}