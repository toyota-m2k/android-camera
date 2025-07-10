package io.github.toyota32k.secureCamera.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.IntDef
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.SCApplication
import io.github.toyota32k.secureCamera.settings.Settings.SecureArchive
import io.github.toyota32k.secureCamera.utils.SharedPreferenceDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class SlotIndex(val index: Int, val label: String="Slot-$index") {
    DEFAULT(0, "Default"),
    SLOT1(1),
    SLOT2(2),
    SLOT3(3),
    SLOT4(4),
    ;

    val slotId:String get() = "slot$index"

    companion object {
        fun fromIndex(index: Int): SlotIndex {
            return entries.firstOrNull { it.index == index } ?: DEFAULT
        }
    }
}
data class SlotInfo(val index: SlotIndex, val slotName: String?, val inUse: Boolean, val sync: Boolean, val secure:Boolean) {
    val isDefault: Boolean
        get() = index == SlotIndex.DEFAULT

    val safeSlotName:String get() {
        return slotName ?: when(index) {
            SlotIndex.DEFAULT -> "Default"
            SlotIndex.SLOT1 -> "Slot-1"
            SlotIndex.SLOT2 -> "Slot-2"
            SlotIndex.SLOT3 -> "Slot-3"
            SlotIndex.SLOT4 -> "Slot-4"
        }
    }

    fun update(slotName: String? = this.slotName, inUse: Boolean = this.inUse, sync: Boolean = this.sync, secure: Boolean = this.secure): SlotInfo {
        return SlotInfo(index, slotName, inUse, sync, secure)
    }

    companion object {
        val Default = SlotInfo(SlotIndex.DEFAULT, null, true, true, true)
    }
}

object SlotSettings {
    val logger = UtLog("Slot", null, SlotSettings.javaClass)

    private val spd :SharedPreferenceDelegate by lazy { SharedPreferenceDelegate(SCApplication.instance.applicationContext, "slot_settings", Context.MODE_PRIVATE)}
//    fun initialize(application: Application) {
//        if(this::spd.isInitialized) return
//        spd = SharedPreferenceDelegate(application.applicationContext, "slot_settings", Context.MODE_PRIVATE)
//    }
    private var currentSlot:Int by spd.pref(0)
    val currentSlotIndex: SlotIndex
        get() = SlotIndex.fromIndex(currentSlot)

    var defaultSlotName:String by spd.pref("")
    val defaultSlot:SlotInfo get() = SlotInfo(SlotIndex.DEFAULT, defaultSlotName, true, true, true)
    var slot1:SlotInfo by spd.typedPref(SlotInfo(SlotIndex.SLOT1, "Slot-1", false, false, false), SlotInfo::class.java)
    var slot2:SlotInfo by spd.typedPref(SlotInfo(SlotIndex.SLOT2, "Slot-2", false, false, false), SlotInfo::class.java)
    var slot3:SlotInfo by spd.typedPref(SlotInfo(SlotIndex.SLOT3, "Slot-3", false, false, false), SlotInfo::class.java)
    var slot4:SlotInfo by spd.typedPref(SlotInfo(SlotIndex.SLOT4, "Slot-4", false, false, false), SlotInfo::class.java)

    val activeSlots : List<SlotInfo>
        get() = listOf(defaultSlot, slot1, slot2, slot3, slot4).filter { it.inUse }

    operator fun get(index: SlotIndex): SlotInfo {
        return when(index) {
            SlotIndex.DEFAULT -> SlotInfo.Default
            SlotIndex.SLOT1 -> slot1
            SlotIndex.SLOT2 -> slot2
            SlotIndex.SLOT3 -> slot3
            SlotIndex.SLOT4 -> slot4
        }
    }
    operator fun set(index: SlotIndex, value: SlotInfo) {
        fun setIfChanged(slot: SlotInfo, newValue: SlotInfo): SlotInfo {
            return if (slot != newValue) newValue else slot
        }
        when(index) {
            SlotIndex.DEFAULT -> throw IllegalArgumentException("Cannot set value for DEFAULT slot")
            SlotIndex.SLOT1 -> if (slot1 != value) { slot1 = value }
            SlotIndex.SLOT2 -> if (slot2 != value) { slot2 = value }
            SlotIndex.SLOT3 -> if (slot3 != value) { slot3 = value }
            SlotIndex.SLOT4 -> if (slot4 != value) { slot4 = value }
        }
    }

    val currentSlotFlow = MutableStateFlow<SlotInfo>(get(currentSlotIndex))
    fun setCurrentSlot(index: SlotIndex) {
        if (index == currentSlotIndex) return
        currentSlot = index.index
        currentSlotFlow.value = get(currentSlotIndex)
        logger.debug { "Current slot changed to ${currentSlotIndex.label}" }
    }

}