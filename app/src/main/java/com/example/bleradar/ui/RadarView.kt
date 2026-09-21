package com.example.bleradar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.bleradar.model.DeviceInfo
import com.example.bleradar.model.Proximity
import com.example.bleradar.util.SignalUtils
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * View customizada que desenha um "radar" usando Canvas.
 *
 * Relação gráfica:
 * - Cada dispositivo vira um ponto.
 * - O raio do ponto depende do RSSI suavizado:
 *   - RSSI mais forte (menos negativo) -> mais perto do centro
 *   - RSSI mais fraco (mais negativo)  -> mais perto da borda
 *
 * Importante:
 * - Isso NÃO é localização real (não calcula posição).
 * - O ângulo é atribuído de forma determinística a partir do address,
 *   apenas para distribuir os pontos e manter estabilidade visual.
 */
class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF9E9E9E.toInt()
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var devices: List<DeviceInfo> = emptyList()

    /**
     * Define a lista de dispositivos a serem desenhados.
     *
     * Objetivo:
     * - Receber dados atualizados do Activity e pedir para redesenhar a view.
     *
     * Parâmetros:
     * @param list lista de DeviceInfo.
     *
     * Retorno:
     * - Sem retorno; chama invalidate() para disparar novo onDraw().
     */
    fun setDevices(list: List<DeviceInfo>) {
        devices = list
        invalidate()
    }

    /**
     * Desenha o radar e os pontos.
     *
     * Objetivo:
     * - Desenhar: círculos concêntricos + cruz central + pontos dos devices.
     *
     * Parâmetros:
     * - Não recebe parâmetros; usa estado interno (devices) e dimenssões da View.
     *
     * Retorno:
     * - Sem retorno; desenha diretamente no Canvas.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.45f

        // Desenha grade do radar
        canvas.drawCircle(cx, cy, r, gridPaint)
        canvas.drawCircle(cx, cy, r * 0.66f, gridPaint)
        canvas.drawCircle(cx, cy, r * 0.33f, gridPaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, gridPaint)

        // Desenha cada dispositivo como um ponto
        for (d in devices) {
            val angle = stableAngle(d.address)
            val norm = SignalUtils.radarRadiusNorm(d.rssiSmoothed) // 0 perto, 1 longe
            val rr = r * norm

            // cos/sin retornam Double -> convertemos para Float para coordenadas
            val x = cx + rr * cos(angle).toFloat()
            val y = cy + rr * sin(angle).toFloat()

            // Cor do ponto baseada na proximidade ("quente/frio")
            pointPaint.color = when (d.proximity) {
                Proximity.VERY_NEAR -> 0xFF2E7D32.toInt()
                Proximity.NEAR      -> 0xFF1565C0.toInt()
                Proximity.MID       -> 0xFFF9A825.toInt()
                Proximity.FAR       -> 0xFFC62828.toInt()
            }

            canvas.drawCircle(x, y, 10f, pointPaint)
        }
    }

    /**
     * Gera um ângulo determinístico (0..2π) a partir do address.
     *
     * Objetivo:
     * - Manter cada dispositivo sempre "no mesmo ângulo" no radar.
     * - Evita que o ponto fique mudando de posição angular a cada atualização.
     *
     * Parâmetros:
     * @param address MAC/address do dispositivo (String).
     *
     * Retorno:
     * @return Double representando um ângulo em radianos.
     */
    private fun stableAngle(address: String): Double {
        val h = address.hashCode()
        val u = (h.toLong() and 0xFFFF).toDouble() / 65535.0
        return u * (2.0 * Math.PI)
    }
}