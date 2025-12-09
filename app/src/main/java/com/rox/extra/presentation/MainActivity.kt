package com.rox.extra.presentation

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.rox.extra.presentation.theme.ExtraTheme
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(),
    CoroutineScope by MainScope(),
    SensorEventListener,
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    var activityContext: Context? = null

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null
    private var sensorType = Sensor.TYPE_HEART_RATE

    var nodeID: String = ""
    private var PAYLOAD: String = "/heart_rate_data"

    private var currentSensorReading = 0
    private val displayedHeartRate = mutableStateOf(0)
    private val sensorActive = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val bodySensorsGranted = permissions[android.Manifest.permission.BODY_SENSORS] ?: false
        val bluetoothGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions[android.Manifest.permission.BLUETOOTH_CONNECT] ?: false
        } else {
            true
        }

        if (bodySensorsGranted && bluetoothGranted) {
            Log.d("PermissionLauncher", "Todos los permisos otorgados")
            activateSensor()
        } else {
            Log.w("PermissionLauncher", "Algunos permisos fueron denegados")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        activityContext = this

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (sensor == null) {
            Log.e("MainActivity", "No se encontró sensor de ritmo cardíaco")
        } else {
            Log.d("MainActivity", "Sensor de ritmo cardíaco encontrado: ${sensor?.name}")
        }

        getConnectedNodes()

        setContent {
            WearApp(
                heartRate = displayedHeartRate.value,
                sensorActive = sensorActive.value,
                onStartSensor = { startSensor() },
                onSendData = { sendData() }
            )
        }
    }

    private fun getConnectedNodes() {
        launch(Dispatchers.Default) {
            try {
                delay(500)
                val nodeList = Wearable.getNodeClient(activityContext!!).connectedNodes
                val nodes = Tasks.await(nodeList)

                if (nodes.isNotEmpty()) {
                    nodeID = nodes[0].id
                    Log.d("getConnectedNodes", "NodeID obtenido: $nodeID")
                    for (node in nodes) {
                        Log.d("getConnectedNodes", "Nodo: ${node.displayName} - ID: ${node.id}")
                    }
                } else {
                    Log.w("getConnectedNodes", "No hay nodos conectados, reintentando...")
                    delay(2000)
                    getConnectedNodes()
                }
            } catch (e: Exception) {
                Log.e("getConnectedNodes", "Error: ${e.message}", e)
            }
        }
    }

    private fun sendData() {
        if (currentSensorReading == 0) {
            Log.w("sendData", "No hay lectura del sensor disponible")
            return
        }

        Log.d("sendData", "Enviando BPM: $currentSensorReading")

        if (nodeID.isNotEmpty()) {
            sendMessage()
        } else {
            Log.w("sendData", "nodeID vacío, obteniendo nodos...")
            getConnectedNodes()
        }
    }

    private fun sendMessage() {
        val mensaje = "BPM:${currentSensorReading}"
        Wearable.getMessageClient(activityContext!!)
            .sendMessage(nodeID, PAYLOAD, mensaje.toByteArray())
            .addOnSuccessListener {
                Log.d("sendMessage", "Mensaje enviado: $mensaje")
            }
            .addOnFailureListener { exception ->
                Log.e("sendMessage", "Error: ${exception.message}", exception)
            }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!).removeListener(this)
            Wearable.getMessageClient(activityContext!!).removeListener(this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)
            if (sensorActive.value) {
                sensorManager.unregisterListener(this)
            }
        } catch (e: Exception) {
            Log.e("onPause", "Error: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Wearable.getDataClient(activityContext!!).addListener(this)
            Wearable.getMessageClient(activityContext!!).addListener(this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)

            if (sensorActive.value && sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (e: Exception) {
            Log.e("onResume", "Error: ${e.message}", e)
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {}

    override fun onMessageReceived(p0: MessageEvent) {}

    override fun onCapabilityChanged(p0: CapabilityInfo) {}

    private fun startSensor() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(android.Manifest.permission.BODY_SENSORS)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("startSensor", "Solicitando permisos: $permissionsToRequest")
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            return
        }

        activateSensor()
    }

    private fun activateSensor() {
        if (sensor != null) {
            val registered = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            if (registered) {
                sensorActive.value = true
                Log.d("activateSensor", "Sensor iniciado exitosamente")
            } else {
                Log.e("activateSensor", "No se pudo registrar el listener")
            }
        } else {
            Log.e("activateSensor", "Sensor no disponible")
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        Log.d("onAccuracyChanged", "Precisión: $p1")
    }

    override fun onSensorChanged(SE: SensorEvent?) {
        if (SE?.sensor?.type == sensorType) {
            currentSensorReading = SE.values[0].toInt()
            // Actualizar UI en tiempo real
            displayedHeartRate.value = currentSensorReading
            Log.d("onSensorChanged", "Lectura: $currentSensorReading bpm")
        }
    }
}

@Composable
fun WearApp(
    heartRate: Int,
    sensorActive: Boolean,
    onStartSensor: () -> Unit,
    onSendData: () -> Unit
) {
    ExtraTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            ClockView(
                heartRate = heartRate,
                sensorActive = sensorActive,
                onStartSensor = onStartSensor,
                onSendData = onSendData
            )
        }
    }
}

@Composable
fun ClockView(
    heartRate: Int,
    sensorActive: Boolean,
    onStartSensor: () -> Unit,
    onSendData: () -> Unit
) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
            val now = Date()

            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now)

            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (heartRate > 0) "$heartRate" else "--",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "BPM",
            fontSize = 14.sp,
            color = MaterialTheme.colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = currentTime,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colors.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = currentDate,
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Button(
                onClick = onStartSensor,
                enabled = !sensorActive,
                modifier = Modifier.size(width = 70.dp, height = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (sensorActive)
                        MaterialTheme.colors.surface
                    else
                        MaterialTheme.colors.primary
                )
            ) {
                Text(
                    text = if (sensorActive) "ON" else "Sensor",
                    fontSize = 10.sp
                )
            }

            Button(
                onClick = onSendData,
                enabled = sensorActive,
                modifier = Modifier.size(width = 70.dp, height = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text(
                    text = "Enviar",
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    ExtraTheme {
        ClockView(
            heartRate = 75,
            sensorActive = true,
            onStartSensor = {},
            onSendData = {}
        )
    }
}