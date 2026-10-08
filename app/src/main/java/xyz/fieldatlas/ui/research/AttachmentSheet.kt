package xyz.fieldatlas.ui.research

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.TileTone

/** The attach choices with a line each on what happens to the file, all of it on the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentSheet(onDismiss: () -> Unit, onCamera: () -> Unit, onPhotos: () -> Unit, onFiles: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = xyz.fieldatlas.ui.theme.FieldAtlasLayout.SheetShape,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 22.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Add to your question", style = xyz.fieldatlas.ui.theme.FieldAtlasSheetTitleStyle, modifier = Modifier.weight(1f))
                xyz.fieldatlas.ui.theme.FieldAtlasCloseButton(onDismiss, "Close attachment choices")
            }
            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Read on this phone. Answers quote the parts they use.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Choice(FieldAtlasIcons.Camera, TileTone.Sage, "Take a photo", "A sign, a menu or a page of notes", onCamera)
            ChoiceDivider()
            Choice(FieldAtlasIcons.Photo, TileTone.Gold, "Choose an image", "Text in it is recognized on the phone", onPhotos)
            ChoiceDivider()
            Choice(FieldAtlasIcons.Document, TileTone.Paper, "Choose a file", "PDF, Word, PowerPoint, EPUB, text or code", onFiles)
        }
    }
}

@Composable
private fun Choice(icon: ImageVector, tone: TileTone, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(FieldAtlasButtonShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldAtlasIconTile(icon, size = 44.dp, tone = tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceDivider() {
    HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
}
