package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val name: String,
    
    val category: String,
    
    val quantity: Double,
    
    val unit: String,
    
    @ColumnInfo(name = "min_stock")
    val minStock: Double,
    
    @ColumnInfo(name = "cost_price")
    val costPrice: Double?,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
