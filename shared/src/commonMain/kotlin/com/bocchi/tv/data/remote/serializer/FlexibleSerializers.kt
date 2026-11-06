package com.bocchi.tv.data.remote.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * 宽松字符串序列化器。
 *
 * 功能：把 JSON 中的字符串、数字、布尔统一读成字符串；null 统一读成空串。
 *      用于兼容各采集站在 vod_id / vod_score / vod_year 等字段上「有时是数字、有时是字符串」的差异。
 * 参数：无（object 单例）。
 * 返回值：KSerializer<String> —— 可直接用于 @file:UseSerializers。
 */
object FlexibleStringSerializer : KSerializer<String> {

    /** 序列化描述符：对外声明为字符串类型。 */
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    /**
     * 反序列化：把任意 JSON 基本类型读成字符串。
     *
     * 参数：decoder —— 当前 JSON 解码器。
     * 返回值：String —— 字段文本；null 或非基本类型返回空串。
     */
    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return ""
        val primitive = element as? JsonPrimitive ?: return ""
        return primitive.content
    }

    /**
     * 序列化：按普通字符串写出。
     *
     * 参数：encoder —— 编码器；value —— 待写出字符串。
     * 返回值：无。
     */
    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

/**
 * 宽松整数序列化器。
 *
 * 功能：把 JSON 中的数字或数字字符串统一读成 Int；无法解析或为 null 时返回 0。
 * 参数：无（object 单例）。
 * 返回值：KSerializer<Int> —— 可直接用于 @file:UseSerializers。
 */
object FlexibleIntSerializer : KSerializer<Int> {

    /** 序列化描述符：对外声明为整数类型。 */
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    /**
     * 反序列化：把数字或数字字符串读成整数。
     *
     * 参数：decoder —— 当前 JSON 解码器。
     * 返回值：Int —— 解析结果；无法解析时返回 0。
     */
    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return 0
        val primitive = element as? JsonPrimitive ?: return 0
        return primitive.content.trim().toIntOrNull() ?: 0
    }

    /**
     * 序列化：按普通整数写出。
     *
     * 参数：encoder —— 编码器；value —— 待写出整数。
     * 返回值：无。
     */
    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}
