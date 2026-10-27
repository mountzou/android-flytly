package com.mountzou.flytly.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mountzou.flytly.R
import java.io.File

internal fun shareImageIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.travelcards", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, "My travels · Flytly")
        clipData = ClipData.newUri(context.contentResolver, "My travels · Flytly", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}
internal fun instagramAppID(value: String): String? = value.trim().takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } && it.any { c -> c != '0' } }
internal fun instagramStoryIntent(context: Context, uri: Uri): Intent? {
    val appID = instagramAppID(context.getString(R.string.instagram_stories_app_id)) ?: return null
    return Intent("com.instagram.share.ADD_TO_STORY").apply {
        setPackage("com.instagram.android")
        setDataAndType(uri, "image/png")
        putExtra("source_application", appID)
        clipData = ClipData.newUri(context.contentResolver, "My travels · Flytly", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
internal fun instagramAvailable(context: Context): Boolean =
    instagramStoryIntent(context, Uri.EMPTY)?.resolveActivity(context.packageManager) != null

internal fun shareToInstagram(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.travelcards", file)
    val intent = instagramStoryIntent(context, uri) ?: error("Instagram unavailable")
    check(intent.resolveActivity(context.packageManager) != null)
    context.startActivity(intent)
}
