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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.extra.R
import com.example.extra.presentation.theme.ExtraTheme
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import java.util.jar.Manifest

class MainActivity : ComponentActivity(),
    SensorEventListener,
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener{

    var activityContext: Context?=null

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor?=null
    private var sensorType=Sensor.TYPE_HEART_RATE

    var nodeID: String = ""
    private var PAYLOAD: String = ""

    // Lectura temporal del sensor (no se muestra hasta presionar Enviar)
    private var currentSensorReading = 0

    // Valor mostrado en la UI (solo se actualiza al presionar Enviar)
    private val displayedHeartRate = mutableStateOf(0)

    // Estado del sensor
    private val sensorActive = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        activityContext = this

        // Inicializar sensor de ritmo cardíaco
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (sensor == null) {
            Log.e("MainActivity", "No se encontró sensor de ritmo cardíaco")
        }

        setContent {
            WearApp(
                heartRate = displayedHeartRate.value,
                sensorActive = sensorActive.value,
                onStartSensor = { startSensor() },
                onSendData = { sendData() }
            )
        }
    }

    private fun sendData() {
        // Actualizar la UI con la lectura actual del sensor
        displayedHeartRate.value = currentSensorReading
        Log.d("sendData", "Actualizando UI con BPM: ${currentSensorReading}")

        // Enviar datos al celular
        if (nodeID.isNotEmpty()) {
            sendMessage()
        } else {
            Log.w("sendData", "nodeID no está inicializado. No se puede enviar mensaje.")
        }
    }

    private fun sendMessage(){
        val mensaje = "BPM:${currentSensorReading}"
        Wearable.getMessageClient(activityContext!!)
            .sendMessage(nodeID, PAYLOAD, mensaje.toByteArray())
            .addOnSuccessListener {
                Log.d("sendMessage", "Mensaje enviado con éxito: $mensaje")
            }
            .addOnFailureListener { exception ->
                Log.d("sendMessage", "Error al enviar mensaje ${exception.message}")
            }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!).removeListener (this)
            Wearable.getMessageClient(activityContext!!).removeListener (this)
            Wearable.getCapabilityClient(activityContext!!).removeListener (this)
            sensorManager.unregisterListener(this)
        }catch (e: Exception){
            Log.d("onPause", e.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Wearable.getDataClient(activityContext!!).addListener (this)
            Wearable.getMessageClient(activityContext!!).addListener (this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener (this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        }catch (e: Exception){
            Log.d("onResume", e.toString())
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {

    }

    override fun onMessageReceived(p0: MessageEvent) {

    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {

    }

    private fun startSensor(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BODY_SENSORS), 1001)
            return
        }
        if (sensor!=null){
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            sensorActive.value = true
            Log.d("startSensor", "Sensor de ritmo cardíaco iniciado")
        } else {
            Log.e("startSensor", "Sensor no disponible")
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

    override fun onSensorChanged(SE: SensorEvent?) {
        if (SE?.sensor?.type==sensorType){
            currentSensorReading = SE.values[0].toInt()
            Log.d("onSensorChanged", "Lectura del sensor: ${currentSensorReading} bpm (no mostrado aún)")
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

            delay(1000L) // Actualizar cada segundo
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ritmo cardíaco
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

        // Hora y fecha
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

        // Botones
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            // Botón Iniciar Sensor
            Button(
                onClick = onStartSensor,
                enabled = !sensorActive,
                modifier = Modifier.size(width = 70.dp, height = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (sensorActive) MaterialTheme.colors.surface else MaterialTheme.colors.primary
                )
            ) {
                Text(
                    text = if (sensorActive) "ON" else "Sensor",
                    fontSize = 10.sp
                )
            }

            // Botón Enviar
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