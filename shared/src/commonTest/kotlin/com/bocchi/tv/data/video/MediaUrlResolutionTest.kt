package com.bocchi.tv.data.video

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 播放地址解析的回归测试。
 *
 * 功能：用采集源真实返回的分享页片段验证相对路径补全结果，确保不会出现
 *      `https://host:` 这类把端口拼成空的非法地址。
 * 参数：无（测试类）。
 * 返回值：无。
 */
class MediaUrlResolutionTest {

    /** 构造只做解析、不发请求的解析器。 */
    private fun resolver() = MediaUrlResolver(HttpClient())

    /**
     * 验证分享页里的相对 m3u8 路径能被补全为完整地址。
     *
     * 参数：无。
     * 返回值：无（断言失败即抛异常）。
     */
    @Test
    fun resolveRelativeMediaFromSharePage() {
        val html = """
            <script>
            var redirecturl = "http://vip.okzybo.com";
            var main = "/20260328/3536_03ebf6e4/index.m3u8?sign=2c8479748b6e206b0b9b03807360487e";
            </script>
        """.trimIndent()
        val base = "https://v.lfthirtytwo.com/share/291d43c696d8c3704cdbe0a72ade5f6c"

        val resolved = resolver().extractMediaUrl(html, base)

        assertEquals(
            "https://v.lfthirtytwo.com/20260328/3536_03ebf6e4/index.m3u8?sign=2c8479748b6e206b0b9b03807360487e",
            resolved
        )
    }

    /**
     * 验证补全结果不会包含空端口（`https://host:`）。
     *
     * 参数：无。
     * 返回值：无（断言失败即抛异常）。
     */
    @Test
    fun absoluteUrlNeverContainsEmptyPort() {
        val html = """<script>var url = "./20250923/abc/index.m3u8";</script>"""
        val base = "https://play.hhuus.com/play/xxx.html"

        val resolved = resolver().extractMediaUrl(html, base)

        assertEquals("https://play.hhuus.com/play/20250923/abc/index.m3u8", resolved)
    }
}
