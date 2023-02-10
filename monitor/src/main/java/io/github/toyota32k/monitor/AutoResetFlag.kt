package io.github.toyota32k.monitor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * set() でフラグを on にした後、duration 後に自動的に off になるフラグ(Flow<Boolean>)クラス
 * touch() したら、duration だけ延長する。
 * hold = true にすると、falseにされるまで、フラグは off にならない。
 */
class TimerFlag(private val duration: Long, private val innerFlow:MutableStateFlow<Boolean> = MutableStateFlow(false)) : StateFlow<Boolean> by innerFlow {
    private val scope = CoroutineScope(Dispatchers.IO+ SupervisorJob())
    private var startTick:Long = 0L
    init {
        innerFlow.onEach {
            if(it) {
                startTick = System.currentTimeMillis()
                watch()
            }
        }.launchIn(scope)
    }

    override var value:Boolean
        get() = innerFlow.value
        set(v) { innerFlow.value = v }

    fun set() {
        value = true
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun touch() {
        startTick = System.currentTimeMillis()
    }

    private val holdFlow = MutableStateFlow(false)
    var hold:Boolean
        get() = holdFlow.value
        set(v) {
            touch()
            holdFlow.value = v
        }

    private suspend fun watch() {
        while (scope.isActive) {
            delay(100)
            holdFlow.first { !it }
            if (System.currentTimeMillis() - startTick >= duration) {
                innerFlow.value = false
                return
            }
        }
    }
}