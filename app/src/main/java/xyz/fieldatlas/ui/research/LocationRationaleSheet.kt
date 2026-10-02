package xyz.fieldatlas.ui.research

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.TileTone

/**
 * Shown before Android's own location prompt for a "near me" question, so the request
 * comes with its reason and an alternative that needs no permission at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationRationaleSheet(onAllow: () -> Unit, onNameCity: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onNameCity,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FieldAtlasIconTile(FieldAtlasIcons.Place, size = 52.dp, tone = TileTone.Sage)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Find places near you?", style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FieldAtlasEditorial, fontWeight = FontWeight.SemiBold)
                Text("Field Atlas reads your position from the phone's GPS to list the closest saved places.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Reason(FieldAtlasIcons.Offline, "Works offline. GPS needs no connection or Google services.")
            Reason(FieldAtlasIcons.Lock, "Your location never leaves this phone. History keeps the answer, which names the nearby places.")
            Reason(FieldAtlasIcons.Info, "Asked only for \"near me\" questions. Change it any time in Android settings.")
            Button(onClick = onAllow, shape = FieldAtlasButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Allow location")
            }
            TextButton(onClick = onNameCity, shape = FieldAtlasButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Name a city instead")
            }
        }
    }
}

@Composable
private fun Reason(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
