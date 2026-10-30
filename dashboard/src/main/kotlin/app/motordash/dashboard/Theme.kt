package app.motordash.dashboard

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** DiLink-style palette: true black ground, charcoal cards, blue accent. */
internal object Palette {
    val Background = Color.Black
    // Cards a step lighter than the black ground, rows a step lighter again, so the layers read.
    val Card = Brush.verticalGradient(listOf(Color(0xFF1B1B1D), Color(0xFF121214)))
    val CardBorder = Color(0xFF2A2A2E)
    val Row = Color(0xFF242427)
    val Track = Color(0xFF34343A)
    val Dialog = Color(0xFF1C1C1F)
    val Blue = Color(0xFF3D8BFF)
    val Cyan = Color(0xFF36D3FF)
    val Green = Color(0xFF2FD27A)
    val Red = Color(0xFFFF5A5A)
    val TextPrimary = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFF8C8C8C)
    val TextMuted = Color(0xFF4D4D4D)
}

internal val CardShape = RoundedCornerShape(24.dp)
internal val Gap = 20.dp


/** "MotorDash" with "Dash" in the accent color. Pairs with [LogoMark]. */
@Composable
internal fun Wordmark(size: TextUnit = 20.sp) {
    // A brand name: "Motor" stays left of "Dash" in Arabic too.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row {
            Text("Motor", color = Palette.TextPrimary, fontSize = size, fontWeight = FontWeight.SemiBold)
            Text("Dash", color = Palette.Blue, fontSize = size, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The same mark is the launcher icon and the device page's icon. Drawn in code on a 100x100 grid. */
@Composable
internal fun LogoMark(modifier: Modifier = Modifier) {
    // Same geometry as the launcher icon (app/src/main/res/drawable/ic_launcher*.xml), 100x100 units.
    Canvas(modifier) {
        val u = size.minDimension / 100f
        val center = Offset(50f * u, 58f * u)
        val radius = 34f * u
        drawArc(
            Brush.linearGradient(listOf(Palette.Blue, Palette.Cyan), Offset(20f * u, 80f * u), Offset(80f * u, 20f * u)),
            150f, 240f, false,
            Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2),
            style = Stroke(14f * u, cap = StrokeCap.Round),
        )
        // Tapered needle pointing up-right, with a round hub.
        drawPath(
            Path().apply {
                moveTo(55.66f * u, 63.66f * u)
                lineTo(71.21f * u, 36.79f * u)
                lineTo(44.34f * u, 52.34f * u)
                close()
            },
            Color.White,
        )
        drawCircle(Color.White, 10f * u, center)
    }
}

@Composable
internal fun FolderIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.18f)
                lineTo(w * 0.4f, h * 0.18f)
                lineTo(w * 0.5f, h * 0.3f)
                lineTo(w, h * 0.3f)
                lineTo(w, h * 0.85f)
                lineTo(0f, h * 0.85f)
                close()
            },
            color,
        )
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
internal fun DashCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(CardShape)
            .background(Palette.Card)
            .border(1.dp, Palette.CardBorder, CardShape)
            .padding(28.dp),
        content = content,
    )
}

@Composable
/** Card heading, Title Case ("File Sharing"). */
internal fun Label(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextPrimary, size: TextUnit = 20.sp) {
    Text(text, color = color, fontSize = size, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
internal fun Message(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Palette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Palette.TextSecondary, fontSize = 17.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun StatusPill(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, fontSize = 15.sp)
    }
}

@Composable
internal fun Chip(text: String, active: Boolean, monospace: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = if (active) Color.White else Palette.TextSecondary,
        fontSize = if (monospace) 14.sp else 16.sp,
        // Monospace for addresses; those always read left-to-right.
        fontFamily = if (monospace) FontFamily.Monospace else null,
        maxLines = 1,
        softWrap = false,
        style = if (monospace) LtrText else LocalTextStyle.current,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Palette.Blue else Palette.Row)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

internal fun formatSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else String.format(Locale.US, if (value < 10) "%.1f %s" else "%.0f %s", value, units[i])
}

/** Dubai, from the app's assets (app/src/main/assets/fonts), shared with the device web page. Null if missing. */
@Composable
internal fun rememberAppFont(): FontFamily? {
    val assets = LocalContext.current.assets
    return remember {
        val files = runCatching { assets.list("fonts")?.toList() }.getOrNull().orEmpty()
        val fonts = listOf("Light" to FontWeight.Light, "Regular" to FontWeight.Normal, "Medium" to FontWeight.Medium, "Bold" to FontWeight.Bold)
            .filter { (style, _) -> "Dubai-$style.ttf" in files }
            .map { (style, weight) -> Font("fonts/Dubai-$style.ttf", assets, weight) }
        if (fonts.isEmpty()) null else FontFamily(fonts)
    }
}

internal fun Typography.withFont(font: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = font), displayMedium = displayMedium.copy(fontFamily = font),
    displaySmall = displaySmall.copy(fontFamily = font), headlineLarge = headlineLarge.copy(fontFamily = font),
    headlineMedium = headlineMedium.copy(fontFamily = font), headlineSmall = headlineSmall.copy(fontFamily = font),
    titleLarge = titleLarge.copy(fontFamily = font), titleMedium = titleMedium.copy(fontFamily = font),
    titleSmall = titleSmall.copy(fontFamily = font), bodyLarge = bodyLarge.copy(fontFamily = font),
    bodyMedium = bodyMedium.copy(fontFamily = font), bodySmall = bodySmall.copy(fontFamily = font),
    labelLarge = labelLarge.copy(fontFamily = font), labelMedium = labelMedium.copy(fontFamily = font),
    labelSmall = labelSmall.copy(fontFamily = font),
)

/** For addresses, PINs and sizes: keep them left-to-right inside Arabic text. */
internal val LtrText: TextStyle
    @Composable get() = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)

/**
 * The app's dialog. Android shows dialogs in their own window, which resets the layout
 * direction to the system's, so this re-applies the chosen language's (right-to-left for Arabic).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    dismiss: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val direction = if (LocalStrings.current.isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.widthIn(min = 560.dp, max = 840.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Palette.Dialog)
                    .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 16.dp),
            ) {
                Text(title, color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(18.dp))
                content()
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    dismiss?.invoke()
                    confirm()
                }
            }
        }
    }
}

