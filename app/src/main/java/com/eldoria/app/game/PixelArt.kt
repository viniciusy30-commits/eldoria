package com.eldoria.app.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * Motor de pixel art: converte os sprites (matrizes de caracteres) em
 * bitmaps, compõe as partes do personagem e mantém cache.
 */
object PixelArt {

    private val cache = HashMap<String, Bitmap>()
    val pinturaSemFiltro: Paint = Paint().apply { isFilterBitmap = false }

    /** Converte uma matriz de caracteres em bitmap. */
    fun de(chave: String, linhas: Array<String>): Bitmap {
        cache[chave]?.let { return it }
        val altura = linhas.size
        var largura = 0
        for (l in linhas) if (l.length > largura) largura = l.length
        val bmp = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        for (y in 0 until altura) {
            val linha = linhas[y]
            for (x in 0 until linha.length) {
                val cor = Sprites.PALETA[linha[x]] ?: 0
                if (cor != 0) bmp.setPixel(x, y, cor)
            }
        }
        cache[chave] = bmp
        return bmp
    }

    private fun blitar(destino: Bitmap, parte: Bitmap, deslocY: Int) {
        val canvas = Canvas(destino)
        canvas.drawBitmap(parte, 0f, deslocY.toFloat(), null)
    }

    /**
     * Compõe o personagem: pernas -> tronco -> cabeça -> chapéu -> arma.
     * direcao: 0 baixo, 1 cima, 2 esquerda, 3 direita
     * pose: parado | anda1 | anda2 | golpe1 | golpe2
     */
    fun personagem(classe: Classe, direcao: Int, pose: String): Bitmap {
        val espelhado = direcao == 2
        val dirSprite = when (direcao) {
            1 -> "CIMA"
            2, 3 -> "LADO"
            else -> "BAIXO"
        }
        val chave = "p_${classe.name}_${dirSprite}_$pose${if (espelhado) "_e" else ""}"
        cache[chave]?.let { return it }

        val tronco = when (classe) {
            Classe.GUERREIRO -> Sprites.TRONCO_GUERREIRO
            Classe.MAGO -> Sprites.TRONCO_MAGO
            Classe.ARQUEIRO -> Sprites.TRONCO_ARQUEIRO
        }
        val chapeu = when (classe) {
            Classe.GUERREIRO -> Sprites.CHAPEU_GUERREIRO
            Classe.MAGO -> Sprites.CHAPEU_MAGO
            Classe.ARQUEIRO -> Sprites.CHAPEU_ARQUEIRO
        }
        val cabeca = when (dirSprite) {
            "CIMA" -> Sprites.CABECA_CIMA
            "LADO" -> Sprites.CABECA_LADO
            else -> Sprites.CABECA_BAIXO
        }
        val pernas = when (classe) {
            Classe.GUERREIRO -> when (pose) {
                "anda1" -> Sprites.PERNAS_GUERREIRO_ANDA1
                "anda2" -> Sprites.PERNAS_GUERREIRO_ANDA2
                else -> Sprites.PERNAS_GUERREIRO_PARADO
            }
            Classe.MAGO -> when (pose) {
                "anda1" -> Sprites.PERNAS_MAGO_ANDA1
                "anda2" -> Sprites.PERNAS_MAGO_ANDA2
                else -> Sprites.PERNAS_MAGO_PARADO
            }
            Classe.ARQUEIRO -> when (pose) {
                "anda1" -> Sprites.PERNAS_ARQUEIRO_ANDA1
                "anda2" -> Sprites.PERNAS_ARQUEIRO_ANDA2
                else -> Sprites.PERNAS_ARQUEIRO_PARADO
            }
        }
        val arma = when (classe) {
            Classe.GUERREIRO -> when (pose) {
                "golpe1" -> Sprites.ARMA_GUERREIRO_GOLPE1
                "golpe2" -> Sprites.ARMA_GUERREIRO_GOLPE2
                else -> Sprites.ARMA_GUERREIRO_PARADO
            }
            Classe.MAGO -> when (pose) {
                "golpe1", "golpe2" -> Sprites.ARMA_MAGO_GOLPE1
                else -> Sprites.ARMA_MAGO_PARADO
            }
            Classe.ARQUEIRO -> when (pose) {
                "golpe1", "golpe2" -> Sprites.ARMA_ARQUEIRO_GOLPE1
                else -> Sprites.ARMA_ARQUEIRO_PARADO
            }
        }

        val g = Bitmap.createBitmap(24, 28, Bitmap.Config.ARGB_8888)
        val bob = if (pose == "anda2" || pose == "golpe1") -1 else 0

        if (dirSprite == "CIMA") blitar(g, de("arma_${classe.name}_$pose", arma), 0)
        blitar(g, de("perna_${classe.name}_$dirSprite$pose", pernas), 21)
        blitar(g, de("tronco_${classe.name}", tronco), 15)
        blitar(g, de("cabeca_$dirSprite", cabeca), 2 + bob)
        blitar(g, de("chapeu_${classe.name}", chapeu), bob)
        if (dirSprite != "CIMA") blitar(g, de("arma_${classe.name}_$pose", arma), 0)

        val resultado = if (espelhado) espelhar(g) else g
        cache[chave] = resultado
        return resultado
    }

    fun espelhar(bmp: Bitmap): Bitmap {
        val matriz = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matriz, false)
    }

    /** Amplia um sprite sem borrar (vizinho mais próximo). */
    fun ampliar(bmp: Bitmap, escala: Int): Bitmap {
        return Bitmap.createScaledBitmap(bmp, bmp.width * escala, bmp.height * escala, false)
    }
}
