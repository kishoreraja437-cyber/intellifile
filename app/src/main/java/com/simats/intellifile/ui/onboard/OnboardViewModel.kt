package com.simats.intellifile.ui.onboard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardViewModel : ViewModel() {

    private val _isStarted = MutableStateFlow(false)
    val isStarted: StateFlow<Boolean> = _isStarted.asStateFlow()

    fun onGetStartedClicked() {
        _isStarted.value = true
    }
}
