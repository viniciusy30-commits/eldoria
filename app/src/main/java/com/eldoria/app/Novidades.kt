package com.eldoria.app

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.eldoria.app.databinding.DialogNovidadesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Lê o arquivo de novidades (assets/novidades.txt) e mostra
 * o resumo "O que mudou" para o jogador.
 *
 * Formato do arquivo:
 *   #id 2026-10-20-a
 *   #titulo Título curto
 *   - uma novidade por linha
 */
object Novidades {

    data class Secao(val id: String, val titulo: String, val data: String, val itens: List<String>)

    fun carregar(context: Context): List<Secao> {
        val secoes = mutableListOf<Secao>()
        val texto = try {
            context.assets.open("novidades.txt").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return secoes
        }

        var idAtual = ""
        var tituloAtual = ""
        var dataAtual = ""
        val itensAtuais = mutableListOf<String>()

        fun salvarSecao() {
            if (idAtual.isNotBlank() || tituloAtual.isNotBlank() || itensAtuais.isNotEmpty()) {
                secoes.add(
                    Secao(
                        id = idAtual,
                        titulo = tituloAtual.ifBlank { "Novidades" },
                        data = dataAtual,
                        itens = itensAtuais.toList()
                    )
                )
            }
            idAtual = ""
            tituloAtual = ""
            dataAtual = ""
            itensAtuais.clear()
        }

        for (linha in texto.lines()) {
            val l = linha.trim()
            when {
                l.startsWith("#id") -> {
                    if (idAtual.isNotBlank() || tituloAtual.isNotBlank() || itensAtuais.isNotEmpty()) {
                        salvarSecao()
                    }
                    idAtual = l.removePrefix("#id").trim()
                    dataAtual = formatarData(idAtual)
                }
                l.startsWith("#titulo") -> tituloAtual = l.removePrefix("#titulo").trim()
                l.startsWith("#data") -> dataAtual = l.removePrefix("#data").trim()
                l.startsWith("-") -> {
                    val item = l.removePrefix("-").trim()
                    if (item.isNotEmpty()) itensAtuais.add(item)
                }
            }
        }
        salvarSecao()
        return secoes
    }

    fun maisRecente(context: Context): Secao? = carregar(context).firstOrNull()

    /** Mostra o diálogo "O que mudou" bonitinho. */
    fun mostrarDialogo(activity: AppCompatActivity, secao: Secao?) {
        val binding = DialogNovidadesBinding.inflate(activity.layoutInflater)

        if (secao == null) {
            binding.textTituloNovidades.text = activity.getString(R.string.novidades_titulo)
            binding.textDataNovidades.text = activity.getString(R.string.novidades_vazio)
        } else {
            binding.textTituloNovidades.text = secao.titulo
            binding.textDataNovidades.text = secao.data
            for (item in secao.itens) {
                val texto = TextView(activity)
                texto.text = "•  $item"
                texto.textSize = 15f
                texto.setTextColor(activity.getColor(R.color.text_secondary))
                texto.setLineSpacing(4f, 1f)
                texto.setPadding(0, 10, 0, 10)
                binding.listaItens.addView(texto)
            }
        }

        val dialogo = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .create()

        binding.botaoFecharNovidades.setOnClickListener { dialogo.dismiss() }
        dialogo.show()
    }

    private fun formatarData(id: String): String {
        val parte = id.take(10)
        val regex = Regex("\\d{4}-\\d{2}-\\d{2}")
        return if (regex.matches(parte)) {
            "${parte.substring(8, 10)}/${parte.substring(5, 7)}/${parte.substring(0, 4)}"
        } else {
            ""
        }
    }
}
