package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reflows using the space actually available to the card, including accessibility text scale. */
internal fun reportColumnCount(widthDp: Float, fontScale: Float, count: Int, minWidthDp: Float = 160f): Int =
    ((widthDp + 12f) / (minWidthDp * fontScale.coerceAtLeast(1f) + 12f)).toInt().coerceIn(1, count.coerceAtLeast(1))

data class ReportMetric(
    val title: String,
    val value: String,
    val color: Color = Color(0xFF0B532E),
    val background: Color = Color(0xFFF0FDF4),
    val icon: ImageVector? = null,
    val detail: String? = null,
    val tag: String = title
)

/** Amounts have their own full-width line; icons and metadata never steal their width. */
@Composable
fun ReportMetrics(metrics: List<ReportMetric>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val amountStyle = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
    // Include the actual amount width, not just a phone breakpoint. Seven-figure amounts with
    // paise must reflow to a wider card rather than split or be silently ellipsized.
    val amountWidth = metrics.maxOfOrNull {
        measurer.measure(AnnotatedString(it.value), style = amountStyle, softWrap = false, maxLines = 1).size.width / density.density
    } ?: 0f
    ReportGrid(metrics.size, modifier, minColumnWidthDp = maxOf(160f, amountWidth + 32f)) { index ->
        val metric = metrics[index]
        Surface(modifier = Modifier.fillMaxWidth().testTag("report_metric_${metric.tag}"),
            shape = RoundedCornerShape(16.dp), color = metric.background,
            border = BorderStroke(1.dp, metric.color.copy(alpha = 0.12f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metric.icon?.let { Icon(it, null, tint = metric.color, modifier = Modifier.size(22.dp)) }
                Text(metric.title, fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
                Text(metric.value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = metric.color,
                    modifier = Modifier.fillMaxWidth().testTag("report_metric_value_${metric.tag}"))
                metric.detail?.let { Text(it, fontSize = 12.sp, color = Color(0xFF64748B)) }
            }
        }
    }
}

/** Normal Row measurement supports subcomposed children; FlowRow intrinsic queries do not. */
@Composable
fun ReportGrid(count: Int, modifier: Modifier = Modifier, minColumnWidthDp: Float = 160f, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = reportColumnCount(maxWidth.value, LocalDensity.current.fontScale, count, minColumnWidthDp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (0 until count).toList().chunked(columns).forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    indices.forEach { index -> Box(Modifier.weight(1f)) { content(index) } }
                }
            }
        }
    }
}

/** Wrapping action/filter rows avoid fixed-height clipping in Tamil and at larger text sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportSectionHeading(title: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
        action()
    }
}
