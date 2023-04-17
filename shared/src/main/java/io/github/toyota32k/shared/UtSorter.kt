package io.github.toyota32k.shared

/**
 * MutableList（主に ObservableListを想定）を内包し、ソート状態を維持しながら、要素の追加ができるようにする。
 */
class UtSorter<T>(val list:MutableList<T>, val allowDuplication:Boolean, val comparator:Comparator<T>) {
    init {
        if(list.size>1) {
            list.sortWith(comparator)
        }
    }

    private val pos = Position()

    fun find(element:T):Int {
        return synchronized(this) {
            find(list, comparator, element, pos)
        }
    }

    fun findPosition(element:T, position:Position):Int {
        return synchronized(this) {
            find(list, comparator, element, position)
        }
    }
    fun findPosition(element:T):Position {
        return Position().apply {
            findPosition(element, this)
        }
    }

    fun replace(elements:Collection<T>) {
        val sorted = elements.sortedWith(comparator)
        list.clear()
        list.addAll(sorted)
    }

    fun add(element:T):Int {
        synchronized(this) {
            if (find(list, comparator, element, pos) >= 0 && !allowDuplication) {
                return -1
            }

            return if (pos.next < 0) {
                list.add(element)
                list.size - 1
            } else {
                list.add(pos.next, element)
                pos.next
            }
        }
    }

    fun add(elements: Collection<T>): Boolean {
        for(e in elements) {
            add(e)
        }
        return true
    }


    data class Position(var hit:Int, var prev:Int, var next:Int){
        constructor() : this(-1,-1,-1)
        fun reset() {
            hit=-1
            prev=-1
            next=-1
        }
    }

    companion object {
        fun <T> find(list:List<T>, comparator: Comparator<T>, element: T, result: Position): Int {
            result.reset()

            val count = list.size
            var s = 0
            var e = count - 1
            var m: Int
            if (e < 0) {
                // 要素が空
                return -1
            }

            if (comparator.compare(list[e], element) < 0) {
                // 最後の要素より後ろ
                result.apply {
                    prev = e
                }
                return -1
            }

            while (s <= e) {
                m = (s + e) / 2
                val v = list[m]
                val cmp = comparator.compare(v, element)
                @Suppress("CascadeIf")
                if (cmp == 0) {
                    result.apply {
                        hit = m
                        prev = m - 1
                        if (m < count - 1) {
                            next = m + 1
                        }
                    }
                    return m     // 一致する要素が見つかった
                } else if (cmp < 0) {
                    s = m + 1
                } else {
                    e = m - 1
                }
            }
            result.apply {
                next = s
                prev = s - 1
            }
            return -1
        }
    }


}