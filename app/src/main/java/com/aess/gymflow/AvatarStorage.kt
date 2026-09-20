package com.aess.gymflow

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File

private const val AVATAR_FILE = "gymflow_avatar.bin"

fun persistAvatar(context: Context, source: Uri): String = runCatching {
    val target = File(context.filesDir, AVATAR_FILE)
    context.contentResolver.openInputStream(source)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Unable to open avatar")
    Uri.fromFile(target).toString()
}.getOrDefault("")

fun avatarBackupData(context: Context): String? = runCatching {
    val file = File(context.filesDir, AVATAR_FILE)
    if (!file.exists() || file.length() <= 0L) return null
    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
}.getOrNull()

fun restoreAvatarFromBackup(context: Context, data: String): String = runCatching {
    val bytes = Base64.decode(data, Base64.DEFAULT)
    if (bytes.isEmpty()) return ""
    val file = File(context.filesDir, AVATAR_FILE)
    file.writeBytes(bytes)
    Uri.fromFile(file).toString()
}.getOrDefault("")

fun deleteStoredAvatar(context: Context) {
    runCatching { File(context.filesDir, AVATAR_FILE).delete() }
}
