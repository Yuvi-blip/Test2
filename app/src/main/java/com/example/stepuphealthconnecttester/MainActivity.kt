
package com.example.stepuphealthconnecttester

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {

    private val permissions = setOf(
        androidx.health.connect.client.permission.HealthPermission
            .getReadPermission(StepsRecord::class),

        androidx.health.connect.client.permission.HealthPermission
            .getWritePermission(StepsRecord::class)
    )

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        permissionStateCallback?.invoke(granted)
    }

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
                        val intent = packageManager.getLaunchIntentForPackage(
                            "com.google.android.apps.healthdata"
                        )

                        if (intent != null) {
                            startActivity(intent)
                        } else {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
                                    )
                                )
                            )
                        }
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

    val client = remember {
        HealthConnectClient.getOrCreate(context)
    }

    var availability by remember {
        mutableStateOf("Checking Health Connect…")
    }

    var granted by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    var total by remember {
        mutableStateOf<Long?>(null)
    }

    var stepInput by remember {
        mutableStateOf("1000")
    }

    var status by remember {
        mutableStateOf(
            "Test mode: records written by this app are labelled as manually entered test data."
        )
    }

    var busy by remember {
        mutableStateOf(false)
    }

    val readPermission =
        androidx.health.connect.client.permission.HealthPermission
            .getReadPermission(StepsRecord::class)

    val writePermission =
        androidx.health.connect.client.permission.HealthPermission
            .getWritePermission(StepsRecord::class)

    val hasRead = readPermission in granted
    val hasWrite = writePermission in granted

    LaunchedEffect(Unit) {
        availability = when (
            HealthConnectClient.getSdkStatus(context)
        ) {
            HealthConnectClient.SDK_AVAILABLE ->
                "Health Connect is available"

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                "Install or update Health Connect"

            else ->
                "Health Connect is not available on this device"
        }

        if (
            HealthConnectClient.getSdkStatus(context) ==
            HealthConnectClient.SDK_AVAILABLE
        ) {
            granted = client.permissionController.getGrantedPermissions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Step Data Lab — Test Mode")
                }
            )
        }
    ) { padding ->

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),

            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Text(
                "Health Connect test records",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "This app writes explicitly manual/test StepsRecord entries to Health Connect. Other apps may exclude them."
            )

            Text(availability)

            Button(
                onClick = onOpenHealthConnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Health Connect")
            }

            Button(
                onClick = {
                    busy = true

                    onRequestPermissions { result ->
                        granted = result
                        busy = false

                        status =
                            "Permissions updated. Read: ${readPermission in result}; Write: ${writePermission in result}."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy &&
                    availability == "Health Connect is available"
            ) {
                Text(
                    if (busy) "Waiting…"
                    else "Request read + write permissions"
                )
            }

            Text(
                "Read permission: ${if (hasRead) "granted" else "not granted"}"
            )

            Text(
                "Write permission: ${if (hasWrite) "granted" else "not granted"}"
            )

            OutlinedTextField(
                value = stepInput,
                onValueChange = {
                    stepInput = it.filter(Char::isDigit).take(7)
                },
                label = {
                    Text("Synthetic test steps")
                },
                supportingText = {
                    Text("Enter 1–100,000. Record uses the current time window.")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val count = stepInput.toLongOrNull()

                    if (count == null || count !in 1L..100_000L) {
                        status = "Enter a value between 1 and 100,000."
                    } else {

                        scope.launch {
                            busy = true

                            try {
                                val end = Instant.now()
                                val start = end.minus(
                                    30,
                                    ChronoUnit.MINUTES
                                )

                                val zone = ZoneId.systemDefault()
                                    .rules.getOffset(end)

                                val record = StepsRecord(
                                    startTime = start,
                                    startZoneOffset = zone,
                                    endTime = end,
                                    endZoneOffset = zone,
                                    count = count,

                                    metadata = Metadata.manualEntry(
    clientRecordId = "step-data-lab-test-${System.currentTimeMillis()}",
    clientRecordVersion = 0
)
                                )

                                val result = client.insertRecords(
                                    listOf(record)
                                )

                                status =
                                    "Inserted ${count} clearly labelled manual test steps. Record IDs: ${result.recordIdsList.size}. Check Health Connect to verify."

                            } catch (e: Exception) {

                                status =
                                    "Write failed: ${e.message ?: e::class.java.simpleName}"

                            } finally {
                                busy = false
                            }
                        }
                    }
                },

                enabled = hasWrite &&
                    !busy &&
                    availability == "Health Connect is available",

                modifier = Modifier.fillMaxWidth()

            ) {
                Text(
                    if (busy) "Writing…"
                    else "Write manual test record"
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        busy = true

                        try {
                            val now = Instant.now()
                            val start = now.minus(
                                1,
                                ChronoUnit.DAYS
                            )

                            val response = client.readRecords(
                                ReadRecordsRequest(
                                    recordType = StepsRecord::class,
                                    timeRangeFilter =
                                        TimeRangeFilter.between(start, now)
                                )
                            )

                            total = response.records.sumOf {
                                it.count
                            }

                            status =
                                "Read ${response.records.size} records in the last 24 hours. Total visible to this app: ${total} steps."

                        } catch (e: Exception) {

                            status =
                                "Read failed: ${e.message ?: e::class.java.simpleName}"

                        } finally {
                            busy = false
                        }
                    }
                },

                enabled = hasRead && !busy,
                modifier = Modifier.fillMaxWidth()

            ) {
                Text("Read last 24 hours")
            }

            Card {
                Column(
                    Modifier.padding(16.dp)
                ) {
                    Text(
                        "Last 24 hours",
                        style = MaterialTheme.typography.labelLarge
                    )

                    Text(
                        total?.toString() ?: "—",
                        style = MaterialTheme.typography.headlineLarge
                    )

                    Text(
                        "Sum of step records visible to this app"
                    )
                }
            }

            Text(status)

            Text(
                "Important: each write creates a real Health Connect record marked as manual entry. StepUp says manually added steps are excluded. Remove test records through Health Connect after testing.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
