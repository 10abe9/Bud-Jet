package com.abe.bud_jet.ui.hello

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HelloViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        // This value is not currently shown in the UI; keep it empty to avoid hardcoded text.
        value = ""
    }
    val text: LiveData<String> = _text
}