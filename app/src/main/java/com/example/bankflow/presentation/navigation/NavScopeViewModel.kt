package com.example.bankflow.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class NavScopeViewModel : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    fun clear() = viewModelStore.clear()
    override fun onCleared() = viewModelStore.clear()
    }
