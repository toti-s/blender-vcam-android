package com.blendervcam.controller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.blendervcam.controller.TrackingMode
import com.blendervcam.controller.VCamViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CameraScreen(vm: VCamViewModel, onEnableAr: () -> Unit) {
    BackHandler { if (vm.settingsOpen) vm.settingsOpen = false else vm.disconnect() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Preview(vm)

        // ARCore needs a live GL surface; it is 1dp and shows nothing.
        if (vm.trackingMode == TrackingMode.ARCORE) {
            AndroidView(
                factory = { ctx -> vm.ar.createGlView(ctx) },
                modifier = Modifier.size(1.dp).align(Alignment.TopStart),
            )
        }

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            StatusChip(vm, Modifier.align(Alignment.TopStart))

            if (vm.status.recording) {
                Text(
                    "REC  frame ${vm.status.frame}",
                    color = RecRed,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(HudDark, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Row(
                Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { vm.recenter() }, colors = ButtonDefaults.outlinedButtonColors(containerColor = HudDark)) {
                    Text("Recenter")
                }
                Button(
                    onClick = { vm.toggleRecord() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (vm.status.recording) RecRed else HudDark,
                        contentColor = Color.White,
                    ),
                ) { Text(if (vm.status.recording) "Stop" else "Record") }
                IconButton(onClick = { vm.settingsOpen = !vm.settingsOpen }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }

            Column(Modifier.align(Alignment.BottomStart), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Move", color = Color(0xAAFFFFFF), fontSize = 12.sp)
                Joystick { x, y ->
                    vm.stickStrafe = x
                    vm.stickForward = y
                }
            }
            Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Lift and turn", color = Color(0xAAFFFFFF), fontSize = 12.sp)
                Joystick { x, y ->
                    vm.stickTurn = x
                    vm.stickLift = y
                }
            }

            FocalBar(vm, Modifier.align(Alignment.BottomCenter))
        }

        if (vm.settingsOpen) {
            SettingsPanel(vm, onEnableAr, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun Preview(vm: VCamViewModel) {
    val img = vm.frame
    if (img != null) {
        Image(
            bitmap = img,
            contentDescription = "Live view from the Blender camera",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (vm.showGrid) ThirdsGrid(img.width.toFloat() / img.height.toFloat())
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Waiting for the Blender camera. Keep a 3D viewport visible in Blender.",
                color = Color(0xAAFFFFFF),
            )
        }
    }
}

@Composable
private fun ThirdsGrid(aspect: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val boxAspect = size.width / size.height
        val w: Float
        val h: Float
        if (boxAspect > aspect) {
            h = size.height
            w = h * aspect
        } else {
            w = size.width
            h = w / aspect
        }
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val line = Color(0x55FFFFFF)
        for (i in 1..2) {
            val x = left + w * i / 3f
            val y = top + h * i / 3f
            drawLine(line, Offset(x, top), Offset(x, top + h), 1f)
            drawLine(line, Offset(left, y), Offset(left + w, y), 1f)
        }
        drawRect(Color(0x66FFFFFF), topLeft = Offset(left, top), size = Size(w, h), style = Stroke(1f))
    }
}

@Composable
private fun StatusChip(vm: VCamViewModel, modifier: Modifier) {
    Column(
        modifier.background(HudDark, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val cam = vm.status.camera.ifEmpty { "No camera" }
        Text(cam, color = Color.White, fontWeight = FontWeight.Medium)
        val ping = if (vm.pingMs >= 0) "${vm.pingMs} ms" else "-"
        Text(
            "${vm.focal.roundToInt()} mm   ${vm.previewFps} fps   $ping",
            color = Color(0xCCFFFFFF),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        if (vm.trackingMode == TrackingMode.ARCORE && !vm.arTracking) {
            Text("AR searching: move the phone slowly", color = BlenderOrange, fontSize = 12.sp)
        }
        if (!vm.status.pillow) {
            Text("Install Pillow in Blender for smoother video", color = BlenderOrange, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FocalBar(vm: VCamViewModel, modifier: Modifier) {
    Column(
        modifier.background(HudDark, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (mm in listOf(18, 24, 35, 50, 85, 135)) {
                FilterChip(
                    selected = abs(vm.focal - mm) < 0.5f,
                    onClick = { vm.updateFocal(mm.toFloat()) },
                    label = { Text("$mm") },
                )
            }
        }
        Slider(
            value = vm.focal,
            onValueChange = { vm.updateFocal(it) },
            valueRange = 10f..200f,
            modifier = Modifier.width(320.dp),
        )
    }
}

@Composable
private fun SettingsPanel(vm: VCamViewModel, onEnableAr: () -> Unit, modifier: Modifier) {
    Surface(modifier.width(340.dp).fillMaxHeight(), color = PanelDark) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.settingsOpen = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Close settings")
                }
            }

            Section("Tracking")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vm.trackingMode == TrackingMode.GYRO,
                    onClick = { vm.setTracking(TrackingMode.GYRO) },
                    label = { Text("Gyro + sticks") },
                )
                FilterChip(
                    selected = vm.trackingMode == TrackingMode.ARCORE,
                    onClick = { onEnableAr() },
                    label = { Text("Walk around (ARCore)") },
                )
            }
            if (vm.trackingMode == TrackingMode.ARCORE) {
                LabeledSlider("Motion scale", "x%.2f".format(vm.motionScale), vm.motionScale, 0.25f..10f, { vm.updateMotionScale(it) })
            }
            LabeledSlider("Stick speed", "%.1f m/s".format(vm.moveSpeed), vm.moveSpeed, 0.2f..20f, { vm.moveSpeed = it })
            LabeledSlider("Smoothing", "%.0f%%".format(vm.smoothing * 100f), vm.smoothing, 0f..0.9f, { vm.smoothing = it }, { vm.applySmoothing() })
            ToggleRow("Level horizon", vm.levelHorizon) { vm.setLevel(it) }

            Section("Viewfinder")
            ToggleRow("Thirds grid", vm.showGrid) { vm.showGrid = it }
            Text("Preview width", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (w in listOf(480, 640, 960, 1280)) {
                    FilterChip(
                        selected = vm.previewWidth == w,
                        onClick = { vm.previewWidth = w; vm.applyStream() },
                        label = { Text("$w") },
                    )
                }
            }
            Text("Frame rate", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (f in listOf(15, 30, 60)) {
                    FilterChip(
                        selected = vm.previewTargetFps == f,
                        onClick = { vm.previewTargetFps = f; vm.applyStream() },
                        label = { Text("$f") },
                    )
                }
            }
            LabeledSlider("Image quality", "${vm.previewQuality}", vm.previewQuality.toFloat(), 30f..95f, { vm.previewQuality = it.roundToInt() }, { vm.applyStream() })

            Section("Depth of field")
            ToggleRow("Enabled", vm.dofOn) { vm.dofOn = it; vm.applyDof() }
            if (vm.dofOn) {
                LabeledSlider("Focus distance", "%.1f m".format(vm.focusDistance), vm.focusDistance, 0.3f..50f, { vm.focusDistance = it; vm.applyDof() })
                LabeledSlider("f-stop", "f/%.1f".format(vm.fstop), vm.fstop, 0.8f..22f, { vm.fstop = it; vm.applyDof() })
            }

            if (vm.cameras.size > 1) {
                Section("Camera")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (name in vm.cameras) {
                        FilterChip(
                            selected = name == vm.status.camera,
                            onClick = { vm.selectCamera(name) },
                            label = { Text(name) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = BlenderOrange,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit = {},
) {
    Column {
        Row {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onFinished, valueRange = range)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
