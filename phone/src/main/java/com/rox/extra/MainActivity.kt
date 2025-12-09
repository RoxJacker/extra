package com.rox.extra

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rox.extra.ui.theme.ExtraTheme
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    var activityContext: Context? = null

    private var receivedMessage = mutableStateOf("Esperando datos del smartwatch...")
    private var isConnected = mutableStateOf(false)
    private var heartRateValue = mutableStateOf("--")

    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("Permissions", "Todos los permisos de Bluetooth otorgados")
            getNodes(this)
        } else {
            Log.w("Permissions", "Algunos permisos fueron denegados")
            receivedMessage.value = "Se requieren permisos de Bluetooth"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityContext = this

        enableEdgeToEdge()
        setContent {
            ExtraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PhoneScreen(
                        receivedMessage = receivedMessage.value,
                        isConnected = isConnected.value,
                        heartRate = heartRateValue.value,
                        onConnect = { checkPermissionsAndConnect() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndConnect() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val bluetoothConnectGranted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val bluetoothScanGranted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (!bluetoothConnectGranted || !bluetoothScanGranted) {
                requestBluetoothPermissions.launch(
                    arrayOf(
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            } else {
                getNodes(this)
            }
        } else {
            getNodes(this)
        }
    }

    private fun getNodes(context: Context) {
        receivedMessage.value = "Buscando dispositivos..."
        launch(Dispatchers.Default) {
            try {
                delay(500)
                val nodeList = Wearable.getNodeClient(context).connectedNodes
                val nodes = Tasks.await(nodeList)

                withContext(Dispatchers.Main) {
                    if (nodes.isNotEmpty()) {
                        for (node in nodes) {
                            Log.d("NODO", "Nodo conectado: ${node.displayName}")
                            Log.d("NODO", "ID del nodo: ${node.id}")
                        }
                        isConnected.value = true
                        receivedMessage.value = "Conectado - Esperando datos..."
                    } else {
                        isConnected.value = false
                        receivedMessage.value = "No se encontraron dispositivos"
                        heartRateValue.value = "--"
                    }
                }
            } catch (exception: Exception) {
                Log.e("Error al obtener nodos", exception.toString())
                withContext(Dispatchers.Main) {
                    isConnected.value = false
                    receivedMessage.value = "Error al conectar"
                    heartRateValue.value = "--"
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!).removeListener(this)
            Wearable.getMessageClient(activityContext!!).removeListener(this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)
        } catch (e: Exception) {
            Log.e("onPause", e.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Wearable.getDataClient(activityContext!!).addListener(this)
            Wearable.getMessageClient(activityContext!!).addListener(this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        } catch (e: Exception) {
            Log.e("onResume", e.toString())
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {
        // No utilizado
    }

    override fun onMessageReceived(ME: MessageEvent) {
        Log.d("onMessageReceived", ME.toString())
        Log.d("onMessageReceived", "Nodo: ${ME.sourceNodeId}")
        Log.d("onMessageReceived", "Path: ${ME.path}")

        val message = String(ME.data, StandardCharsets.UTF_8)
        Log.d("onMessageReceived", "Mensaje: $message")

        launch(Dispatchers.Main) {
            receivedMessage.value = "Datos recibidos correctamente"
            // Extraer valor numérico de "BPM:75"
            if (message.startsWith("BPM:")) {
                heartRateValue.value = message.substringAfter("BPM:")
            }
        }
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        // No utilizado
    }
}

@Composable
fun PhoneScreen(
    receivedMessage: String,
    isConnected: Boolean,
    heartRate: String,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Monitor de Ritmo Cardíaco",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = if (isConnected) "Reconectar" else "Conectar",
                fontSize = 18.sp
            )
        }

        // TextView principal - muestra el valor BPM
        Text(
            text = heartRate,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = "BPM",
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 40.dp)
        )

        // Mensaje de estado
        Text(
            text = receivedMessage,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Indicador de conexión
        Text(
            text = if (isConnected) "● Conectado" else "○ Desconectado",
            fontSize = 14.sp,
            color = if (isConnected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PhoneScreenPreview() {
    ExtraTheme {
        PhoneScreen(
            receivedMessage = "Datos recibidos correctamente",
            isConnected = true,
            heartRate = "75",
            onConnect = {}
        )
    }
}