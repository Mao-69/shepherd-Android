package com.shepherd

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shepherd.ui.MainViewModel
import com.shepherd.ui.ShepherdApp
import com.shepherd.ui.ShepherdTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark by vm.darkTheme.collectAsState()
            ShepherdTheme(dark) {
                var granted by remember { mutableStateOf(hasPermissions()) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result -> granted = result.values.all { it } }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (granted) {
                        ShepherdApp(vm)
                    } else {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Shepherd needs Bluetooth access",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.height(8.dp))
                            Text("Used to connect your watch for live biometrics. " +
                                "You can also run a session without a watch.",
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { launcher.launch(permissions) }) {
                                Text("Grant permission")
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { granted = true }) {
                                Text("Continue without watch")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasPermissions(): Boolean = permissions.all {
        checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
