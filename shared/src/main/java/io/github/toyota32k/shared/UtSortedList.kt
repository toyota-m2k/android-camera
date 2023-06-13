package io.github.toyota32k.shared

import io.github.toyota32k.utils.UtLog

open class UtSortedList<T>(
    private val innerList:MutableList<T>,
    actionOnDuplicate: UtSorter.ActionOnDuplicate,
    comparator:Comparator<T>,
    )
    : MutableList<T> by innerList {
    constructor(actionOnDuplicate: UtSorter.ActionOnDuplicate, comparator: Comparator<T>):this(mutableListOf(),actionOnDuplicate,comparator)
//    constructor(list:List<T>, actionOnDuplicate: UtSorter.ActionOnDuplicate, comparator: Comparator<T>):this(list.toMutableList(),actionOnDuplicate,comparator)

    val sorter = UtSorter<T>(innerList, actionOnDuplicate, comparator)
    override fun add(element: T): Boolean {
        return sorter.add(element)>=0
    }

//    override fun removeAt(index:Int):T {
//        return innerList.removeAt(index)
//    }

    override fun set(index: Int, element: T): T {
        removeAt(index)
        add(element)
        return element
    }

//    override fun retainAll(elements: Collection<T>): Boolean {
//        return innerList.retainAll(elements)
//    }

    fun replace(value:T):Boolean {
        val index = sorter.find(value)
        if(index<0) return false
        innerList[index] = value
        return true
    }

    // 以下は、innerList のメンバーを呼びだすだけだが、List/MutableCollectionの両方に存在するので委譲がエラーになるから明示的にオーバーライドして逃げる。

//    override fun iterator(): MutableIterator<T> {
//        return innerList.iterator()
//    }

//    override fun removeAll(elements: Collection<T>): Boolean {
//        return innerList.removeAll(elements)
//    }

//    override fun remove(element: T): Boolean {
//        return innerList.remove(element)
//    }

//    override val size: Int
//        get() = innerList.size

//    override fun clear() {
//        innerList.clear()
//    }

    override fun addAll(elements: Collection<T>): Boolean {
        return sorter.add(elements)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        UtLog.libLogger.warn("index=$index will be ignored in UtSortedList")
        return addAll(elements)
    }

    override fun add(index: Int, element: T) {
        UtLog.libLogger.warn("index=$index will be ignored in UtSortedList")
        add(element)
    }

//    override fun contains(element: T): Boolean {
//        return innerList.contains(element)
//    }
//
//    override fun isEmpty(): Boolean {
//        return innerList.isEmpty()
//    }

//    override fun containsAll(elements: Collection<T>): Boolean {
//        return innerList.containsAll(elements)
//    }
}
