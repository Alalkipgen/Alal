package com.alal.notes.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alal.notes.R
import com.alal.notes.data.dao.GroupCount
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.DailyStat
import com.alal.notes.data.entity.Note
import com.alal.notes.data.prefs.Settings
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.ui.components.GoalProgressBar
import com.alal.notes.ui.components.label
import com.alal.notes.ui.theme.Alal
import com.alal.notes.ui.theme.color
import com.alal.notes.ui.util.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

// ------------------------------------------------------------------ view model

data class DayBar(val day: LocalDate, val words: Int, val saves: Int)

data class StatsUiState(
    val noteCount: Int = 0,
    val totalWords: Int = 0,
    val totalChars: Int = 0,
    val todayWords: Int = 0,
    val streakDays: Int = 0,
    val bestDayWords: Int = 0,
    val last14: List<DayBar> = emptyList(),
    val byStatus: List<GroupCount> = emptyList(),
    val byCategory: List<GroupCount> = emptyList(),
    val categories: List<Category> = emptyList(),
    val longest: Note? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class StatsViewModel @Inject constructor(repository: NoteRepository) : ViewModel() {

    private val totals: Flow<Triple<Int, Int, Int>> = combine(
        repository.observeNoteCount(), repository.observeTotalWords(), repository.observeTotalChars(),
    ) { n, w, c -> Triple(n, w, c) }

    private val groups: Flow<Triple<List<GroupCount>, List<GroupCount>, List<Category>>> = combine(
        repository.observeByStatus(), repository.observeByCategory(), repository.observeCategories(),
    ) { s, c, cats -> Triple(s, c, cats) }

    val state: StateFlow<StatsUiState> = combine(
        totals, groups, repository.observeDailyStats(), repository.observeLongest(),
    ) { t, g, daily, longest ->
        val today = LocalDate.now()
        val byDay = daily.associateBy { it.day }
        val last14 = (13 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val s = byDay[d.toString()]
            DayBar(d, s?.wordsAdded ?: 0, s?.saves ?: 0)
        }
        StatsUiState(
            noteCount = t.first,
            totalWords = t.second,
            totalChars = t.third,
            todayWords = byDay[today.toString()]?.wordsAdded ?: 0,
            streakDays = streak(byDay, today),
            bestDayWords = daily.maxOfOrNull { it.wordsAdded } ?: 0,
            last14 = last14,
            byStatus = g.first,
            byCategory = g.second,
            categories = g.third,
            longest = longest,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** Consecutive days (ending today or yesterday) with at least one save. */
    private fun streak(byDay: Map<String, DailyStat>, today: LocalDate): Int {
        fun active(d: LocalDate) = (byDay[d.toString()]?.saves ?: 0) > 0
        var d = if (active(today)) today else today.minusDays(1)
        var n = 0
        while (active(d)) { n++; d = d.minusDays(1) }
        return n
    }
}

// ------------------------------------------------------------------ screen

@Composable
fun StatsScreen(settings: Settings, vm: StatsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val dark = Alal.extras.dark

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_stats), style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- headline numbers
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.total_notes), Format.number(state.noteCount), Modifier.weight(1f))
                StatCard(stringResource(R.string.total_words), Format.compact(state.totalWords), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.stats_today), Format.number(state.todayWords), Modifier.weight(1f), accent = true)
                StatCard(
                    stringResource(R.string.stats_streak),
                    stringResource(R.string.stats_days, state.streakDays),
                    Modifier.weight(1f),
                )
            }

            // ---- daily goal
            if (settings.dailyGoal > 0) {
                SectionCard(stringResource(R.string.daily_goal)) {
                    val progress = (state.todayWords.toFloat() / settings.dailyGoal).coerceIn(0f, 1f)
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.stats_goal_progress, Format.number(state.todayWords), Format.number(settings.dailyGoal)),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        )
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    }
                    GoalProgressBar(progress, Modifier.fillMaxWidth().height(8.dp))
                    if (progress >= 1f) {
                        Text(stringResource(R.string.stats_goal_reached), style = MaterialTheme.typography.bodySmall, color = cs.primary)
                    }
                }
            }

            // ---- 14-day chart
            SectionCard(stringResource(R.string.stats_last_14_days)) {
                Text(
                    stringResource(R.string.stats_best_day, Format.number(state.bestDayWords)),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                BarChart(state.last14, Modifier.fillMaxWidth().height(140.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val first = state.last14.firstOrNull()?.day
                    val last = state.last14.lastOrNull()?.day
                    Text(first?.let { "${it.dayOfMonth}/${it.monthValue}" } ?: "", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Text(stringResource(R.string.stats_today), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Text(last?.let { "${it.dayOfMonth}/${it.monthValue}" } ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Transparent)
                }
            }

            // ---- by status
            if (state.byStatus.isNotEmpty()) {
                SectionCard(stringResource(R.string.stats_by_status)) {
                    val total = state.byStatus.sumOf { it.n }.coerceAtLeast(1)
                    val ordered = NoteStatus.entries.mapNotNull { st -> state.byStatus.firstOrNull { it.key == st.name }?.let { st to it } }
                    for ((st, g) in ordered) {
                        DistributionRow(
                            color = st.color(dark),
                            label = st.label(),
                            count = g.n,
                            words = g.words,
                            fraction = g.n.toFloat() / total,
                        )
                    }
                }
            }

            // ---- by category
            if (state.byCategory.isNotEmpty()) {
                SectionCard(stringResource(R.string.stats_by_category)) {
                    val total = state.byCategory.sumOf { it.n }.coerceAtLeast(1)
                    for (g in state.byCategory) {
                        val cat = g.key?.let { k -> state.categories.firstOrNull { it.name == k } }
                        DistributionRow(
                            color = cat?.let { Color(it.color) } ?: cs.outlineVariant,
                            label = g.key ?: stringResource(R.string.stats_uncategorized),
                            count = g.n,
                            words = g.words,
                            fraction = g.n.toFloat() / total,
                        )
                    }
                }
            }

            // ---- longest note
            state.longest?.let { note ->
                SectionCard(stringResource(R.string.stats_longest_note)) {
                    Text(
                        note.title.ifBlank { stringResource(R.string.untitled) },
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = Alal.extras.type.title),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.stats_words_chars, Format.number(note.wordCount), Format.number(note.charCount)),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    val status = note.status
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(status.color(dark), CircleShape))
                        Text(status.label(), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
            }

            Text(
                stringResource(R.string.stats_total_chars, Format.number(state.totalChars)),
                style = MaterialTheme.typography.labelSmall, color = cs.outline,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = if (accent) cs.primaryContainer else cs.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Alal.extras.type.title),
                color = if (accent) cs.onPrimaryContainer else cs.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (accent) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DistributionRow(color: Color, label: String, count: Int, words: Int, fraction: Float) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.stats_count_words, count, Format.compact(words)),
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(cs.surfaceVariant, cornerRadius = r)
            drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0.02f, 1f), size.height), cornerRadius = r)
        }
    }
}

/** 14 bars, last one (today) in the accent colour. Zero days still draw a faint stub so the axis reads. */
@Composable
private fun BarChart(bars: List<DayBar>, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary
    val track = cs.surfaceVariant
    val secondary = cs.primary.copy(alpha = 0.45f)
    Canvas(modifier) {
        if (bars.isEmpty()) return@Canvas
        val max = bars.maxOf { it.words }.coerceAtLeast(1)
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (bars.size - 1)) / bars.size
        val r = CornerRadius(w / 3)
        val stub = 3.dp.toPx()
        bars.forEachIndexed { i, bar ->
            val x = i * (w + gap)
            val h = if (bar.words == 0) stub else (size.height * bar.words / max).coerceAtLeast(stub)
            val color = when {
                bar.words == 0 -> track
                i == bars.lastIndex -> primary
                else -> secondary
            }
            drawRoundRect(color, topLeft = Offset(x, size.height - h), size = Size(w, h), cornerRadius = r)
        }
    }
}
