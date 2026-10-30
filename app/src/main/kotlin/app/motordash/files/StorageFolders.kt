package app.motordash.files

import android.content.Context
import android.os.Environment
import app.motordash.BuildConfig
import app.motordash.api.StorageLocation
import java.io.File

/**
 * Where received files are saved: Download/ or Documents/ (the only public folders that take any
 * file type without "All files access", which DiLink doesn't offer), plus an optional folder name.
 * The dev build defaults to its own folder: Android hides other apps' non-media files from us anyway.
 */
class StorageFolders(context: Context) {
    private val prefs = context.getSharedPreferences("motordash-files", Context.MODE_PRIVATE)

    fun load() = StorageLocation(
        base = runCatching { StorageLocation.Base.valueOf(prefs.getString("base", null) ?: "") }.getOrDefault(StorageLocation.Base.Download),
        name = prefs.getString("name", null) ?: BuildConfig.FILES_FOLDER,
    )

    fun save(location: StorageLocation) {
        prefs.edit().putString("base", location.base.name).putString("name", location.name).apply()
    }

    fun dirFor(location: StorageLocation): File {
        val type = if (location.base == StorageLocation.Base.Documents) Environment.DIRECTORY_DOCUMENTS else Environment.DIRECTORY_DOWNLOADS
        val base = Environment.getExternalStoragePublicDirectory(type)
        return if (location.name.isEmpty()) base else File(base, location.name)
    }
}

/**
 * Moves everything in [from] into [to]; a name already taken there gets a numbered copy.
 * False if something couldn't be moved (it stays in [from]).
 */
internal fun moveContents(from: File, to: File): Boolean {
    var ok = true
    for (item in from.listFiles().orEmpty()) {
        if (item == to) continue // the new folder may sit inside the old one
        val dest = FileServer.uniqueFile(to, item.name)
        // Same storage volume, so a rename is instant even for big videos.
        if (!item.renameTo(dest)) {
            ok = runCatching { item.copyRecursively(dest) && item.deleteRecursively() }.getOrDefault(false) && ok
        }
    }
    return ok
}
