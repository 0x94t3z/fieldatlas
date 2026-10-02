package xyz.fieldatlas.ui.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.sourcePresentation
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasInformationAction
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasTopBar
import xyz.fieldatlas.ui.theme.TileTone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.attachments.AttachmentKind
import xyz.fieldatlas.attachments.TextFileTypes
import xyz.fieldatlas.research.AttachmentProvenance
import xyz.fieldatlas.ui.research.AttachmentThumbnail
import java.io.File

data class SourceOriginal(val file: File, val kind: AttachmentKind)

@Composable
fun SourcesScreen(
    evidence: Evidence,
    sourceNumber: Int,
    sourceCount: Int,
    packLicense: String?,
    onBack: () -> Unit,
    original: SourceOriginal? = null,
    isCited: Boolean = true,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    var showDetails by rememberSaveable(evidence.documentId, evidence.chunkId) { mutableStateOf(false) }
    var showOriginal by rememberSaveable(evidence.documentId, evidence.chunkId) { mutableStateOf(false) }
    val presentation = sourcePresentation(evidence)
    val clipboard = LocalClipboardManager.current
    val verifiedKind = evidence.source.split(" · ").getOrNull(1)
    val language = if (presentation.isAttachment && verifiedKind !in setOf("pdf", "image")) TextFileTypes.language(evidence.title) else null
    if (showOriginal && original != null) OriginalSourcePreview(original, evidence.title,
        Regex("page (\\d+)", RegexOption.IGNORE_CASE).find(evidence.source)?.groupValues?.get(1)?.toIntOrNull() ?: 1,
        onClose = { showOriginal = false })
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            FieldAtlasTopBar(title = "Source $sourceNumber of $sourceCount", onBack = onBack, action = {
                IconButton(onClick = { clipboard.setText(AnnotatedString(evidence.text)) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy source text",
                        modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                }
            })
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                  if (original?.kind == AttachmentKind.IMAGE && original.file.isFile) {
                      AttachmentThumbnail(original.file, evidence.title, Modifier.size(56.dp)) { showOriginal = true }
                  } else {
                      val place = evidence.documentId.startsWith("wv-place-") || evidence.documentId.startsWith("osm-place-")
                      FieldAtlasIconTile(
                          when {
                              verifiedKind == "image" -> FieldAtlasIcons.Photo
                              place -> FieldAtlasIcons.Place
                              else -> FieldAtlasIcons.Document
                          },
                          size = 40.dp,
                          tone = if (place) TileTone.Gold else TileTone.Sage,
                      )
                  }
                  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer { Text(evidence.title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp, lineHeight = 28.sp)) }
                    Text(
                        presentation.metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
            }
            if (presentation.isAttachment) item {
                if (original?.file?.isFile == true) {
                    TextButton(onClick = { showOriginal = true }) {
                        Text(if (original.kind == AttachmentKind.PDF) "View original PDF" else "View original file")
                    }
                } else Text("Original file is no longer available locally. The saved excerpt is still available.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                FieldAtlasCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentPadding = 16.dp,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (presentation.isRecognizedText) "Recognized text" else "Source excerpt", style = MaterialTheme.typography.titleMedium)
                            Text(if (isCited) "Cited in this answer" else "Provided to the model · not cited",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (AttachmentProvenance.needsWarning(evidence)) Text(
                        "This file contains an answer labeled as unverified. It isn’t independent confirmation of its claims.",
                        modifier = Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SourceTextContent(evidence.text, language, recognizedText = presentation.isRecognizedText)
                    if (presentation.isAttachment) Text("This may be an excerpt, not the whole file.",
                        modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!presentation.isAttachment || presentation.isRecognizedText) item {
                Text(
                    if (presentation.isRecognizedText) "Text recognition can make mistakes. Check the original." else "This is a local copy. Check its date before treating changing details as current.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (!presentation.isAttachment) {
                    Text("Source details", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (evidence.source.startsWith("http://") || evidence.source.startsWith("https://")) {
                            "Original URL"
                        } else {
                            "Source"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FieldAtlasIconTile(
                            if (evidence.source.startsWith("http://") || evidence.source.startsWith("https://")) {
                                Icons.Outlined.Link
                            } else {
                                FieldAtlasIcons.Document
                            },
                            size = 32.dp,
                        )
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(evidence.source, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    packLicense?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                  }
                    FieldAtlasInformationAction(
                        label = if (showDetails) "Hide technical identifiers" else "Technical identifiers",
                        onClick = { showDetails = !showDetails },
                        expanded = showDetails,
                    )
                    if (showDetails) {
                        Text("Document ${evidence.documentId}", style = MaterialTheme.typography.bodySmall)
                        Text("Chunk ${evidence.chunkId}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            }
            if (sourceCount > 1 && (onPrevious != null || onNext != null)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(FieldAtlasIcons.Back, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Previous source", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { onNext?.invoke() }, enabled = onNext != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("Next source", modifier = Modifier.padding(end = 8.dp))
                        Icon(FieldAtlasIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
