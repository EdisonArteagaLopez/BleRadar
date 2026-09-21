package com.example.bleradar.util

import com.example.bleradar.model.Proximity
import kotlin.math.max
import kotlin.math.min

/**
 * Funções utilitárias de sinal:
 * - suavização do RSSI (EMA)
 * - classificação de proximidade
 * - normalização para radar
 */
object SignalUtils {

    /**
     * Calcula uma média móvel exponencial (EMA) do RSSI.
     *
     * Objetivo:
     * - Reduzir a oscilação do RSSI bruto, deixando a UI mais estável.
     *
     * Parâmetros:
     * @param prev valor suavizado anterior (Double).
     * @param current RSSI atual (Int) vindo do scan.
     * @param alpha peso do valor atual: quanto maior, mais "rápido" responde (mas mais ruidoso).
     *
     * Retorno:
     * @return novo valor suavizado (Double).
     */
    fun ema(prev: Double, current: Int, alpha: Double = 0.35): Double {
        return alpha * current + (1.0 - alpha) * prev
    }

    /**
     * Converte RSSI suavizado em uma categoria de proximidade.
     *
     * Objetivo:
     * - Transformar números (dBm) em um "sinal" didático para IoT:
     *   VERY_NEAR / NEAR / MID / FAR.
     *
     * Parâmetros:
     * @param rssiSmoothed RSSI suavizado (Double).
     *
     * Retorno:
     * @return Proximity (enum).
     *
     * Observação:
     * - Os thresholds dependem do ambiente (sala, obstáculos, orientação).
     */
    fun proximityFromRssi(rssiSmoothed: Double): Proximity {
        return when {
            //rssiSmoothed >= -55 -> Proximity.VERY_NEAR
            //rssiSmoothed >= -65 -> Proximity.NEAR
            //rssiSmoothed >= -75 -> Proximity.MID
            rssiSmoothed >= -45 -> Proximity.VERY_NEAR
            rssiSmoothed >= -63 -> Proximity.NEAR
            rssiSmoothed >= -76 -> Proximity.MID
            else -> Proximity.FAR
        }
    }

    /**
     * Normaliza RSSI em um valor [0..1] para desenhar no radar.
     *
     * Objetivo:
     * - Converter RSSI em raio relativo:
     *   0 = mais perto (no centro)
     *   1 = mais longe (na borda)
     *
     * Parâmetros:
     * @param rssiSmoothed RSSI suavizado (Double).
     *
     * Retorno:
     * @return Float em [0..1] para multiplicar pelo raio máximo do radar.
     *
     * Observação:
     * - rssiMin/rssiMax são valores típicos de BLE indoor; podem ser ajustados.
     */
    fun radarRadiusNorm(rssiSmoothed: Double): Float {
        val rssiMin = -95.0
        val rssiMax = -45.0
        val clamped = min(max(rssiSmoothed, rssiMin), rssiMax)
        val t = (clamped - rssiMax) / (rssiMin - rssiMax)
        return t.toFloat()
    }

}