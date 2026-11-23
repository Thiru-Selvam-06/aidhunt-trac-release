package com.example.ui.components

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.util.ReportDateFilter
import com.example.ui.util.ReportPeriod
import com.example.ui.util.reportCalendar
import com.example.ui.util.reportDate
import com.example.ui.util.reportDay
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

@Composable
internal fun ReportDateFilterControl(
    filter: ReportDateFilter,
    isTamil: Boolean,
    onChange: (ReportDateFilter) -> Unit,
    hasUndated: Boolean,
    showExtraPeriods: Boolean = false
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ReportPeriod?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (isTamil) "தேதி வடிகட்டி" else "Filter by date", style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("report_date_filter")) {
                Text(filter.selectionLabel(isTamil))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ReportPeriod.entries.filter { showExtraPeriods || it !in setOf(ReportPeriod.THIS_WEEK, ReportPeriod.THIS_YEAR) }.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(period.label(isTamil)) },
                        modifier = Modifier.testTag("report_period_$period"),
                        onClick = {
                            menuOpen = false
                            when (period) {
                                ReportPeriod.SPECIFIC_DATE -> pickReportDate(context, filter.from.takeIf { filter.period == period } ?: System.currentTimeMillis(), isTamil) {
                                    onChange(ReportDateFilter(period, it))
                                }
                                ReportPeriod.MONTH_YEAR, ReportPeriod.CUSTOM_RANGE -> dialog = period
                                else -> onChange(ReportDateFilter(period))
                            }
                        }
                    )
                }
            }
        }
        if (hasUndated) {
            Text(
                if (isTamil) "தேதி இல்லாத பதிவுகள் எல்லா தேதிகளும் என்ற தேர்வில் மட்டும் தெரியும்."
                else "Transactions without a recorded date appear in All Time only.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    dialog?.let { period ->
        val initial = remember(filter, period) {
            filter.takeIf { it.period == period } ?: ReportDateFilter(period, System.currentTimeMillis())
        }
        ReportPeriodDialog(initial, isTamil, onDismiss = { dialog = null }, onApply = {
            onChange(it)
            dialog = null
        })
    }
}

@Composable
private fun ReportPeriodDialog(
    initial: ReportDateFilter,
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onApply: (ReportDateFilter) -> Unit
) {
    val context = LocalContext.current
    val selectingMonth = initial.period == ReportPeriod.MONTH_YEAR
    val initialCalendar = remember(initial) { reportCalendar(initial.from) }
    var from by remember(initial) { mutableLongStateOf(initial.from) }
    var to by remember(initial) { mutableLongStateOf(initial.to) }
    var month by remember(initial) { mutableIntStateOf(initialCalendar.get(Calendar.MONTH)) }
    var year by remember(initial) { mutableIntStateOf(initialCalendar.get(Calendar.YEAR)) }
    val months = remember(isTamil) {
        DateFormatSymbols(Locale.forLanguageTag(if (isTamil) "ta-IN" else "en-IN")).shortMonths.take(12)
    }
    val validRange = reportCalendar(from).let { reportDay(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH)) } <=
        reportCalendar(to).let { reportDay(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.period.label(isTamil)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectingMonth) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { year-- }, enabled = year > 1900, modifier = Modifier.testTag("report_previous_year")) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = if (isTamil) "முந்தைய ஆண்டு" else "Previous year")
                        }
                        Text(year.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("report_year"))
                        IconButton(onClick = { year++ }, enabled = year < 9999, modifier = Modifier.testTag("report_next_year")) {
                            Icon(Icons.Default.ChevronRight, contentDescription = if (isTamil) "அடுத்த ஆண்டு" else "Next year")
                        }
                    }
                    months.indices.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { index ->
                                FilterChip(
                                    selected = month == index,
                                    onClick = { month = index },
                                    label = { Text(months[index]) },
                                    modifier = Modifier.weight(1f).testTag("report_month_$index")
                                )
                            }
                        }
                    }
                } else {
                    OutlinedButton(onClick = { pickReportDate(context, from, isTamil) { from = it } }, modifier = Modifier.fillMaxWidth().testTag("report_from")) {
                        Text("${if (isTamil) "தொடக்கம்" else "From"}: ${reportDate(from, isTamil)}")
                    }
                    OutlinedButton(onClick = { pickReportDate(context, to, isTamil) { to = it } }, modifier = Modifier.fillMaxWidth().testTag("report_to")) {
                        Text("${if (isTamil) "முடிவு" else "To"}: ${reportDate(to, isTamil)}")
                    }
                    if (!validRange) Text(
                        if (isTamil) "தொடக்கத் தேதி முடிவுத் தேதிக்குப் பிறகு இருக்கக்கூடாது."
                        else "From Date must be on or before To Date.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectingMonth || validRange,
                onClick = {
                    if (selectingMonth) onApply(ReportDateFilter(initial.period, reportDay(year, month, 1)))
                    else onApply(ReportDateFilter(initial.period, from, to))
                },
                modifier = Modifier.testTag("apply_report_dates")
            ) { Text(if (isTamil) "பயன்படுத்து" else "Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isTamil) "ரத்து" else "Cancel") } }
    )
}

private fun pickReportDate(context: Context, value: Long, isTamil: Boolean, onPicked: (Long) -> Unit) {
    val initial = reportCalendar(value)
    val locale = Locale.forLanguageTag(if (isTamil) "ta-IN" else "en-IN")
    val localized = ContextThemeWrapper(context, 0).apply {
        applyOverrideConfiguration(Configuration(context.resources.configuration).apply { setLocale(locale) })
    }
    DatePickerDialog(localized, { _, year, month, day -> onPicked(reportDay(year, month, day)) },
        initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
}
