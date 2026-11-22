package com.danny.dannyapp.ui.oom

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.appContainer
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StickyUserState {
    object Hidden : StickyUserState
    data class VisibleTop(val entry: OrderOfMeritEntry, val userIndex: Int) : StickyUserState
    data class VisibleBottom(val entry: OrderOfMeritEntry, val userIndex: Int) : StickyUserState
}

class OrderOfMeritViewModel(application: Application) : AndroidViewModel(application) {

    private val getOrderOfMeritUseCase = application.appContainer.getOrderOfMeritUseCase

    private val _oomEntries = MutableStateFlow<List<OrderOfMeritEntry>>(emptyList())
    val oomEntries: StateFlow<List<OrderOfMeritEntry>> = _oomEntries.asStateFlow()

    private val _ptOomEntries = MutableStateFlow<List<OrderOfMeritEntry>>(emptyList())
    val ptOomEntries: StateFlow<List<OrderOfMeritEntry>> = _ptOomEntries.asStateFlow()

    private val _pcOomEntries = MutableStateFlow<List<OrderOfMeritEntry>>(emptyList())
    val pcOomEntries: StateFlow<List<OrderOfMeritEntry>> = _pcOomEntries.asStateFlow()

    private val _etOomEntries = MutableStateFlow<List<OrderOfMeritEntry>>(emptyList())
    val etOomEntries: StateFlow<List<OrderOfMeritEntry>> = _etOomEntries.asStateFlow()

    private val _stickyUserState = MutableStateFlow<StickyUserState>(StickyUserState.Hidden)
    val stickyUserState: StateFlow<StickyUserState> = _stickyUserState.asStateFlow()

    private val _scrollToPositionEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToPositionEvent: SharedFlow<Int> = _scrollToPositionEvent.asSharedFlow()

    private val _selectedOomType = MutableStateFlow<OrderOfMeritType>(OrderOfMeritType.OOM)
    val selectedOomType: StateFlow<OrderOfMeritType> = _selectedOomType.asStateFlow()

    fun selectOomType(type: OrderOfMeritType) {
        _selectedOomType.value = type
    }

    private var currentCareerId: Long = -1L

    fun updateStickyUserState(state: StickyUserState) {
        _stickyUserState.value = state
    }

    fun requestScrollToUser(userIndex: Int) {
        _scrollToPositionEvent.tryEmit(userIndex)
    }

    fun loadOrderOfMerit(careerId: Long) {
        if (careerId <= 0L) return
        this.currentCareerId = careerId

        viewModelScope.launch {
            launch {
                getOrderOfMeritUseCase.observeOrderOfMerit(careerId, OrderOfMeritType.OOM)
                    .collect { _oomEntries.value = it }
            }
            launch {
                getOrderOfMeritUseCase.observeOrderOfMerit(careerId, OrderOfMeritType.PT_OOM)
                    .collect { _ptOomEntries.value = it }
            }
            launch {
                getOrderOfMeritUseCase.observeOrderOfMerit(careerId, OrderOfMeritType.PC_OOM)
                    .collect { _pcOomEntries.value = it }
            }
            launch {
                getOrderOfMeritUseCase.observeOrderOfMerit(careerId, OrderOfMeritType.ET_OOM)
                    .collect { _etOomEntries.value = it }
            }
        }
    }
}
