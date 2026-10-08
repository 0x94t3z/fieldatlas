package xyz.fieldatlas.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Settings sheets keep close/back controls visible even in long legal or technical views. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldAtlasInfoSheet(
    title: String, onDismiss: () -> Unit,
    closeDescription: String = "Close $title",
    onBack: (() -> Unit)? = null, backDescription: String = "Back",
    contentKey: Any = title, content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    ModalBottomSheet(onDismissRequest = onDismiss, shape = FieldAtlasLayout.SheetShape,
        containerColor = MaterialTheme.colorScheme.surface, dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (onBack != null) BackHandler(onBack = onBack)
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).imePadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(50)))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) FieldAtlasBareIconButton(onBack, backDescription, FieldAtlasIcons.Back)
                Text(title, Modifier.weight(1f).semantics { heading() }, style = FieldAtlasSheetTitleStyle)
                FieldAtlasCloseButton(onDismiss, closeDescription)
            }
            key(contentKey) {
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}
