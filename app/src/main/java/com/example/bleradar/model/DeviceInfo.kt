package com.example.bleradar.model

/**
 * Estrutura de dados que representa um dispositivo BLE encontrado no scan.
 *
 * Campos:
 * - address: identificador estável do device (MAC/address).
 * - name: nome anunciado (pode ser null).
 * - rssiRaw: RSSI instantâneo (dBm).
 * - rssiSmoothed: RSSI suavizado (EMA) para reduzir ruído.
 * - proximity: classe categórica derivada do RSSI suavizado.
 */
data class DeviceInfo(
    val address: String,
    var name: String?,
    var rssiRaw: Int,
    var rssiSmoothed: Double,
    var proximity: Proximity
)

/**
 * Enum de proximidade (heurística).
 * Não é distância em metros, é uma classificação baseada em RSSI.
 */
enum class Proximity {
    VERY_NEAR, NEAR, MID, FAR
}