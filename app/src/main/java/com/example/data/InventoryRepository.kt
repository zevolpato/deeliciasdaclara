package com.example.data

import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val dao: InventoryItemDao) {
    val allItems: Flow<List<InventoryItem>> = dao.getAllItems()

    fun searchItems(query: String): Flow<List<InventoryItem>> {
        return dao.searchItems("%$query%")
    }

    suspend fun insert(item: InventoryItem) {
        dao.insertItem(item)
    }

    suspend fun update(item: InventoryItem) {
        dao.updateItem(item)
    }

    suspend fun delete(item: InventoryItem) {
        dao.deleteItem(item)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteItemById(id)
    }

    suspend fun updateQuantity(id: Int, quantity: Double, updatedAt: String) {
        dao.updateQuantity(id, quantity, updatedAt)
    }
}
