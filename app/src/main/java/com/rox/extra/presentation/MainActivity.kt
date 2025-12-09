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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.extra.R
import com.example.extra.presentation.theme.ExtraTheme
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
    CapabilityClient.OnCapabilityChangedListener{

    var activityContext: Context?=null

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor?=null
    private var sensorType=Sensor.TYPE_GYROSCOPE

    var nodeID: String = ""
    private val PAYLOAD: String = "/sensor_data"

    // Estado para mostrar en la UI
    var sensorData by mutableStateOf("0.0")
        private set

    var statusMessage by mutableStateOf("Listo para conectar")
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        activityContext = this

        // Inicializar sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(sensorType)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(
                onStartSensor = { startSensor() },
                onSendData = { sendMessage() },
                sensorData = sensorData,
                statusMessage = statusMessage
            )
        }
    }

    private fun getNodes() {
        launch(Dispatchers.Default) {
            val nodeList = Wearable.getNodeClient(activityContext!!).connectedNodes
            try {
                val nodes = Tasks.await(nodeList)
                if (nodes.isNotEmpty()) {
                    nodeID = nodes[0].id
                    launch(Dispatchers.Main) {
                        statusMessage = "Conectado: ${nodes[0].displayName}"
                    }
                    Log.d("getNodes", "Nodo conectado: ${nodes[0].id}")
                } else {
                    launch(Dispatchers.Main) {
                        statusMessage = "No se encontró celular"
                    }
                    Log.d("getNodes", "No se encontraron nodos")
                }
            } catch (exception: Exception) {
                launch(Dispatchers.Main) {
                    statusMessage = "Error al conectar"
                }
                Log.d("getNodes", "Error: ${exception.message}")
            }
        }
    }

    private fun sendMessage(){
        if (nodeID.isEmpty()) {
            statusMessage = "Primero obtén el nodo con getNodes"
            Log.d("sendMessage", "NodeID vacío")
            return
        }

        val sendMessage= Wearable.getMessageClient(activityContext!!)
            .sendMessage(nodeID, PAYLOAD, sensorData.toByteArray())
            .addOnSuccessListener {
                statusMessage = "Datos enviados: $sensorData"
                Log.d("sendMessage", "Mensaje enviado con éxito: $sensorData")
            }
            .addOnFailureListener { exception ->
                statusMessage = "Error al enviar"
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
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

    override fun onSensorChanged(SE: SensorEvent?) {
        if (SE?.sensor?.type==sensorType){
            val lectura=SE.values[0]
            sensorData = String.format("%.2f", lectura)
            Log.d("onSensorChanged", "lectura: ${lectura}")
        }
    }
}

@Composable
fun WearApp(
    onStartSensor: () -> Unit,
    onSendData: () -> Unit,
    sensorData: String,
    statusMessage: String
) {
    ExtraTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Botón Iniciar Sensor
                Button(
                    onClick = onStartSensor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Iniciar sensor")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mostrar datos del sensor
                Text(
                    text = "DATO A ENVIAR",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.secondary
                )

                Text(
                    text = sensorData,
                    style = MaterialTheme.typography.title1,
                    color = MaterialTheme.colors.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Botón Enviar
                Button(
                    onClick = onSendData,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ENVIAR")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mensaje de estado
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.secondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(
        onStartSensor = {},
        onSendData = {},
        sensorData = "0.0",
        statusMessage = "Listo para conectar"
    )
}