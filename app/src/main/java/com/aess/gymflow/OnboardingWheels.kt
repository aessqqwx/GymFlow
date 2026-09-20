package com.aess.gymflow

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun PickerValueCard(label: String, value: String, onClick: () -> Unit) {
    ExpressiveSurfaceButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(value, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDateWheelSheet(
    language: String,
    current: String?,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    val today = remember { LocalDate.now() }
    val initial = remember(current) { parseBirthDate(current) ?: today.minusYears(18) }
    var year by remember { mutableIntStateOf(initial.year.coerceIn(1900, today.year)) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    var day by remember { mutableIntStateOf(initial.dayOfMonth) }

    val years = remember(today.year) { (1900..today.year).toList() }
    val maxMonth = maxBirthMonth(year, today)
    val months = remember(maxMonth) { (1..maxMonth).toList() }
    LaunchedEffect(year, maxMonth) { month = month.coerceIn(1, maxMonth) }

    val maxDay = remember(year, month, today) { maxBirthDay(year, month, today) }
    val days = remember(maxDay) { (1..maxDay).toList() }
    LaunchedEffect(year, month, maxDay) { day = day.coerceIn(1, maxDay) }

    val wheelMonths = gsa(language, R.array.months_wheel)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(gs(language, R.string.select_birth_date), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateWheelColumn(
                    header = gs(language, R.string.day).uppercase(),
                    items = days,
                    selected = day,
                    label = { it.toString() },
                    onSelected = { day = it },
                    modifier = Modifier.weight(.8f)
                )
                DateWheelColumn(
                    header = gs(language, R.string.month).uppercase(),
                    items = months,
                    selected = month,
                    label = { wheelMonths[it - 1] },
                    onSelected = { month = it },
                    modifier = Modifier.weight(1.35f)
                )
                DateWheelColumn(
                    header = gs(language, R.string.year).uppercase(),
                    items = years,
                    selected = year,
                    label = { it.toString() },
                    onSelected = { year = it },
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = {
                    onSelected(clampBirthDate(year, month, day, today).toString())
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(22.dp)
            ) { Text(gs(language, R.string.done), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegerWheelSheet(
    language: String,
    title: String,
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit
) {
    var value by remember(selected, values) { mutableIntStateOf(selected.coerceIn(values.first(), values.last())) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            WheelPicker(values, value, label, { value = it }, Modifier.fillMaxWidth())
            Button(
                onClick = { onSelected(value); onDismiss() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(22.dp)
            ) { Text(gs(language, R.string.done), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun <T> DateWheelColumn(
    header: String,
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(header, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        WheelPicker(items, selected, label, onSelected, Modifier.fillMaxWidth())
    }
}

@Composable
private fun <T> WheelPicker(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val itemHeight = 48.dp

    LaunchedEffect(values.size) {
        if (values.isNotEmpty() && state.firstVisibleItemIndex > values.lastIndex) {
            state.scrollToItem(values.lastIndex)
        }
    }

    LaunchedEffect(values, state) {
        snapshotFlow {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item -> abs(item.offset + item.size / 2 - center) }?.index
        }.filterNotNull().distinctUntilChanged().collect { index ->
            values.getOrNull(index)?.let(onSelected)
        }
    }

    Box(modifier.height(itemHeight * 5), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(itemHeight)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(18.dp))
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * 2),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(count = values.size) { index ->
                val item = values[index]
                val active = item == selected
                Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        label(item),
                        fontSize = if (active) 20.sp else 16.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
