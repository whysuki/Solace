package com.solace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.solace.app.ui.MediaAccess
import com.solace.app.ui.SettingsScreen
import com.solace.app.ui.SolaceApp
import com.solace.app.ui.fullAccessPermissions
import com.solace.app.ui.mediaAccess
import com.solace.app.ui.upgradePermissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionGate()
                }
            }
        }
    }
}

@Composable
private fun PermissionGate() {
    val context = LocalContext.current
    var access by remember { mutableStateOf(mediaAccess(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        access = mediaAccess(context)
    }

    LaunchedEffect(Unit) {
        if (access == MediaAccess.NONE) {
            launcher.launch(fullAccessPermissions())
        }
    }

    if (showSettings) {
        SettingsScreen(
            access = access,
            onBack = { showSettings = false },
            onMediaPermissionClick = {
                access = mediaAccess(context)
                showDialog = true
            },
        )
    } else {
        when (access) {
            MediaAccess.NONE -> PermissionRequestScreen(
                onRetry = { launcher.launch(fullAccessPermissions()) },
            )
            MediaAccess.FULL, MediaAccess.PARTIAL -> Box(modifier = Modifier.fillMaxSize()) {
                SolaceApp(
                    access = access,
                    onOpenSettings = { showSettings = true },
                )
                // SolaceApp 保持常驻组合，设置页只叠加不被替换——返回时不重建、不重扫。
                if (showSettings) {
                    SettingsScreen(
                        access = access,
                        onBack = { showSettings = false },
                        onMediaPermissionClick = {
                            access = mediaAccess(context)
                            showDialog = true
                        },
                    )
                }
            }
        }
    }

    if (showDialog) {
        AccessOptionsDialog(
            access = access,
            onAllowAll = {
                showDialog = false
                launcher.launch(upgradePermissions())
            },
            onSelectMore = {
                showDialog = false
                launcher.launch(fullAccessPermissions())
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun AccessOptionsDialog(
    access: MediaAccess,
    onAllowAll: () -> Unit,
    onSelectMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (access) {
        MediaAccess.NONE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("媒体权限") },
            text = { Text("当前未获得媒体文件访问权限。") },
            confirmButton = {
                TextButton(onClick = onAllowAll) { Text("允许全部") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
        )
        MediaAccess.PARTIAL -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("媒体权限") },
            text = { Text("当前仅显示部分照片/视频。可以继续选择更多内容，或授权访问全部媒体文件。") },
            confirmButton = {
                TextButton(onClick = onAllowAll) { Text("允许全部") }
            },
            dismissButton = {
                TextButton(onClick = onSelectMore) { Text("选择更多") }
            },
        )
        MediaAccess.FULL -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("媒体权限") },
            text = { Text("已授权访问全部照片和视频。") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun PermissionRequestScreen(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "需要访问媒体文件的权限才能浏览照片和视频")
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "授予权限",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onRetry)
                    .padding(16.dp),
            )
        }
    }
}
