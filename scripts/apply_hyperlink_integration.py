from pathlib import Path

path = Path("app/src/main/java/com/alal/notes/ui/editor/EditorScreen.kt")
text = path.read_text()

def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match, found {count}: {old[:80]!r}")
    text = text.replace(old, new, 1)

replace_once(
    "    var savedVisible by remember { mutableStateOf(false) }\n",
    "    var savedVisible by remember { mutableStateOf(false) }\n"
    "    var linkActions by remember { mutableStateOf<MarkdownLinkTarget?>(null) }\n"
    "    var editingLink by remember { mutableStateOf<MarkdownLinkTarget?>(null) }\n",
)

replace_once(
    "                focusMode = focusMode,\n                modifier = Modifier\n",
    "                focusMode = focusMode,\n"
    "                onOpenLink = { link ->\n"
    "                    if (!openExternalLink(context, link.url)) {\n"
    "                        scope.launch { snackbar.showSnackbar(\"Unable to open link\") }\n"
    "                    }\n"
    "                },\n"
    "                onLinkLongPress = { linkActions = it },\n"
    "                modifier = Modifier\n",
)

replace_once(
    "        Dialog.REMINDER -> ReminderDialog(\n            current = n?.reminderAt,\n            onDismiss = { dialog = Dialog.NONE },\n            onSet = { at -> vm.setReminder(at); dialog = Dialog.NONE },\n        )\n    }\n}\n\n@Composable\nprivate fun UndoRedoButtons",
    "        Dialog.REMINDER -> ReminderDialog(\n            current = n?.reminderAt,\n            onDismiss = { dialog = Dialog.NONE },\n            onSet = { at -> vm.setReminder(at); dialog = Dialog.NONE },\n        )\n    }\n\n"
    "    linkActions?.let { link ->\n"
    "        MarkdownLinkActionsDialog(\n"
    "            link = link,\n"
    "            onDismiss = { linkActions = null },\n"
    "            onOpen = {\n"
    "                linkActions = null\n"
    "                if (!openExternalLink(context, link.url)) {\n"
    "                    scope.launch { snackbar.showSnackbar(\"Unable to open link\") }\n"
    "                }\n"
    "            },\n"
    "            onEdit = { linkActions = null; editingLink = link },\n"
    "            onCopy = {\n"
    "                copyLink(context, link.url)\n"
    "                linkActions = null\n"
    "                scope.launch { snackbar.showSnackbar(\"Link copied\") }\n"
    "            },\n"
    "            onRemove = {\n"
    "                replaceMarkdownLink(vm.bodyState, link, link.label, null)\n"
    "                linkActions = null\n"
    "            },\n"
    "        )\n"
    "    }\n"
    "    editingLink?.let { link ->\n"
    "        EditMarkdownLinkDialog(\n"
    "            link = link,\n"
    "            onDismiss = { editingLink = null },\n"
    "            onSave = { label, url ->\n"
    "                replaceMarkdownLink(vm.bodyState, link, label, url)\n"
    "                editingLink = null\n"
    "            },\n"
    "        )\n"
    "    }\n"
    "}\n\n@Composable\nprivate fun UndoRedoButtons",
)

replace_once(
    "    hPad: androidx.compose.ui.unit.Dp,\n    focusMode: Boolean,\n    modifier: Modifier = Modifier,\n",
    "    hPad: androidx.compose.ui.unit.Dp,\n"
    "    focusMode: Boolean,\n"
    "    onOpenLink: (MarkdownLinkTarget) -> Unit,\n"
    "    onLinkLongPress: (MarkdownLinkTarget) -> Unit,\n"
    "    modifier: Modifier = Modifier,\n",
)

replace_once(
    "                .padding(horizontal = hPad)\n                .onSizeChanged { viewportH = it.height },\n",
    "                .padding(horizontal = hPad)\n"
    "                .onSizeChanged { viewportH = it.height }\n"
    "                .markdownLinkGestures(\n"
    "                    text = { vm.bodyState.text },\n"
    "                    layout = { layout },\n"
    "                    scrollY = { scroll.value },\n"
    "                    onOpen = onOpenLink,\n"
    "                    onLongPress = onLinkLongPress,\n"
    "                ),\n",
)

path.write_text(text)
