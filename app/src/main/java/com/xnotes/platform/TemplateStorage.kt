package com.xnotes.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Manages SAF external storage for user templates in the `.xnotes-templates` folder beside notes.
 * Keeps user templates accessible across notes sync and backup boundaries.
 */
internal object TemplateStorage {

    const val STORAGE_DIR = ".xnotes-templates"

    /** Finds or creates the `.xnotes-templates` directory under the granted [tree]. */
    fun ensureStorageDir(context: Context, tree: Uri): String? = runCatching {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        externalChildren(context, tree, rootId).firstOrNull { it.first == STORAGE_DIR }?.second?.let { return it }

        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val created = DocumentsContract.createDocument(
            context.contentResolver, root, DocumentsContract.Document.MIME_TYPE_DIR, STORAGE_DIR,
        ) ?: return null
        DocumentsContract.getDocumentId(created)
    }.getOrNull()

    /** Queries children inside [parentId] under [tree], returning pairs of display name and document id. */
    fun externalChildren(context: Context, tree: Uri, parentId: String): List<Pair<String, String>> = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = ArrayList<Pair<String, String>>()
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                val id = cursor.getString(1) ?: continue
                out += name to id
            }
        }
        out
    }.getOrDefault(emptyList())

    /** Writes template [bytes] into a file named [name] within [dirId]. */
    fun writeExternal(context: Context, tree: Uri, dirId: String, name: String, bytes: ByteArray) {
        runCatching {
            val existing = externalChildren(context, tree, dirId).firstOrNull { it.first == name }?.second
            val target = existing?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
                ?: DocumentsContract.createDocument(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(tree, dirId),
                    "application/json", name,
                ) ?: return
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(bytes) }
        }
    }

    /** Deletes the template document matching [name] within [dirId]. */
    fun deleteExternal(context: Context, tree: Uri, dirId: String, name: String) {
        runCatching {
            val id = externalChildren(context, tree, dirId).firstOrNull { it.first == name }?.second ?: return
            DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
        }
    }

    /** Reads UTF-8 template text from an external document URI. */
    fun readExternal(context: Context, tree: Uri, id: String): String? = runCatching {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }.getOrNull()
}
