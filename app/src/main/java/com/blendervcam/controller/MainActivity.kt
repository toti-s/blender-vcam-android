package com.blendervcam.controller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blendervcam.controller.ui.CameraScreen
import com.blendervcam.controller.ui.ConnectScreen
import com.blendervcam.controller.ui.VCamTheme
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableException
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: VCamViewModel by viewModels()
    private var arInstallRequested = false
    private var arPending = false

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableAr() else vm.message = "Camera permission is needed for ARCore tracking"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { VCamTheme { VCamApp(vm) { enableAr() } } }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        if (arPending) enableAr() // returning from the ARCore install screen
    }

    override fun onPause() {
        vm.onPause()
        super.onPause()
    }

    private fun enableAr() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        try {
            val status = ArCoreApk.getInstance().requestInstall(this, !arInstallRequested)
            if (status == ArCoreApk.InstallStatus.INSTALLED) {
                arPending = false
                vm.setTracking(TrackingMode.ARCORE)
            } else {
                arInstallRequested = true
                arPending = true
            }
        } catch (e: UnavailableException) {
            arPending = false
            vm.message = "ARCore is not available on this device"
        }
    }
}

@Composable
fun VCamApp(vm: VCamViewModel, onEnableAr: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (vm.screen) {
            Screen.CONNECT -> ConnectScreen(vm)
            Screen.CAMERA -> CameraScreen(vm, onEnableAr)
        }
        val msg = vm.message
        if (msg != null) {
            LaunchedEffect(msg) {
                delay(4500)
                if (vm.message == msg) vm.message = null
            }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)) {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
