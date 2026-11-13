package com.oppo.checkin.automation

/**
 * Lightweight, testable snapshot of a visible UI node from the target application.
 * Never persisted or logged in full.
 */
data class UiNodeSnapshot(
    val nodeId: Int,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val isClickable: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisibleToUser: Boolean = true,
    val children: List<UiNodeSnapshot> = emptyList()
)

data class MatchedNode(
    val clickableNodeId: Int,
    val matchedLabel: String
)

object UiNodeInspector {

    private val LOGIN_KEYWORDS = listOf("密码登录", "账号登录", "立即登录", "请先登录", "一键登录", "手机号登录")
    private val CAPTCHA_KEYWORDS = listOf("验证码", "滑块验证", "图形验证", "安全验证", "人机验证", "拖动滑块")
    private val SMS_KEYWORDS = listOf("短信验证", "获取验证码", "输入验证码")
    private val BIOMETRIC_KEYWORDS = listOf("指纹验证", "面容识别", "人脸识别", "刷脸验证")
    private val NETWORK_KEYWORDS = listOf("网络异常", "无网络连接", "网络不可用", "加载失败", "网络连接失败")
    private val UPDATE_KEYWORDS = listOf("发现新版本", "立即更新", "版本更新", "立即升级")
    private val RESTRICTION_KEYWORDS = listOf("账号异常", "账号受限", "存在风险", "已被限制")
    private val PERMISSION_KEYWORDS = listOf("始终允许", "仅在使用中允许", "禁止后不再询问")

    /**
     * Detects unexpected blocking screens or dialogs (login, CAPTCHA, SMS, biometric,
     * network error, app update, account restriction, permission prompt).
     */
    fun detectUnexpectedBlocker(root: UiNodeSnapshot): String? {
        val visibleTexts = collectVisibleLabels(root)
        for (label in visibleTexts) {
            when {
                CAPTCHA_KEYWORDS.any { label.contains(it) } -> return "检测到安全验证或验证码弹窗"
                SMS_KEYWORDS.any { label.contains(it) } -> return "检测到短信验证界面"
                BIOMETRIC_KEYWORDS.any { label.contains(it) } -> return "检测到生物识别验证"
                LOGIN_KEYWORDS.any { label.contains(it) } -> return "检测到登录界面，需要手动登录"
                RESTRICTION_KEYWORDS.any { label.contains(it) } -> return "检测到账号状态异常或受限提示"
                UPDATE_KEYWORDS.any { label.contains(it) } -> return "检测到应用更新弹窗"
                NETWORK_KEYWORDS.any { label.contains(it) } -> return "检测到网络异常或加载失败"
                PERMISSION_KEYWORDS.any { label.contains(it) } -> return "检测到系统权限弹窗"
            }
        }
        return null
    }

    /**
     * Checks if any visible enabled node matches one of the target labels.
     * Prefers exact match, then substring match.
     */
    fun findMatchingLabel(root: UiNodeSnapshot, labels: List<String>): String? {
        val visible = collectVisibleLabels(root)
        for (target in labels) {
            val trimmed = target.trim()
            if (trimmed.isEmpty()) continue
            if (visible.any { it.equals(trimmed, ignoreCase = true) }) {
                return trimmed
            }
        }
        for (target in labels) {
            val trimmed = target.trim()
            if (trimmed.isEmpty()) continue
            if (visible.any { it.contains(trimmed, ignoreCase = true) }) {
                return trimmed
            }
        }
        return null
    }

    /**
     * Finds a visible, enabled, clickable node matching any of the given labels.
     * Prefers exact text/contentDescription match over substring match.
     * If the matching text node itself is not clickable, inspects up to [maxParentHops]
     * ancestor nodes for an enabled, visible, clickable container.
     */
    fun findClickableTarget(
        root: UiNodeSnapshot,
        labels: List<String>,
        maxParentHops: Int = 4
    ): MatchedNode? {
        val cleanLabels = labels.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanLabels.isEmpty()) return null

        val pathList = mutableListOf<List<UiNodeSnapshot>>()
        collectPaths(root, emptyList(), pathList)

        // Pass 1: exact match on text or contentDescription
        for (target in cleanLabels) {
            for (path in pathList) {
                val node = path.last()
                if (!node.isVisibleToUser || !node.isEnabled) continue
                val textMatch = node.text?.trim()?.equals(target, ignoreCase = true) == true
                val descMatch = node.contentDescription?.trim()?.equals(target, ignoreCase = true) == true
                if (textMatch || descMatch) {
                    val clickable = findClickableInAncestors(path, maxParentHops)
                    if (clickable != null) {
                        return MatchedNode(clickable.nodeId, target)
                    }
                }
            }
        }

        // Pass 2: contains match on text or contentDescription
        for (target in cleanLabels) {
            for (path in pathList) {
                val node = path.last()
                if (!node.isVisibleToUser || !node.isEnabled) continue
                val textMatch = node.text?.contains(target, ignoreCase = true) == true
                val descMatch = node.contentDescription?.contains(target, ignoreCase = true) == true
                if (textMatch || descMatch) {
                    val clickable = findClickableInAncestors(path, maxParentHops)
                    if (clickable != null) {
                        return MatchedNode(clickable.nodeId, target)
                    }
                }
            }
        }

        return null
    }

    private fun findClickableInAncestors(
        path: List<UiNodeSnapshot>,
        maxParentHops: Int
    ): UiNodeSnapshot? {
        val lastIndex = path.lastIndex
        val minIndex = (lastIndex - maxParentHops).coerceAtLeast(0)
        for (i in lastIndex downTo minIndex) {
            val candidate = path[i]
            if (candidate.isVisibleToUser && candidate.isEnabled && candidate.isClickable) {
                return candidate
            }
        }
        return null
    }

    private fun collectPaths(
        node: UiNodeSnapshot,
        prefix: List<UiNodeSnapshot>,
        out: MutableList<List<UiNodeSnapshot>>
    ) {
        val currentPath = prefix + node
        out.add(currentPath)
        for (child in node.children) {
            collectPaths(child, currentPath, out)
        }
    }

    private fun collectVisibleLabels(root: UiNodeSnapshot): List<String> {
        val result = mutableListOf<String>()
        fun traverse(node: UiNodeSnapshot) {
            if (node.isVisibleToUser) {
                node.text?.trim()?.takeIf { it.isNotEmpty() }?.let { result.add(it) }
                node.contentDescription?.trim()?.takeIf { it.isNotEmpty() }?.let { result.add(it) }
            }
            for (child in node.children) {
                traverse(child)
            }
        }
        traverse(root)
        return result
    }
}
