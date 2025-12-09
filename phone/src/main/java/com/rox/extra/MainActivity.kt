package com.rox.extra

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.extra.ui.theme.ExtraTheme
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

    var activityContext: Context?=null

    // Estado para mostrar los datos recibidos
    private var receivedMessage = mutableStateOf("Esperando datos del smartwatch...")
    private var isConnected = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityContext=this

        enableEdgeToEdge()
        setContent {
            ExtraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PhoneScreen(
                        receivedMessage = receivedMessage.value,
                        isConnected = isConnected.value,
                        onConnect = { getNodes(this) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    private fun getNodes(context: Context) {
        launch(Dispatchers.Default) {
            val nodeList = Wearable.getNodeClient(context).connectedNodes
            try {
                val nodes = Tasks.await(nodeList)
                if (nodes.isNotEmpty()) {
                    for (node in nodes) {
                        Log.d("NODO", node.toString())
                        Log.d("NODO", "El id del nodo es: ${node.id}")
                    }
                    isConnected.value = true
                    receivedMessage.value = "Conectado con ${nodes.size} dispositivo(s)"
                } else {
                    isConnected.value = false
                    receivedMessage.value = "No se encontraron dispositivos conectados"
                }
            } catch (exception: Exception) {
                Log.d("Error al obtener nodos", exception.toString())
                isConnected.value = false
                receivedMessage.value = "Error al conectar: ${exception.message}"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!).removeListener (this)
            Wearable.getMessageClient(activityContext!!).removeListener (this)
            Wearable.getCapabilityClient(activityContext!!).removeListener (this)
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

    override fun onMessageReceived(ME: MessageEvent) {
        Log.d("onMessageReceived", ME.toString())
        Log.d("onMessageReceived", "nodo ${ME.sourceNodeId}")
        Log.d("onMessageReceived", "Payload: ${ME.path}")
        val message= String(ME.data, StandardCharsets.UTF_8)
        Log.d("onMessageReceived", "Mensaje: ${message}")

        // Actualizar la UI con el mensaje recibido
        receivedMessage.value = "Último dato recibido:\n$message"
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {

    }
}

@Composable
fun PhoneScreen(
    receivedMessage: String,
    isConnected: Boolean,
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
        // Título
        Text(
            text = "Monitor de Ritmo Cardíaco",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Botón Conectar
        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (isConnected) "Reconectar" else "Conectar",
                fontSize = 18.sp
            )
        }

        // TextView para mostrar datos recibidos
        Text(
            text = receivedMessage,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = if (receivedMessage.contains("BPM:"))
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        // Estado de conexión
        Text(
            text = if (isConnected) "● Conectado" else "○ Desconectado",
            fontSize = 14.sp,
            color = if (isConnected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PhoneScreenPreview() {
    ExtraTheme {
        PhoneScreen(
            receivedMessage = "Último dato recibido:\nBPM:75",
            isConnected = true,
            onConnect = {}
        )
    }
}