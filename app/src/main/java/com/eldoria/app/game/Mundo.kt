package com.eldoria.app.game

import kotlin.math.sin
import kotlin.random.Random

/** Tipos de terreno do mundo. */
object Tiles {
    const val GRAMA = 0
    const val FLOR = 1
    const val AREIA = 2
    const val AGUA = 3
    const val PEDRA = 4
    const val ARVORE = 5
    const val PAREDE = 6
}

/**
 * Mundo do jogo: mapa de terreno gerado por ruído simples.
 * Água, árvores e paredes bloqueiam o caminho.
 */
class Mundo(val largura: Int = 90, val altura: Int = 90, semente: Long = 20261020L) {

    val tiles: Array<IntArray> = Array(altura) { IntArray(largura) }
    val spawnX: Int = largura / 2
    val spawnY: Int = altura / 2

    private val rand = Random(semente)

    init {
        gerar()
    }

    private fun gerar() {
        val f1 = rand.nextDouble(0.0, 6.28)
        val f2 = rand.nextDouble(0.0, 6.28)
        val f3 = rand.nextDouble(0.0, 6.28)
        val f4 = rand.nextDouble(0.0, 6.28)

        for (y in 0 until altura) {
            for (x in 0 until largura) {
                val ruido = sin(x * 0.11 + f1) + sin(y * 0.09 + f2) +
                        sin((x + y) * 0.05 + f3) + sin((x - y) * 0.07 + f4)
                val v = ruido / 4.0
                tiles[y][x] = when {
                    v < -0.34 -> Tiles.AGUA
                    v < -0.24 -> Tiles.AREIA
                    v > 0.42 -> Tiles.PEDRA
                    else -> {
                        val sorte = rand.nextDouble()
                        when {
                            sorte < 0.09 -> Tiles.ARVORE
                            sorte < 0.20 -> Tiles.FLOR
                            else -> Tiles.GRAMA
                        }
                    }
                }
            }
        }

        // Deixa o centro (onde o herói nasce) sempre livre e seguro
        val iniY = (spawnY - 5).coerceAtLeast(0)
        val fimY = (spawnY + 5).coerceAtMost(altura - 1)
        val iniX = (spawnX - 5).coerceAtLeast(0)
        val fimX = (spawnX + 5).coerceAtMost(largura - 1)
        for (y in iniY..fimY) {
            for (x in iniX..fimX) {
                val t = tiles[y][x]
                if (t == Tiles.AGUA || t == Tiles.ARVORE || t == Tiles.PAREDE) {
                    tiles[y][x] = Tiles.GRAMA
                }
            }
        }
    }

    fun tile(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= largura || y >= altura) return Tiles.PAREDE
        return tiles[y][x]
    }

    fun bloqueado(x: Int, y: Int): Boolean = when (tile(x, y)) {
        Tiles.AGUA, Tiles.ARVORE, Tiles.PAREDE -> true
        else -> false
    }
}
