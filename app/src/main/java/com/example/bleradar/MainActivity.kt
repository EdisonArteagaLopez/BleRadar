package com.example.bleradar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bleradar.databinding.ActivityMainBinding
import com.example.bleradar.model.DeviceInfo
import com.example.bleradar.ui.DeviceAdapter
import com.example.bleradar.util.SignalUtils

/**
 * Activity principal do app.
 *
 * Responsabilidades:
 * - Inicializar UI (ViewBinding, RecyclerView, botões)
 * - Garantir Bluetooth ligado e permissões concedidas
 * - Iniciar/parar BLE scan
 * - Atualizar lista e radar periodicamente
 * - Ajustar padding da lista via WindowInsets (tela 7" / nav bar)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = DeviceAdapter()

    private lateinit var btAdapter: BluetoothAdapter
    private var bleScanner: BluetoothLeScanner? = null

    // Mapa address -> DeviceInfo (permite atualizar sempre o mesmo dispositivo)
    private val devicesByAddr = linkedMapOf<String, DeviceInfo>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanning = false

    /**
     * Runnable que atualiza UI em intervalos fixos.
     *
     * Objetivo:
     * - Evitar chamar notifyDataSetChanged a cada callback de scan (que pode ser muito frequente).
     *
     * Retorno:
     * - Não retorna; chama refreshUi() e agenda nova execução.
     */
    private val uiTicker = object : Runnable {
        override fun run() {
            refreshUi()
            mainHandler.postDelayed(this, 600)
        }
    }

    /**
     * Launcher de permissões em runtime (API moderna).
     *
     * Objetivo:
     * - Solicitar permissões necessárias (Bluetooth Scan/Connect) quando faltarem.
     *
     * Retorno:
     * - Não retorna; callback atualiza status após o usuário responder.
     */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            updateStatus("Permissões avaliadas. Clique em 'Iniciar scan'.")
        }

    /**
     * onCreate: ponto de entrada da Activity.
     *
     * Objetivo:
     * - Configurar binding/layout
     * - Ajustar WindowInsets (corrige lista cortada em display 7")
     * - Inicializar RecyclerView e Bluetooth
     * - Configurar listeners dos botões
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**
         * WindowInsets:
         * Objetivo:
         * - Evitar que o último item da lista fique escondido atrás da barra inferior
         *   (nav bar / gesture bar), muito comum em telas pequenas e Android embarcado.
         *
         * Efeito:
         * - Ajusta paddingBottom do RecyclerView dinamicamente de acordo com systemBars.bottom.
         */
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.recyclerDevices.updatePadding(bottom = systemBars.bottom + 16)
            insets
        }

        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager.adapter

        binding.btnStart.setOnClickListener {
            if (!ensureBluetoothAndPermissions()) return@setOnClickListener
            startScan()
        }

        binding.btnStop.setOnClickListener { stopScan() }

        updateStatus("Pronto. Ligue o Bluetooth e clique em 'Iniciar scan'.")
    }

    /**
     * onDestroy: ciclo de vida.
     *
     * Objetivo:
     * - Garantir que o scan pare ao destruir a Activity (evita leaks e uso de recursos).
     */
    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }

    /**
     * Verifica se BLE existe, Bluetooth está ligado e permissões estão concedidas.
     *
     * Objetivo:
     * - Centralizar checagens antes de iniciar o scan.
     *
     * Retorno:
     * @return true se pode iniciar o scan; false se precisa corrigir algo (ligar BT / pedir permissões).
     */
    private fun ensureBluetoothAndPermissions(): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            updateStatus("Este dispositivo não suporta BLE.")
            return false
        }

        if (!btAdapter.isEnabled) {
            updateStatus("Bluetooth desligado. Ligue em Configurações do Android (Raspberry).")
            return false
        }

        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            updateStatus("Solicitando permissões: ${missing.joinToString()}")
            permissionLauncher.launch(missing.toTypedArray())
            return false
        }

        return true
    }

    /**
     * Retorna a lista de permissões necessárias conforme versão do Android.
     *
     * Objetivo:
     * - Android 12+ (inclui 16): BLUETOOTH_SCAN + BLUETOOTH_CONNECT
     * - Android 11 e abaixo: permissões de localização (compatibilidade)
     *
     * Retorno:
     * @return List<String> com nomes das permissões.
     */
    private fun requiredPermissions(): List<String> {
        return listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    /**
     * Inicia o scan BLE.
     *
     * Objetivo:
     * - Obter o BluetoothLeScanner
     * - Limpar estado e UI
     * - Configurar ScanSettings (LOW_LATENCY)
     * - startScan e iniciar ticker de atualização da UI
     */
    private fun startScan() {
        if (scanning) return

        bleScanner = btAdapter.bluetoothLeScanner
        if (bleScanner == null) {
            updateStatus("BluetoothLeScanner nulo. Verifique ROM/driver.")
            return
        }

        devicesByAddr.clear()
        adapter.submitList(emptyList())
        binding.radarView.setDevices(emptyList())

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanning = true
        updateStatus("Scan BLE iniciado...")
        bleScanner?.startScan(null, settings, scanCallback)

        mainHandler.post(uiTicker)
    }

    /**
     * Para o scan BLE.
     *
     * Objetivo:
     * - stopScan no scanner
     * - parar ticker de UI
     * - atualizar status
     */
    private fun stopScan() {
        if (!scanning) return
        scanning = false

        try {
            bleScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // Em algumas ROMs, stopScan pode lançar exceções esporádicas.
            // Aqui ignoramos para manter robustez em sala.
        }

        mainHandler.removeCallbacks(uiTicker)
        updateStatus("Scan parado.")
    }

    /**
     * Callback do scan BLE.
     *
     * Objetivo:
     * - Receber resultados de dispositivos BLE.
     * - Atualizar/registrar DeviceInfo por address.
     * - Aplicar suavização EMA e recalcular proximidade.
     *
     * Retorno:
     * - Sem retorno; atualiza o mapa devicesByAddr.
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val addr = device.address ?: return

            val rssi = result.rssi
            val name = device.name

            val existing = devicesByAddr[addr]
            if (existing == null) {
                val smoothed = rssi.toDouble()
                devicesByAddr[addr] = DeviceInfo(
                    address = addr,
                    name = name,
                    rssiRaw = rssi,
                    rssiSmoothed = smoothed,
                    proximity = SignalUtils.proximityFromRssi(smoothed)
                )
            } else {
                existing.rssiRaw = rssi
                existing.name = existing.name ?: name
                //existing.rssiSmoothed = SignalUtils.ema(existing.rssiSmoothed, rssi)
                existing.rssiSmoothed = rssi.toDouble()   // usa o RSSI cru
                existing.proximity = SignalUtils.proximityFromRssi(existing.rssiSmoothed)
            }
        }

        /**
         * Chamado quando o scan falha.
         *
         * Objetivo:
         * - Informar o erro ao usuário e parar o scan.
         *
         * Parâmetros:
         * @param errorCode código de erro do BLE scan.
         */
        override fun onScanFailed(errorCode: Int) {
            updateStatus("Scan falhou. Código: $errorCode")
            stopScan()
        }
    }

    /**
     * Atualiza lista e radar com o estado atual do mapa.
     *
     * Objetivo:
     * - Converter map -> list ordenada (mais perto primeiro)
     * - Atualizar RecyclerView e RadarView
     *
     * Retorno:
     * - Sem retorno; atualiza UI.
     */
    private fun refreshUi() {
        val list = devicesByAddr.values.sortedByDescending { it.rssiSmoothed }
        adapter.submitList(list)
        binding.radarView.setDevices(list)

        if (scanning) updateStatus("Scan ativo. Encontrados: ${list.size}")
    }

    /**
     * Atualiza o texto de status na UI.
     *
     * Objetivo:
     * - Centralizar mensagens de estado num único lugar.
     *
     * Parâmetros:
     * @param msg mensagem a ser exibida.
     */
    private fun updateStatus(msg: String) {
        binding.tvStatus.text = "Status: $msg"
    }
}