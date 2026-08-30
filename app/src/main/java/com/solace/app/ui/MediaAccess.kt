package com.solace.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccess { NONE, PARTIAL, FULL }

fun mediaAccess(context: Context): MediaAccess {
    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    return when {
        Build.VERSION.SDK_INT >= 34 -> {
            val images = granted(Manifest.permission.READ_MEDIA_IMAGES)
            val videos = granted(Manifest.permission.READ_MEDIA_VIDEO)
            val selected = granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            when {
                images && videos -> MediaAccess.FULL
                images || videos || selected -> MediaAccess.PARTIAL
                else -> MediaAccess.NONE
            }
        }
        Build.VERSION.SDK_INT >= 33 -> {
            if (granted(Manifest.permission.READ_MEDIA_IMAGES) &&
                granted(Manifest.permission.READ_MEDIA_VIDEO)
            ) {
                MediaAccess.FULL
            } else {
                MediaAccess.NONE
            }
        }
        else -> {
            if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                MediaAccess.FULL
            } else {
                MediaAccess.NONE
            }
        }
    }
}

fun fullAccessPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun upgradePermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
