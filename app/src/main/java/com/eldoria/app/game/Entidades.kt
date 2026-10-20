package com.eldoria.app.game

import java.util.UUID
import kotlin.random.Random

/** Tipos de monstro do jogo. */
enum class TipoMonstro(
    val nome: String,
    val vidaMax: Int,
    val dano: Int,
    val experiencia: Int,
    val ouroMin: Int,
    val ouroMax: Int,
    val raioPerseguicao: Int,
    val velocidade: Float
) {
    SLIME("Slime", 32, 6, 18, 2, 6, 5, 3.0f),
    LOBO("Lobo", 58, 11, 34, 5, 12, 7, 4.2f),
    ORC("Orc", 98, 17, 62, 10, 22, 8, 3.4f),
    ARANHA("Aranha Gigante", 132, 24, 95, 16, 34, 9, 4.6f)
}

/** Estado resumido do jogador, usado para atualizar o HUD. */
data class EstadoJogador(
    val nivel: Int,
    val xp: Int,
    val xpProx: Int,
    val vida: Int,
    val vidaMax: Int,
    val ouro: Int,
    val pocoes: Int,
    val mortes: Int,
    val abates: Int
)

/** O herói controlado pelo jogador. */
class Jogador(val spawnX: Int, val spawnY: Int) {
    var tileX: Int = spawnX
    var tileY: Int = spawnY
    var destinoX: Int = spawnX
    var destinoY: Int = spawnY

    var x: Float = spawnX.toFloat()
    var y: Float = spawnY.toFloat()

    var andando: Boolean = false
    var faseAndar: Float = 0f
    var direcao: Int = 1 // 0 cima, 1 baixo, 2 esquerda, 3 direita

    var nivel: Int = 1
    var xp: Int = 0
    var vida: Int = 100
    var vidaMax: Int = 100
    var ouro: Int = 0
    var pocoes: Int = 5
    var mortes: Int = 0
    var abates: Int = 0

    var recargaAtaque: Float = 0f
    var tempoRegeneracao: Float = 0f

    fun xpProximo(): Int = 80 + (nivel - 1) * 55 + (nivel - 1) * (nivel - 1) * 22

    fun danoBase(): Int = 12 + (nivel - 1) * 4

    fun estado(): EstadoJogador = EstadoJogador(
        nivel = nivel,
        xp = xp,
        xpProx = xpProximo(),
        vida = vida,
        vidaMax = vidaMax,
        ouro = ouro,
        pocoes = pocoes,
        mortes = mortes,
        abates = abates
    )
}

/** Um monstro vivo no mundo. */
class Monstro(val tipo: TipoMonstro, tileXInicial: Int, tileYInicial: Int) {
    val id: String = UUID.randomUUID().toString()

    var x: Float = tileXInicial.toFloat()
    var y: Float = tileYInicial.toFloat()
    var destinoX: Int = tileXInicial
    var destinoY: Int = tileYInicial

    var vida: Int = tipo.vidaMax
    var recargaAtaque: Float = 0f
    var tempoIa: Float = Random.nextFloat() * 0.6f
    var andando: Boolean = false
    var faseAndar: Float = 0f

    val vidaMax: Int get() = tipo.vidaMax
}

/** Texto que sobe na tela (dano, ouro, experiência). */
class TextoFlutuante(val x: Float, val y: Float, val texto: String, val cor: Int) {
    var tempo: Float = 0f
    val duracao: Float = 1.3f
}
