package com.bocchi.tv.data.video

import com.bocchi.tv.data.remote.dto.MacCmsClassDto
import com.bocchi.tv.data.video.model.BrowseCategory

/**
 * 分类映射解析器。
 *
 * 功能：把采集源的分类表映射为「统一分类 -> 该源内需要请求的分类 ID 列表」。
 *      多数采集站的顶层分类几乎没有内容（如「电影片」仅十几条，而下属「动作片 / 喜剧片」各有数千条），
 *      因此除顶层分类外还会带上若干子分类一起取样，在请求量与内容量之间取平衡。
 * 参数：无（object 单例）。
 * 返回值：无。
 */
object ClassMappingResolver {

    /** 每个统一分类在每个源上最多请求的分类 ID 数量（含顶层分类本身）。 */
    const val MAX_TYPE_IDS_PER_CATEGORY: Int = 4

    /**
     * 取某统一分类在指定源下的二级分类名列表。
     *
     * 参数：classes —— 采集源返回的分类表；category —— 统一分类。
     * 返回值：List<String> —— 子分类名列表；该源没有对应分类或没有子分类时返回空列表。
     */
    fun buildSubCategories(classes: List<MacCmsClassDto>, category: BrowseCategory): List<String> {
        val matched = findMatchedTopLevel(classes, category) ?: return emptyList()
        return classes
            .filter { it.typePid == matched.typeId && it.typeName.isNotBlank() }
            .map { it.typeName.trim() }
            .distinct()
    }

    /**
     * 按二级分类名查找源内分类 ID。
     *
     * 功能：优先精确匹配子分类名，其次做双向包含匹配（兼容「动作」与「动作片」这类写法差异）。
     * 参数：classes —— 采集源返回的分类表；category —— 统一分类；subCategoryName —— 二级分类名。
     * 返回值：String? —— 源内分类 ID；未匹配到时返回 null。
     */
    fun findTypeIdBySubName(
        classes: List<MacCmsClassDto>,
        category: BrowseCategory,
        subCategoryName: String
    ): String? {
        val matched = findMatchedTopLevel(classes, category) ?: return null
        val children = classes.filter { it.typePid == matched.typeId }
        val target = subCategoryName.trim()
        if (target.isEmpty()) return null
        val hit = children.firstOrNull { it.typeName.trim() == target }
            ?: children.firstOrNull {
                val name = it.typeName.trim()
                name.isNotEmpty() && (name.contains(target) || target.contains(name))
            }
        return hit?.typeId?.takeIf { it.isNotBlank() }
    }

    /**
     * 构建统一分类到源内分类 ID 列表的映射。
     *
     * 功能：优先在顶层分类中按名称匹配；匹配到的分类若带子分类，则返回「顶层 + 前 N-1 个子分类」，
     *      否则只返回该分类自身。
     * 参数：classes —— 采集源返回的分类表。
     * 返回值：Map<BrowseCategory, List<String>> —— 分类 ID 映射。
     */
    fun build(classes: List<MacCmsClassDto>): Map<BrowseCategory, List<String>> {
        if (classes.isEmpty()) return emptyMap()
        return BrowseCategory.entries.mapNotNull { category ->
            val matched = findMatchedTopLevel(classes, category) ?: return@mapNotNull null
            val children = classes
                .filter { it.typePid == matched.typeId && it.typeId.isNotBlank() }
                .map { it.typeId }
            val typeIds = (listOf(matched.typeId) + children)
                .distinct()
                .take(MAX_TYPE_IDS_PER_CATEGORY)
            category to typeIds
        }.toMap()
    }

    /**
     * 查找某统一分类对应的顶层分类节点。
     *
     * 功能：优先在顶层分类（type_pid 为空或 0）中按名称匹配；该源未标注父子关系时退化为全量匹配。
     * 参数：classes —— 采集源返回的分类表；category —— 统一分类。
     * 返回值：MacCmsClassDto? —— 匹配到的分类节点；未匹配到时返回 null。
     */
    private fun findMatchedTopLevel(
        classes: List<MacCmsClassDto>,
        category: BrowseCategory
    ): MacCmsClassDto? {
        if (classes.isEmpty()) return null
        val topLevel = classes
            .filter { it.typePid.isBlank() || it.typePid == "0" }
            .ifEmpty { classes }
        return topLevel.firstOrNull { it.typeId.isNotBlank() && category.matches(it.typeName) }
    }
}
