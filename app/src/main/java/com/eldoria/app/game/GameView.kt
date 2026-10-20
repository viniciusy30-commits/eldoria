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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tela do jogo: desenha o mundo em pixel art, anima o herói e os monstros,
 * cuida do combate corpo a corpo e dos projéteis (flechas e bolas de fogo).
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
        private const val ESCALA = 3f
    }

    val mundo = Mundo()
    val jogador = Jogador(mundo.spawnX, mundo.spawnY)
    val monstros: MutableList<Monstro> = mutableListOf()
    private val projeteis: MutableList<Projetil> = mutableListOf()
    private val textos: MutableList<TextoFlutuante> = mutableListOf()
    private val rand = Random(20261020)

    var aoAtualizarEstado: ((EstadoJogador) -> Unit)? = null
    var aoMorrer: (() -> Unit)? = null

    private var rodando = false
    private var ultimoNanos = 0L
    private var tempoJogo = 0f
    private var tempoHud = 0f
    private var tempoSpawn = 0f

    private var camX = 0f
    private var camY = 0f

    private var joystickX = 0f
    private var joystickY = 0f
    private var joystickAtivo = false

    private var toqueX = 0f
    private var toqueY = 0f
    private var tempoToque = 0L

    private val pSombra = Paint().apply { color = Color.argb(60, 0, 0, 0) }
    private val pBarraFundo = Paint().apply { color = Color.argb(170, 25, 25, 30) }
    private val pBarraVida = Paint().apply { color = Color.rgb(92, 214, 96) }
    private val pBarraBaixa = Paint().apply { color = Color.rgb(232, 82, 72) }
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
            .filter { distancia(it.x, it.y, jogador.x, jogador.y) < jogador.classe.alcance + 0.6f }
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
        desenharEntidades(canvas)
        desenharProjeteis(canvas)
        desenharTextos(canvas)

        if (rodando) postInvalidateOnAnimation()
    }

    private fun atualizar(dt: Float) {
        atualizarJogador(dt)

        jogador.recargaAtaque = (jogador.recargaAtaque - dt).coerceAtLeast(0f)
        if (jogador.tempoGolpe > 0f) {
            jogador.tempoGolpe = (jogador.tempoGolpe - dt).coerceAtLeast(0f)
            if (jogador.tempoGolpe == 0f) jogador.poseGolpe = ""
        }

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

        atualizarProjeteis(dt)

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
                        passoY > 0 -> 0
                        else -> 1
                    }
                }
            }
        }

        val alvoX = j.destinoX.toFloat()
        val alvoY = j.destinoY.toFloat()
        val fator = (dt * j.classe.velocidade).coerceAtMost(1f)
        j.x += (alvoX - j.x) * fator
        j.y += (alvoY - j.y) * fator

        if (abs(alvoX - j.x) < 0.02f && abs(alvoY - j.y) < 0.02f) {
            j.x = alvoX
            j.y = alvoY
            j.tileX = j.destinoX
            j.tileY = j.destinoY
            j.andando = false
        } else {
            j.faseAndar += dt * 9f
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
                tentarMoverMonstro(m, m.destinoX + rand.nextInt(-1, 2), m.destinoY + rand.nextInt(-1, 2))
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
            m.faseAndar += dt * 8f
        }

        if (distJogador < 1.25f && m.recargaAtaque <= 0f && jogador.vida > 0) {
            m.recargaAtaque = 1.4f
            val dano = m.tipo.dano + rand.nextInt(0, 5)
            jogador.vida = (jogador.vida - dano).coerceAtLeast(0)
            adicionarTexto(jogador.x, jogador.y - 1.2f, "-$dano", Color.rgb(255, 110, 110))
            if (jogador.vida <= 0) morrer()
        }
    }

    private fun atualizarProjeteis(dt: Float) {
        val iterador = projeteis.iterator()
        while (iterador.hasNext()) {
            val p = iterador.next()
            p.x += p.dx * p.velocidade * dt
            p.y += p.dy * p.velocidade * dt
            p.distancia += p.velocidade * dt

            var acertou = false
            for (m in ArrayList(monstros)) {
                if (distancia(p.x, p.y, m.x, m.y) < 0.6f) {
                    aplicarDano(m, p.dano)
                    acertou = true
                    break
                }
            }
            if (acertou || p.distancia > p.alcance || mundo.bloqueado(p.x.toInt(), p.y.toInt())) {
                iterador.remove()
            }
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
        val dist = distancia(alvo.x, alvo.y, jogador.x, jogador.y)
        if (dist > jogador.classe.alcance) {
            adicionarTexto(jogador.x, jogador.y - 1.2f, "Longe demais!", Color.WHITE)
            return
        }

        virarPara(alvo.x, alvo.y)
        jogador.recargaAtaque = jogador.classe.recarga
        jogador.tempoGolpe = 0.42f
        jogador.poseGolpe = "golpe1"

        val dano = jogador.danoBase() + rand.nextInt(2, 6)
        when (jogador.classe) {
            Classe.GUERREIRO -> aplicarDano(alvo, dano)
            Classe.MAGO -> criarProjetil(alvo, dano, 1, 9f, 7.5f)
            Classe.ARQUEIRO -> criarProjetil(alvo, dano, 0, 16f, 9f)
        }
    }

    private fun criarProjetil(alvo: Monstro, dano: Int, tipo: Int, velocidade: Float, alcance: Float) {
        val dx = alvo.x - jogador.x
        val dy = alvo.y - jogador.y
        val dist = max(0.01f, hypot(dx, dy))
        projeteis.add(
            Projetil(
                x = jogador.x,
                y = jogador.y - 0.25f,
                dx = dx / dist,
                dy = dy / dist,
                dano = dano,
                tipo = tipo,
                alcance = alcance,
                velocidade = velocidade
            )
        )
    }

    private fun virarPara(x: Float, y: Float) {
        val dx = x - jogador.x
        val dy = y - jogador.y
        jogador.direcao = if (abs(dx) > abs(dy)) {
            if (dx > 0f) 3 else 2
        } else {
            if (dy > 0f) 0 else 1
        }
    }

    private fun aplicarDano(alvo: Monstro, dano: Int) {
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
        projeteis.clear()
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

    // ---------- desenho em pixel art ----------

    private fun visivel(x: Float, y: Float): Boolean {
        val sx = x * TAMANHO_TILE - camX
        val sy = y * TAMANHO_TILE - camY
        return sx > -TAMANHO_TILE * 2 && sy > -TAMANHO_TILE * 2 &&
                sx < width + TAMANHO_TILE * 2 && sy < height + TAMANHO_TILE * 2
    }

    private fun tile(tipo: Int, variante: Int): android.graphics.Bitmap = when (tipo) {
        Tiles.AREIA -> PixelArt.de("t_areia", Sprites.TILE_AREIA)
        Tiles.AGUA -> when ((tempoJogo * 2f).toInt() % 3) {
            0 -> PixelArt.de("t_agua1", Sprites.TILE_AGUA1)
            1 -> PixelArt.de("t_agua2", Sprites.TILE_AGUA2)
            else -> PixelArt.de("t_agua3", Sprites.TILE_AGUA3)
        }
        Tiles.PEDRA -> PixelArt.de("t_pedra", Sprites.TILE_PEDRA)
        Tiles.PAREDE -> PixelArt.de("t_parede", Sprites.TILE_PAREDE)
        Tiles.FLOR -> PixelArt.de("t_flor", Sprites.TILE_FLOR)
        Tiles.ARVORE -> if (variante % 2 == 0) PixelArt.de("t_grama1", Sprites.TILE_GRAMA1)
        else PixelArt.de("t_grama2", Sprites.TILE_GRAMA2)
        else -> if (variante % 2 == 0) PixelArt.de("t_grama1", Sprites.TILE_GRAMA1)
        else PixelArt.de("t_grama2", Sprites.TILE_GRAMA2)
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
                val h = (x * 73856093) xor (y * 19349663)
                val bmp = tile(mundo.tile(x, y), h and Int.MAX_VALUE)
                canvas.drawBitmap(
                    bmp, null,
                    RectF(sx, sy, sx + TAMANHO_TILE, sy + TAMANHO_TILE),
                    PixelArt.pinturaSemFiltro
                )
            }
        }
    }

    private fun desenharEntidades(canvas: Canvas) {
        val objetos = mutableListOf<Pair<Float, (Canvas) -> Unit>>()

        // árvores entram na ordenação por profundidade
        val iniX = floor(camX / TAMANHO_TILE).toInt() - 1
        val fimX = ceil((camX + width) / TAMANHO_TILE).toInt() + 1
        val iniY = floor(camY / TAMANHO_TILE).toInt() - 1
        val fimY = ceil((camY + height) / TAMANHO_TILE).toInt() + 1
        val arvore = PixelArt.de("t_arvore", Sprites.TILE_ARVORE)
        for (y in iniY..fimY) {
            for (x in iniX..fimX) {
                if (mundo.tile(x, y) == Tiles.ARVORE) {
                    val sx = x * TAMANHO_TILE - camX
                    val sy = y * TAMANHO_TILE - camY
                    objetos.add((y + 0.9f) to { c: Canvas ->
                        c.drawBitmap(
                            arvore, null,
                            RectF(sx, sy - TAMANHO_TILE / 3f - 8f, sx + TAMANHO_TILE, sy + TAMANHO_TILE - TAMANHO_TILE / 3f + 8f),
                            PixelArt.pinturaSemFiltro
                        )
                    })
                }
            }
        }

        for (m in monstros) {
            if (visivel(m.x, m.y)) {
                objetos.add(m.y to { c: Canvas -> desenharMonstro(c, m) })
            }
        }
        objetos.add(jogador.y to { c: Canvas -> desenharJogador(c) })

        objetos.sortBy { it.first }
        for (obj in objetos) {
            obj.second(canvas)
        }
    }

    private fun desenharJogador(canvas: Canvas) {
        val j = jogador
        val pose = when {
            j.tempoGolpe > 0.22f -> "golpe1"
            j.tempoGolpe > 0f -> "golpe2"
            j.andando -> if (j.faseAndar.toInt() % 2 == 0) "anda1" else "anda2"
            else -> "parado"
        }
        val bmp = PixelArt.personagem(j.classe, j.direcao, pose)
        val larg = bmp.width * ESCALA
        val alt = bmp.height * ESCALA
        val cx = j.x * TAMANHO_TILE + TAMANHO_TILE / 2f - camX
        val pe = j.y * TAMANHO_TILE + TAMANHO_TILE - camY + 8f

        canvas.drawOval(RectF(cx - 26f, pe - 12f, cx + 26f, pe + 4f), pSombra)
        canvas.drawBitmap(
            bmp, null,
            RectF(cx - larg / 2f, pe - alt + 4f, cx + larg / 2f, pe + 4f),
            PixelArt.pinturaSemFiltro
        )
    }

    private fun desenharMonstro(canvas: Canvas, m: Monstro) {
        val quadro = if (m.andando && (m.faseAndar.toInt() % 2 == 1)) 2 else 1
        val bmp = when (m.tipo) {
            TipoMonstro.SLIME -> PixelArt.de("m_slime$quadro", if (quadro == 1) Sprites.MONSTRO_SLIME_1 else Sprites.MONSTRO_SLIME_2)
            TipoMonstro.LOBO -> PixelArt.de("m_lobo$quadro", if (quadro == 1) Sprites.MONSTRO_LOBO_1 else Sprites.MONSTRO_LOBO_2)
            TipoMonstro.ORC -> PixelArt.de("m_orc$quadro", if (quadro == 1) Sprites.MONSTRO_ORC_1 else Sprites.MONSTRO_ORC_2)
            TipoMonstro.ARANHA -> PixelArt.de("m_aranha$quadro", if (quadro == 1) Sprites.MONSTRO_ARANHA_1 else Sprites.MONSTRO_ARANHA_2)
        }
        val larg = bmp.width * ESCALA
        val alt = bmp.height * ESCALA
        val cx = m.x * TAMANHO_TILE + TAMANHO_TILE / 2f - camX
        val pe = m.y * TAMANHO_TILE + TAMANHO_TILE - camY + 6f

        canvas.drawOval(RectF(cx - 24f, pe - 10f, cx + 24f, pe + 4f), pSombra)
        canvas.drawBitmap(
            bmp, null,
            RectF(cx - larg / 2f, pe - alt + 2f, cx + larg / 2f, pe + 2f),
            PixelArt.pinturaSemFiltro
        )

        if (m.vida < m.vidaMax) {
            val larguraBarra = 52f
            val inicioX = cx - larguraBarra / 2f
            val topo = pe - alt - 8f
            canvas.drawRoundRect(RectF(inicioX, topo, inicioX + larguraBarra, topo + 8f), 4f, 4f, pBarraFundo)
            val pct = (m.vida.toFloat() / m.vidaMax).coerceIn(0f, 1f)
            val cor = if (pct < 0.35f) pBarraBaixa else pBarraVida
            canvas.drawRoundRect(RectF(inicioX, topo, inicioX + larguraBarra * pct, topo + 8f), 4f, 4f, cor)
        }
    }

    private fun desenharProjeteis(canvas: Canvas) {
        for (p in projeteis) {
            val bmp = if (p.tipo == 1) {
                PixelArt.de("p_fogo", Sprites.PROJETIL_FOGO)
            } else {
                PixelArt.de("p_flecha", Sprites.PROJETIL_FLECHA)
            }
            val larg = bmp.width * ESCALA * 0.7f
            val alt = bmp.height * ESCALA * 0.7f
            val cx = p.x * TAMANHO_TILE + TAMANHO_TILE / 2f - camX
            val cy = p.y * TAMANHO_TILE + TAMANHO_TILE / 2f - camY
            val sprite = if (p.dx < 0 && p.tipo == 0) PixelArt.espelhar(bmp) else bmp
            canvas.drawBitmap(
                sprite, null,
                RectF(cx - larg / 2f, cy - alt / 2f, cx + larg / 2f, cy + alt / 2f),
                PixelArt.pinturaSemFiltro
            )
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
