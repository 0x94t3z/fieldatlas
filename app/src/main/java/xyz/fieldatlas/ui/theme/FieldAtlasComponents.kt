package xyz.fieldatlas.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

enum class StatusTone { Neutral, Positive, Attention }

/** Buttons are rounded rectangles across the app; Material's default pill shape is not used. */
val FieldAtlasButtonShape = FieldAtlasLayout.ControlShape

/** Status labels retain the approved pill silhouette, independent of control corners. */
val FieldAtlasChipShape = androidx.compose.foundation.shape.RoundedCornerShape(50)

/** Mockup-sized artwork inside an independent 48dp accessible switch target. */
@Composable
fun FieldAtlasSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val position by animateFloatAsState(if (checked) 1f else 0f, label = "switch thumb")
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(48.dp)
        .then(if (focused) Modifier.border(2.dp, colors.primary, FieldAtlasButtonShape) else Modifier)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch,
            interactionSource = interaction, indication = null, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = if (compact) 36.dp else 40.dp, height = if (compact) 22.dp else 26.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .background(if (checked) colors.primary else colors.outline, CircleShape)
            .testTag("compact-switch-track")) {
            // Relative offset follows reading direction, without scaling the touch target.
            val inset = if (compact) 3f else 4f
            Box(Modifier.offset(x = (inset + 14f * position).dp, y = inset.dp)
                .size(if (compact) 16.dp else 18.dp)
                .background(if (checked) colors.onPrimary else colors.surface, CircleShape)
                .testTag("compact-switch-thumb"))
        }
    }
}

/** No surface or press fill; a focus outline keeps keyboard navigation visible. */
@Composable
fun FieldAtlasBareIconButton(
    onClick: () -> Unit,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(modifier.size(48.dp)
        .then(if (focused) Modifier.border(2.dp, tint, FieldAtlasButtonShape) else Modifier)
        .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(iconSize), tint = if (enabled) tint else tint.copy(alpha = tint.alpha * 0.38f))
    }
}

@Composable
fun FieldAtlasPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineLarge,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = titleStyle)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldAtlasTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                FieldAtlasBareIconButton(onBack, "Back", FieldAtlasIcons.Back)
            }
        },
        actions = { action?.invoke() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
fun FieldAtlasStatusPill(
    text: String,
    state: StatusTone = StatusTone.Neutral,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val defaultColors = when (state) {
        StatusTone.Neutral -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.Positive -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Attention -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier,
        color = containerColor ?: defaultColors.first,
        contentColor = contentColor ?: defaultColors.second,
        shape = FieldAtlasChipShape,
        border = if (state == StatusTone.Neutral && containerColor == null)
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 4.dp else 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(
                if (icon == FieldAtlasIcons.Offline || icon == FieldAtlasIcons.DinoWalkA || icon == FieldAtlasIcons.DinoWalkB ||
                    icon == FieldAtlasIcons.Citation) 16.dp else 14.dp))
            Text(text = text, style = if (compact) MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp, lineHeight = 14.sp) else MaterialTheme.typography.labelMedium)
        }
    }
}

/** One compact visible close control with a full 48dp touch target. */
@Composable
fun FieldAtlasCloseButton(onClick: () -> Unit, description: String) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(FieldAtlasIcons.Close, description, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun FieldAtlasCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.medium,
        // A hairline keeps cards distinct from the paper without heavy shadows.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(
            alpha = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.09f else 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Icon tile tints drawn only from the notebook palette: sage, gold, paper and forest. */
enum class TileTone { Sage, Gold, Paper, Forest, Clay }

@Composable
fun FieldAtlasIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    tone: TileTone = TileTone.Sage,
    iconSize: Dp = size / 2,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        TileTone.Sage -> colors.primaryContainer to colors.onPrimaryContainer
        TileTone.Gold -> colors.secondaryContainer to colors.onSecondaryContainer
        TileTone.Paper -> colors.surfaceVariant to colors.onSurfaceVariant
        TileTone.Forest -> colors.primary to colors.onPrimary
        // A warm wash outside the green family, for when tiles in one list should all differ.
        TileTone.Clay -> if (colors.background.luminance() > 0.5f) Color(0xFFF2DED2) to Color(0xFF754333)
            else Color(0xFF51372C) to Color(0xFFEFC7B2)
    }
    Surface(
        modifier = modifier.size(size),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun FieldAtlasPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconSize: Dp = 18.dp,
    trailingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (leadingIcon == FieldAtlasIcons.ResearchSparkles) 58.dp else 52.dp).semantics { role = Role.Button },
        shape = if (leadingIcon == FieldAtlasIcons.ResearchSparkles) FieldAtlasLayout.ResearchButtonShape else FieldAtlasButtonShape,
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(leadingIconSize))
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        }
        Text(text, style = if (leadingIcon == FieldAtlasIcons.ResearchSparkles) FieldAtlasResearchButtonStyle
            else MaterialTheme.typography.labelLarge)
        if (trailingIcon != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun FieldAtlasInformationAction(
    label: String,
    onClick: () -> Unit,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    TextButton(shape = FieldAtlasButtonShape, onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.heightIn(max = 22.dp),
        )
        Text(
            label,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        FieldAtlasExpandIcon(expanded)
    }
}

/** One chevron for every disclosure: down when closed, up when open. */
@Composable
fun FieldAtlasExpandIcon(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "expand")
    Icon(
        FieldAtlasIcons.ChevronRight,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(20.dp).rotate(rotation + 90f),
    )
}

/** A full-width tappable row that opens or closes a section below it. */
@Composable
fun FieldAtlasDisclosureRow(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    labelStyle: androidx.compose.ui.text.TextStyle = FieldAtlasDisclosureStyle,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = labelStyle,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        FieldAtlasExpandIcon(expanded, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Small label that opens a group of rows or cards. Sentence case on purpose: screen readers
 * spell out all-caps words letter by letter.
 */
@Composable
fun FieldAtlasSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A label/value line for facts such as metrics, device details and structured excerpts. */
@Composable
fun FieldAtlasFactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 112.dp,
    emphasized: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            modifier = Modifier.width(labelWidth),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** A coloured dot that marks a status next to its text label (never colour alone). */
@Composable
fun FieldAtlasStatusDot(tone: StatusTone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        StatusTone.Positive -> MaterialTheme.colorScheme.primary
        StatusTone.Attention -> Color(0xFFC9A85C)
        StatusTone.Neutral -> MaterialTheme.colorScheme.outline
    }
    Box(modifier.size(8.dp).background(color, CircleShape))
}

/** The app's mark: a forest tile with a leaf, beside the editorial wordmark. */
@Composable
fun FieldAtlasBrandMark(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        FieldAtlasIconTile(FieldAtlasIcons.Knowledge, size = 34.dp, tone = TileTone.Forest)
        Text("Field Atlas", style = MaterialTheme.typography.titleLarge, fontFamily = FieldAtlasEditorial)
    }
}

@Composable
fun FieldAtlasHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    FieldAtlasPageHeader(title, subtitle, modifier)
}

@Composable
fun NotebookSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

@Composable
fun StatusStrip(
    items: List<String>,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FieldAtlasStatusPill(text = item, containerColor = containerColor, contentColor = contentColor)
        }
    }
}

@Composable
fun MetricChip(label: String, value: String? = null, modifier: Modifier = Modifier) {
    FieldAtlasStatusPill(
        text = listOfNotNull(label, value).joinToString(" · "),
        modifier = modifier,
    )
}

/**
 * Confirmation for an action that removes something. The safe choice is outlined and first;
 * the removal is the one filled, red button, so it is never the visual default by accident.
 */
@Composable
fun FieldAtlasDeleteDialog(
    title: String,
    message: String,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: String = "Delete",
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onKeep) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(48.dp), shape = FieldAtlasButtonShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.error) {
                    Icon(FieldAtlasIcons.Trash, contentDescription = null,
                        modifier = Modifier.padding(12.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = onKeep, shape = FieldAtlasButtonShape,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Keep") }
                    androidx.compose.material3.Button(onClick = onDelete, shape = FieldAtlasButtonShape,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(deleteLabel) }
                }
            }
        }
    }
}

/**
 * A determinate progress bar drawn as one continuous track. Material 3's default leaves a gap
 * after the filled part and a stop dot at the end of the track, which reads as a stray full stop.
 */
@Composable
fun FieldAtlasProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    trackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
