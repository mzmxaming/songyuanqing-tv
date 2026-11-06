package com.bocchi.tv.data.video

import java.util.concurrent.locks.ReentrantLock

/**
 * JVM 端互斥锁实现。
 *
 * 功能：包装 ReentrantLock，提供与 iOS 端一致的 lock / unlock 语义。
 * 参数：无。
 * 返回值：无。
 */
internal actual class PlatformLock actual constructor() {

    /** 底层可重入锁。 */
    private val delegate = ReentrantLock()

    /** 加锁。 */
    actual fun lock() {
        delegate.lock()
    }

    /** 解锁。 */
    actual fun unlock() {
        delegate.unlock()
    }
}
