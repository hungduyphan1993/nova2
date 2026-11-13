package com.example.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ExternalPlayerHelper {
    fun launchExternalPlayer(context: Context, url: String, title: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open stream with...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "No external video player installed", Toast.LENGTH_SHORT).show()
        }
    }
}
