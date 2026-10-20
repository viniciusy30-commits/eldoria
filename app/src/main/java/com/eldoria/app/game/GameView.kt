package com.eldoria.app.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tela do jogo: desenha o mundo, o herói e os monstros,
 * cuida do movimento, da inteligência dos monstros e do combate.
 * Tudo é desenhado por código (sem imagens externas).
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        const val TAMANHO_TILE = 72f
        private const val INTERVALO_SPAWN = 4f
        private const val MAX_MONSTROS = 24
    }

    val mundo = Mundo()
    val jogador = Jogador(mundo.spawnX, mundo.spawnY)
    val monstros: MutableList<Monstro> = mutableListOf()
    private val textos: MutableList<TextoFlutuante> = mutableListOf()
    private val rand = Random(20261020)

    var aoAtualizarEstado: ((EstadoJogador) -> Unit)? = null
    var aoMorrer: (() -> Unit)? = null

    private var rodando = false
    private var ultimoNanos = 0L
    private var tempoJogo = 0f
    private var tempoHud = 0f
    private var tempoSpawn = 0f
    private var ataqueFlash = 0f

    private var camX = 0f
    private var camY = 0f

    private var joystickX = 0f
    private var joystickY = 0f
    private var joystickAtivo = false

    private var toqueX = 0f
    private var toqueY = 0f
    private var tempoToque = 0L

    // ---------- tintas ----------
    private val pGramaA = Paint().apply { color = Color.rgb(98, 168, 96) }
    private val pGramaB = Paint().apply { color = Color.rgb(88, 154, 90) }
    private val pGramaEscuro = Paint().apply { color = Color.rgb(72, 132, 76) }
    private val pAreia = Paint().apply { color = Color.rgb(224, 201, 138) }
    private val pAreiaEscuro = Paint().apply { color = Color.rgb(206, 182, 120) }
    private val pAgua = Paint().apply { color = Color.rgb(59, 111, 212) }
    private val pAguaClara = Paint().apply { color = Color.rgb(112, 162, 238) }
    private val pPedra = Paint().apply { color = Color.rgb(150, 150, 158) }
    private val pPedraClara = Paint().apply { color = Color.rgb(178, 178, 186) }
    private val pParede = Paint().apply { color = Color.rgb(86, 74, 72) }
    private val pParedeTopo = Paint().apply { color = Color.rgb(116, 100, 96) }
    private val pTronco = Paint().apply { color = Color.rgb(112, 78, 56) }
    private val pCopa = Paint().apply { color = Color.rgb(58, 122, 62) }
    private val pCopaEscuro = Paint().apply { color = Color.rgb(42, 96, 50) }
    private val pFlor1 = Paint().apply { color = Color.rgb(238, 96, 122) }
    private val pFlor2 = Paint().apply { color = Color.rgb(255, 205, 84) }
    private val pFlor3 = Paint().apply { color = Color.rgb(250, 250, 250) }

    private val pSombra = Paint().apply { color = Color.argb(70, 0, 0, 0) }

    private val pArmadura = Paint().apply { color = Color.rgb(63, 81, 181) }
    private val pArmaduraEscuro = Paint().apply { color = Color.rgb(40, 53, 147) }
    private val pCinto = Paint().apply { color = Color.rgb(255, 179, 0) }
    private val pPele = Paint().apply { color = Color.rgb(255, 205, 170) }
    private val pCabelo = Paint().apply { color = Color.rgb(70, 50, 35) }
    private val pPerna = Paint().apply { color = Color.rgb(44, 52, 90) }
    private val pOlho = Paint().apply { color = Color.rgb(30, 30, 40) }
    private val pLamina = Paint().apply { color = Color.rgb(226, 230, 240) }

    private val pSlime = Paint().apply { color = Color.rgb(120, 200, 90) }
    private val pSlimeClaro = Paint().apply { color = Color.rgb(168, 228, 138) }
    private val pLobo = Paint().apply { color = Color.rgb(122, 126, 138) }
    private val pLoboClaro = Paint().apply { color = Color.rgb(178, 182, 192) }
    private val pOrc = Paint().apply { color = Color.rgb(96, 150, 80) }
    private val pOrcEscuro = Paint().apply { color = Color.rgb(62, 102, 52) }
    private val pAranha = Paint().apply { color = Color.rgb(62, 54, 68) }
    private val pAranhaOlho = Paint().apply { color = Color.rgb(232, 76, 76) }

    private val pBarraFundo = Paint().apply { color = Color.argb(170, 25, 25, 30) }
    private val pBarraVida = Paint().apply { color = Color.rgb(92, 214, 96) }
    private val pBarraVidaBaixa = Paint().apply { color = Color.rgb(232, 82, 72) }

    private val pTexto = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    private val pTextoContorno = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        color = Color.rgb(24, 24, 34)
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    init {
        for (i in 0 until 18) nascerMonstro()
    }

    // ---------- ciclo de vida ----------

    fun continuar() {
        if (rodando) return
        rodando = true
        ultimoNanos = System.nanoTime()
        postInvalidateOnAnimation()
    }

    fun pausar() {
        rodando = false
    }

    // ---------- entrada ----------

    fun definirDirecaoJoystick(dx: Float, dy: Float) {
        joystickX = dx
        joystickY = dy
        joystickAtivo = abs(dx) > 0.15f || abs(dy) > 0.15f
    }

    fun atacar() {
        val alvo = monstros
            .filter { distancia(it.x, it.y, jogador.x, jogador.y) < 2.1f }
            .minByOrNull { distancia(it.x, it.y, jogador.x, jogador.y) }
        if (alvo == null) {
            adicionarTexto(jogador.x, jogador.y - 1.2f, "Sem alvo por perto", Color.WHITE)
            return
        }
        atacar(alvo)
    }

    fun usarPocao(): String? {
        if (jogador.pocoes <= 0) return "Você não tem mais poções"
        if (jogador.vida >= jogador.vidaMax) return "Sua vida já está cheia"
        jogador.pocoes--
        val cura = min(60, jogador.vidaMax - jogador.vida)
        jogador.vida += cura
        adicionarTexto(jogador.x, jogador.y - 1.2f, "+$cura vida", Color.rgb(120, 230, 120))
        aoAtualizarEstado?.invoke(jogador.estado())
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                toqueX = event.x
                toqueY = event.y
                tempoToque = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val duracao = System.currentTimeMillis() - tempoToque
                val deslocamento = hypot(event.x - toqueX, event.y - toqueY)
                if (duracao < 450 && deslocamento < 48f) {
                    val mundoX = (event.x + camX) / TAMANHO_TILE
                    val mundoY = (event.y + camY) / TAMANHO_TILE
                    val alvo = monstros.minByOrNull { distancia(it.x, it.y, mundoX, mundoY) }
                    if (alvo != null && distancia(alvo.x, alvo.y, mundoX, mundoY) < 1.3f) {
                        atacar(alvo)
                    }
                }
            }
        }
        return true
    }

    // ---------- atualização ----------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val agora = System.nanoTime()
        var dt = (agora - ultimoNanos) / 1_000_000_000f
        ultimoNanos = agora
        if (dt > 0.05f) dt = 0.05f
        if (dt < 0f) dt = 0f

        if (rodando) {
            tempoJogo += dt
            atualizar(dt)
        }

        desenharMundo(canvas)

        val ordenados = monstros.sortedBy { it.y }
        for (m in ordenados) {
            if (visivel(m.x, m.y)) desenharMonstro(canvas, m)
        }
        desenharJogador(canvas)
        desenharTextos(canvas)

        if (rodando) postInvalidateOnAnimation()
    }

    private fun atualizar(dt: Float) {
        atualizarJogador(dt)

        jogador.recargaAtaque = (jogador.recargaAtaque - dt).coerceAtLeast(0f)
        ataqueFlash = (ataqueFlash - dt).coerceAtLeast(0f)

        jogador.tempoRegeneracao += dt
        if (jogador.tempoRegeneracao >= 4f) {
            jogador.tempoRegeneracao = 0f
            if (jogador.vida in 1 until jogador.vidaMax) {
                jogador.vida = min(jogador.vidaMax, jogador.vida + 2)
            }
        }

        for (m in ArrayList(monstros)) {
            atualizarMonstro(m, dt)
        }

        tempoSpawn += dt
        if (tempoSpawn >= INTERVALO_SPAWN) {
            tempoSpawn = 0f
            if (monstros.size < MAX_MONSTROS) nascerMonstro()
        }

        val iterador = textos.iterator()
        while (iterador.hasNext()) {
            val t = iterador.next()
            t.tempo += dt
            if (t.tempo >= t.duracao) iterador.remove()
        }

        tempoHud += dt
        if (tempoHud >= 0.25f) {
            tempoHud = 0f
            aoAtualizarEstado?.invoke(jogador.estado())
        }

        // câmera acompanha o herói
        val alvoCamX = jogador.x * TAMANHO_TILE + TAMANHO_TILE / 2f - width / 2f
        val alvoCamY = jogador.y * TAMANHO_TILE + TAMANHO_TILE / 2f - height / 2f
        val fator = (dt * 8f).coerceAtMost(1f)
        camX += (alvoCamX - camX) * fator
        camY += (alvoCamY - camY) * fator
        camX = camX.coerceIn(0f, (mundo.largura * TAMANHO_TILE - width).coerceAtLeast(0f))
        camY = camY.coerceIn(0f, (mundo.altura * TAMANHO_TILE - height).coerceAtLeast(0f))
    }

    private fun atualizarJogador(dt: Float) {
        val j = jogador

        if (!j.andando && joystickAtivo) {
            val passoX = if (abs(joystickX) > 0.30f) (if (joystickX > 0f) 1 else -1) else 0
            val passoY = if (abs(joystickY) > 0.30f) (if (joystickY > 0f) 1 else -1) else 0
            if (passoX != 0 || passoY != 0) {
                val novoX = j.tileX + passoX
                val novoY = j.tileY + passoY
                if (!mundo.bloqueado(novoX, novoY) && !ocupadoPorMonstro(novoX, novoY)) {
                    j.destinoX = novoX
                    j.destinoY = novoY
                    j.andando = true
                    j.direcao = when {
                        passoX > 0 -> 3
                        passoX < 0 -> 2
                        passoY > 0 -> 1
                        else -> 0
                    }
                }
            }
        }

        val alvoX = j.destinoX.toFloat()
        val alvoY = j.destinoY.toFloat()
        val fator = (dt * 7f).coerceAtMost(1f)
        j.x += (alvoX - j.x) * fator
        j.y += (alvoY - j.y) * fator

        if (abs(alvoX - j.x) < 0.02f && abs(alvoY - j.y) < 0.02f) {
            j.x = alvoX
            j.y = alvoY
            j.tileX = j.destinoX
            j.tileY = j.destinoY
            j.andando = false
        } else {
            j.faseAndar += dt * 11f
        }
    }

    private fun atualizarMonstro(m: Monstro, dt: Float) {
        m.recargaAtaque = (m.recargaAtaque - dt).coerceAtLeast(0f)
        m.tempoIa -= dt

        val distJogador = distancia(m.x, m.y, jogador.x, jogador.y)

        if (m.tempoIa <= 0f) {
            m.tempoIa = 0.45f

            if (distJogador < m.tipo.raioPerseguicao && distJogador > 0.9f) {
                val dx = jogador.x - m.x
                val dy = jogador.y - m.y
                val passoX = if (abs(dx) > 0.1f) (if (dx > 0f) 1 else -1) else 0
                val passoY = if (abs(dy) > 0.1f) (if (dy > 0f) 1 else -1) else 0
                tentarMoverMonstro(m, m.destinoX + passoX, m.destinoY + passoY)
            } else if (distJogador > m.tipo.raioPerseguicao && rand.nextFloat() < 0.45f) {
                val passoX = rand.nextInt(-1, 2)
                val passoY = rand.nextInt(-1, 2)
                tentarMoverMonstro(m, m.destinoX + passoX, m.destinoY + passoY)
            }
        }

        val alvoX = m.destinoX.toFloat()
        val alvoY = m.destinoY.toFloat()
        val fator = (dt * m.tipo.velocidade).coerceAtMost(1f)
        m.x += (alvoX - m.x) * fator
        m.y += (alvoY - m.y) * fator

        if (abs(alvoX - m.x) < 0.02f && abs(alvoY - m.y) < 0.02f) {
            m.x = alvoX
            m.y = alvoY
            m.andando = false
        } else {
            m.andando = true
            m.faseAndar += dt * 9f
        }

        // ataque do monstro
        if (distJogador < 1.25f && m.recargaAtaque <= 0f && jogador.vida > 0) {
            m.recargaAtaque = 1.4f
            val dano = m.tipo.dano + rand.nextInt(0, 5)
            jogador.vida = (jogador.vida - dano).coerceAtLeast(0)
            adicionarTexto(jogador.x, jogador.y - 1.2f, "-$dano", Color.rgb(255, 110, 110))
            if (jogador.vida <= 0) morrer()
        }
    }

    private fun tentarMoverMonstro(m: Monstro, novoX: Int, novoY: Int) {
        if (novoX == m.destinoX && novoY == m.destinoY) return
        if (mundo.bloqueado(novoX, novoY)) return
        if (novoX == jogador.tileX && novoY == jogador.tileY) return
        for (outro in monstros) {
            if (outro !== m && outro.destinoX == novoX && outro.destinoY == novoY) return
        }
        m.destinoX = novoX
        m.destinoY = novoY
    }

    private fun ocupadoPorMonstro(x: Int, y: Int): Boolean {
        for (m in monstros) {
            if (m.destinoX == x && m.destinoY == y) return true
        }
        return false
    }

    // ---------- combate ----------

    private fun atacar(alvo: Monstro) {
        if (jogador.recargaAtaque > 0f) return
        if (distancia(alvo.x, alvo.y, jogador.x, jogador.y) > 1.9f) {
            adicionarTexto(jogador.x, jogador.y - 1.2f, "Longe demais!", Color.WHITE)
            return
        }

        jogador.recargaAtaque = 0.5f
        ataqueFlash = 0.22f

        val dano = jogador.danoBase() + rand.nextInt(2, 6)
        alvo.vida -= dano
        adicionarTexto(alvo.x, alvo.y - 0.9f, "-$dano", Color.WHITE)

        if (alvo.vida <= 0) {
            monstros.remove(alvo)
            jogador.abates++
            val experiencia = alvo.tipo.experiencia + rand.nextInt(0, 8)
            val ouro = rand.nextInt(alvo.tipo.ouroMin, alvo.tipo.ouroMax + 1)
            jogador.ouro += ouro
            adicionarTexto(alvo.x, alvo.y - 1.4f, "+$ouro ouro", Color.rgb(255, 200, 80))
            ganharExperiencia(experiencia)

            if (rand.nextFloat() < 0.25f) {
                jogador.pocoes++
                adicionarTexto(alvo.x, alvo.y - 1.9f, "+1 poção", Color.rgb(170, 120, 240))
            }
            aoAtualizarEstado?.invoke(jogador.estado())
        }
    }

    private fun ganharExperiencia(qtd: Int) {
        jogador.xp += qtd
        adicionarTexto(jogador.x, jogador.y - 1.7f, "+$qtd XP", Color.rgb(150, 230, 140))
        while (jogador.xp >= jogador.xpProximo()) {
            jogador.xp -= jogador.xpProximo()
            jogador.nivel++
            jogador.vidaMax += 18
            jogador.vida = jogador.vidaMax
            adicionarTexto(jogador.x, jogador.y - 2.2f, "Nível ${jogador.nivel}!", Color.rgb(255, 205, 80))
        }
    }

    private fun morrer() {
        jogador.mortes++
        jogador.ouro = (jogador.ouro * 0.9f).toInt()
        jogador.vida = jogador.vidaMax
        jogador.tileX = mundo.spawnX
        jogador.tileY = mundo.spawnY
        jogador.destinoX = mundo.spawnX
        jogador.destinoY = mundo.spawnY
        jogador.x = mundo.spawnX.toFloat()
        jogador.y = mundo.spawnY.toFloat()
        aoAtualizarEstado?.invoke(jogador.estado())
        aoMorrer?.invoke()
    }

    // ---------- nascimento de monstros ----------

    private fun nascerMonstro() {
        for (tentativa in 0 until 40) {
            val angulo = rand.nextDouble(0.0, 6.2831)
            val distanciaCentro = rand.nextDouble(6.0, 15.0)
            val x = (jogador.x + cos(angulo) * distanciaCentro).toInt()
            val y = (jogador.y + sin(angulo) * distanciaCentro).toInt()
            if (!mundo.bloqueado(x, y) && !ocupadoPorMonstro(x, y)) {
                monstros.add(Monstro(sortearTipo(), x, y))
                return
            }
        }
    }

    private fun sortearTipo(): TipoMonstro {
        val tipos: List<TipoMonstro> = when {
            jogador.nivel >= 8 -> TipoMonstro.values().toList()
            jogador.nivel >= 5 -> TipoMonstro.values().take(3)
            jogador.nivel >= 2 -> TipoMonstro.values().take(2)
            else -> TipoMonstro.values().take(1)
        }
        return tipos[rand.nextInt(tipos.size)]
    }

    // ---------- desenho ----------

    private fun visivel(x: Float, y: Float): Boolean {
        val sx = x * TAMANHO_TILE - camX
        val sy = y * TAMANHO_TILE - camY
        return sx > -TAMANHO_TILE * 2 && sy > -TAMANHO_TILE * 2 &&
                sx < width + TAMANHO_TILE * 2 && sy < height + TAMANHO_TILE * 2
    }

    private fun desenharMundo(canvas: Canvas) {
        val iniX = floor(camX / TAMANHO_TILE).toInt() - 1
        val fimX = ceil((camX + width) / TAMANHO_TILE).toInt() + 1
        val iniY = floor(camY / TAMANHO_TILE).toInt() - 1
        val fimY = ceil((camY + height) / TAMANHO_TILE).toInt() + 1

        for (y in iniY..fimY) {
            for (x in iniX..fimX) {
                val sx = x * TAMANHO_TILE - camX
                val sy = y * TAMANHO_TILE - camY
                desenharTile(canvas, x, y, sx, sy)
            }
        }
    }

    private fun desenharTile(canvas: Canvas, tileX: Int, tileY: Int, sx: Float, sy: Float) {
        val h = (tileX * 73856093) xor (tileY * 19349663)
        val sorte = h and Int.MAX_VALUE
        val tipo = mundo.tile(tileX, tileY)

        val base = when (tipo) {
            Tiles.AREIA -> if (sorte % 2 == 0) pAreia else pAreiaEscuro
            Tiles.AGUA -> pAgua
            Tiles.PEDRA -> pPedra
            Tiles.PAREDE -> pParede
            else -> if (sorte % 3 == 0) pGramaA else pGramaB
        }
        canvas.drawRect(sx, sy, sx + TAMANHO_TILE, sy + TAMANHO_TILE, base)

        when (tipo) {
            Tiles.AGUA -> {
                val desloc = sin(tempoJogo * 1.7f + tileX * 0.6f + tileY * 0.9f) * 5f
                canvas.drawRoundRect(
                    RectF(sx + 12f, sy + 18f + desloc, sx + TAMANHO_TILE - 12f, sy + 28f + desloc),
                    6f, 6f, pAguaClara
                )
                val desloc2 = sin(tempoJogo * 1.4f + tileX * 0.8f + tileY * 0.5f + 2f) * 5f
                canvas.drawRoundRect(
                    RectF(sx + 20f, sy + 46f + desloc2, sx + TAMANHO_TILE - 22f, sy + 54f + desloc2),
                    5f, 5f, pAguaClara
                )
            }
            Tiles.GRAMA, Tiles.FLOR -> {
                canvas.drawRect(sx + 12f + sorte % 20, sy + 16f + sorte % 24, sx + 18f + sorte % 20, sy + 22f + sorte % 24, pGramaEscuro)
                if (tipo == Tiles.FLOR) {
                    val cor = when (sorte % 3) {
                        0 -> pFlor1
                        1 -> pFlor2
                        else -> pFlor3
                    }
                    val fx = sx + 18f + (sorte % 36)
                    val fy = sy + 26f + (sorte % 30)
                    canvas.drawCircle(fx, fy, 7f, cor)
                    canvas.drawCircle(fx, fy, 3f, pFlor2)
                }
            }
            Tiles.AREIA -> {
                canvas.drawCircle(sx + 20f + sorte % 30, sy + 24f + sorte % 28, 3f, pAreiaEscuro)
            }
            Tiles.PEDRA -> {
                canvas.drawRoundRect(RectF(sx + 8f, sy + 10f, sx + TAMANHO_TILE - 8f, sy + 26f), 8f, 8f, pPedraClara)
            }
            Tiles.PAREDE -> {
                canvas.drawRect(sx, sy, sx + TAMANHO_TILE, sy + 18f, pParedeTopo)
                canvas.drawRect(sx + 10f, sy + 30f, sx + 32f, sy + 48f, pParedeTopo)
                canvas.drawRect(sx + 40f, sy + 52f, sx + 62f, sy + 70f, pParedeTopo)
            }
            Tiles.ARVORE -> {
                canvas.drawRect(sx + 30f, sy + 38f, sx + 42f, sy + 66f, pTronco)
                canvas.drawCircle(sx + 36f, sy + 26f, 26f, pCopaEscuro)
                canvas.drawCircle(sx + 26f, sy + 34f, 18f, pCopa)
                canvas.drawCircle(sx + 48f, sy + 32f, 16f, pCopa)
            }
        }
    }

    private fun desenharJogador(canvas: Canvas) {
        val j = jogador
        val cx = j.x * TAMANHO_TILE + TAMANHO_TILE / 2f - camX
        val cy = j.y * TAMANHO_TILE + TAMANHO_TILE / 2f - camY

        if (cx < -200f || cy < -200f || cx > width + 200f || cy > height + 200f) return

        canvas.drawOval(RectF(cx - 26f, cy + 22f, cx + 26f, cy + 38f), pSombra)

        val balanco = if (j.andando) sin(j.faseAndar) * 5f else 0f

        // pernas
        canvas.drawRoundRect(RectF(cx - 13f, cy + 6f, cx - 3f, cy + 30f + balanco), 6f, 6f, pPerna)
        canvas.drawRoundRect(RectF(cx + 3f, cy + 6f, cx + 13f, cy + 30f - balanco), 6f, 6f, pPerna)

        // corpo
        canvas.drawRoundRect(RectF(cx - 17f, cy - 14f, cx + 17f, cy + 14f), 13f, 13f, pArmadura)
        canvas.drawRect(cx - 17f, cy + 3f, cx + 17f, cy + 10f, pCinto)
        canvas.drawRoundRect(RectF(cx - 17f, cy - 14f, cx - 6f, cy + 14f), 10f, 10f, pArmaduraEscuro)

        // cabeça
        canvas.drawCircle(cx, cy - 26f, 15f, pPele)
        canvas.drawRoundRect(RectF(cx - 15f, cy - 42f, cx + 15f, cy - 30f), 8f, 8f, pCabelo)

        // olhos (mudam de lugar conforme a direção)
        val olhoX = when (j.direcao) {
            2 -> -6f
            3 -> 6f
            else -> 0f
        }
        val olhoY = when (j.direcao) {
            0 -> -30f
            else -> -26f
        }
        if (j.direcao != 0) {
            canvas.drawCircle(cx - 5f + olhoX, olhoY, 2.4f, pOlho)
            canvas.drawCircle(cx + 5f + olhoX, olhoY, 2.4f, pOlho)
        }

        // espada quando ataca
        if (ataqueFlash > 0f) {
            val comprimento = 34f + ataqueFlash * 40f
            val dirX = when (j.direcao) {
                2 -> -1f
                3 -> 1f
                else -> 0f
            }
            val dirY = when (j.direcao) {
                0 -> -1f
                1 -> 1f
                else -> 0.4f
            }
            canvas.drawLine(
                cx + dirX * 12f, cy + dirY * 6f,
                cx + dirX * comprimento, cy + dirY * comprimento * 0.7f,
                pLamina.apply { strokeWidth = 7f }
            )
        }
    }

    private fun desenharMonstro(canvas: Canvas, m: Monstro) {
        val cx = m.x * TAMANHO_TILE + TAMANHO_TILE / 2f - camX
        val cy = m.y * TAMANHO_TILE + TAMANHO_TILE / 2f - camY

        canvas.drawOval(RectF(cx - 24f, cy + 18f, cx + 24f, cy + 32f), pSombra)

        when (m.tipo) {
            TipoMonstro.SLIME -> {
                val balanco = if (m.andando) sin(m.faseAndar) * 3f else 0f
                canvas.drawRoundRect(RectF(cx - 20f, cy - 12f + balanco, cx + 20f, cy + 22f), 16f, 16f, pSlime)
                canvas.drawRoundRect(RectF(cx - 12f, cy - 8f + balanco, cx + 2f, cy + 2f), 5f, 5f, pSlimeClaro)
                canvas.drawCircle(cx - 7f, cy - 2f, 3.2f, pOlho)
                canvas.drawCircle(cx + 7f, cy - 2f, 3.2f, pOlho)
            }
            TipoMonstro.LOBO -> {
                val balanco = if (m.andando) sin(m.faseAndar) * 3f else 0f
                canvas.drawRoundRect(RectF(cx - 22f, cy - 6f, cx + 18f, cy + 16f), 12f, 12f, pLobo)
                canvas.drawRoundRect(RectF(cx + 6f, cy - 18f + balanco, cx + 28f, cy + 6f + balanco), 10f, 10f, pLobo)
                canvas.drawRoundRect(RectF(cx + 8f, cy - 26f + balanco, cx + 15f, cy - 14f + balanco), 4f, 4f, pLobo)
                canvas.drawRoundRect(RectF(cx + 19f, cy - 26f + balanco, cx + 26f, cy - 14f + balanco), 4f, 4f, pLobo)
                canvas.drawRect(cx - 12f, cy + 4f, cx + 10f, cy + 12f, pLoboClaro)
                canvas.drawCircle(cx + 20f, cy - 8f + balanco, 2.8f, pOlho)
                canvas.drawLine(cx - 22f, cy + 2f, cx - 34f, cy - 8f, pLobo.apply { strokeWidth = 6f })
            }
            TipoMonstro.ORC -> {
                val balanco = if (m.andando) sin(m.faseAndar) * 3f else 0f
                canvas.drawRoundRect(RectF(cx - 18f, cy - 12f, cx + 18f, cy + 16f), 12f, 12f, pOrc)
                canvas.drawCircle(cx, cy - 22f + balanco, 13f, pOrc)
                canvas.drawRect(cx - 18f, cy + 2f, cx + 18f, cy + 10f, pOrcEscuro)
                canvas.drawCircle(cx - 5f, cy - 24f + balanco, 2.6f, pOlho)
                canvas.drawCircle(cx + 5f, cy - 24f + balanco, 2.6f, pOlho)
                canvas.drawRoundRect(RectF(cx - 9f, cy - 16f + balanco, cx - 5f, cy - 11f + balanco), 2f, 2f, pLamina)
                canvas.drawRoundRect(RectF(cx + 5f, cy - 16f + balanco, cx + 9f, cy - 11f + balanco), 2f, 2f, pLamina)
            }
            TipoMonstro.ARANHA -> {
                val tremor = if (m.andando) sin(m.faseAndar) * 2f else 0f
                for (i in 0 until 4) {
                    val dy = -12f + i * 9f
                    canvas.drawLine(cx - 16f, cy + dy, cx - 36f, cy + dy - 10f + tremor, pAranha.apply { strokeWidth = 5f })
                    canvas.drawLine(cx + 16f, cy + dy, cx + 36f, cy + dy - 10f + tremor, pAranha.apply { strokeWidth = 5f })
                }
                canvas.drawCircle(cx, cy, 22f, pAranha)
                canvas.drawCircle(cx - 8f, cy - 6f, 4f, pAranhaOlho)
                canvas.drawCircle(cx + 8f, cy - 6f, 4f, pAranhaOlho)
                canvas.drawCircle(cx - 4f, cy - 13f, 2.6f, pAranhaOlho)
                canvas.drawCircle(cx + 4f, cy - 13f, 2.6f, pAranhaOlho)
            }
        }

        // barra de vida do monstro
        if (m.vida < m.vidaMax) {
            val largura = 52f
            val inicioX = cx - largura / 2f
            val topo = cy - 46f
            canvas.drawRoundRect(RectF(inicioX, topo, inicioX + largura, topo + 8f), 4f, 4f, pBarraFundo)
            val pct = (m.vida.toFloat() / m.vidaMax).coerceIn(0f, 1f)
            val corVida = if (pct < 0.35f) pBarraVidaBaixa else pBarraVida
            canvas.drawRoundRect(RectF(inicioX, topo, inicioX + largura * pct, topo + 8f), 4f, 4f, corVida)
        }
    }

    private fun desenharTextos(canvas: Canvas) {
        for (t in textos) {
            val cx = t.x * TAMANHO_TILE + TAMANHO_TILE / 2f - camX
            val progresso = t.tempo / t.duracao
            val cy = t.y * TAMANHO_TILE - camY - progresso * 70f
            val alfa = ((1f - progresso) * 255f).toInt().coerceIn(0, 255)
            pTexto.color = (t.cor and 0x00FFFFFF) or (alfa shl 24)
            pTextoContorno.alpha = alfa
            canvas.drawText(t.texto, cx, cy, pTextoContorno)
            canvas.drawText(t.texto, cx, cy, pTexto)
        }
    }

    // ---------- utilidades ----------

    private fun distancia(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x1 - x2, y1 - y2)

    private fun adicionarTexto(x: Float, y: Float, texto: String, cor: Int) {
        textos.add(TextoFlutuante(x, y, texto, cor))
        if (textos.size > 40) textos.removeAt(0)
    }
}
