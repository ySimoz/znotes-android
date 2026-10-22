package com.xnotes.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.xnotes.platform.AppStorageDocumentsProvider
import java.io.File

data class SyncItem(
    val relativePath: String,
    val name: String,
    val uri: Uri?,
    val file: File?,
    val lastModified: Long,
    val isNote: Boolean,
    val isCanvas: Boolean,
    val isPdf: Boolean,
)

class DriveSyncScanner(private val context: Context) {

    fun scan(browseRoot: String?, syncScope: SyncScope, selectedFolders: Set<String>): List<SyncItem> {
        val items = mutableListOf<SyncItem>()

        if (browseRoot != null && !browseRoot.startsWith("content://${context.packageName}.documents")) {
            // External SAF root
            val treeUri = Uri.parse(browseRoot)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanSafDirectory(treeUri, rootDocId, "", syncScope, selectedFolders, items)
        } else {
            // Internal in-app storage root
            val rootFile = AppStorageDocumentsProvider.rootDir(context)
            if (rootFile.exists() && rootFile.isDirectory) {
                scanLocalFile(rootFile, "", syncScope, selectedFolders, items)
            }
        }

        return items
    }

    private fun scanSafDirectory(
        treeUri: Uri,
        folderDocId: String,
        currentPath: String,
        syncScope: SyncScope,
        selectedFolders: Set<String>,
        out: MutableList<SyncItem>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val mtimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val mtime = cursor.getLong(mtimeIdx)

                    if (name.startsWith(".")) continue
                    val rel = if (currentPath.isEmpty()) name else "$currentPath/$name"

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (syncScope == SyncScope.ALL || isFolderIncluded(rel, selectedFolders)) {
                            scanSafDirectory(treeUri, docId, rel, syncScope, selectedFolders, out)
                        }
                    } else {
                        val isNote = name.endsWith(".xnote", ignoreCase = true) || name.endsWith(".note", ignoreCase = true)
                        val isCanvas = name.endsWith(".xcanvas", ignoreCase = true)
                        val isPdf = name.endsWith(".pdf", ignoreCase = true)
                        if (isNote || isCanvas || isPdf) {
                            if (syncScope == SyncScope.ALL || isFolderIncluded(currentPath, selectedFolders)) {
                                val itemUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                out.add(
                                    SyncItem(
                                        relativePath = rel,
                                        name = name,
                                        uri = itemUri,
                                        file = null,
                                        lastModified = mtime,
                                        isNote = isNote,
                                        isCanvas = isCanvas,
                                        isPdf = isPdf,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("DriveSyncScanner", "Failed to scan SAF folder: $folderDocId", e)
        }
    }

    private fun scanLocalFile(
        dir: File,
        currentPath: String,
        syncScope: SyncScope,
        selectedFolders: Set<String>,
        out: MutableList<SyncItem>,
    ) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            val name = f.name
            if (name.startsWith(".")) continue

            val rel = if (currentPath.isEmpty()) name else "$currentPath/$name"

            if (f.isDirectory) {
                if (syncScope == SyncScope.ALL || isFolderIncluded(rel, selectedFolders)) {
                    scanLocalFile(f, rel, syncScope, selectedFolders, out)
                }
            } else {
                val isNote = name.endsWith(".xnote", ignoreCase = true) || name.endsWith(".note", ignoreCase = true)
                val isCanvas = name.endsWith(".xcanvas", ignoreCase = true)
                val isPdf = name.endsWith(".pdf", ignoreCase = true)
                if (isNote || isCanvas || isPdf) {
                    if (syncScope == SyncScope.ALL || isFolderIncluded(currentPath, selectedFolders)) {
                        out.add(
                            SyncItem(
                                relativePath = rel,
                                name = name,
                                uri = null,
                                file = f,
                                lastModified = f.lastModified(),
                                isNote = isNote,
                                isCanvas = isCanvas,
                                isPdf = isPdf,
                            )
                        )
                    }
                }
            }
        }
    }

    private fun isFolderIncluded(folderPath: String, selectedFolders: Set<String>): Boolean {
        if (folderPath.isEmpty()) return true
        if (selectedFolders.contains(folderPath)) return true
        return selectedFolders.any { selected ->
            folderPath.startsWith("$selected/") || selected.startsWith("$folderPath/")
        }
    }
}
