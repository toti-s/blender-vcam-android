package com.blendervcam.controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blendervcam.controller.VCamViewModel

@Composable
fun ConnectScreen(vm: VCamViewModel) {
    Row(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // Left: discovery
        Column(Modifier.weight(1f)) {
            Text("Blender VCam", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "Use your phone as a handheld camera in Blender.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Blender on this network", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.scan() }, enabled = !vm.scanning) {
                    Text(if (vm.scanning) "Searching..." else "Search")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (vm.hosts.isEmpty()) {
                Text(
                    if (vm.scanning) "Looking for Blender..." else "Nothing found yet. In Blender press N, open the VCam tab and choose Start Server, then tap Search. You can also type the address on the right.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.hosts) { h ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                .clickable { vm.connect(h.address, h.port) }
                                .padding(14.dp),
                        ) {
                            Text(h.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${h.address}:${h.port}" + if (h.blender.isNotEmpty()) "  -  Blender ${h.blender}" else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        // Right: manual connect
        Column(Modifier.width(300.dp)) {
            Spacer(Modifier.height(64.dp))
            Text("Connect by address", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.hostText,
                onValueChange = { vm.hostText = it },
                label = { Text("IP address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.portText,
                onValueChange = { vm.portText = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = { vm.connectManual() }, enabled = !vm.connecting, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.connecting) "Connecting..." else "Connect")
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Phone and computer must be on the same Wi-Fi. If nothing connects, allow Blender through the firewall.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}
