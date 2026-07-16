package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.InventoryItem
import com.example.data.InventoryRepository
import com.example.ui.InventoryViewModel
import com.example.ui.InventoryViewModelFactory
import com.example.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var repository: InventoryRepository

    private val viewModel: InventoryViewModel by viewModels {
        InventoryViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = AppDatabase.getDatabase(applicationContext)
        repository = InventoryRepository(database.inventoryItemDao())

        // Auto pre-populate if database is empty on very first launch only
        val prefs = getSharedPreferences("confeitaria_prefs", MODE_PRIVATE)
        val isPrepopulated = prefs.getBoolean("is_prepopulated", false)
        if (!isPrepopulated) {
            lifecycleScope.launch {
                val currentItems = repository.allItems.first()
                if (currentItems.isEmpty()) {
                    prePopulateDatabase()
                }
                prefs.edit().putBoolean("is_prepopulated", true).apply()
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddDialog = true },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            shape = CircleShape,
                            modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Adicionar Insumo", modifier = Modifier.size(28.dp))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private var showAddDialog by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(
        modifier: Modifier = Modifier,
        viewModel: InventoryViewModel
    ) {
        val items by viewModel.inventoryItems.collectAsStateWithLifecycle()
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
        
        var selectedCategory by remember { mutableStateOf("Todos") }
        var showOnlyLowStock by remember { mutableStateOf(false) }
        var itemToDelete by remember { mutableStateOf<InventoryItem?>(null) }
        var itemToEdit by remember { mutableStateOf<InventoryItem?>(null) }
        
        val context = LocalContext.current

        // Extract list of unique categories
        val categories = remember(items) {
            val cats = items.map { it.category }.distinct().toMutableList()
            cats.add(0, "Todos")
            cats
        }

        // Filter items locally by category and low stock status
        val filteredItems = remember(items, selectedCategory, showOnlyLowStock) {
            val baseList = if (selectedCategory == "Todos") {
                items
            } else {
                items.filter { it.category.equals(selectedCategory, ignoreCase = true) }
            }
            if (showOnlyLowStock) {
                baseList.filter { it.quantity <= it.minStock }
            } else {
                baseList
            }
        }

        // Calculate statistics for the dashboard
        val totalProductsCount = items.size
        val alertCount = items.count { it.quantity <= it.minStock }
        val totalValue = items.sumOf { (it.costPrice ?: 0.0) * it.quantity }

        Column(
            modifier = modifier
                .fillMaxSize()
        ) {
            // Elegant Header Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondary)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "@DeeliciasdaClara",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Controle de Estoque",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar with magnifying glass and clear button
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Buscar ingrediente ou categoria...", color = TextLightBrown) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Buscar", tint = ChocolateBrown) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Limpar", tint = ChocolateBrown)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        focusedTextColor = TextDarkCocoa,
                        unfocusedTextColor = TextDarkCocoa
                    ),
                    singleLine = true
                )
            }

            // Quick Stats Dashboard Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Itens", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        Text("$totalProductsCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    
                    Box(modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color.White.copy(alpha = 0.3f)))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1.2f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (showOnlyLowStock) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { showOnlyLowStock = !showOnlyLowStock }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber, 
                            contentDescription = "Filtrar Baixo Estoque", 
                            tint = if (showOnlyLowStock) Color(0xFFFFEB3B) else if (alertCount > 0) Color(0xFFFFEB3B) else Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Baixo Estoque",
                            fontSize = 11.sp,
                            fontWeight = if (showOnlyLowStock) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White
                        )
                        Text(
                            text = "$alertCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showOnlyLowStock) Color(0xFFFFEB3B) else if (alertCount > 0) Color(0xFFFFEB3B) else Color.White
                        )
                    }

                    Box(modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color.White.copy(alpha = 0.3f)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.4f)) {
                        Icon(Icons.Outlined.Payments, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Valor de Custo", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        Text(String.format(Locale.getDefault(), "R$ %.2f", totalValue), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Category Filter Pills
            if (categories.size > 2) {
                LazyRow(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory.equals(category, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = TextDarkCocoa
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = Color.Transparent,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Active Filter Information Banner
            if (showOnlyLowStock) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FilterList,
                                contentDescription = null,
                                tint = AlertRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mostrando apenas itens com baixo estoque",
                                fontSize = 12.sp,
                                color = AlertRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = { showOnlyLowStock = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Limpar Filtro",
                                tint = AlertRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Main Inventory List
            Box(modifier = Modifier.weight(1f)) {
                if (filteredItems.isEmpty()) {
                    EmptyStateView {
                        lifecycleScope.launch {
                            prePopulateDatabase()
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            InventoryCardView(
                                item = item,
                                onEdit = { itemToEdit = item },
                                onDelete = { itemToDelete = item },
                                onQuickIncrease = { viewModel.quickAdjustQuantity(item, 1.0) },
                                onQuickDecrease = { viewModel.quickAdjustQuantity(item, -1.0) }
                            )
                        }
                    }
                }
            }
        }

        // Add Dialog
        if (showAddDialog) {
            ItemFormDialog(
                onDismiss = { showAddDialog = false },
                onSave = { name, category, quantity, unit, minStock, costPrice ->
                    viewModel.addOrUpdateItem(null, name, category, quantity, unit, minStock, costPrice)
                    showAddDialog = false
                    Toast.makeText(context, "Insumo adicionado com sucesso!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Edit Dialog
        if (itemToEdit != null) {
            ItemFormDialog(
                item = itemToEdit,
                onDismiss = { itemToEdit = null },
                onSave = { name, category, quantity, unit, minStock, costPrice ->
                    viewModel.addOrUpdateItem(itemToEdit!!.id, name, category, quantity, unit, minStock, costPrice)
                    itemToEdit = null
                    Toast.makeText(context, "Insumo atualizado!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Delete confirmation Dialog
        if (itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteItem(itemToDelete!!)
                            itemToDelete = null
                            Toast.makeText(context, "Insumo excluído!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                    ) {
                        Text("Excluir", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancelar", color = TextLightBrown)
                    }
                },
                title = { Text("Excluir Insumo", fontWeight = FontWeight.Bold, color = TextDarkCocoa) },
                text = { Text("Tem certeza que deseja remover ${itemToDelete!!.name} do estoque?", color = TextDarkCocoa) },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }

    @Composable
    fun InventoryCardView(
        item: InventoryItem,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onQuickIncrease: () -> Unit,
        onQuickDecrease: () -> Unit
    ) {
        val isLowStock = item.quantity <= item.minStock
        val cardBorder = if (isLowStock) BorderStroke(2.dp, AlertRed) else BorderStroke(1.dp, DividerLine)
        val cardBg = if (isLowStock) AlertBackground else CreamySurface

        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = cardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header inside Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkCocoa,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.category,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            color = TextLightBrown
                        )
                    }

                    if (isLowStock) {
                        Box(
                            modifier = Modifier
                                .background(AlertRed, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Repor Estoque",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detail Values Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Estoque Atual", fontSize = 11.sp, color = TextLightBrown)
                        Text(
                            text = "${formatQuantity(item.quantity)} ${item.unit}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLowStock) AlertRed else TextDarkCocoa
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mínimo Requerido", fontSize = 11.sp, color = TextLightBrown)
                        Text(
                            text = "${formatQuantity(item.minStock)} ${item.unit}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDarkCocoa
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preço de Custo", fontSize = 11.sp, color = TextLightBrown)
                        Text(
                            text = item.costPrice?.let { String.format(Locale.getDefault(), "R$ %.2f", it) } ?: "R$ 0,00",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDarkCocoa
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DividerLine.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                // Action Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Quantity adjustments (- / +)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(DividerLine, RoundedCornerShape(20.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onQuickDecrease,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Diminuir Estoque", tint = ChocolateBrown, modifier = Modifier.size(18.dp))
                        }
                        
                        Text(
                            text = "Ajuste Rápido",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkCocoa,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = onQuickIncrease,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Aumentar Estoque", tint = ChocolateBrown, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Edit & Delete Icons
                    Row {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Editar", tint = TextLightBrown)
                        }
                        
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Excluir", tint = AlertRed)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyStateView(onPopulate: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Cake,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Seu estoque está vazio!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkCocoa,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cadastre insumos como Farinha, Manteiga, Chocolate, Embalagens ou produtos prontos.",
                fontSize = 14.sp,
                color = TextLightBrown,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onPopulate,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Preencher com Itens de Teste", fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    fun ItemFormDialog(
        item: InventoryItem? = null,
        onDismiss: () -> Unit,
        onSave: (name: String, category: String, quantity: Double, unit: String, minStock: Double, costPrice: Double?) -> Unit
    ) {
        var name by remember { mutableStateOf(item?.name ?: "") }
        var category by remember { mutableStateOf(item?.category ?: "") }
        var quantityString by remember { mutableStateOf(item?.quantity?.let { formatQuantity(it) } ?: "0") }
        var unit by remember { mutableStateOf(item?.unit ?: "kg") }
        var minStockString by remember { mutableStateOf(item?.minStock?.let { formatQuantity(it) } ?: "1") }
        var costPriceString by remember { mutableStateOf(item?.costPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "") }

        var nameError by remember { mutableStateOf(false) }
        var categoryError by remember { mutableStateOf(false) }
        var unitError by remember { mutableStateOf(false) }

        val categoriesSuggestions = listOf("Ingredientes", "Embalagens", "Produtos Prontos", "Utensílios")
        val unitsSuggestions = listOf("kg", "g", "L", "ml", "un", "pacote")

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = if (item == null) "Novo Insumo" else "Editar Insumo",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = TextDarkCocoa,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Nome
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                nameError = it.isBlank()
                            },
                            label = { Text("Nome do Insumo *", color = TextLightBrown) },
                            isError = nameError,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = TextLightBrown.copy(alpha = 0.5f),
                                focusedTextColor = TextDarkCocoa,
                                unfocusedTextColor = TextDarkCocoa
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (nameError) {
                            Text("O nome é obrigatório", color = AlertRed, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }
                    }

                    // Categoria
                    item {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {
                                category = it
                                categoryError = it.isBlank()
                            },
                            label = { Text("Categoria *", color = TextLightBrown) },
                            isError = categoryError,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = TextLightBrown.copy(alpha = 0.5f),
                                focusedTextColor = TextDarkCocoa,
                                unfocusedTextColor = TextDarkCocoa
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (categoryError) {
                            Text("A categoria é obrigatória", color = AlertRed, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }

                        // Category Pills suggestions
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categoriesSuggestions.forEach { cat ->
                                Box(
                                    modifier = Modifier
                                        .background(DividerLine, RoundedCornerShape(12.dp))
                                        .clickable { 
                                            category = cat
                                            categoryError = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(cat, fontSize = 11.sp, color = TextDarkCocoa)
                                }
                            }
                        }
                    }

                    // Qtd e Unidade Row
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = quantityString,
                                onValueChange = { quantityString = it },
                                label = { Text("Qtd *", color = TextLightBrown) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = TextLightBrown.copy(alpha = 0.5f),
                                    focusedTextColor = TextDarkCocoa,
                                    unfocusedTextColor = TextDarkCocoa
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = unit,
                                onValueChange = {
                                    unit = it
                                    unitError = it.isBlank()
                                },
                                label = { Text("Unidade *", color = TextLightBrown) },
                                isError = unitError,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = TextLightBrown.copy(alpha = 0.5f),
                                    focusedTextColor = TextDarkCocoa,
                                    unfocusedTextColor = TextDarkCocoa
                                ),
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                        if (unitError) {
                            Text("A unidade é obrigatória", color = AlertRed, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }

                        // Unit Pills suggestions
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            unitsSuggestions.forEach { un ->
                                Box(
                                    modifier = Modifier
                                        .background(DividerLine, RoundedCornerShape(12.dp))
                                        .clickable { 
                                            unit = un
                                            unitError = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(un, fontSize = 11.sp, color = TextDarkCocoa)
                                }
                            }
                        }
                    }

                    // Estoque Mínimo e Preço de Custo Row
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = minStockString,
                                onValueChange = { minStockString = it },
                                label = { Text("Estoque Mín. *", color = TextLightBrown) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = TextLightBrown.copy(alpha = 0.5f),
                                    focusedTextColor = TextDarkCocoa,
                                    unfocusedTextColor = TextDarkCocoa
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = costPriceString,
                                onValueChange = { costPriceString = it },
                                label = { Text("Custo Un. (R$)", color = TextLightBrown) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = TextLightBrown.copy(alpha = 0.5f),
                                    focusedTextColor = TextDarkCocoa,
                                    unfocusedTextColor = TextDarkCocoa
                                ),
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                    }

                    // Dialog Actions
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.textButtonColors(contentColor = TextLightBrown)
                            ) {
                                Text("Cancelar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Button(
                                onClick = {
                                    if (name.isBlank()) {
                                        nameError = true
                                        return@Button
                                    }
                                    if (category.isBlank()) {
                                        categoryError = true
                                        return@Button
                                    }
                                    if (unit.isBlank()) {
                                        unitError = true
                                        return@Button
                                    }

                                    val qty = quantityString.replace(",", ".").toDoubleOrNull() ?: 0.0
                                    val minStk = minStockString.replace(",", ".").toDoubleOrNull() ?: 1.0
                                    val cost = costPriceString.replace(",", ".").toDoubleOrNull()

                                    onSave(name, category, qty, unit, minStk, cost)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Salvar", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatQuantity(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    private suspend fun prePopulateDatabase() {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        val timestamp = sdf.format(Date())

        val testItems = listOf(
            InventoryItem(
                name = "Chocolate Belga 50%",
                category = "Ingredientes",
                quantity = 3.0,
                unit = "kg",
                minStock = 5.0,
                costPrice = 75.0,
                updatedAt = timestamp
            ),
            InventoryItem(
                name = "Farinha de Trigo Especial",
                category = "Ingredientes",
                quantity = 25.0,
                unit = "kg",
                minStock = 10.0,
                costPrice = 4.50,
                updatedAt = timestamp
            ),
            InventoryItem(
                name = "Manteiga sem Sal",
                category = "Ingredientes",
                quantity = 1.5,
                unit = "kg",
                minStock = 4.0,
                costPrice = 28.0,
                updatedAt = timestamp
            ),
            InventoryItem(
                name = "Embalagem para Tortas G",
                category = "Embalagens",
                quantity = 15.0,
                unit = "un",
                minStock = 20.0,
                costPrice = 2.50,
                updatedAt = timestamp
            ),
            InventoryItem(
                name = "Granulado Colorido",
                category = "Ingredientes",
                quantity = 8.0,
                unit = "kg",
                minStock = 5.0,
                costPrice = 18.0,
                updatedAt = timestamp
            ),
            InventoryItem(
                name = "Bolo de Cenoura Inteiro",
                category = "Produtos Prontos",
                quantity = 6.0,
                unit = "un",
                minStock = 2.0,
                costPrice = 15.0,
                updatedAt = timestamp
            )
        )

        // Clear existing and populate
        val current = repository.allItems.first()
        for (item in current) {
            repository.delete(item)
        }
        for (item in testItems) {
            repository.insert(item)
        }
    }
}
