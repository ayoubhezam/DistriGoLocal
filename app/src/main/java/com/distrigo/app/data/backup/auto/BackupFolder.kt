package com.distrigo.app.data.backup.auto

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.distrigo.app.data.backup.BackupFormat
import java.io.File

/** A file in a [BackupFolder]. */
data class FolderFile(val name: String, val uri: Uri, val size: Long)

/**
 * Where automatic backups are written: the folder the user chose, or the app's own storage when that folder
 * cannot be used. Every call may fail for reasons outside the app (the folder was deleted, its permission
 * revoked, the storage is full), so each reports failure rather than assuming it cannot happen.
 */
interface BackupFolder {
    /** A name the user recognises, for the screen. */
    val displayName: String

    /** Whether files can be listed and written here right now. */
    fun isAvailable(): Boolean

    /** An empty file named [name], ready to be written through the content resolver. */
    fun create(name: String): Uri

    fun list(): List<FolderFile>

    fun delete(file: FolderFile): Boolean
}

/** A folder in the app's own storage: always available while the app is installed, and gone when it is not. */
class PrivateFolder(private val dir: File, override val displayName: String = "Stockage de l'application") : BackupFolder {

    override fun isAvailable(): Boolean = dir.isDirectory || dir.mkdirs()

    override fun create(name: String): Uri {
        require(isAvailable()) { "cannot create $dir" }
        val file = File(dir, name)
        file.writeBytes(ByteArray(0))
        return Uri.fromFile(file)
    }

    override fun list(): List<FolderFile> =
        dir.listFiles { file -> file.isFile }.orEmpty().map { FolderFile(it.name, Uri.fromFile(it), it.length()) }

    override fun delete(file: FolderFile): Boolean = File(requireNotNull(file.uri.path)).delete()
}

/**
 * A folder the user picked with the system folder picker, written through its documents provider.
 *
 * The app keeps access across restarts through a persisted permission, which the user can take back, and
 * the folder can be deleted from a file manager: [isAvailable] checks both before anything is written.
 */
class PickedFolder(private val resolver: ContentResolver, val treeUri: Uri) : BackupFolder {

    private val folderDocument: Uri
        get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    override val displayName: String
        get() = try {
            resolver.query(folderDocument, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        } ?: treeUri.lastPathSegment?.substringAfterLast(':')?.ifEmpty { null } ?: "Dossier choisi"

    override fun isAvailable(): Boolean {
        val permitted = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission && it.isWritePermission }
        if (!permitted) return false
        return try {
            resolver.query(folderDocument, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use {
                it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "the backup folder cannot be read", e)
            false
        }
    }

    override fun create(name: String): Uri =
        DocumentsContract.createDocument(resolver, folderDocument, BackupFormat.MIME_TYPE, name)
            ?: throw java.io.IOException("the folder refused to create $name")

    override fun list(): List<FolderFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        return resolver.query(children, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                    add(FolderFile(cursor.getString(1) ?: continue, uri, if (cursor.isNull(2)) 0L else cursor.getLong(2)))
                }
            }
        }.orEmpty()
    }

    override fun delete(file: FolderFile): Boolean = try {
        DocumentsContract.deleteDocument(resolver, file.uri)
    } catch (e: Exception) {
        Log.w(TAG, "could not delete ${file.name}", e)
        false
    }

    companion object {
        private const val TAG = "PickedFolder"

        const val PERMISSION_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
