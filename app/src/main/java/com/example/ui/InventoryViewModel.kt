package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.InventoryItem
import com.example.data.InventoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryViewModel(private val repository: InventoryRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val inventoryItems: StateFlow<List<InventoryItem>> = _searchQuery
        .flatMapLatest { query ->
            if (query.trim().isEmpty()) {
                repository.allItems
            } else {
                repository.searchItems(query.trim())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addOrUpdateItem(
        id: Int?,
        name: String,
        category: String,
        quantity: Double,
        unit: String,
        minStock: Double,
        costPrice: Double?
    ) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val timestamp = sdf.format(Date())
            
            val item = InventoryItem(
                id = id ?: 0,
                name = name.trim(),
                category = category.trim(),
                quantity = quantity,
                unit = unit.trim(),
                minStock = minStock,
                costPrice = costPrice,
                updatedAt = timestamp
            )
            
            if (id == null) {
                repository.insert(item)
            } else {
                repository.update(item)
            }
        }
    }

    fun deleteItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun quickAdjustQuantity(item: InventoryItem, adjustment: Double) {
        viewModelScope.launch {
            val newQty = (item.quantity + adjustment).coerceAtLeast(0.0)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val timestamp = sdf.format(Date())
            repository.updateQuantity(item.id, newQty, timestamp)
        }
    }
}

class InventoryViewModelFactory(private val repository: InventoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InventoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
