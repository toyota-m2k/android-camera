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
        private val nf = MutableSharedFlow<DBChange>(replay = 0, extraBufferCapacity=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val observable : Flow<DBChange> = nf

        fun add(itemId:Int) {
            nf.tryEmit(DBChange(Type.Add,itemId))
        }
        fun update(itemId:Int) {
            nf.tryEmit(DBChange(Type.Update,itemId))
        }
        fun delete(itemId:Int) {
            nf.tryEmit(DBChange(Type.Delete,itemId))
        }
        fun refresh() {
            nf.tryEmit(DBChange(Type.Refresh,0))
        }

//        fun observe(coroutineScope: CoroutineScope, fn:(DBChange)->Unit) {
//            observable.onEach(fn).launchIn(coroutineScope)
//        }
    }
}