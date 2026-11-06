package com.bioakustic.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bioakustic.app.AppContainer
import com.bioakustic.app.data.db.DetectionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val entries: StateFlow<List<DetectionEntity>> = _query
        .flatMapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun delete(entry: DetectionEntity) {
        viewModelScope.launch { repository.delete(entry) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(container) as T
        }
    }
}
