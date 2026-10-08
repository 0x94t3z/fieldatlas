package xyz.fieldatlas.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.ui.theme.*

/** Quiet model actions, using the same callbacks and safeguards as the original Library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelActionsSheet(
    title: String, detail: String, selected: Boolean, notice: String?, useEnabled: Boolean,
    onUse: (() -> Unit)?, onDetails: () -> Unit, onOptions: (() -> Unit)?,
    onDelete: () -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = FieldAtlasLayout.SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = 22.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Answer model", Modifier.weight(1f), style = FieldAtlasSheetTitleStyle)
                FieldAtlasCloseButton(onDismiss, "Close model options")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FieldAtlasIconTile(FieldAtlasIcons.Model, size = 36.dp, iconSize = 20.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight(650)))
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected) Text("In use", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                // Original licence/version/checksum details remain available without another large CTA.
                FieldAtlasBareIconButton(onDetails, "Model details", FieldAtlasIcons.Info)
            }
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column {
                if (!selected && onUse != null) ModelAction("Use this model", {
                    onDismiss(); onUse()
                }, enabled = useEnabled, color = MaterialTheme.colorScheme.primary)
                onOptions?.let { ModelAction("Review model options", it) }
                ModelAction("Delete model from phone", onDelete, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ModelAction(label: String, onClick: () -> Unit, enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface) {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(FieldAtlasButtonShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}
