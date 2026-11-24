package com.jassemsendi.unpacked

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.jassemsendi.unpacked.ui.UnpackedApp
import org.koin.mp.KoinPlatform

/** The one Activity: hosts the shared UI and opens the system picker for the Export picker while it is on screen. */
class MainActivity :
    ComponentActivity(),
    AndroidExportPicker.Host {
    private val picker: AndroidExportPicker by lazy { KoinPlatform.getKoin().get() }

    private val zipPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> picker.onPicked(uri) }

    // Asked before a pick while not yet granted, so the Import's progress can be shown while the user is elsewhere;
    // the picker opens whatever the answer, since the Import itself needs no notification.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { openZipPicker() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        picker.attach(this)
        setContent {
            UnpackedApp()
        }
    }

    override fun onDestroy() {
        picker.detach(this)
        super.onDestroy()
    }

    override fun openZip() {
        if (needsNotificationPermission()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openZipPicker()
        }
    }

    private fun openZipPicker() {
        zipPicker.launch(ZIP_MIME_TYPES)
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private companion object {
        // Downloads label a zip as the first; some providers as the second, and some not at all.
        val ZIP_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
    }
}
