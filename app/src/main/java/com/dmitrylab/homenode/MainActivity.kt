package com.dmitrylab.homenode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dmitrylab.homenode.server.HomeNodeService
import com.dmitrylab.homenode.server.NodeServerController
import com.dmitrylab.homenode.server.ServerStatus

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            HomeNodeService.startService(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val status by NodeServerController.status.collectAsState()
            val lastError by NodeServerController.lastError.collectAsState()

            val isActive = status == ServerStatus.RUNNING || status == ServerStatus.STARTING
            val isTransitioning = status == ServerStatus.STARTING || status == ServerStatus.STOPPING

            val statusLabel = when (status) {
                ServerStatus.STOPPED -> "Сервер остановлен"
                ServerStatus.STARTING -> "Запуск сервера..."
                ServerStatus.RUNNING -> "Сервер запущен на порту 8080"
                ServerStatus.STOPPING -> "Остановка сервера..."
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "HomeNode API", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = statusLabel, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Локально: http://127.0.0.1:8080/", fontSize = 12.sp)

                        if (lastError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Ошибка: $lastError",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            enabled = !isTransitioning,
                            onClick = {
                                if (isActive) {
                                    HomeNodeService.stopService(this@MainActivity)
                                } else {
                                    startServerWithPermissionCheck()
                                }
                            }
                        ) {
                            Text(if (isActive) "Выключить сервер" else "Включить сервер")
                        }
                    }
                }
            }
        }
    }

    private fun startServerWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        HomeNodeService.startService(this)
    }
}