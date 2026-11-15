
package com.example.stepuphealthconnecttester

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    private val permissions = setOf(
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class)
    )

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> permissionStateCallback?.invoke(granted) }

    private var permissionStateCallback: ((Set<String>) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StepLabScreen(
                    onRequestPermissions = { callback ->
                        permissionStateCallback = callback
                        permissionLauncher.launch(permissions)
                    },
                    onOpenHealthConnect = {
                        val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
                        if (intent != null) startActivity(intent)
                        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepLabScreen(
    onRequestPermissions: ((Set<String>) -> Unit) -> Unit,
    onOpenHealthConnect: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { HealthConnectClient.getOrCreate(context) }
    var availability by remember { mutableStateOf("Checking Health Connect…") }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var total by remember { mutableStateOf<Long?>(null) }
    var mockSteps by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Read-only inspector. No records are written by this app.") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        availability = when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> "Health Connect is available"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Install or update Health Connect"
            else -> "Health Connect is not available on this device"
        }
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            granted = client.permissionController.getGrantedPermissions()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Step Data Lab") }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Local synthetic-data sandbox", style = MaterialTheme.typography.titleLarge)
            Text("These values exist only inside this app. They are not written to Health Connect or StepUp.")
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mock step count", style = MaterialTheme.typography.labelLarge)
                    Text(mockSteps.toString(), style = MaterialTheme.typography.headlineLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { mockSteps += 100 }) { Text("+100") }
                        Button(onClick = { mockSteps += 500 }) { Text("+500") }
                        OutlinedButton(onClick = { mockSteps = 0 }) { Text("Reset") }
                    }
                }
            }
            Divider()
            Text("Health Connect inspector", style = MaterialTheme.typography.titleLarge)
            Text(availability)
            Text("Read-only inspection of real records in Health Connect. No Health Connect records are created or modified.")
            Button(onClick = onOpenHealthConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Open Health Connect")
            }
            Button(
                onClick = {
                    busy = true
                    onRequestPermissions { result ->
                        granted = result
                        busy = false
                        status = if (permissionsForSteps().all { it in result }) "Read permission granted" else "Some permissions were not granted"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && availability == "Health Connect is available"
            ) { Text(if (busy) "Waiting…" else "Request step permissions") }

            Text("Step read permission: ${if (permissionsForSteps().all { it in granted }) "granted" else "not granted"}")
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val now = Instant.now()
                            val start = now.minus(1, ChronoUnit.DAYS)
                            val response = client.readRecords(
                                ReadRecordsRequest(
                                    recordType = StepsRecord::class,
                                    timeRangeFilter = TimeRangeFilter.between(start, now)
                                )
                            )
                            total = response.records.sumOf { it.count }
                            status = "Read ${response.records.size} step records from the last 24 hours."
                        } catch (e: Exception) {
                            status = "Read failed: ${e.message ?: e::class.java.simpleName}"
                        }
                    }
                },
                enabled = permissionsForSteps().all { it in granted },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Read last 24 hours") }

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Last 24 hours", style = MaterialTheme.typography.labelLarge)
                    Text(total?.toString() ?: "—", style = MaterialTheme.typography.headlineLarge)
                    Text("Sum of records visible to this app")
                }
            }
            Text(status)
            Text(
                "Privacy: Health Connect records are read locally with your permission. The mock counter is app-local. Nothing is uploaded to a server.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun permissionsForSteps(): Set<String> = setOf(
    androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class)
)
