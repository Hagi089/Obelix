package de.hagi089.obelix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Eintrag einer Auswahlliste. [key] = null steht für „nichts gewählt" (z. B. „Alle"). */
data class SelectorOption(val key: String?, val text: String)

/**
 * Einfache Auswahl (Dropdown) mit Beschriftung. Bewusst nur mit Standardbausteinen gebaut
 * (Button + Menü), damit sie auch mit dem Screenreader gut bedienbar ist.
 */
@Composable
fun SelectorField(
    label: String,
    value: String,
    options: List<SelectorOption>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center) {
                    Text(text = label, style = MaterialTheme.typography.labelSmall)
                    Text(text = value, style = MaterialTheme.typography.bodyLarge)
                }
                Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.text) },
                        onClick = {
                            expanded = false
                            onSelect(option.key)
                        },
                    )
                }
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}
