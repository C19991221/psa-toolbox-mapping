package com.bandwa.openadb.presentation.main.binding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.entity.MusicPlayer
import com.bandwa.openadb.presentation.dialog.PlayerSelectDialog
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun AppKeyBindingSection(
    shortClickTitle: String,
    longClickTitle: String,
    binding: AppKeyBindingState,
    onRequestInstalledApps: () -> Unit,
    onSelectShortClickApp: (String) -> Unit,
    onSelectLongClickApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showShortClickDialog by remember { mutableStateOf(false) }
    var showLongClickDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        BindingRow(
            title = shortClickTitle,
            buttonText = "选择短按APP",
            app = binding.shortClickApp,
            onClick = {
                onRequestInstalledApps()
                showShortClickDialog = true
            },
        )
        BindingRow(
            title = longClickTitle,
            buttonText = "选择长按APP",
            app = binding.longClickApp,
            onClick = {
                onRequestInstalledApps()
                showLongClickDialog = true
            },
        )
    }

    if (showShortClickDialog) {
        PlayerSelectDialog(
            themeManager = LocalThemeManager.current,
            onDismissRequest = { showShortClickDialog = false },
            onSelect = {
                onSelectShortClickApp(it)
                showShortClickDialog = false
            },
            players = binding.installedApps.toMutableList(),
        )
    }

    if (showLongClickDialog) {
        PlayerSelectDialog(
            themeManager = LocalThemeManager.current,
            onDismissRequest = { showLongClickDialog = false },
            onSelect = {
                onSelectLongClickApp(it)
                showLongClickDialog = false
            },
            players = binding.installedApps.toMutableList(),
        )
    }
}

@Composable
private fun BindingRow(
    title: String,
    buttonText: String,
    app: MusicPlayer?,
    onClick: () -> Unit,
) {
    SelectionText(
        modifier = Modifier.padding(top = 32.dp),
        text = title,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(text = buttonText) { onClick() }
        app?.let {
            Image(
                modifier = Modifier
                    .padding(start = 32.dp)
                    .size(48.dp),
                painter = rememberDrawablePainter(drawable = it.iconRes),
                contentDescription = title,
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = it.name,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textColorSecondary,
            )
        }
    }
}
