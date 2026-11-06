package com.bocchi.tv.data.video

import kotlin.time.TimeSource

/**
 * 简单 LRU 缓存。
 *
 * 功能：固定容量、按最近访问顺序淘汰的线程安全缓存，供「详情 / 聚合结果 / 解析结果」等
 *      纯内存热点数据复用，避免同一份数据反复走多源网络请求。
 *      条目可带过期时间：读取时命中但已过期则视为未命中并顺带移除。
 *      计时使用单调时钟，因此跨平台可用且不受系统时间被修改的影响。
 * 参数：maxEntries —— 最大条目数（超出后淘汰最久未访问的条目）。
 * 返回值：无。
 */
class SimpleLruCache<K : Any, V : Any>(private val maxEntries: Int) {

    /**
     * 缓存条目。
     *
     * 参数：value 数据；expireAtMs 相对单调时钟的过期时刻（0 表示永不过期）。
     * 返回值：无（数据类）。
     */
    private data class Entry<V>(val value: V, val expireAtMs: Long)

    /** 单调时钟起点，用于计算相对过期时刻。 */
    private val origin = TimeSource.Monotonic.markNow()

    /**
     * 底层存储。
     *
     * 说明：`accessOrder = true` 让每次读取都会把条目移到队尾，`removeEldestEntry` 据此淘汰队首，
     *      即「最久未使用」的条目。
     */
    private val store = object : LinkedHashMap<K, Entry<V>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
            size > maxEntries
    }

    /**
     * 读取缓存。
     *
     * 功能：命中且未过期时返回数据；命中但已过期时移除并返回 null。
     * 参数：key —— 缓存键。
     * 返回值：V? —— 缓存数据；未命中或已过期返回 null。
     */
    fun get(key: K): V? = synchronized(store) {
        val entry = store[key] ?: return null
        if (entry.expireAtMs in 1..nowMs()) {
            store.remove(key)
            return null
        }
        return entry.value
    }

    /**
     * 写入缓存。
     *
     * 参数：key —— 缓存键；value —— 数据；ttlMs —— 存活时长（毫秒，传 0 表示永不过期）。
     * 返回值：无。
     */
    fun put(key: K, value: V, ttlMs: Long = 0L) {
        val expireAt = if (ttlMs > 0L) nowMs() + ttlMs else 0L
        synchronized(store) { store[key] = Entry(value, expireAt) }
    }

    /**
     * 清空缓存。
     *
     * 参数：无。
     * 返回值：无。
     */
    fun clear() {
        synchronized(store) { store.clear() }
    }

    /**
     * 读取单调时钟毫秒数。
     *
     * 参数：无。
     * 返回值：Long —— 自缓存创建以来经过的毫秒数。
     */
    private fun nowMs(): Long = origin.elapsedNow().inWholeMilliseconds
}
