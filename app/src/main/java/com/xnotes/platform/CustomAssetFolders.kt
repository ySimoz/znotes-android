package com.xnotes.platform

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File

/** User-owned image libraries kept in hidden folders beside the selected notes root. */
object CustomAssetFolders {
    enum class Kind(val folder: String) { PAGE("page-patterns"), COVER("cover-patterns") }

    /** Lightweight image reference storing file or URI pointers instead of eager raw byte arrays. */
    data class CustomImage(val id: String, val name: String, val file: File? = null, val uri: Uri? = null) {
        constructor(displayName: String, file: File? = null, uri: Uri? = null) : this(
            file?.name ?: uri?.lastPathSegment ?: displayName, displayName, file, uri
        )
        val displayName: String get() = CustomAssetFolders.displayName(name)

        /** Reads raw bytes on-demand from backing file or SAF URI without retaining them in heap. */
        fun readBytes(context: Context): ByteArray =
            file?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrDefault(ByteArray(0)) }
                ?: uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull() } ?: ByteArray(0)

        /** Reads raw bytes on-demand from backing local file without requiring Android context. */
        fun readBytes(): ByteArray =
            file?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrDefault(ByteArray(0)) } ?: ByteArray(0)
    }

    const val ROOT_DIR = ".xnotes-pages"

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "avif", "heic", "heif", "img")

    fun isImageName(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /** Hide the import timestamp from thumbnail captions without renaming stored files. */
    fun displayName(fileName: String): String =
        fileName.substringBeforeLast('.', fileName).replace(Regex("-[0-9]{13}$"), "")

    fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int =
        ThumbnailDecoder.calculateInSampleSize(width, height, reqWidth, reqHeight)

    fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX, reqHeight: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX): Bitmap? =
        ThumbnailDecoder.decodeSampledBitmap(bytes, reqWidth, reqHeight)

    suspend fun decodeThumbnailAsync(bytes: ByteArray, reqWidth: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX, reqHeight: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX): Bitmap? =
        ThumbnailDecoder.decodeThumbnailAsync(bytes, reqWidth, reqHeight)

    suspend fun decodeThumbnail(context: Context, uri: Uri, reqWidth: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX, reqHeight: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX): Bitmap? =
        ThumbnailDecoder.decodeThumbnail(context, uri, reqWidth, reqHeight)

    suspend fun decodeThumbnail(file: File, reqWidth: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX, reqHeight: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX): Bitmap? =
        ThumbnailDecoder.decodeThumbnail(file, reqWidth, reqHeight)

    suspend fun decodeThumbnail(context: Context, image: CustomImage, reqWidth: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX, reqHeight: Int = ThumbnailDecoder.DEFAULT_THUMB_SIZE_PX): Bitmap? =
        image.file?.let { ThumbnailDecoder.decodeThumbnail(it, reqWidth, reqHeight) } ?: image.uri?.let { ThumbnailDecoder.decodeThumbnail(context, it, reqWidth, reqHeight) }

    /** Save an image to the page- or cover-pattern library. The new note embeds its own copy too. */
    fun storeImage(context: Context, treeUri: String?, bytes: ByteArray, sourceName: String?, kind: Kind): Boolean {
        if (treeUri.isNullOrBlank() || bytes.isEmpty()) return false
        if (!treeUri.startsWith("content:")) {
            val path = if (treeUri.startsWith("file:")) Uri.parse(treeUri).path else treeUri
            return path?.let { storeImage(File(it), bytes, sourceName, kind) } ?: false
        }
        return runCatching {
            val resolver = context.applicationContext.contentResolver
            val tree = Uri.parse(treeUri)
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val pagesId = ensureDirectory(context, tree, rootId, root, ROOT_DIR) ?: return false
            val kindId = ensureDirectory(context, tree, pagesId, DocumentsContract.buildDocumentUriUsingTree(tree, pagesId), kind.folder) ?: return false
            val kindUri = DocumentsContract.buildDocumentUriUsingTree(tree, kindId)
            val name = formatAssetName(sourceName, kind)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "")) ?: "application/octet-stream"
            val target = DocumentsContract.createDocument(resolver, kindUri, mime, name) ?: return false
            resolver.openOutputStream(target, "wt")?.use { it.write(bytes) } ?: return false
            true
        }.getOrDefault(false)
    }

    /** Save an image into a local filesystem root folder beside notes. */
    fun storeImage(rootDirectory: File, bytes: ByteArray, sourceName: String?, kind: Kind): Boolean {
        if (!rootDirectory.exists() || bytes.isEmpty()) return false
        return runCatching {
            val targetDir = File(File(rootDirectory, ROOT_DIR), kind.folder)
            if (!targetDir.exists() && !targetDir.mkdirs()) return false
            val name = formatAssetName(sourceName, kind)
            File(targetDir, name).writeBytes(bytes)
            true
        }.getOrDefault(false)
    }

    /** Remove only a file that still belongs to the requested custom-image folder. */
    fun deleteImage(context: Context, treeUri: String?, kind: Kind, documentId: String): Boolean {
        if (treeUri.isNullOrBlank() || documentId.isBlank()) return false
        if (!treeUri.startsWith("content:")) {
            val path = if (treeUri.startsWith("file:")) Uri.parse(treeUri).path else treeUri
            return path?.let { deleteImage(File(it), kind, documentId) } ?: false
        }
        return runCatching {
            val resolver = context.applicationContext.contentResolver
            val tree = Uri.parse(treeUri)
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val pagesId = childId(context, tree, rootId, ROOT_DIR) ?: return false
            val kindId = childId(context, tree, pagesId, kind.folder) ?: return false
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, kindId)
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
            var belongs = false
            resolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) if (c.getString(0) == documentId && c.getString(1) != DocumentsContract.Document.MIME_TYPE_DIR) { belongs = true; break }
            }
            if (!belongs) return false
            DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, documentId))
        }.getOrDefault(false)
    }

    /** Remove a custom image from a local filesystem root directory. */
    fun deleteImage(rootDirectory: File, kind: Kind, fileName: String): Boolean =
        File(File(File(rootDirectory, ROOT_DIR), kind.folder), fileName).let { it.exists() && it.delete() }

    /** Read the selected kind's image library from the granted notes folder. */
    fun loadImages(context: Context, treeUri: String?, kind: Kind): List<CustomImage> {
        if (treeUri.isNullOrBlank()) return emptyList()
        if (!treeUri.startsWith("content:")) {
            val path = if (treeUri.startsWith("file:")) Uri.parse(treeUri).path else treeUri
            return path?.let { loadImages(File(it), kind) } ?: emptyList()
        }
        return runCatching {
            val resolver = context.applicationContext.contentResolver
            val tree = Uri.parse(treeUri)
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val pagesId = childId(context, tree, rootId, ROOT_DIR) ?: return emptyList()
            val kindId = childId(context, tree, pagesId, kind.folder) ?: return emptyList()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, kindId)
            val refs = ArrayList<Pair<String, String>>()
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
            resolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val id = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    if (mime.startsWith("image/") || isImageName(name)) refs += name to id
                }
            }
            refs.map { (name, id) -> CustomImage(id = id, name = name, uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }.getOrDefault(emptyList())
    }

    /** Load custom images stored in a local directory structure. */
    fun loadImages(rootDirectory: File, kind: Kind): List<CustomImage> =
        loadImagesFromDirectory(File(File(rootDirectory, ROOT_DIR), kind.folder))

    /** Load images directly from a directory, filtering non-images and corrupt files gracefully. */
    fun loadImagesFromDirectory(folder: File): List<CustomImage> {
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        val files = folder.listFiles() ?: return emptyList()
        return files.mapNotNull { f ->
            if (!f.isFile || !isImageName(f.name) || f.length() < 4L) return@mapNotNull null
            // Check only first 16 bytes for magic signature to avoid loading full image into memory
            val header = runCatching {
                f.inputStream().use { s ->
                    val buf = ByteArray(16)
                    val r = s.read(buf, 0, 16)
                    if (r > 0) buf.copyOf(r) else null
                }
            }.getOrNull() ?: return@mapNotNull null
            if (!ThumbnailDecoder.isValidImageBytes(header)) return@mapNotNull null
            CustomImage(id = f.name, name = f.name, file = f)
        }
    }

    private fun formatAssetName(sourceName: String?, kind: Kind): String {
        val raw = sourceName?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        val ext = raw.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        val stem = raw.substringBeforeLast('.', raw).take(80).ifBlank { if (kind == Kind.COVER) "custom-cover" else "custom-page" }
        return "$stem-${System.currentTimeMillis()}.${ext ?: "img"}"
    }

    private fun ensureDirectory(context: Context, tree: Uri, parentId: String, parentUri: Uri, name: String): String? {
        childId(context, tree, parentId, name)?.let { return it }
        val created = DocumentsContract.createDocument(context.contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name) ?: return null
        return DocumentsContract.getDocumentId(created)
    }

    private fun childId(context: Context, tree: Uri, parentId: String, name: String): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        return context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(0) == name) return c.getString(1)
            null
        }
    }
}
