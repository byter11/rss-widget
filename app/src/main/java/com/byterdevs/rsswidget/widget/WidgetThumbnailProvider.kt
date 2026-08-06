package com.byterdevs.rsswidget.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class WidgetThumbnailProvider : ContentProvider() {

    companion object {
        const val AUTHORITY_SUFFIX = "widgetthumbnails"

        fun uriFor(context: android.content.Context, fileName: String): Uri {
            val authority = "${context.packageName}.$AUTHORITY_SUFFIX"
            return Uri.parse("content://$authority/$fileName")
        }

        fun cacheDir(context: android.content.Context): File {
            val dir = File(context.cacheDir, "widget_thumbs")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val fileName = uri.lastPathSegment ?: return null
        val file = File(cacheDir(context!!), fileName)
        if (!file.exists()) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val fileName = uri.lastPathSegment ?: return null
        val file = File(cacheDir(context!!), fileName)
        val cursor = MatrixCursor(arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE))
        cursor.addRow(arrayOf(fileName, file.length()))
        return cursor
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
