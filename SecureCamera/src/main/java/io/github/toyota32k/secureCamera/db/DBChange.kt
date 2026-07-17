package io.github.toyota32k.secureCamera.db

import io.github.toyota32k.utils.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DBChange(val type:Type, val itemId:Int) {
    enum class Type {
        Add,
        Update,
        Delete,
        Refresh,
    }
    companion object {
        private val nf = MutableSharedFlow<DBChange>(replay = 0, extraBufferCapacity=1, onBufferOverflow = BufferOverflow.SUSPEND)
        val observable : Flow<DBChange> = nf

        suspend fun add(itemId:Int) {
            nf.emit(DBChange(Type.Add,itemId))
        }
        suspend fun update(itemId:Int) {
            nf.emit(DBChange(Type.Update,itemId))
        }
        suspend fun delete(itemId:Int) {
            nf.emit(DBChange(Type.Delete,itemId))
        }
        suspend fun refresh() {
            nf.emit(DBChange(Type.Refresh,0))
        }

//        fun observe(coroutineScope: CoroutineScope, fn:(DBChange)->Unit) {
//            observable.onEach(fn).launchIn(coroutineScope)
//        }
    }
}