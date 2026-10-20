package com.eldoria.app.ui

import androidx.appcompat.app.AppCompatActivity
import com.eldoria.app.R
import com.eldoria.app.databinding.DialogClasseBinding
import com.eldoria.app.game.Classe
import com.eldoria.app.game.PixelArt
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Diálogo de escolha de classe, com retratos em pixel art. */
object DialogoClasse {

    fun mostrar(activity: AppCompatActivity, aoEscolher: (Classe) -> Unit) {
        val binding = DialogClasseBinding.inflate(activity.layoutInflater)

        val dialogo = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.classe_titulo)
            .setView(binding.root)
            .create()

        val retratos = mapOf(
            Classe.GUERREIRO to Pair(binding.imgGuerreiro, binding.infoGuerreiro),
            Classe.MAGO to Pair(binding.imgMago, binding.infoMago),
            Classe.ARQUEIRO to Pair(binding.imgArqueiro, binding.infoArqueiro)
        )

        for ((classe, par) in retratos) {
            val (imagem, info) = par
            val retrato = PixelArt.ampliar(PixelArt.personagem(classe, 0, "parado"), 4)
            imagem.setImageBitmap(retrato)
            info.text = "Vida ${classe.vidaBase}  •  Dano ${classe.danoBase}  •  Alcance ${if (classe.alcance > 3f) "longo" else "corpo a corpo"}"
        }

        binding.cardGuerreiro.setOnClickListener { dialogo.dismiss(); aoEscolher(Classe.GUERREIRO) }
        binding.cardMago.setOnClickListener { dialogo.dismiss(); aoEscolher(Classe.MAGO) }
        binding.cardArqueiro.setOnClickListener { dialogo.dismiss(); aoEscolher(Classe.ARQUEIRO) }

        dialogo.show()
    }
}
