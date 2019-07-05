package com.tunjid.rcswitchcontrol.data.persistence

import android.content.Context
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.converter
import com.zsmartsystems.zigbee.IeeeAddress
import com.zsmartsystems.zigbee.database.ZigBeeNetworkDataStore
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao

class ZigBeeDataStore(private val networkId: String) : ZigBeeNetworkDataStore {

    val hasNoDevices: Boolean
        get() = preferences(networkId).all.isEmpty()

    override fun removeNode(address: IeeeAddress) =
            preferences(networkId).edit().remove(address.toString()).apply()

    override fun readNetworkNodes(): MutableSet<IeeeAddress> =
            preferences(networkId).all.keys.map { IeeeAddress(it.substring(0, 16)) }.toMutableSet()

    override fun readNode(address: IeeeAddress): ZigBeeNodeDao =
            converter.fromJson(preferences(networkId).getString(address.toString(), ""), ZigBeeNodeDao::class.java)

    override fun writeNode(node: ZigBeeNodeDao) =
            preferences(networkId).edit().putString(node.ieeeAddress.toString(), converter.toJson(node)).apply()

    private fun preferences(key: String) = App.instance.getSharedPreferences(key, Context.MODE_PRIVATE)

}