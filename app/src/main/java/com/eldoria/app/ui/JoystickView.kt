package com.eldoria.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** Joystick virtual desenhado por código. */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface Listener {
        fun emMovimento(dx: Float, dy: Float)
        fun emRepouso()
    }

    var listener: Listener? = null

    private var centroX = 0f
    private var centroY = 0f
    private var raioBase = 0f
    private var raioBotao = 0f
    private var botaoX = 0f
    private var botaoY = 0f

    private val pBase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val pAnel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val pBotao = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centroX = w / 2f
        centroY = h / 2f
        raioBase = min(w, h) / 2f - 6f
        raioBotao = raioBase * 0.42f
        botaoX = centroX
        botaoY = centroY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(centroX, centroY, raioBase, pBase)
        canvas.drawCircle(centroX, centroY, raioBase, pAnel)
        canvas.drawCircle(botaoX, botaoY, raioBotao, pBotao)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val limite = raioBase - raioBotao
                if (limite <= 0f) return true
                var dx = event.x - centroX
                var dy = event.y - centroY
                val dist = hypot(dx, dy)
                if (dist > limite) {
                    dx = dx / dist * limite
                    dy = dy / dist * limite
                }
                botaoX = centroX + dx
                botaoY = centroY + dy
                listener?.emMovimento(dx / limite, dy / limite)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                botaoX = centroX
                botaoY = centroY
                listener?.emRepouso()
                invalidate()
            }
        }
        return true
    }
}
