package com.eldoria.app.game

import java.util.UUID
import kotlin.random.Random

/** Classes jogáveis do jogo. */
enum class Classe(
    val nome: String,
    val descricao: String,
    val vidaBase: Int,
    val danoBase: Int,
    val alcance: Float,
    val recarga: Float,
    val velocidade: Float
) {
    GUERREIRO(
        "Guerreiro",
        "Forte e resistente, luta corpo a corpo com a espada.",
        130, 14, 1.9f, 0.55f, 7.0f
    ),
    MAGO(
        "Mago",
        "Lança bolas de fogo à distância, mas tem pouca vida.",
        95, 20, 7.0f, 0.90f, 6.2f
    ),
    ARQUEIRO(
        "Arqueiro",
        "Rápido e ágil, atira flechas precisas de longe.",
        110, 13, 8.0f, 0.45f, 7.5f
    )
}

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
class Jogador(val spawnX: Int, val spawnY: Int, var classe: Classe = Classe.GUERREIRO) {
    var tileX: Int = spawnX
    var tileY: Int = spawnY
    var destinoX: Int = spawnX
    var destinoY: Int = spawnY

    var x: Float = spawnX.toFloat()
    var y: Float = spawnY.toFloat()

    var andando: Boolean = false
    var faseAndar: Float = 0f
    var direcao: Int = 1 // 0 baixo, 1 cima, 2 esquerda, 3 direita
    var poseGolpe: String = "" // "", golpe1 ou golpe2
    var tempoGolpe: Float = 0f

    var nivel: Int = 1
    var xp: Int = 0
    var vida: Int = classe.vidaBase
    var vidaMax: Int = classe.vidaBase
    var ouro: Int = 0
    var pocoes: Int = 5
    var mortes: Int = 0
    var abates: Int = 0

    var recargaAtaque: Float = 0f
    var tempoRegeneracao: Float = 0f

    fun xpProximo(): Int = 80 + (nivel - 1) * 55 + (nivel - 1) * (nivel - 1) * 22

    fun danoBase(): Int = classe.danoBase + (nivel - 1) * 4

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

/** Flecha ou bola de fogo disparada pelo jogador. */
class Projetil(
    var x: Float,
    var y: Float,
    val dx: Float,
    val dy: Float,
    val dano: Int,
    val tipo: Int,          // 0 flecha, 1 fogo
    val alcance: Float,
    val velocidade: Float
) {
    var distancia: Float = 0f
}
