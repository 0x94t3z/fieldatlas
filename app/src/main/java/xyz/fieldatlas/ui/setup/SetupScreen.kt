package xyz.fieldatlas.ui.setup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.presentation.SetupNextAction
import xyz.fieldatlas.ui.presentation.deriveSetupNextAction
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasColors
import xyz.fieldatlas.ui.theme.FieldAtlasHeader
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasInformationAction
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton

@Composable
fun SetupScreen(
    packs: List<InstalledAsset>,
    importing: Boolean,
    error: String?,
    onImportPack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val hasModel = packs.any { it.type == PackType.MODEL }
    val hasKnowledge = packs.any { it.type == PackType.KNOWLEDGE }
    val nextAction = deriveSetupNextAction(packs, importing)
    var showErrorDetails by rememberSaveable(error) { mutableStateOf(false) }
    val fieldPanelColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        FieldAtlasColors.SageWash
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(20.dp))
                SetupHeader()
            }
            item {
                PackRequirementCard(
                    "1", "Import model pack", hasModel, "Select a model that runs on your device.",
                    fieldPanelColor, actionLabel(nextAction), onImportPack,
                    nextAction != SetupNextAction.Importing && nextAction != SetupNextAction.OpenResearch,
                )
            }
            item {
                PackRequirementCard(
                    "2", "Knowledge pack optional", hasKnowledge,
                    "Add one later in Library for source-backed answers.", fieldPanelColor,
                )
            }
            item { OfflineByDesignNote(fieldPanelColor) }
            if (error != null) {
                item {
                    FieldAtlasCard(Modifier.fillMaxWidth()) {
                        Text(
                            "This pack could not be imported. Check the file and try again.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FieldAtlasInformationAction(
                            label = if (showErrorDetails) "Hide technical details" else "Technical details",
                            onClick = { showErrorDetails = !showErrorDetails },
                            expanded = showErrorDetails,
                        )
                        if (showErrorDetails) Text(error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SetupHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "FIELD ATLAS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 4.sp,
        )
        FieldAtlasHeader(
            title = "Set up Field Atlas",
            subtitle = "Import a model pack to start. Knowledge packs are optional and add cited answers.",
        )
    }
}

@Composable
private fun PackRequirementCard(
    number: String,
    title: String,
    ready: Boolean,
    detail: String,
    iconBackground: androidx.compose.ui.graphics.Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = iconBackground,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (ready) Icon(FieldAtlasIcons.Check, contentDescription = "$title ready",
                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        else Text(number, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (ready) "Ready and verified" else detail, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (actionLabel != null && onAction != null) {
                FieldAtlasPrimaryButton(actionLabel, onAction, Modifier.fillMaxWidth(),
                    enabled = actionEnabled, trailingIcon = FieldAtlasIcons.Import)
            }
        }
    }
}

@Composable
private fun OfflineByDesignNote(background: androidx.compose.ui.graphics.Color) {
    FieldAtlasCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = background,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(FieldAtlasIcons.Offline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Offline by design",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "After the model is imported, turn off Wi-Fi and mobile data. Field Atlas works entirely from files on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun actionLabel(action: SetupNextAction): String = when (action) {
    SetupNextAction.ChooseModel -> "Choose model pack"
    SetupNextAction.OpenResearch -> "Open research"
    SetupNextAction.Importing -> "Verifying pack"
}
