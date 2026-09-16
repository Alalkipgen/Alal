package com.alal.notes.ui.editor

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.notes.R
import com.alal.notes.data.prefs.Settings
import com.alal.notes.domain.model.MarginMode
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.domain.model.PaperTexture
import com.alal.notes.domain.markdown.MarkdownSpans
import com.alal.notes.ui.components.ConfettiBurst
import com.alal.notes.ui.components.GoalProgressBar
import com.alal.notes.ui.components.PaperTextureBackground
import com.alal.notes.ui.components.StatusChip
import com.alal.notes.ui.components.label
import com.alal.notes.ui.theme.ActionColors
import com.alal.notes.ui.theme.Alal
import com.alal.notes.ui.theme.NoteBackgrounds
import com.alal.notes.ui.theme.color
import com.alal.notes.ui.util.Format
import com.alal.notes.ui.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Sheet { NONE, TEXT, BACKGROUND, STATS, TEMPLATE, EXPORT, OUTLINE }
private enum class Dialog { NONE, LINK, DETAILS, TAG, REMINDER }

@Composable
fun EditorScreen(
    noteId: Long,
    settings: Settings,
    onBack: () -> Unit,
    onVersions: (Long) -> Unit,
    vm: EditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(noteId) { vm.load(noteId) }

    val note by vm.note.collectAsStateWithLifecycle()
    val noteUnlocked by vm.noteUnlocked.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val statsReady by vm.statsReady.collectAsStateWithLifecycle()
    val selectionStats by vm.selectionStats.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val find by vm.find.collectAsStateWithLifecycle()
    val savedTick by vm.savedTick.collectAsStateWithLifecycle()
    val goalTick by vm.goalReachedTick.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val extras = Alal.extras
    val dark = extras.dark
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }

    var focusMode by rememberSaveable { mutableStateOf(false) }
    var reading by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var dialog by rememberSaveable { mutableStateOf(Dialog.NONE) }
    var overflow by remember { mutableStateOf(false) }
    var headingMenu by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }
    var savedVisible by remember { mutableStateOf(false) }
    var linkActions by remember { mutableStateOf<MarkdownLinkTarget?>(null) }
    var editingLink by remember { mutableStateOf<MarkdownLinkTarget?>(null) }

    // Saving lifecycle
    BackHandler(enabled = reading) { reading = false }
    BackHandler(enabled = !reading && focusMode) { focusMode = false }
    BackHandler(enabled = !reading && !focusMode && find.visible) { vm.closeFind() }
    BackHandler(enabled = !reading && !focusMode && !find.visible) { vm.finishEditing(); onBack() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.saveNow() }

    LaunchedEffect(savedTick) {
        if (savedTick > 0) { savedVisible = true; delay(1500); savedVisible = false }
    }
    LaunchedEffect(goalTick) { if (goalTick > 0) { confetti++; haptics.confirm() } }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(context.getString(it)); vm.consumeMessage() }
    }

    // Reminders need POST_NOTIFICATIONS on Android 13+. Ask right before opening the picker.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dialog = Dialog.REMINDER
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.reminder_permission_denied)) }
    }
    val openReminder: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            dialog = Dialog.REMINDER
        }
    }
    val lockAvailable = settings.lockEnabled && settings.lockPinHash.isNotBlank()
    val onLockToggle: () -> Unit = {
        if (note?.isLocked == true || lockAvailable) { haptics.confirm(); vm.toggleLock() }
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.lock_setup_first)) }
    }

    // Fonts & size (pinch zoom updates local size immediately, persisted per note)
    val current = note
    var localSize by remember(current?.id) { mutableIntStateOf(current?.fontSizeOverride ?: settings.bodySize) }
    LaunchedEffect(current?.fontSizeOverride, settings.bodySize) { localSize = current?.fontSizeOverride ?: settings.bodySize }
    val bodySize = localSize.sp
    val bodyFamily = if (stats.myanmarWords > 0) extras.type.myanmar else extras.type.body

    // Background
    val gradientIdx = current?.backgroundGradient
    val baseColor = current?.backgroundColor?.let { Color(it) }
    val bgColor by animateColorAsState(
        targetValue = when {
            gradientIdx != null && gradientIdx in NoteBackgrounds.gradients.indices -> Color.Transparent
            baseColor != null -> if (dark) NoteBackgrounds.forDark(baseColor) else baseColor
            else -> cs.background
        },
        animationSpec = tween(350), label = "bg",
    )
    val brush = gradientIdx?.let { NoteBackgrounds.gradients.getOrNull(it) }?.let { g ->
        val s = if (dark) NoteBackgrounds.forDark(g.start) else g.start
        val e = if (dark) NoteBackgrounds.forDark(g.end) else g.end
        Brush.verticalGradient(listOf(s, e))
    }
    val texture = if (focusMode) PaperTexture.PLAIN else (current?.paperTexture ?: settings.paperTexture)
    val hPad = when (settings.margin) { MarginMode.NARROW -> 16.dp; MarginMode.NORMAL -> 24.dp; MarginMode.WIDE -> 36.dp }

    val chromeAlpha by animateFloatAsState(if (focusMode) 0f else 1f, tween(250), label = "chrome")
    // The screen slides in immediately; the body fades in as soon as Room has delivered the
    // note. This replaces the old fixed 400 ms hold before the open animation, which made
    // every note open feel laggy even though the load itself took only a few milliseconds.
    val contentAlpha by animateFloatAsState(if (current != null) 1f else 0f, tween(160), label = "content")

    Box(Modifier.fillMaxSize().then(if (brush != null) Modifier.background(brush) else Modifier.background(bgColor))) {
        PaperTextureBackground(texture, cs.onSurface.copy(alpha = if (dark) 0.10f else 0.08f), Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (!focusMode) {
                    Column(Modifier.alpha(chromeAlpha)) {
                        TopAppBar(
                            title = {
                                // Category picker lives in the title slot
                                Box {
                                    val cat = categories.firstOrNull { it.id == current?.categoryId }
                                    AssistChip(
                                        onClick = { categoryMenu = true },
                                        label = { Text(cat?.name ?: stringResource(R.string.no_category), maxLines = 1) },
                                        leadingIcon = { Icon(Icons.Rounded.Label, null, tint = cat?.let { Color(it.color) } ?: cs.onSurfaceVariant, modifier = Modifier.size(16.dp)) },
                                    )
                                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.no_category)) }, onClick = { categoryMenu = false; vm.setCategory(null) })
                                        for (c in categories) {
                                            DropdownMenuItem(
                                                text = { Text(c.name) },
                                                leadingIcon = { Box(Modifier.size(12.dp).background(Color(c.color), MaterialTheme.shapes.extraSmall)) },
                                                trailingIcon = { if (c.id == current?.categoryId) Icon(Icons.Rounded.Check, null) },
                                                onClick = { categoryMenu = false; vm.setCategory(c.id) },
                                            )
                                        }
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { vm.finishEditing(); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                            },
                            actions = {
                                // Own composable so that typing (which flips canUndo/canRedo)
                                // only recomposes these two buttons, not the whole app bar.
                                UndoRedoButtons(vm)
                                IconButton(onClick = {
                                    scope.launch {
                                        val text = vm.shareText()
                                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                                        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
                                    }
                                }) { Icon(Icons.Rounded.Share, stringResource(R.string.share)) }
                                Box {
                                    IconButton(onClick = { overflow = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                                    EditorOverflowMenu(
                                        expanded = overflow,
                                        onDismiss = { overflow = false },
                                        pinned = current?.isPinned == true,
                                        onPin = { haptics.confirm(); vm.togglePin() },
                                        reminderSet = current?.reminderAt != null,
                                        locked = current?.isLocked == true,
                                        onReminder = openReminder,
                                        onLock = onLockToggle,
                                        onGoal = { sheet = Sheet.STATS },
                                        onFocus = { focusMode = true },
                                        onFind = { vm.openFind(withReplace = true) },
                                        onStatus = { statusMenu = true },
                                        onTemplate = { sheet = Sheet.TEMPLATE },
                                        onDuplicate = { scope.launch { vm.duplicate()?.let { snackbar.showSnackbar(context.getString(R.string.snackbar_duplicated)) } } },
                                        onArchive = { vm.archive(); onBack() },
                                        onTrash = { vm.trash(); onBack() },
                                        onDetails = { vm.refreshDetailedStats(); dialog = Dialog.DETAILS },
                                        onBackground = { sheet = Sheet.BACKGROUND },
                                        onVersions = { current?.let { onVersions(it.id) } },
                                        onExport = { sheet = Sheet.EXPORT },
                                        onReading = { vm.saveNow(); reading = true },
                                        onOutline = { sheet = Sheet.OUTLINE },
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        )
                        // Status + tags + goal row
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = hPad - 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                StatusChip(current?.status ?: NoteStatus.DRAFT, onClick = { statusMenu = true })
                                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                                    for (s in NoteStatus.entries) {
                                        DropdownMenuItem(
                                            text = { Text(s.label()) },
                                            leadingIcon = { Box(Modifier.size(10.dp).background(s.color(dark), MaterialTheme.shapes.extraSmall)) },
                                            onClick = { statusMenu = false; haptics.select(); vm.setStatus(s) },
                                        )
                                    }
                                }
                            }
                            for (t in tags) {
                                AssistChip(
                                    onClick = { vm.removeTag(t.id) },
                                    label = { Text("#${t.name}") },
                                    trailingIcon = { Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp)) },
                                )
                            }
                            AssistChip(
                                onClick = { dialog = Dialog.TAG },
                                label = { Text(stringResource(R.string.add_tag)) },
                                leadingIcon = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp)) },
                            )
                            val goal = current?.wordGoal
                            AssistChip(
                                onClick = { sheet = Sheet.STATS },
                                label = {
                                    Text(
                                        if (goal == null) stringResource(R.string.goal)
                                        else "${Format.compact(stats.words)} / ${Format.compact(goal)}",
                                    )
                                },
                                leadingIcon = { Icon(Icons.Rounded.Flag, null, modifier = Modifier.size(16.dp), tint = if (goal != null && stats.words >= goal) cs.tertiary else cs.onSurfaceVariant) },
                            )
                        }
                        AnimatedVisibility(visible = find.visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            FindBar(find, vm)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().statusBarsPadding(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { focusMode = false }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.exit_focus), tint = cs.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            },
            bottomBar = {
                // Use the IME's target inset instead of its frame-by-frame animated inset. This
                // moves the toolbar once at keyboard-show/hide start rather than remeasuring the
                // full editor and long text layout on every keyboard animation frame.
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.imeAnimationTarget))) {
                    // Status strip
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = hPad, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val sel = selectionStats
                        Text(
                            if (sel != null) stringResource(R.string.selection) + ": " + Format.number(sel.words) + " w · " + Format.number(sel.chars) + " c"
                            else "${Format.number(stats.words)} w · ${Format.compact(stats.chars)} c · ${stringResource(R.string.min_read, stats.readMinutes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.alpha(if (statsReady) 1f else 0f),
                        )
                        Spacer(Modifier.weight(1f))
                        AnimatedVisibility(visible = savedVisible, enter = fadeIn(), exit = fadeOut(tween(600))) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = cs.tertiary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.saved), style = MaterialTheme.typography.labelMedium, color = cs.tertiary)
                            }
                        }
                    }
                    val goal = current?.wordGoal
                    if (statsReady && goal != null && goal > 0) {
                        val p = (stats.words.toFloat() / goal).coerceIn(0f, 1f)
                        GoalProgressBar(p, Modifier.fillMaxWidth().padding(horizontal = hPad).height(3.dp), color = if (p >= 1f) cs.tertiary else cs.primary)
                    }
                    if (!focusMode) {
                        EditorToolbar(
                            vm = vm,
                            onText = { sheet = Sheet.TEXT },
                            onHeading = { headingMenu = true },
                            headingMenu = headingMenu,
                            onHeadingDismiss = { headingMenu = false },
                            onList = { listMenu = true },
                            listMenu = listMenu,
                            onListDismiss = { listMenu = false },
                            onLink = { dialog = Dialog.LINK },
                            onFind = { if (find.visible) vm.closeFind() else vm.openFind() },
                            onBackground = { sheet = Sheet.BACKGROUND },
                            findActive = find.visible,
                            modifier = Modifier.alpha(chromeAlpha),
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
        ) { padding ->
            EditorBody(
                vm = vm,
                settings = settings,
                bodySize = bodySize,
                bodyFamily = bodyFamily,
                hPad = hPad,
                focusMode = focusMode,
                onOpenLink = { link ->
                    if (!openExternalLink(context, link.url)) {
                        scope.launch { snackbar.showSnackbar("Unable to open link") }
                    }
                },
                onLinkLongPress = { linkActions = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .alpha(contentAlpha)
                    .pinchToZoom { zoom ->
                        val next = (localSize * zoom).roundToInt().coerceIn(14, 30)
                        if (next != localSize) { localSize = next; haptics.step(); vm.setFontSizeOverride(next) }
                    },
            )
        }

        ConfettiBurst(trigger = confetti, modifier = Modifier.fillMaxSize())

        // Reading mode: rendered Markdown, no keyboard, chrome hides on tap.
        if (reading) {
            ReadingModeScreen(
                title = vm.titleState.text.toString(),
                body = vm.bodyState.text.toString(),
                stats = stats,
                settings = settings,
                titleFamily = extras.type.title,
                bodyFamily = bodyFamily,
                onClose = { reading = false },
            )
        }

        // Locked note: cover the editor until the user authenticates (PIN / biometric).
        if (current?.isLocked == true && !noteUnlocked) {
            com.alal.notes.ui.lock.NoteLockOverlay(
                settings = settings,
                onUnlocked = { vm.unlockNote() },
                onBack = { vm.finishEditing(); onBack() },
            )
        }
    }

    // ---------------------------------------------------------------- sheets & dialogs
    val n = current
    when (sheet) {
        Sheet.NONE -> Unit
        Sheet.TEXT -> if (n != null) TextSheet(n, settings.bodySize, onSize = { vm.setFontSizeOverride(it); localSize = it ?: settings.bodySize }, onDismiss = { sheet = Sheet.NONE })
        Sheet.BACKGROUND -> if (n != null) BackgroundSheet(
            note = n, dark = dark, defaultTexture = settings.paperTexture,
            onBackground = { c, g -> vm.setBackground(c, g) },
            onTexture = vm::setPaperTexture,
            onShowOnCard = vm::setShowOnCard,
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.STATS -> StatsSheet(stats, selectionStats, n?.wordGoal, onGoal = vm::setWordGoal, onDismiss = { sheet = Sheet.NONE })
        Sheet.TEMPLATE -> com.alal.notes.ui.home.TemplatePickerSheet(
            templates = templates,
            onDismiss = { sheet = Sheet.NONE },
            onPick = { t -> sheet = Sheet.NONE; if (t != null) vm.applyTemplate(t) },
        )
        Sheet.EXPORT -> ExportSheet(vm, onDismiss = { sheet = Sheet.NONE })
        Sheet.OUTLINE -> OutlineSheet(
            items = remember(vm.bodyState.text) { vm.outline() },
            cursor = vm.bodyState.selection.start,
            onPick = { vm.jumpTo(it) },
            onDismiss = { sheet = Sheet.NONE },
        )
    }
    when (dialog) {
        Dialog.NONE -> Unit
        Dialog.LINK -> LinkDialog(vm.selectedText(), onDismiss = { dialog = Dialog.NONE }) { label, url -> vm.insertLink(label, url); dialog = Dialog.NONE }
        Dialog.DETAILS -> if (n != null) DetailsDialog(n, stats) { dialog = Dialog.NONE }
        Dialog.TAG -> TagDialog(onDismiss = { dialog = Dialog.NONE }) { vm.addTag(it); dialog = Dialog.NONE }
        Dialog.REMINDER -> ReminderDialog(
            current = n?.reminderAt,
            onDismiss = { dialog = Dialog.NONE },
            onSet = { at -> vm.setReminder(at); dialog = Dialog.NONE },
        )
    }

    linkActions?.let { link ->
        MarkdownLinkActionsDialog(
            link = link,
            onDismiss = { linkActions = null },
            onOpen = {
                linkActions = null
                if (!openExternalLink(context, link.url)) {
                    scope.launch { snackbar.showSnackbar("Unable to open link") }
                }
            },
            onEdit = { linkActions = null; editingLink = link },
            onCopy = {
                copyLink(context, link.url)
                linkActions = null
                scope.launch { snackbar.showSnackbar("Link copied") }
            },
            onRemove = {
                replaceMarkdownLink(vm.bodyState, link, link.label, null)
                linkActions = null
            },
        )
    }
    editingLink?.let { link ->
        EditMarkdownLinkDialog(
            link = link,
            onDismiss = { editingLink = null },
            onSave = { label, url ->
                replaceMarkdownLink(vm.bodyState, link, label, url)
                editingLink = null
            },
        )
    }
}

@Composable
private fun UndoRedoButtons(vm: EditorViewModel) {
    IconButton(onClick = { vm.undo() }, enabled = vm.canUndo) {
        Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo))
    }
    IconButton(onClick = { vm.redo() }, enabled = vm.canRedo) {
        Icon(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.redo))
    }
}

@Composable
private fun EditorBody(
    vm: EditorViewModel,
    settings: Settings,
    bodySize: androidx.compose.ui.unit.TextUnit,
    bodyFamily: androidx.compose.ui.text.font.FontFamily,
    hPad: androidx.compose.ui.unit.Dp,
    focusMode: Boolean,
    onOpenLink: (MarkdownLinkTarget) -> Unit,
    onLinkLongPress: (MarkdownLinkTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = Alal.extras
    val cs = MaterialTheme.colorScheme
    // The body field scrolls itself (see `scrollState` below) instead of living inside a
    // parent `verticalScroll`. Nesting a full-height text field in a scroll container made the
    // container re-measure on every text layout, which is what threw the view back to the top
    // once you reached the bottom of a long note.
    val scroll = rememberScrollState()
    var viewportH by remember { mutableIntStateOf(0) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val typewriter = focusMode && settings.typewriterMode
    val paragraphFocus = focusMode && settings.paragraphFocus
    // The caret is read through derivedStateOf so a keystroke no longer recomposes this whole
    // screen: with paragraph focus / typewriter off the derived value stays -1 and nothing
    // invalidates, which is what made typing in a long note feel heavy.
    val focusCursor by remember(paragraphFocus) {
        derivedStateOf { if (paragraphFocus) vm.bodyState.selection.start else -1 }
    }
    val typeCursor by remember(typewriter) {
        derivedStateOf { if (typewriter) vm.bodyState.selection.start else -1 }
    }
    // Placeholder visibility only flips between empty and non-empty, so derive it instead of
    // reading the text itself inside the decorators.
    val titleEmpty by remember { derivedStateOf { vm.titleState.text.isEmpty() } }
    val bodyEmpty by remember { derivedStateOf { vm.bodyState.text.isEmpty() } }

    // Inline Markdown styling (bold/italic/heading/quote/list marks) + paragraph focus dimming.
    // Requires Compose Foundation 1.9+ (BOM 2025.08.00) for TextFieldBuffer.addStyle.
    val output = remember(cs.primary, cs.onSurface, cs.onSurfaceVariant, extras.type.title, bodySize, paragraphFocus) {
        MarkdownOutputTransformation(
            accent = cs.primary,
            onSurface = cs.onSurface,
            muted = cs.onSurfaceVariant,
            highlight = cs.primary.copy(alpha = 0.18f),
            titleFont = extras.type.title,
            bodySize = bodySize,
            paragraphFocus = paragraphFocus,
            initialCursor = focusCursor,
        )
    }
    // Feeding the caret through snapshot state keeps the transformation instance stable, so a
    // cursor move only re-styles - it no longer rebuilds the whole text layout.
    SideEffect { output.cursor = focusCursor }

    // Find & Replace: when the current match changes (arrows / new query), scroll so its line is visible.
    val reveal by vm.revealSelection.collectAsStateWithLifecycle()
    LaunchedEffect(reveal) {
        if (reveal == 0 || viewportH == 0) return@LaunchedEffect
        // Layout may lag one frame behind the selection change; wait for it.
        withFrameNanos { }
        val l = layout ?: return@LaunchedEffect
        val sel = vm.bodyState.selection.start.coerceIn(0, l.layoutInput.text.length)
        val rect = runCatching { l.getCursorRect(sel) }.getOrNull() ?: return@LaunchedEffect
        val visibleTop = scroll.value.toFloat()
        val visibleBottom = visibleTop + viewportH
        val margin = viewportH * 0.15f
        if (rect.top < visibleTop + margin || rect.bottom > visibleBottom - margin) {
            // Place the match roughly a third of the way down the viewport.
            val target = (rect.top - viewportH * 0.33f).roundToInt().coerceIn(0, scroll.maxValue)
            scroll.animateScrollTo(target)
        }
    }

    // Typewriter scrolling: keep the caret line around 40% of the viewport. Only reacts to real
    // caret moves, so simply scrolling through the note no longer fights the user.
    LaunchedEffect(typewriter, typeCursor) {
        if (!typewriter || viewportH == 0) return@LaunchedEffect
        withFrameNanos { }
        val l = layout ?: return@LaunchedEffect
        val rect = runCatching { l.getCursorRect(typeCursor.coerceIn(0, l.layoutInput.text.length)) }.getOrNull() ?: return@LaunchedEffect
        val target = (rect.top - viewportH * 0.4f).roundToInt().coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(target)
    }

    // BasicTextField already keeps its caret visible while the IME changes the viewport.
    // A second animated correction here caused the text and toolbar to move in two stages.

    // Leaving the app (Home / recents) drops focus and the keyboard, so coming back shows the
    // note in full instead of restoring a keyboard over the text.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
    }

    val titleElevated by remember { derivedStateOf { scroll.value > 4 } }
    val dividerAlpha by animateFloatAsState(if (titleElevated) 1f else 0f, label = "titleDivider")

    Column(modifier) {
        Spacer(Modifier.height(8.dp))
        // Title - pinned above the body so the caret never fights two scroll containers.
        BasicTextField(
            state = vm.titleState,
            textStyle = TextStyle(
                fontFamily = extras.type.title,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            ),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            cursorBrush = SolidColor(cs.primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = hPad),
            decorator = TextFieldDecorator { inner ->
                Box {
                    if (titleEmpty) {
                        Text(
                            stringResource(R.string.title_hint),
                            fontFamily = extras.type.title, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                            color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            Modifier.padding(horizontal = hPad).alpha(dividerAlpha),
            color = cs.outlineVariant.copy(alpha = 0.6f),
        )
        // Body
        BasicTextField(
            state = vm.bodyState,
            textStyle = TextStyle(
                fontFamily = bodyFamily,
                fontSize = bodySize,
                lineHeight = bodySize * settings.lineHeight,
                color = cs.onSurface,
            ),
            inputTransformation = ListContinuationTransformation,
            outputTransformation = output,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            cursorBrush = SolidColor(cs.primary),
            lineLimits = TextFieldLineLimits.MultiLine(),
            scrollState = scroll,
            onTextLayout = { getResult -> layout = getResult() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = hPad)
                .onSizeChanged { viewportH = it.height }
                .markdownLinkGestures(
                    text = { vm.bodyState.text },
                    layout = { layout },
                    scrollY = { scroll.value },
                    onOpen = onOpenLink,
                    onLongPress = onLinkLongPress,
                ),
            decorator = TextFieldDecorator { inner ->
                Box(Modifier.padding(top = 12.dp)) {
                    if (bodyEmpty) {
                        Text(
                            stringResource(R.string.body_hint),
                            fontFamily = bodyFamily, fontSize = bodySize, lineHeight = bodySize * settings.lineHeight,
                            color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun EditorToolbar(
    vm: EditorViewModel,
    onText: () -> Unit,
    onHeading: () -> Unit,
    headingMenu: Boolean,
    onHeadingDismiss: () -> Unit,
    onList: () -> Unit,
    listMenu: Boolean,
    onListDismiss: () -> Unit,
    onLink: () -> Unit,
    onFind: () -> Unit,
    onBackground: () -> Unit,
    findActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    // Which inline styles surround the caret. Only the caret's line is scanned, and the derived
    // value changes only when the flag set changes, so typing inside plain text never
    // recomposes the toolbar.
    val inlineState by remember(vm) {
        derivedStateOf { MarkdownSpans.inlineStateAt(vm.bodyState.text, vm.bodyState.selection.start) }
    }
    val boldActive = (inlineState and MarkdownSpans.FLAG_BOLD) != 0
    val italicActive = (inlineState and MarkdownSpans.FLAG_ITALIC) != 0
    val underlineActive = (inlineState and MarkdownSpans.FLAG_UNDERLINE) != 0
    val strikeActive = (inlineState and MarkdownSpans.FLAG_STRIKE) != 0
    val highlightActive = (inlineState and MarkdownSpans.FLAG_HIGHLIGHT) != 0

    Surface(
        color = cs.surfaceContainer.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            @Composable
            fun Tool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, active: Boolean = false, onClick: () -> Unit) {
                val bg by animateColorAsState(if (active) cs.primary.copy(alpha = 0.14f) else Color.Transparent, tween(120), label = "toolBg")
                val tint by animateColorAsState(if (active) cs.primary else cs.onSurface, tween(120), label = "toolTint")
                IconButton(
                    onClick = { haptics.light(); onClick() },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(bg),
                ) {
                    Icon(icon, stringResource(label), tint = tint, modifier = Modifier.size(22.dp))
                }
            }

            @Composable
            fun Group() {
                VerticalDivider(
                    modifier = Modifier.padding(horizontal = 4.dp).height(22.dp),
                    color = cs.outlineVariant.copy(alpha = 0.7f),
                )
            }

            // Text size
            Tool(Icons.Rounded.FormatSize, R.string.text_settings, onClick = onText)
            Group()
            // Inline styles
            Tool(Icons.Rounded.FormatBold, R.string.bold, active = boldActive) { vm.toggleBold() }
            Tool(Icons.Rounded.FormatItalic, R.string.italic, active = italicActive) { vm.toggleItalic() }
            Tool(Icons.Rounded.FormatUnderlined, R.string.underline, active = underlineActive) { vm.toggleUnderline() }
            Box {
                Tool(Icons.Rounded.Title, R.string.heading, active = strikeActive || highlightActive, onClick = onHeading)
                DropdownMenu(expanded = headingMenu, onDismissRequest = onHeadingDismiss) {
                    HeadingPicker { level -> vm.toggleHeading(level); onHeadingDismiss() }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.strikethrough)) },
                        trailingIcon = { if (strikeActive) Icon(Icons.Rounded.Check, null, tint = cs.primary) },
                        onClick = { vm.toggleStrikethrough(); onHeadingDismiss() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.highlight)) },
                        trailingIcon = { if (highlightActive) Icon(Icons.Rounded.Check, null, tint = cs.primary) },
                        onClick = { vm.toggleHighlight(); onHeadingDismiss() },
                    )
                }
            }
            Group()
            // Blocks
            Tool(Icons.Rounded.FormatQuote, R.string.quote) { vm.toggleQuote() }
            Box {
                Tool(Icons.AutoMirrored.Rounded.FormatListBulleted, R.string.list, onClick = onList)
                DropdownMenu(expanded = listMenu, onDismissRequest = onListDismiss) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.bullet_list)) }, onClick = { vm.toggleBulletList(); onListDismiss() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.numbered_list)) }, onClick = { vm.toggleNumberedList(); onListDismiss() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.checklist)) }, onClick = { vm.toggleChecklist(); onListDismiss() })
                    DropdownMenuItem(text = { Text("———") }, onClick = { vm.insertHorizontalRule(); onListDismiss() })
                }
            }
            Tool(Icons.Rounded.Link, R.string.link, onClick = onLink)
            Group()
            // Tools
            Tool(Icons.Rounded.FindReplace, R.string.find_replace, active = findActive, onClick = onFind)
            Tool(Icons.Rounded.Palette, R.string.background, onClick = onBackground)
        }
    }
}

@Composable
private fun EditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    pinned: Boolean,
    reminderSet: Boolean,
    locked: Boolean,
    onPin: () -> Unit,
    onReminder: () -> Unit,
    onLock: () -> Unit,
    onGoal: () -> Unit,
    onFocus: () -> Unit,
    onFind: () -> Unit,
    onStatus: () -> Unit,
    onTemplate: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onDetails: () -> Unit,
    onBackground: () -> Unit,
    onVersions: () -> Unit,
    onExport: () -> Unit,
    onReading: () -> Unit,
    onOutline: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Colour action row: Pin | Reminder | Lock | Goal
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            @Composable
            fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, tint: Color, onClick: () -> Unit) {
                IconButton(onClick = { onDismiss(); onClick() }) { Icon(icon, stringResource(label), tint = tint) }
            }
            Action(Icons.Rounded.PushPin, if (pinned) R.string.unpin else R.string.pin, if (pinned) ActionColors.pin else cs.onSurfaceVariant, onPin)
            Action(Icons.Rounded.Notifications, R.string.reminder, if (reminderSet) ActionColors.reminder else cs.onSurfaceVariant, onReminder)
            Action(Icons.Rounded.Lock, if (locked) R.string.unlock else R.string.lock, if (locked) ActionColors.lock else cs.onSurfaceVariant, onLock)
            Action(Icons.Rounded.Flag, R.string.word_goal, cs.primary, onGoal)
        }
        HorizontalDivider()
        @Composable
        fun Item(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector?, tint: Color? = null, onClick: () -> Unit) {
            DropdownMenuItem(
                text = { Text(stringResource(label), color = tint ?: cs.onSurface) },
                leadingIcon = icon?.let { ic -> { Icon(ic, null, tint = tint ?: cs.onSurfaceVariant) } },
                onClick = { onDismiss(); onClick() },
            )
        }
        Item(R.string.focus_mode, Icons.Rounded.CenterFocusStrong, onClick = onFocus)
        Item(R.string.find_replace, Icons.Rounded.FindReplace, onClick = onFind)
        Item(R.string.change_status, null, onClick = onStatus)
        Item(R.string.apply_template, null, onClick = onTemplate)
        Item(R.string.background, Icons.Rounded.Palette, onClick = onBackground)
        Item(R.string.duplicate, Icons.Rounded.ContentCopy, onClick = onDuplicate)
        Item(R.string.details, Icons.Rounded.Info, onClick = onDetails)
        HorizontalDivider()
        Item(R.string.export, Icons.Rounded.FileDownload, onClick = onExport)
        Item(R.string.reading_mode, Icons.AutoMirrored.Rounded.MenuBook, onClick = onReading)
        Item(R.string.version_history, Icons.Rounded.History, onClick = onVersions)
        Item(R.string.outline, Icons.AutoMirrored.Rounded.FormatListBulleted, onClick = onOutline)
        HorizontalDivider()
        Item(R.string.archive, Icons.Rounded.Archive, tint = ActionColors.archive, onClick = onArchive)
        Item(R.string.trash, Icons.Rounded.Delete, tint = ActionColors.trash, onClick = onTrash)
    }
}
