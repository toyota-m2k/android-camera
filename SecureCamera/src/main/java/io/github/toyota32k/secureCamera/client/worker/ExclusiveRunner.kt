package io.github.toyota32k.secureCamera.client.worker

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.db.ItemEx

class ExclusiveRunner(val logger: UtLog, private val name: String) {
    val taskSet = mutableSetOf<String>()
    fun key(slot:Int, itemId:Int): String {
        return "$name:$slot@$itemId"
    }
    fun setRunning(slot:Int, itemId:Int):Boolean {
        return synchronized(taskSet) {
            val key = key(slot,itemId)
            if (taskSet.contains(key)) {
                logger.debug("Task already running for $key")
                false // already running
            } else {
                logger.debug("Setting task running for $key")
                taskSet.add(key) // add to set
                true // set running
            }
        }
    }

    inline fun run(slot:Int, itemId:Int, block: () -> Unit) {
        if (!setRunning(slot, itemId)) return // already running
        try {
             block() // execute the block
        } finally {
            synchronized(taskSet) {
                logger.debug("Removing task for slot=$slot, itemId=$itemId")
                taskSet.remove(key(slot, itemId)) // remove from set
            }
        }
    }
}