package com.rubenrups.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A tap target that screen readers announce as a button with [label]. For controls drawn as a symbol, an icon or a
 * single glyph (▲, −, ▶), which would otherwise be read out as "black up-pointing triangle" or not at all.
 */
fun Modifier.accessibleButton(label: String, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }
