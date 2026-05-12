package com.example.shrink.share

import android.content.Context
import android.content.Intent
import android.net.Uri

class ShareIntentHandler(private val context: Context) {
    fun incomingVideoUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        val type = intent.type ?: context.contentResolver.getType(uri)
        return if (type?.startsWith("video/") == true) uri else null
    }
}
