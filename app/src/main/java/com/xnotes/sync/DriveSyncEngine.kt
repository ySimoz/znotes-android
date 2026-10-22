package com.xnotes.sync

import android.content.Context
import android.net.Uri
import com.xnotes.core.model.Page
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.resolvedPageColor
import com.xnotes.format.DocumentCodec
import com.xnotes.platform.AndroidImageCodec
import com.xnotes.platform.AndroidTextMeasurer
import com.xnotes.platform.PdfExporter
import com.xnotes.platform.PdfSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DriveSyncEngine(private val context: Context) {

    private val ledger = DriveSyncLedger(context)
    private val scanner = DriveSyncScanner(context)
    private val folderIdCache = mutableMapOf<String, String>()

    suspend fun sync(
        prefs: DriveSyncPreferences,
        browseRoot: String?,
        onProgress: ((current: Int, total: Int, name: String) -> Unit)? = null,
    ): SyncResult = withContext(Dispatchers.IO) {
        val accessToken = GoogleAuthManager.getValidAccessToken(context, prefs.customClientId)
            ?: return@withContext SyncResult(false, message = "Not authenticated with Google Drive")

        val client = GoogleDriveClient(accessToken)

        val rootFolderId = try {
            client.getOrCreateZNotesRoot()
        } catch (e: Exception) {
            return@withContext SyncResult(false, message = "Failed to access zNotes folder on Google Drive: ${e.message}")
        }
        folderIdCache[""] = rootFolderId

        val localItems = scanner.scan(browseRoot, prefs.syncScope, prefs.selectedFolders)
        val ledgerMap = ledger.getAll()

        // Map target remote relative path (e.g. "Math/Lecture1.pdf") -> local item
        val targetPathToLocal = mutableMapOf<String, SyncItem>()
        for (item in localItems) {
            val remoteRelPath = if (item.isNote) {
                item.relativePath.removeSuffix(".note") + ".pdf"
            } else {
                item.relativePath
            }
            targetPathToLocal[remoteRelPath] = item
        }

        // Determine deletions
        val deletedRelPaths = ledgerMap.keys.filter { it !in targetPathToLocal.keys }
        var deletedCount = 0

        if (prefs.deletionPolicy == DeletionPolicy.MOVE_TO_TRASH) {
            for (delPath in deletedRelPaths) {
                val entry = ledgerMap[delPath] ?: continue
                try {
                    client.trashFile(entry.driveFileId)
                    ledger.remove(delPath)
                    deletedCount++
                } catch (_: Exception) { }
            }
        } else if (prefs.deletionPolicy == DeletionPolicy.KEEP_ON_DRIVE) {
            for (delPath in deletedRelPaths) {
                ledger.remove(delPath)
            }
        }

        // Determine additions and updates
        val toSync = mutableListOf<Pair<String, SyncItem>>()
        for ((remotePath, item) in targetPathToLocal) {
            val entry = ledgerMap[remotePath]
            if (entry == null || item.lastModified > entry.lastSyncedMtime) {
                toSync.add(remotePath to item)
            }
        }

        var syncedCount = 0
        var errorCount = 0
        val tempDir = File(context.cacheDir, "sync_pdf_temps").apply { mkdirs() }

        for ((index, pair) in toSync.withIndex()) {
            val (remoteRelPath, item) = pair
            val displayName = remoteRelPath.substringAfterLast('/')
            onProgress?.invoke(index + 1, toSync.size, displayName)

            try {
                // Determine remote parent folder ID
                val pathSegments = remoteRelPath.split('/')
                val folderSegments = pathSegments.dropLast(1)
                val folderKey = folderSegments.joinToString("/")
                val parentFolderId = folderIdCache.getOrPut(folderKey) {
                    client.getOrCreateSubfolder(folderSegments, rootFolderId)
                }

                val remoteName = pathSegments.last()
                val tempPdfFile = File(tempDir, "temp_${System.currentTimeMillis()}_$remoteName")

                try {
                    // Prepare PDF content
                    if (item.isNote) {
                        openInputStream(item).use { inStream ->
                            if (inStream == null) throw IllegalStateException("Cannot read ${item.name}")
                            compileNoteToPdf(inStream, tempPdfFile)
                        }
                    } else {
                        // Plain PDF copy
                        openInputStream(item).use { inStream ->
                            if (inStream == null) throw IllegalStateException("Cannot read ${item.name}")
                            FileOutputStream(tempPdfFile).use { out -> inStream.copyTo(out) }
                        }
                    }

                    val existingEntry = ledgerMap[remoteRelPath]
                    val driveFileId = if (existingEntry != null) {
                        client.updatePdf(existingEntry.driveFileId, remoteName, tempPdfFile)
                        existingEntry.driveFileId
                    } else {
                        client.uploadPdf(remoteName, parentFolderId, tempPdfFile)
                    }

                    ledger.put(
                        remoteRelPath,
                        LedgerEntry(
                            driveFileId = driveFileId,
                            driveParentId = parentFolderId,
                            localMtime = item.lastModified,
                            lastSyncedMtime = System.currentTimeMillis(),
                            sizeBytes = tempPdfFile.length(),
                        )
                    )
                    syncedCount++
                } finally {
                    if (tempPdfFile.exists()) tempPdfFile.delete()
                }
            } catch (e: Exception) {
                errorCount++
                android.util.Log.e("DriveSyncEngine", "Failed to sync $remoteRelPath", e)
            }
        }

        val msg = when {
            errorCount > 0 -> "Synced $syncedCount, $errorCount errors"
            syncedCount == 0 && deletedCount == 0 -> "Up to date"
            else -> "Synced $syncedCount notes" + (if (deletedCount > 0) ", $deletedCount deleted" else "")
        }

        SyncResult(
            success = errorCount == 0,
            syncedCount = syncedCount,
            deletedCount = deletedCount,
            errorCount = errorCount,
            message = msg,
        )
    }

    private fun openInputStream(item: SyncItem): InputStream? {
        return if (item.file != null) {
            item.file.inputStream()
        } else if (item.uri != null) {
            context.contentResolver.openInputStream(item.uri)
        } else null
    }

    private fun compileNoteToPdf(inputStream: InputStream, outputFile: File) {
        val pdfDir = File(context.cacheDir, "sync_pdf_sources").apply { mkdirs() }
        val imageDir = File(context.cacheDir, "sync_img_sources").apply { mkdirs() }
        val imageCodec = AndroidImageCodec()
        val textMeasurer = AndroidTextMeasurer()
        val codec = DocumentCodec(imageCodec, textMeasurer)

        val doc = codec.read(inputStream, pdfDir, imageDir)
            ?: throw IllegalStateException("Failed to parse note")

        val source = doc.pdfFile?.let { PdfSource.open(it) }
        try {
            outputFile.outputStream().use { out ->
                PdfExporter.export(
                    context = context,
                    doc = doc,
                    source = source,
                    out = out,
                    paperColor = { p: Page -> p.resolvedPageColor(doc, null) ?: Rgba(255, 255, 255) },
                )
            }
        } finally {
            source?.close()
            doc.pdfFile?.delete()
        }
    }
}
