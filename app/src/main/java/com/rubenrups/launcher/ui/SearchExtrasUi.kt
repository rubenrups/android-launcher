package com.rubenrups.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rubenrups.launcher.data.Calculator
import com.rubenrups.launcher.data.ContactHit
import com.rubenrups.launcher.data.ContactSearch
import com.rubenrups.launcher.data.CurrencyConverter
import com.rubenrups.launcher.data.LauncherSettings
import com.rubenrups.launcher.data.SystemScreens
import com.rubenrups.launcher.data.UnitConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Answers shown above the app results while searching: a sum, a conversion, matching contacts, phone settings. */
@Composable
internal fun SearchExtras(query: String, settings: LauncherSettings, onLeave: () -> Unit) {
    val context = LocalContext.current
    val calculation = remember(query, settings.searchCalculator) {
        if (settings.searchCalculator) Calculator.evaluate(query)?.let(Calculator::format) else null
    }
    val conversion = remember(query, settings.searchConversions) {
        if (settings.searchConversions) UnitConverter.convert(query) else null
    }
    // Currency needs the network, so wait for a pause in typing and fetch off the main thread.
    val currency by produceState<String?>(null, query, settings.searchConversions) {
        value = null
        if (settings.searchConversions && CurrencyConverter.looksLikeCurrency(query)) {
            delay(500)
            value = withContext(Dispatchers.IO) { CurrencyConverter.convert(query) }
        }
    }
    val contacts by produceState<List<ContactHit>>(emptyList(), query, settings.searchContacts) {
        value = emptyList()
        if (settings.searchContacts) {
            delay(150)
            value = withContext(Dispatchers.IO) { ContactSearch.find(context, query) }
        }
    }
    val screens = remember(query, settings.searchSystemScreens) {
        if (settings.searchSystemScreens) SystemScreens.matching(query) else emptyList()
    }

    Column(Modifier.fillMaxWidth()) {
        calculation?.let { ResultRow("= $it", "Calculator · tap to copy") { copyToClipboard(context, it) } }
        conversion?.let { ResultRow(it, "Conversion · tap to copy") { copyToClipboard(context, it) } }
        currency?.let { ResultRow(it, "Currency · tap to copy") { copyToClipboard(context, it) } }
        contacts.forEach { hit ->
            ResultRow("👤 ${hit.name}", "Contact") {
                ContactSearch.open(context, hit)
                onLeave()
            }
        }
        screens.forEach { screen ->
            ResultRow("⚙️ ${screen.title}", "Phone settings") {
                runCatching { context.startActivity(Intent(screen.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                onLeave()
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("result", text))
    }
}
