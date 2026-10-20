package com.eldoria.app.game

import android.content.Context
import org.json.JSONObject

/** Salva e carrega o progresso do jogo no próprio celular. */
object Salvamento {

    private const val ARQUIVO = "jogo"
    private const val CHAVE = "dados"

    fun salvar(context: Context, j: Jogador) {
        val json = JSONObject()
        json.put("nivel", j.nivel)
        json.put("xp", j.xp)
        json.put("vida", j.vida)
        json.put("vidaMax", j.vidaMax)
        json.put("ouro", j.ouro)
        json.put("pocoes", j.pocoes)
        json.put("mortes", j.mortes)
        json.put("abates", j.abates)
        json.put("tileX", j.tileX)
        json.put("tileY", j.tileY)
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            .edit()
            .putString(CHAVE, json.toString())
            .apply()
    }

    fun carregar(context: Context, j: Jogador) {
        val texto = context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            .getString(CHAVE, null) ?: return
        try {
            val json = JSONObject(texto)
            j.nivel = json.optInt("nivel", 1).coerceAtLeast(1)
            j.xp = json.optInt("xp", 0).coerceAtLeast(0)
            j.vidaMax = json.optInt("vidaMax", 100).coerceAtLeast(100)
            j.vida = json.optInt("vida", j.vidaMax).coerceIn(1, j.vidaMax)
            j.ouro = json.optInt("ouro", 0).coerceAtLeast(0)
            j.pocoes = json.optInt("pocoes", 5).coerceAtLeast(0)
            j.mortes = json.optInt("mortes", 0).coerceAtLeast(0)
            j.abates = json.optInt("abates", 0).coerceAtLeast(0)

            val tx = json.optInt("tileX", j.spawnX)
            val ty = json.optInt("tileY", j.spawnY)
            j.tileX = tx
            j.tileY = ty
            j.destinoX = tx
            j.destinoY = ty
            j.x = tx.toFloat()
            j.y = ty.toFloat()
        } catch (_: Exception) {
            // save corrompido: mantém o padrão
        }
    }

    fun limpar(context: Context) {
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
