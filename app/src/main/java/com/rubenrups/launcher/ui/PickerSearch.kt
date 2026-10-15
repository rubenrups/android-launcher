package com.rubenrups.launcher.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The search box at the top of a picker dialog. Not focused automatically, so the keyboard only appears on request. */
@Composable
fun PickerSearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Search") },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** True when every word typed appears somewhere in [texts], ignoring case. A blank query matches everything. */
fun matchesQuery(query: String, vararg texts: String): Boolean {
    val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = texts.joinToString(" ").lowercase()
    return words.all { it in haystack }
}
