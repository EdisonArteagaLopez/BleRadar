package com.example.bleradar.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.bleradar.databinding.ItemDeviceBinding
import com.example.bleradar.model.DeviceInfo

/**
 * Adapter do RecyclerView.
 *
 * Objetivo:
 * - Transformar uma lista de DeviceInfo em "linhas" na UI (item_device.xml).
 */
class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private val items = mutableListOf<DeviceInfo>()

    /**
     * Substitui completamente a lista exibida.
     *
     * Objetivo:
     * - Atualizar a UI com os dispositivos mais recentes.
     *
     * Parâmetros:
     * @param newList lista nova (List<DeviceInfo>).
     *
     * Retorno:
     * - Não retorna valor; atualiza estado interno e notifica o RecyclerView.
     */
    fun submitList(newList: List<DeviceInfo>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    /**
     * Cria um ViewHolder (uma "célula" da lista).
     *
     * Objetivo:
     * - Inflar o layout item_device.xml usando ViewBinding.
     *
     * Parâmetros:
     * @param parent container do RecyclerView.
     * @param viewType tipo do item (não usamos aqui, mas faz parte da API).
     *
     * Retorno:
     * @return VH (ViewHolder) pronto para receber dados.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    /**
     * Associa (bind) um DeviceInfo a um ViewHolder.
     *
     * Objetivo:
     * - Preencher o item da lista com os valores do dispositivo.
     *
     * Parâmetros:
     * @param holder ViewHolder que será preenchido.
     * @param position posição do item na lista.
     *
     * Retorno:
     * - Sem retorno; atualiza UI daquele item.
     */
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    /**
     * Informa quantos itens existem na lista.
     *
     * Retorno:
     * @return Int (tamanho da lista).
     */
    override fun getItemCount(): Int = items.size

    /**
     * ViewHolder: representa um item renderizado na lista.
     */
    class VH(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Preenche os TextViews do item com dados do DeviceInfo.
         *
         * Objetivo:
         * - Exibir nome, endereço, RSSI e proximidade.
         *
         * Parâmetros:
         * @param item o dispositivo a ser exibido.
         *
         * Retorno:
         * - Sem retorno; altera componentes de UI via binding.
         */
        fun bind(item: DeviceInfo) {
            binding.tvDeviceName.text = item.name ?: "(sem nome)"
            binding.tvDeviceAddr.text = item.address
            binding.tvDeviceRssi.text =
                "RSSI: ${item.rssiRaw} dBm | Suavizado: ${"%.1f".format(item.rssiSmoothed)} | Proximidade: ${item.proximity}"
        }
    }
}