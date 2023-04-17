package io.github.toyota32k.shared

open class UtSortedList<T>(
    private val innerList:MutableList<T>,
    allowDuplication:Boolean,
    comparator:Comparator<T>,
    )
    : List<T> by innerList, MutableCollection<T> by innerList {
    constructor(allowDuplication: Boolean, comparator: Comparator<T>):this(mutableListOf(),allowDuplication,comparator)

    val sorter = UtSorter<T>(innerList, allowDuplication, comparator)
    override fun addAll(elements: Collection<T>): Boolean {
        return sorter.add(elements)
    }

    override fun add(element: T): Boolean {
        return sorter.add(element)>=0
    }

    fun removeAt(index:Int) {
        innerList.removeAt(index)
    }
    fun replace(value:T):Boolean {
        val index = sorter.find(value)
        if(index<0) return false
        innerList[index] = value
        return true
    }

    // 以下は、innerList のメンバーを呼びだすだけだが、List/MutableCollectionの両方に存在するので委譲がエラーになるから明示的にオーバーライドして逃げる。

    override fun iterator(): MutableIterator<T> {
        return innerList.iterator()
    }

    override val size: Int
        get() = innerList.size

    override fun contains(element: T): Boolean {
        return innerList.contains(element)
    }

    override fun isEmpty(): Boolean {
        return innerList.isEmpty()
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return innerList.containsAll(elements)
    }
}
