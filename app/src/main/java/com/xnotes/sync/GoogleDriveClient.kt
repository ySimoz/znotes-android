package com.xnotes.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleDriveClient(private val accessToken: String) {

    private fun openConnection(urlStr: String, method: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        return conn
    }

    suspend fun getOrCreateZNotesRoot(): String = withContext(Dispatchers.IO) {
        val existing = findFile("zNotes", parentId = "root", isFolder = true)
        if (existing != null) return@withContext existing
        createFolder("zNotes", parentId = "root")
    }

    suspend fun getOrCreateSubfolder(pathSegments: List<String>, rootFolderId: String): String = withContext(Dispatchers.IO) {
        var currentParent = rootFolderId
        for (segment in pathSegments) {
            val existing = findFile(segment, parentId = currentParent, isFolder = true)
            currentParent = existing ?: createFolder(segment, parentId = currentParent)
        }
        currentParent
    }

    suspend fun findFile(name: String, parentId: String? = null, isFolder: Boolean? = null): String? = withContext(Dispatchers.IO) {
        val conditions = mutableListOf("name = '${escapeQuery(name)}'", "trashed = false")
        if (parentId != null) conditions.add("'$parentId' in parents")
        if (isFolder == true) conditions.add("mimeType = 'application/vnd.google-apps.folder'")
        if (isFolder == false) conditions.add("mimeType != 'application/vnd.google-apps.folder'")

        val q = conditions.joinToString(" and ")
        val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(q, "UTF-8")}&fields=files(id,name)&spaces=drive"
        val conn = openConnection(url, "GET")

        if (conn.responseCode !in 200..299) return@withContext null
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val files = json.optJSONArray("files") ?: return@withContext null
        if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    suspend fun createFolder(name: String, parentId: String = "root"): String = withContext(Dispatchers.IO) {
        val conn = openConnection("https://www.googleapis.com/drive/v3/files", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

        val meta = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            put("parents", JSONArray().put(parentId))
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(meta.toString()) }
        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw IllegalStateException("Failed to create folder $name: $err")
        }

        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        json.getString("id")
    }

    suspend fun uploadPdf(name: String, parentId: String, pdfFile: File): String = withContext(Dispatchers.IO) {
        val boundary = "==ZNOTES_MULTIPART_BOUNDARY_${System.currentTimeMillis()}=="
        val conn = openConnection("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")

        val meta = JSONObject().apply {
            put("name", name)
            put("parents", JSONArray().put(parentId))
        }

        conn.outputStream.use { out ->
            writeMultipartBody(out, boundary, meta, pdfFile)
        }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw IllegalStateException("Failed to upload $name: $err")
        }

        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        json.getString("id")
    }

    suspend fun updatePdf(fileId: String, name: String, pdfFile: File): Boolean = withContext(Dispatchers.IO) {
        val boundary = "==ZNOTES_MULTIPART_BOUNDARY_${System.currentTimeMillis()}=="
        val conn = openConnection("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=multipart&fields=id,name", "PATCH")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")

        val meta = JSONObject().apply {
            put("name", name)
        }

        conn.outputStream.use { out ->
            writeMultipartBody(out, boundary, meta, pdfFile)
        }

        conn.responseCode in 200..299
    }

    suspend fun trashFile(fileId: String): Boolean = withContext(Dispatchers.IO) {
        val conn = openConnection("https://www.googleapis.com/drive/v3/files/$fileId", "PATCH")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

        val body = JSONObject().put("trashed", true).toString()
        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }
        conn.responseCode in 200..299
    }

    suspend fun moveFile(fileId: String, newParentId: String, oldParentId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?addParents=${URLEncoder.encode(newParentId, "UTF-8")}&removeParents=${URLEncoder.encode(oldParentId, "UTF-8")}"
        val conn = openConnection(url, "PATCH")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write("{}") }
        conn.responseCode in 200..299
    }

    private fun writeMultipartBody(out: OutputStream, boundary: String, meta: JSONObject, file: File) {
        val writer = OutputStreamWriter(out, "UTF-8")

        // Part 1: Metadata
        writer.write("--$boundary\r\n")
        writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        writer.write(meta.toString())
        writer.write("\r\n")
        writer.flush()

        // Part 2: File content
        writer.write("--$boundary\r\n")
        writer.write("Content-Type: application/pdf\r\n\r\n")
        writer.flush()

        FileInputStream(file).use { input ->
            val buf = ByteArray(16384)
            var bytesRead: Int
            while (input.read(buf).also { bytesRead = it } != -1) {
                out.write(buf, 0, bytesRead)
            }
        }
        out.flush()

        writer.write("\r\n--$boundary--\r\n")
        writer.flush()
    }

    private fun escapeQuery(value: String): String = value.replace("'", "\\'")
}
