package com.eldoria.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.eldoria.app.databinding.ActivityConfiguracoesBinding
import com.eldoria.app.game.Jogador
import com.eldoria.app.game.Salvamento
import com.eldoria.app.ui.DialogoClasse
import com.eldoria.app.updater.UpdateHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ConfiguracoesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracoesBinding
    private lateinit var updateHelper: UpdateHelper

    private val launcherPermissao = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (::updateHelper.isInitialized) updateHelper.aoVoltarDaPermissao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracoesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateHelper = UpdateHelper(this, launcherPermissao)

        binding.textVersao.text = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }

        val jogadorAtual = Jogador(0, 0)
        Salvamento.carregar(this, jogadorAtual)
        binding.textClasseAtual.text = jogadorAtual.classe.nome

        binding.botaoTrocarClasse.setOnClickListener {
            DialogoClasse.mostrar(this) { classe ->
                val j = Jogador(0, 0)
                Salvamento.carregar(this, j)
                j.classe = classe
                j.vidaMax = classe.vidaBase + (j.nivel - 1) * 18
                j.vida = j.vidaMax
                Salvamento.salvar(this, j)
                binding.textClasseAtual.text = classe.nome
            }
        }

        binding.botaoVoltar.setOnClickListener { finish() }

        binding.botaoVerificarAtualizacao.setOnClickListener {
            updateHelper.verificarManual()
        }

        binding.botaoVerNovidades.setOnClickListener {
            Novidades.mostrarDialogo(this, Novidades.maisRecente(this))
        }

        binding.botaoReiniciarJogo.setOnClickListener { confirmarReinicio() }
    }

    private fun confirmarReinicio() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reiniciar jogo")
            .setMessage("Isso apaga o seu save atual (nível, ouro, poções e posição). Deseja continuar?")
            .setPositiveButton("Apagar") { _, _ ->
                Salvamento.limpar(this)
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
