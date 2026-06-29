package ru.bruce.dispatcherinbox.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.data.InboxRepository

class QueueViewModel(private val repository: InboxRepository) : ViewModel() {
    val items: StateFlow<List<InboxItemWithAttachments>> = repository.items.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun retry(id: String) = viewModelScope.launch { repository.retry(id) }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }

    class Factory(private val repository: InboxRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = QueueViewModel(repository) as T
    }
}
