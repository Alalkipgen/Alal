#!/usr/bin/env python3
"""Restore the tail of EditorScreen.kt, which was truncated by an earlier commit."""
from pathlib import Path

path = Path("app/src/main/java/com/alal/notes/ui/editor/EditorScreen.kt")
text = path.read_text()

anchor = "        Item(R.string.focus_mode, Icons.Rounded.CenterFocusStrong, onClick = onFocus)\n"
if anchor not in text:
    raise SystemExit("anchor line not found")

if "R.string.trash" in text:
    print("EditorScreen.kt already complete; nothing to do")
    raise SystemExit(0)

tail = """        Item(R.string.find_replace, Icons.Rounded.FindReplace, onClick = onFind)
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
"""

head = text[: text.index(anchor) + len(anchor)]
path.write_text(head + tail)
print("restored EditorScreen.kt tail")
