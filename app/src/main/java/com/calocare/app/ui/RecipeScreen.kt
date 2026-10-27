package com.calocare.app.ui

import com.calocare.app.L
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.calocare.app.AppViewModel
import com.calocare.app.data.AppData
import com.calocare.app.net.Recipe
import com.calocare.app.net.RecipeItem
import com.calocare.app.net.RecipeListItem
import kotlinx.coroutines.launch

@Composable
fun RecipeScreen(vm: AppViewModel, data: AppData, padding: PaddingValues) {
    var recipes by remember { mutableStateOf<List<RecipeListItem>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var editingRecipe by remember { mutableStateOf<Recipe?>(null) }
    var selectedRecipeId by remember { mutableStateOf<String?>(null) }

    // Load recipes on first load
    if (recipes == null && !loading) {
        loading = true
        vm.viewModelScope.launch {
            recipes = vm.api.getRecipes()
            loading = false
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("📖 Công thức yêu thích", "📖 My recipes"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { editingRecipe = Recipe(id = "", name = "", items = emptyList()) }) {
                    Icon(Icons.Default.Add, null)
                    Text(L(" Tạo", " New"))
                }
            }
        }

        if (loading) {
            item {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        } else if (recipes.isNullOrEmpty()) {
            item {
                Text(L("Bạn chưa lưu công thức nào. Hãy tạo công thức đầu tiên!", "No recipes yet. Create your first one!"), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(recipes ?: emptyList(), key = { it.id }) { recipe ->
                RecipeCard(recipe, onClick = { selectedRecipeId = it }, onEdit = { editingRecipe = recipe as? Recipe })
            }
        }
    }

    // Edit dialog
    editingRecipe?.let { recipe ->
        RecipeEditDialog(recipe, onSave = { saved ->
            vm.viewModelScope.launch {
                vm.api.saveRecipe(saved)
                recipes = vm.api.getRecipes()
                editingRecipe = null
            }
        }, onDismiss = { editingRecipe = null })
    }

    // View recipe
    selectedRecipeId?.let { id ->
        vm.viewModelScope.launch {
            val recipe = vm.api.getRecipe(id)
            if (recipe != null) {
                editingRecipe = recipe
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeListItem, onClick: (String) -> Unit, onEdit: (RecipeListItem) -> Unit) {
    SectionCard(
        title = recipe.name,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${recipe.kcal} kcal/${recipe.servings} phần", style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { onEdit(recipe) }) { Icon(Icons.Default.Close, L("Sửa", "Edit")) }
            }
        },
    ) {
        if (recipe.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                recipe.tags.forEach { tag ->
                    Text(tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        FilledTonalButton(onClick = { onClick(recipe.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(L("Xem chi tiết", "View details"))
        }
    }
}

@Composable
private fun RecipeEditDialog(
    initial: Recipe,
    onSave: (Recipe) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description ?: "") }
    var servings by remember { mutableStateOf(initial.servings.toString()) }
    var prepTime by remember { mutableStateOf(initial.prepTime?.toString() ?: "") }
    var cookTime by remember { mutableStateOf(initial.cookTime?.toString() ?: "") }
    var items by remember { mutableStateOf(initial.items) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isEmpty()) L("Tạo công thức mới", "New recipe") else L("Sửa công thức", "Edit recipe")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(L("Tên công thức", "Recipe name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text(L("Mô tả", "Description")) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(servings, { servings = it }, L("Khẩu phần", "Servings"), Modifier.weight(1f))
                    NumberField(prepTime, { prepTime = it }, L("Chuẩn bị (phút)", "Prep (min)"), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(cookTime, { cookTime = it }, L("Nấu (phút)", "Cook (min)"), Modifier.weight(1f))
                }

                Text(L("Thành phần (${items.size})", "Ingredients (${items.size})"), fontWeight = FontWeight.SemiBold)
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name ?: item.foodId, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text("×${item.qty}", style = MaterialTheme.typography.labelSmall)
                        IconButton(onClick = { items = items.filterNot { it == item } }, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave(initial.copy(
                    name = name.trim(),
                    description = description.ifBlank { null },
                    servings = servings.toIntOrNull() ?: 1,
                    prepTime = prepTime.toIntOrNull(),
                    cookTime = cookTime.toIntOrNull(),
                    items = items,
                ))
            }) { Text(L("Lưu", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Hủy", "Cancel")) } },
    )
}
