package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.meucoach.core.designsystem.R

/**
 * Brand assets shared by the story preview and the exported video (ADR 0015 §2.8).
 * The wordmark lives in `core:designsystem` so every surface renders the same logo.
 */
object StoryBranding {
    fun logo(context: Context): Bitmap? = BitmapFactory.decodeResource(context.resources, R.drawable.logo_mc)
}
