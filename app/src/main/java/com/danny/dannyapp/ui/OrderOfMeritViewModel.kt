package com.danny.dannyapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.OrderOfMeritType
import com.danny.dannyapp.domain.usecase.GetOrderOfMeritUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class OrderOfMeritViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val seasonDao = db.seasonDao()
    private val placementDao = db.tournamentPlacementDao()
    private val getOrderOfMeritUseCase = GetOrderOfMeritUseCase(application)

    private val _oomEntries = MutableStateFlow<List<OrderOfMeritEntry>>(emptyList())
    val oomEntries: StateFlow<List<OrderOfMeritEntry>> = _oomEntries.asStateFlow()

    private val _ptOomEntries = MutableStateFlow<List<OrderOfMeritEntry>>(emptyList())
    val ptOomEntries: StateFlow<List<OrderOfMeritEntry>> = _ptOomEntries.asStateFlow()

    private val _pcOomEntries = MutableStateFlow<List<OrderOfMeritEntry>>(emptyList())
    val pcOomEntries: StateFlow<List<OrderOfMeritEntry>> = _pcOomEntries.asStateFlow()

    private val _etOomEntries = MutableStateFlow<List<OrderOfMeritEntry>>(emptyList())
    val etOomEntries: StateFlow<List<OrderOfMeritEntry>> = _etOomEntries.asStateFlow()

    private var currentCareerId: Long = -1L

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun loadOrderOfMerit(careerId: Long) {
        if (careerId <= 0L) return
        this.currentCareerId = careerId

        viewModelScope.launch {
            seasonDao.getSeasonsForCareer(careerId)
                .flatMapLatest { seasons ->
                    val seasonIds = seasons.map { it.id }
                    if (seasonIds.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        placementDao.getPlacementsFlowForSeasons(seasonIds)
                    }
                }
                .collect { _ ->
                    _oomEntries.value = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
                    _ptOomEntries.value = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
                    _pcOomEntries.value = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PC_OOM)
                    _etOomEntries.value = getOrderOfMeritUseCase(careerId, OrderOfMeritType.ET_OOM)
                }
        }
    }
}
