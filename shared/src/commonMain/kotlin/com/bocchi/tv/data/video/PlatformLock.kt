package com.bocchi.tv.data.video

/**
 * 跨平台互斥锁。
 *
 * 功能：为共享层的内存缓存提供线程安全保护；JVM 端用可重入锁，iOS 端用 NSRecursiveLock。
 * 参数：无。
 * 返回值：无（expect 声明）。
 */
internal expect class PlatformLock() {

    /** 加锁。 */
    fun lock()

    /** 解锁。 */
    fun unlock()
}

/**
 * 加锁执行代码块。
 *
 * 参数：block —— 需要互斥执行的逻辑。
 * 返回值：T —— block 的返回值。
 */
internal inline fun <T> PlatformLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
