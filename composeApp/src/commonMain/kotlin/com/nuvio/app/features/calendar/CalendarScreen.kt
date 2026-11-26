package com.nuvio.app.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatCalendarDate
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.calendar_day_header
import nuvio.composeapp.generated.resources.calendar_empty_sources
import nuvio.composeapp.generated.resources.calendar_episode_count
import nuvio.composeapp.generated.resources.calendar_episode_range
import nuvio.composeapp.generated.resources.calendar_episode_single
import nuvio.composeapp.generated.resources.calendar_load_failed
import nuvio.composeapp.generated.resources.calendar_month_1
import nuvio.composeapp.generated.resources.calendar_month_10
import nuvio.composeapp.generated.resources.calendar_month_11
import nuvio.composeapp.generated.resources.calendar_month_12
import nuvio.composeapp.generated.resources.calendar_month_2
import nuvio.composeapp.generated.resources.calendar_month_3
import nuvio.composeapp.generated.resources.calendar_month_4
import nuvio.composeapp.generated.resources.calendar_month_5
import nuvio.composeapp.generated.resources.calendar_month_6
import nuvio.composeapp.generated.resources.calendar_month_7
import nuvio.composeapp.generated.resources.calendar_month_8
import nuvio.composeapp.generated.resources.calendar_month_9
import nuvio.composeapp.generated.resources.calendar_month_title
import nuvio.composeapp.generated.resources.calendar_movie_release
import nuvio.composeapp.generated.resources.calendar_my_shows
import nuvio.composeapp.generated.resources.calendar_next_month
import nuvio.composeapp.generated.resources.calendar_nothing_on_day
import nuvio.composeapp.generated.resources.calendar_nothing_this_month
import nuvio.composeapp.generated.resources.calendar_prev_month
import nuvio.composeapp.generated.resources.calendar_refresh
import nuvio.composeapp.generated.resources.calendar_switch_to_grid
import nuvio.composeapp.generated.resources.calendar_switch_to_list
import nuvio.composeapp.generated.resources.calendar_today
import nuvio.composeapp.generated.resources.calendar_weekday_1
import nuvio.composeapp.generated.resources.calendar_weekday_2
import nuvio.composeapp.generated.resources.calendar_weekday_3
import nuvio.composeapp.generated.resources.calendar_weekday_4
import nuvio.composeapp.generated.resources.calendar_weekday_5
import nuvio.composeapp.generated.resources.calendar_weekday_6
import nuvio.composeapp.generated.resources.calendar_weekday_7
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val MonthNames: List<StringResource> = listOf(
    Res.string.calendar_month_1, Res.string.calendar_month_2, Res.string.calendar_month_3,
    Res.string.calendar_month_4, Res.string.calendar_month_5, Res.string.calendar_month_6,
    Res.string.calendar_month_7, Res.string.calendar_month_8, Res.string.calendar_month_9,
    Res.string.calendar_month_10, Res.string.calendar_month_11, Res.string.calendar_month_12,
)

private val WeekdayNames: List<StringResource> = listOf(
    Res.string.calendar_weekday_1, Res.string.calendar_weekday_2, Res.string.calendar_weekday_3,
    Res.string.calendar_weekday_4, Res.string.calendar_weekday_5, Res.string.calendar_weekday_6,
    Res.string.calendar_weekday_7,
)

private sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data object Error : CalendarUiState
    data class Loaded(val entries: List<CalendarEntry>) : CalendarUiState
}

/**
 * Month calendar of episodes and releases for the user's shows (library + Continue Watching) and
 * library movies. Tap a day to see what airs; switch to a chronological list view.
 */
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { CurrentDateProvider.todayIsoDate() }
    val todayMonth = remember(today) { CalendarMonth.fromIso(today) ?: CalendarMonth(2026, 1) }
    var monthKey by rememberSaveable { mutableStateOf(todayMonth.key) }
    val month = CalendarMonth.fromIso("$monthKey-01") ?: todayMonth
    var selectedDate by rememberSaveable { mutableStateOf<String?>(today) }
    var listView by rememberSaveable { mutableStateOf(false) }
    var myShowsOnly by rememberSaveable { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val libraryState by remember {
        LibraryRepository.ensureLoaded()
        LibraryRepository.uiState
    }.collectAsStateWithLifecycle()
    val progressState by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val sources = remember(libraryState.items, progressState.entries) {
        (libraryCalendarSources(libraryState.items) + continueWatchingCalendarSources(progressState.entries))
            .distinctBy { it.key }
    }
    val sourcesSignature = remember(sources) { sources.map { it.key }.sorted().joinToString(",") }

    var uiState by remember { mutableStateOf<CalendarUiState>(CalendarUiState.Loading) }
    LaunchedEffect(month.key, sourcesSignature, reloadToken) {
        val cached = CalendarRepository.cachedMonth(month, sources)
        if (cached != null) {
            uiState = CalendarUiState.Loaded(cached)
            return@LaunchedEffect
        }
        uiState = CalendarUiState.Loading
        uiState = runCatching { CalendarRepository.loadMonth(month, sources) }
            .fold(onSuccess = { CalendarUiState.Loaded(it) }, onFailure = { CalendarUiState.Error })
    }

    val allEntries = (uiState as? CalendarUiState.Loaded)?.entries.orEmpty()
    // "My shows": only series from the library / watchlist / Continue Watching.
    val entries = remember(allEntries, myShowsOnly) {
        if (myShowsOnly) allEntries.filter { it.fromUserList && !it.isMovie } else allEntries
    }
    val byDate = remember(entries) { entries.groupBy { it.dateIso } }
    // Keep the selection inside the visible month: today when visible, otherwise the first busy day.
    val effectiveSelected = selectedDate?.takeIf(month::contains)
        ?: today.takeIf(month::contains)
        ?: byDate.keys.minOrNull()
        ?: month.startIso

    fun changeMonth(delta: Int) {
        val next = month.plusMonths(delta)
        monthKey = next.key
        selectedDate = if (next.contains(today)) today else null
    }

    val header: @Composable () -> Unit = {
        CalendarHeader(
            month = month,
            isCurrentMonth = month == todayMonth,
            listView = listView,
            myShowsOnly = myShowsOnly,
            onToggleMyShows = { myShowsOnly = !myShowsOnly },
            onPrev = { changeMonth(-1) },
            onNext = { changeMonth(1) },
            onToday = {
                monthKey = todayMonth.key
                selectedDate = today
            },
            onRefresh = {
                scope.launch {
                    CalendarRepository.clear()
                    reloadToken += 1
                }
            },
            onToggleView = { listView = !listView },
        )
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = statusTop + 56.dp
    val bottomPadding = nuvioSafeBottomPadding(28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 840.dp
            if (isWide && !listView) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.5f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = topPadding, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        header()
                        CalendarBody(uiState, entries.isEmpty(), true, onRetry = { reloadToken += 1 }) {
                            MonthGrid(
                                month = month,
                                today = today,
                                selected = effectiveSelected,
                                byDate = byDate,
                                onSelect = { selectedDate = it },
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (uiState is CalendarUiState.Loaded) {
                            dayPanel(effectiveSelected, today, byDate[effectiveSelected].orEmpty(), onOpenMeta)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topPadding,
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "calendar_header") { header() }
                    val state = uiState
                    if (state !is CalendarUiState.Loaded || entries.isEmpty()) {
                        item(key = "calendar_status") {
                            CalendarBody(state, entries.isEmpty(), true, onRetry = { reloadToken += 1 }) {}
                        }
                    } else if (listView) {
                        if (byDate.isEmpty()) {
                            item(key = "calendar_month_empty") { EmptyLine(stringResource(Res.string.calendar_nothing_this_month)) }
                        }
                        byDate.keys.sorted().forEach { date ->
                            item(key = "header_$date") {
                                DayHeader(date = date, isToday = date == today, modifier = Modifier.padding(top = 8.dp))
                            }
                            items(byDate[date].orEmpty(), key = { "list_${it.key}" }) { entry ->
                                CalendarEntryRow(entry = entry, onClick = { onOpenMeta(entry.preview) })
                            }
                        }
                    } else {
                        item(key = "calendar_grid") {
                            MonthGrid(
                                month = month,
                                today = today,
                                selected = effectiveSelected,
                                byDate = byDate,
                                onSelect = { selectedDate = it },
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            )
                        }
                        dayPanel(effectiveSelected, today, byDate[effectiveSelected].orEmpty(), onOpenMeta)
                    }
                }
            }
        }

        NuvioBackButton(
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 4.dp)
                .align(Alignment.TopStart),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            iconSize = 24.dp,
        )
    }
}

private fun LazyListScope.dayPanel(
    date: String,
    today: String,
    entries: List<CalendarEntry>,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    item(key = "panel_header_$date") { DayHeader(date = date, isToday = date == today) }
    if (entries.isEmpty()) {
        item(key = "panel_empty_$date") { EmptyLine(stringResource(Res.string.calendar_nothing_on_day)) }
    } else {
        items(entries, key = { "panel_${it.key}" }) { entry ->
            CalendarEntryRow(entry = entry, onClick = { onOpenMeta(entry.preview) })
        }
    }
}

/** Loading / error / empty-library states; renders [content] once the month is loaded. */
@Composable
private fun CalendarBody(
    state: CalendarUiState,
    noSources: Boolean,
    libraryLoaded: Boolean,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        state is CalendarUiState.Loading || (noSources && !libraryLoaded) -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            NuvioLoadingIndicator(modifier = Modifier.size(28.dp))
        }

        state is CalendarUiState.Error -> HomeEmptyStateCard(
            title = stringResource(Res.string.calendar_load_failed),
            message = "",
            actionLabel = stringResource(Res.string.action_retry),
            onActionClick = onRetry,
        )

        noSources -> HomeEmptyStateCard(
            title = stringResource(Res.string.calendar_nothing_this_month),
            message = "",
        )

        else -> content()
    }
}

@Composable
private fun CalendarHeader(
    month: CalendarMonth,
    isCurrentMonth: Boolean,
    listView: Boolean,
    myShowsOnly: Boolean,
    onToggleMyShows: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit,
    onToggleView: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    Res.string.calendar_month_title,
                    stringResource(MonthNames[month.month - 1]),
                    month.year,
                ),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPrev) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(Res.string.calendar_prev_month),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(Res.string.calendar_next_month),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderPill(
                text = stringResource(if (listView) Res.string.calendar_switch_to_grid else Res.string.calendar_switch_to_list),
                icon = { tint ->
                    Icon(
                        imageVector = if (listView) Icons.Rounded.CalendarMonth else Icons.AutoMirrored.Rounded.ViewList,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = onToggleView,
            )
            HeaderPill(
                text = stringResource(Res.string.calendar_my_shows),
                icon = null,
                selected = myShowsOnly,
                onClick = onToggleMyShows,
            )
            if (!isCurrentMonth) {
                HeaderPill(text = stringResource(Res.string.calendar_today), icon = null, onClick = onToday)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(Res.string.calendar_refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeaderPill(
    text: String,
    icon: (@Composable (androidx.compose.ui.graphics.Color) -> Unit)?,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.invoke(tint)
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

@Composable
private fun MonthGrid(
    month: CalendarMonth,
    today: String,
    selected: String,
    byDate: Map<String, List<CalendarEntry>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val leading = month.leadingBlankDays
    val cellCount = leading + month.dayCount
    val weeks = (cellCount + 6) / 7
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WeekdayNames.forEach { res ->
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(bottom = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        repeat(weeks) { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { column ->
                    val day = week * 7 + column - leading + 1
                    if (day < 1 || day > month.dayCount) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val iso = month.dayIso(day)
                        DayCell(
                            day = day,
                            entries = byDate[iso].orEmpty(),
                            isToday = iso == today,
                            isSelected = iso == selected,
                            onClick = { onSelect(iso) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    entries: List<CalendarEntry>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .clip(shape)
            .background(
                when {
                    isSelected -> primary.copy(alpha = 0.2f)
                    entries.isNotEmpty() -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                },
            )
            .then(
                if (isToday || isSelected) {
                    Modifier.border(if (isToday) 1.5.dp else 1.dp, primary.copy(alpha = if (isToday) 1f else 0.6f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || entries.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> primary
                    entries.isNotEmpty() -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            if (entries.isNotEmpty()) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(primary))
            }
        }
        if (entries.isNotEmpty()) {
            PosterStrip(entries = entries, modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

/** Small overlapping poster thumbnails of what airs on a day. */
@Composable
private fun PosterStrip(entries: List<CalendarEntry>, modifier: Modifier = Modifier) {
    val shown = entries.distinctBy { it.preview.id }.take(3)
    val thumbWidth = 15.dp
    val step = 9.dp
    Box(modifier = modifier.height(23.dp).width(thumbWidth + step * (shown.size - 1).coerceAtLeast(0))) {
        shown.forEachIndexed { index, entry ->
            AsyncImage(
                model = entry.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = step * index)
                    .size(width = thumbWidth, height = 23.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(0.5.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun DayHeader(date: String, isToday: Boolean, modifier: Modifier = Modifier) {
    val weekday = dayOfWeekMondayFirst(date)?.let { stringResource(WeekdayNames[it]) }.orEmpty()
    val localeTag = Locale.current.toLanguageTag()
    val formatted = remember(date, localeTag) {
        runCatching { formatCalendarDate(date, localeTag, includeYear = false) }.getOrDefault(date)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.calendar_day_header, weekday, formatted),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (isToday) {
            Text(
                text = stringResource(Res.string.calendar_today),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

@Composable
private fun CalendarEntryRow(entry: CalendarEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = entry.poster,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 52.dp, height = 78.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entrySubtitle(entry),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.episodeTitle?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun entrySubtitle(entry: CalendarEntry): String {
    val season = entry.seasonNumber
    val episodes = entry.episodeNumbers
    return when {
        entry.isMovie || season == null || episodes.isEmpty() -> stringResource(Res.string.calendar_movie_release)
        episodes.size == 1 -> stringResource(Res.string.calendar_episode_single, season, episodes.first())
        else -> stringResource(
            Res.string.calendar_episode_range,
            season,
            episodes.first(),
            episodes.last(),
            pluralStringResource(Res.plurals.calendar_episode_count, episodes.size, episodes.size),
        )
    }
}
