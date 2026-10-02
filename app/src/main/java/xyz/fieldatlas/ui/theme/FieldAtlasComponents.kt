package xyz.fieldatlas.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

enum class StatusTone { Neutral, Positive, Attention }

/** Buttons are rounded rectangles across the app; Material's default pill shape is not used. */
val FieldAtlasButtonShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)

/** Status chips use a tighter corner so they read as labels, not as buttons. */
val FieldAtlasChipShape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)

@Composable
fun FieldAtlasPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
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
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(FieldAtlasIcons.Back, contentDescription = "Back")
                }
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
) {
    val defaultColors = when (state) {
        StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.Positive -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Attention -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier,
        color = containerColor ?: defaultColors.first,
        contentColor = contentColor ?: defaultColors.second,
        shape = FieldAtlasChipShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Icon tile tints drawn only from the notebook palette: sage, gold, paper and forest. */
enum class TileTone { Sage, Gold, Paper, Forest }

@Composable
fun FieldAtlasIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    tone: TileTone = TileTone.Sage,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        TileTone.Sage -> colors.primaryContainer to colors.onPrimaryContainer
        TileTone.Gold -> colors.secondaryContainer to colors.onSecondaryContainer
        TileTone.Paper -> colors.surfaceVariant to colors.onSurfaceVariant
        TileTone.Forest -> colors.primary to colors.onPrimary
    }
    Surface(
        modifier = modifier.size(size),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(size / 4))
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
        modifier = modifier.heightIn(min = 52.dp).semantics { role = Role.Button },
        // The main call to action keeps the softer card radius.
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(leadingIconSize))
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
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
        Icons.Outlined.KeyboardArrowDown,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(22.dp).rotate(rotation),
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
            style = MaterialTheme.typography.bodyLarge,
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

/** The app's mark: the launcher logo on its own dark green tile, beside the editorial wordmark. */
@Composable
fun FieldAtlasBrandMark(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.res.colorResource(xyz.fieldatlas.R.color.launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            // The adaptive-icon foreground keeps its 108-unit canvas; drawing it larger than the
            // tile crops the outer padding, as the launcher does.
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(xyz.fieldatlas.R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(54.dp),
            )
        }
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
