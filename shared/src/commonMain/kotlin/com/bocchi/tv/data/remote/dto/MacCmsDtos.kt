@file:UseSerializers(FlexibleStringSerializer::class, FlexibleIntSerializer::class)

package com.bocchi.tv.data.remote.dto

import com.bocchi.tv.data.remote.serializer.FlexibleIntSerializer
import com.bocchi.tv.data.remote.serializer.FlexibleStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * 苹果CMS V10 采集接口通用响应（列表与详情共用外层结构）。
 *
 * 功能：承载 code / msg / 分页信息与影片数组；字段全部带默认值，缺失或类型异常时不会导致解析失败。
 * 参数：code 状态码（1 表示成功）；msg 提示信息；page 当前页；pageCount 总页数；
 *      total 总条数；list 影片数组；classes 分类表。
 * 返回值：无（DTO）。
 */
@Serializable
data class MacCmsVodResponseDto(
    val code: Int = 0,
    val msg: String = "",
    val page: Int = 1,
    @SerialName("pagecount") val pageCount: Int = 0,
    val total: Int = 0,
    val list: List<MacCmsVodDto> = emptyList(),
    @SerialName("class") val classes: List<MacCmsClassDto> = emptyList()
)

/**
 * 苹果CMS V10 分类条目。
 *
 * 功能：描述数据源的分类树节点；`typePid` 为空或为 0 表示顶层分类。
 * 参数：typeId 分类 ID；typePid 父级分类 ID；typeName 分类名称。
 * 返回值：无（DTO）。
 */
@Serializable
data class MacCmsClassDto(
    @SerialName("type_id") val typeId: String = "",
    @SerialName("type_pid") val typePid: String = "",
    @SerialName("type_name") val typeName: String = ""
)

/**
 * 苹果CMS V10 影片条目。
 *
 * 功能：映射采集接口返回的影片字段；列表接口不返回播放信息，详情接口（ac=detail）才包含播放字段。
 * 参数：见各字段注释。
 * 返回值：无（DTO）。
 */
@Serializable
data class MacCmsVodDto(
    /** 影片 ID（同一源内唯一）。 */
    @SerialName("vod_id") val vodId: String = "",

    /** 片名。 */
    @SerialName("vod_name") val vodName: String = "",

    /** 竖版海报地址。 */
    @SerialName("vod_pic") val vodPic: String = "",

    /** 备注，如「已完结」「HD」。 */
    @SerialName("vod_remarks") val vodRemarks: String = "",

    /** 年份。 */
    @SerialName("vod_year") val vodYear: String = "",

    /** 地区。 */
    @SerialName("vod_area") val vodArea: String = "",

    /** 语言。 */
    @SerialName("vod_lang") val vodLang: String = "",

    /** 主演。 */
    @SerialName("vod_actor") val vodActor: String = "",

    /** 导演。 */
    @SerialName("vod_director") val vodDirector: String = "",

    /** 评分。 */
    @SerialName("vod_score") val vodScore: String = "",

    /** 剧情简介（含 HTML 标签，展示前需清洗）。 */
    @SerialName("vod_content") val vodContent: String = "",

    /** 更新时间。 */
    @SerialName("vod_time") val vodTime: String = "",

    /** 分类 ID。 */
    @SerialName("type_id") val typeId: String = "",

    /** 分类名称。 */
    @SerialName("type_name") val typeName: String = "",

    /** 播放线路名，多条线路以 `$$$` 分隔。 */
    @SerialName("vod_play_from") val vodPlayFrom: String = "",

    /** 播放地址，格式为 `集数$地址`，多集以 `#` 分隔，多线路与 vod_play_from 按顺序对应。 */
    @SerialName("vod_play_url") val vodPlayUrl: String = ""
)
