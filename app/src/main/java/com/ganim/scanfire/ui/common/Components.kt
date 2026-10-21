package com.ganim.scanfire.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.ui.theme.Ember
import com.ganim.scanfire.ui.theme.LocalIsDark
import com.ganim.scanfire.ui.theme.ScanFireTheme
import com.ganim.scanfire.ui.theme.Tints

/** Background/foreground pair for an icon tile, picked for the current light or dark scheme. */
enum class Tint { Ember, Blue, Green, Violet }

@Composable
fun Tint.colors(): Pair<Color, Color> {
    val dark = LocalIsDark.current
    return when (this) {
        Tint.Ember -> if (dark) Tints.emberDark else Tints.ember
        Tint.Blue -> if (dark) Tints.blueDark else Tints.blue
        Tint.Green -> if (dark) Tints.greenDark else Tints.green
        Tint.Violet -> if (dark) Tints.violetDark else Tints.violet
    }
}

/** Big page title with an optional line under it and actions on the right; replaces top app bars on tab screens. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** A rounded tinted square holding an icon. */
@Composable
fun IconTile(icon: ImageVector, tint: Tint, size: Int = 40) {
    val (bg, fg) = tint.colors()
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** Square quick action: tinted icon above a short label. */
@Composable
fun QuickAction(label: String, icon: ImageVector, tint: Tint, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTile(icon, tint)
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Wide tool button: tinted icon then a label, at least 64dp tall. */
@Composable
fun ToolButton(label: String, icon: ImageVector, tint: Tint, modifier: Modifier = Modifier, inverted: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (inverted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (inverted) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (inverted) 0.dp else 1.dp,
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon, tint, size = 36)
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** White rounded group of rows with hairline dividers between them. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) { Column(content = content) }
}

@Composable
fun GroupDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** A row inside a [GroupCard]: title, optional detail, optional trailing control; shows a chevron when clickable. */
@Composable
fun GroupRow(
    title: String,
    detail: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class BarItem(val label: String, val icon: ImageVector, val highlighted: Boolean = false, val onClick: () -> Unit)

/** Floating ink action bar at the bottom of document and viewer screens. Highlighted item gets the ember fill. */
@Composable
fun InkActionBar(items: List<BarItem>, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { item ->
                Surface(
                    onClick = item.onClick,
                    color = if (item.highlighted) Ember else Color.Transparent,
                    contentColor = if (item.highlighted) Color.White else MaterialTheme.colorScheme.onSecondary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(item.icon, null, Modifier.size(22.dp))
                        Text(item.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Image work (viewing, editing, cropping, signing) happens on a dark "studio" regardless of the app theme. */
@Composable
fun DarkStudio(content: @Composable () -> Unit) = ScanFireTheme(themeMode = 2, studio = true, content = content)
