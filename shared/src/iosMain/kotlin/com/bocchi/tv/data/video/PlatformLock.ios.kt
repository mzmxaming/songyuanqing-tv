package com.bocchi.tv.data.video

import platform.Foundation.NSRecursiveLock

/**
 * iOS 端互斥锁实现。
 *
 * 功能：包装 NSRecursiveLock；递归锁可避免同一线程在回调中再次进入缓存时死锁。
 * 参数：无。
 * 返回值：无。
 */
internal actual class PlatformLock actual constructor() {

    /** 底层递归锁。 */
    private val delegate = NSRecursiveLock()

    /** 加锁。 */
    actual fun lock() {
        delegate.lock()
    }

    /** 解锁。 */
    actual fun unlock() {
        delegate.unlock()
    }
}
