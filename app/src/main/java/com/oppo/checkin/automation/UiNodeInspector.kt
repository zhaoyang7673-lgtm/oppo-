package com.oppo.checkin.automation

import com.oppo.checkin.data.UiLabelConfig

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
    val isScrollable: Boolean = false,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisibleToUser: Boolean = true,
    val boundsLeft: Int = 0,
    val boundsTop: Int = 0,
    val boundsRight: Int = 0,
    val boundsBottom: Int = 0,
    val children: List<UiNodeSnapshot> = emptyList()
)

data class MatchedNode(
    val clickableNodeId: Int,
    val matchedLabel: String
)

enum class CheckInButtonStatus {
    NOT_CHECKED_IN,
    ALREADY_CHECKED_IN,
    UNKNOWN
}

data class CheckInButtonInspection(
    val cardFound: Boolean,
    val contextAnchorFound: String?,
    val buttonStatus: CheckInButtonStatus,
    val buttonText: String?,
    val clickableNodeId: Int?,
    val insideVerifiedCard: Boolean
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

    private val DISMISS_POPUP_LABELS = listOf("跳过", "关闭", "我知道了", "以后再说", "暂不", "取消")

    fun detectUnexpectedBlocker(root: UiNodeSnapshot): String? {
        val visibleTexts = collectAllLabels(root, requireVisibleFlag = false)
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
     * Finds a safe dismiss button ("跳过", "关闭", "我知道了") if a splash ad or home promo dialog
     * is covering the OPPO Mall home screen.
     */
    fun findSafePopupDismiss(root: UiNodeSnapshot): MatchedNode? {
        val pathList = mutableListOf<List<UiNodeSnapshot>>()
        collectPaths(root, emptyList(), pathList)

        for (target in DISMISS_POPUP_LABELS) {
            for (path in pathList) {
                val node = path.last()
                if (!node.isEnabled) continue
                val ownText = node.text?.trim()
                val ownDesc = node.contentDescription?.trim()
                if (ownText.equals(target, ignoreCase = true) ||
                    ownDesc.equals(target, ignoreCase = true) ||
                    (target == "跳过" && ownText?.startsWith("跳过") == true && ownText.length <= 6)
                ) {
                    val clickable = findClickableInAncestors(path, maxParentHops = 3) ?: node
                    return MatchedNode(clickable.nodeId, target)
                }
            }
        }
        return null
    }

    /**
     * Part 2: Precise Membership tab detection inside the bottom navigation / Dock.
     * Supports:
     * 1. Exact text/contentDescription match ("会员") anywhere in the tree (ignoring false `isVisibleToUser=false`
     *    on custom OPPO tab views).
     * 2. Contains match ("会员" / "会员中心") inside bottom dock containers.
     * 3. Structural 5-tab bottom dock fallback (首页 | 选购 | 会员 | 服务 | 我的 -> index 2) when OPPO Mall
     *    renders custom tab icons without exposing text strings on the middle tab.
     */
    fun findBottomDockTab(root: UiNodeSnapshot, config: UiLabelConfig): MatchedNode? {
        val pathList = mutableListOf<List<UiNodeSnapshot>>()
        collectPaths(root, emptyList(), pathList)

        val targetLabels = config.membershipLabels.map { it.trim() }.filter { it.isNotEmpty() }
        val dockSiblings = config.dockSiblingLabels.map { it.trim() }.filter { it.isNotEmpty() }

        val dockCandidates = mutableListOf<Pair<MatchedNode, Int>>()

        // Pass 1: Exact or contains match on text / contentDescription / viewIdResourceName
        for (path in pathList) {
            val node = path.last()
            if (!node.isEnabled) continue
            val nodeText = node.text?.trim()
            val nodeDesc = node.contentDescription?.trim()

            val matchedLabel = targetLabels.firstOrNull { target ->
                nodeText?.equals(target, ignoreCase = true) == true ||
                    nodeDesc?.equals(target, ignoreCase = true) == true ||
                    (nodeText != null && nodeText.contains(target) && nodeText.length <= 5) ||
                    (nodeDesc != null && nodeDesc.contains(target) && nodeDesc.length <= 5)
            } ?: continue

            val clickable = findClickableInAncestors(path, maxParentHops = 6) ?: node

            var dockScore = if (node.isVisibleToUser) 1 else 0
            if (nodeText?.equals(matchedLabel, ignoreCase = true) == true ||
                nodeDesc?.equals(matchedLabel, ignoreCase = true) == true
            ) {
                dockScore += 2
            }

            for (ancestorIdx in (path.lastIndex - 1) downTo (path.lastIndex - 6).coerceAtLeast(0)) {
                val ancestor = path[ancestorIdx]
                val ancestorLabels = collectAllLabels(ancestor, requireVisibleFlag = false)
                val siblingHits = dockSiblings.count { sibling ->
                    ancestorLabels.any { it.contains(sibling, ignoreCase = true) }
                }
                if (siblingHits >= 3) {
                    dockScore += 5
                    break
                } else if (siblingHits >= 2) {
                    dockScore += 3
                }
                val resId = ancestor.viewIdResourceName?.lowercase().orEmpty()
                if (resId.contains("bottom") || resId.contains("dock") || resId.contains("tab") || resId.contains("nav")) {
                    dockScore += 3
                }
            }

            dockCandidates.add(MatchedNode(clickable.nodeId, matchedLabel) to dockScore)
        }

        if (dockCandidates.isNotEmpty()) {
            return dockCandidates.maxByOrNull { it.second }?.first
        }

        // Pass 2: Structural 5-tab bottom navigation container fallback (首页, 选购, [会员], 服务, 我的)
        for (path in pathList) {
            val container = path.last()
            if (container.children.size == 5) {
                val resId = container.viewIdResourceName?.lowercase().orEmpty()
                val containerLabels = collectAllLabels(container, requireVisibleFlag = false)
                val siblingHits = dockSiblings.count { s -> containerLabels.any { it.contains(s) } }
                val isBottomBar = siblingHits >= 2 ||
                    resId.contains("bottom") ||
                    resId.contains("tab") ||
                    resId.contains("nav") ||
                    resId.contains("dock")
                if (isBottomBar) {
                    val middleTab = container.children[2]
                    if (middleTab.isEnabled) {
                        return MatchedNode(middleTab.nodeId, "会员")
                    }
                }
            }
        }

        return null
    }

    /**
     * Part 3: Confidence-based Membership page verification.
     */
    fun verifyMembershipPage(root: UiNodeSnapshot, config: UiLabelConfig): String? {
        val visible = collectAllLabels(root, requireVisibleFlag = false)
        val strongAnchor = config.membershipPageAnchors.firstOrNull { anchor ->
            visible.any { it.contains(anchor.trim(), ignoreCase = true) }
        }
        val hasMyPoints = config.pointsPageLabels.any { label ->
            visible.any { it.contains(label.trim(), ignoreCase = true) }
        }
        if (strongAnchor != null) return strongAnchor
        if (hasMyPoints) return "我的积分"
        return null
    }

    /**
     * Part 4: Precise "My Points" entry detection on the Membership page.
     * Never uses numeric point balances (e.g., 280).
     */
    fun findMyPointsEntry(root: UiNodeSnapshot, config: UiLabelConfig): MatchedNode? {
        val cleanLabels = config.pointsPageLabels.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanLabels.isEmpty()) return null

        val pathList = mutableListOf<List<UiNodeSnapshot>>()
        collectPaths(root, emptyList(), pathList)

        for (target in cleanLabels) {
            for (path in pathList) {
                val node = path.last()
                if (!node.isEnabled) continue
                val text = node.text?.trim()?.removeSuffix(">")?.trim()
                val desc = node.contentDescription?.trim()?.removeSuffix(">")?.trim()
                val isMatch = text?.equals(target, ignoreCase = true) == true ||
                    desc?.equals(target, ignoreCase = true) == true ||
                    text?.contains(target, ignoreCase = true) == true ||
                    desc?.contains(target, ignoreCase = true) == true
                if (isMatch) {
                    val clickable = findClickableInAncestors(path, maxParentHops = 6) ?: node
                    return MatchedNode(clickable.nodeId, target)
                }
            }
        }
        return null
    }

    /**
     * Part 5: Points Mall page verification using stable textual anchors ("积分商城" or check-in card).
     */
    fun verifyPointsPage(root: UiNodeSnapshot, config: UiLabelConfig): String? {
        val visible = collectAllLabels(root, requireVisibleFlag = false)
        val mallAnchor = config.pointsMallAnchors.firstOrNull { anchor ->
            visible.any { it.contains(anchor.trim(), ignoreCase = true) }
        }
        if (mallAnchor != null) return mallAnchor

        val cardAnchor = config.checkinCardAnchors.firstOrNull { anchor ->
            visible.any { it.contains(anchor.trim(), ignoreCase = true) }
        }
        return cardAnchor
    }

    /**
     * Parts 6, 7, 8, 9: Authoritative daily check-in card & button hierarchy validation.
     * - Never performs a global search for "已签到".
     * - Never treats "已签到 11 天" as today's check-in state.
     * - Rejects "去参与" and unrelated promo/product cards.
     * - Returns UNKNOWN whenever the actual button cannot be confidently identified.
     */
    fun inspectDailyCheckInCard(
        root: UiNodeSnapshot,
        config: UiLabelConfig
    ): CheckInButtonInspection {
        val pathList = mutableListOf<List<UiNodeSnapshot>>()
        collectPaths(root, emptyList(), pathList)

        var cardSubtree: UiNodeSnapshot? = null
        var foundAnchorText: String? = null

        for (anchor in config.checkinCardAnchors) {
            val trimmedAnchor = anchor.trim()
            if (trimmedAnchor.isEmpty()) continue
            val anchorPath = pathList.firstOrNull { path ->
                val node = path.last()
                node.text?.contains(trimmedAnchor, ignoreCase = true) == true ||
                    node.contentDescription?.contains(trimmedAnchor, ignoreCase = true) == true
            }
            if (anchorPath != null) {
                foundAnchorText = trimmedAnchor
                val startIdx = (anchorPath.lastIndex - 1).coerceAtLeast(0)
                val minIdx = (anchorPath.lastIndex - 7).coerceAtLeast(0)
                var chosenCard: UiNodeSnapshot = anchorPath[startIdx]
                for (idx in startIdx downTo minIdx) {
                    val candidateCard = anchorPath[idx]
                    if (containsExactButtonText(candidateCard, config)) {
                        chosenCard = candidateCard
                        break
                    }
                }
                cardSubtree = chosenCard
                break
            }
        }

        if (cardSubtree == null) {
            return CheckInButtonInspection(
                cardFound = false,
                contextAnchorFound = null,
                buttonStatus = CheckInButtonStatus.UNKNOWN,
                buttonText = null,
                clickableNodeId = null,
                insideVerifiedCard = false
            )
        }

        val cardPaths = mutableListOf<List<UiNodeSnapshot>>()
        collectPaths(cardSubtree, emptyList(), cardPaths)

        val checkinExactLabels = config.checkinButtonLabels.map { it.trim() }.filter { it.isNotEmpty() }
        val alreadyExactLabels = config.alreadyCheckedInLabels.map { it.trim() }.filter { it.isNotEmpty() }

        for (path in cardPaths) {
            val node = path.last()
            if (!node.isVisibleToUser) continue
            if (isExcludedContext(path, config.excludedActionLabels)) continue

            val ownText = node.text?.trim()
            val ownDesc = node.contentDescription?.trim()
            val matchedAlready = alreadyExactLabels.firstOrNull { label ->
                ownText.equals(label, ignoreCase = true) || ownDesc.equals(label, ignoreCase = true)
            }
            if (matchedAlready != null) {
                return CheckInButtonInspection(
                    cardFound = true,
                    contextAnchorFound = foundAnchorText,
                    buttonStatus = CheckInButtonStatus.ALREADY_CHECKED_IN,
                    buttonText = ownText ?: ownDesc ?: matchedAlready,
                    clickableNodeId = null,
                    insideVerifiedCard = true
                )
            }
        }

        for (path in cardPaths) {
            val node = path.last()
            if (!node.isVisibleToUser || !node.isEnabled) continue
            if (isExcludedContext(path, config.excludedActionLabels)) continue

            val ownText = node.text?.trim()
            val ownDesc = node.contentDescription?.trim()
            val matchedCheckin = checkinExactLabels.firstOrNull { label ->
                ownText.equals(label, ignoreCase = true) || ownDesc.equals(label, ignoreCase = true)
            }
            if (matchedCheckin != null) {
                val clickable = findClickableInAncestors(path, maxParentHops = 5) ?: node
                if (!nodeSubtreeContainsExcluded(clickable, config.excludedActionLabels)) {
                    return CheckInButtonInspection(
                        cardFound = true,
                        contextAnchorFound = foundAnchorText,
                        buttonStatus = CheckInButtonStatus.NOT_CHECKED_IN,
                        buttonText = ownText ?: ownDesc ?: matchedCheckin,
                        clickableNodeId = clickable.nodeId,
                        insideVerifiedCard = true
                    )
                }
            }
        }

        return CheckInButtonInspection(
            cardFound = true,
            contextAnchorFound = foundAnchorText,
            buttonStatus = CheckInButtonStatus.UNKNOWN,
            buttonText = null,
            clickableNodeId = null,
            insideVerifiedCard = false
        )
    }

    fun findScrollableNodeId(root: UiNodeSnapshot): Int? {
        var found: Int? = null
        fun walk(node: UiNodeSnapshot) {
            if (found != null) return
            if (node.isScrollable && node.isEnabled) {
                found = node.nodeId
                return
            }
            node.children.forEach { walk(it) }
        }
        walk(root)
        return found
    }

    fun findMatchingLabel(root: UiNodeSnapshot, labels: List<String>): String? {
        val visible = collectAllLabels(root, requireVisibleFlag = false)
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

    fun sampleTopVisibleLabels(root: UiNodeSnapshot, limit: Int = 8): String {
        val labels = collectAllLabels(root, requireVisibleFlag = false).distinct().take(limit)
        return if (labels.isEmpty()) "无可见文本节点" else labels.joinToString(" / ")
    }

    private fun containsExactButtonText(subtree: UiNodeSnapshot, config: UiLabelConfig): Boolean {
        val targets = (config.checkinButtonLabels + config.alreadyCheckedInLabels)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var found = false
        fun walk(node: UiNodeSnapshot) {
            if (found) return
            val ownText = node.text?.trim()
            val ownDesc = node.contentDescription?.trim()
            if (targets.any { ownText.equals(it, ignoreCase = true) || ownDesc.equals(it, ignoreCase = true) }) {
                found = true
                return
            }
            node.children.forEach { walk(it) }
        }
        walk(subtree)
        return found
    }

    private fun isExcludedContext(path: List<UiNodeSnapshot>, excludedLabels: List<String>): Boolean {
        val node = path.last()
        val ownText = node.text?.trim().orEmpty()
        val ownDesc = node.contentDescription?.trim().orEmpty()
        if (excludedLabels.any { ownText.contains(it, ignoreCase = true) || ownDesc.contains(it, ignoreCase = true) }) {
            return true
        }
        val parent = path.getOrNull(path.lastIndex - 1) ?: return false
        val parentText = parent.text?.trim().orEmpty()
        if (excludedLabels.any { parentText.contains(it, ignoreCase = true) }) {
            return true
        }
        return false
    }

    private fun nodeSubtreeContainsExcluded(node: UiNodeSnapshot, excludedLabels: List<String>): Boolean {
        val labels = collectAllLabels(node, requireVisibleFlag = false)
        return excludedLabels.any { ex -> labels.any { it.contains(ex, ignoreCase = true) } }
    }

    private fun findClickableInAncestors(
        path: List<UiNodeSnapshot>,
        maxParentHops: Int
    ): UiNodeSnapshot? {
        val lastIndex = path.lastIndex
        val minIndex = (lastIndex - maxParentHops).coerceAtLeast(0)
        for (i in lastIndex downTo minIndex) {
            val candidate = path[i]
            if (candidate.isEnabled && candidate.isClickable) {
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

    private fun collectAllLabels(
        root: UiNodeSnapshot,
        requireVisibleFlag: Boolean
    ): List<String> {
        val result = mutableListOf<String>()
        fun traverse(node: UiNodeSnapshot) {
            if (!requireVisibleFlag || node.isVisibleToUser) {
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
