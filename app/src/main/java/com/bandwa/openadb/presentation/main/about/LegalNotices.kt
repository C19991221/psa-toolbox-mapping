package com.bandwa.openadb.presentation.main.about

/**
 * 「关于软件」页要展示的法律声明文本集中在这里。
 *
 * 为什么必须留着这些（GPL-3.0 对我们的三条强制要求）：
 * - §5(a)：修改过的作品必须**显著**写明「改过、改了哪一部分、什么时候改的」⇒ [MODIFIED_NOTICE]；
 * - §5(b)：保留原版权声明，并写明整个作品仍以 GPL-3.0 发布 ⇒ [COPYRIGHT] 与页脚版权行；
 * - §5(d)：带交互界面的作品必须展示 Appropriate Legal Notices（版权 / 无担保 / 许可证及查看途径）
 *   ⇒ [LICENSE_SUMMARY] + 关于页的「GPL-3.0 许可证」按钮。
 *
 * 反过来，原版「支持开发组」一段（赞赏二维码 + AI 工具 Logo）**不在** GPL 要求之列：
 * 那是原作者 bandwa 的收款码，我们自己的分发继续挂着它会让打赏错付，也没有任何条款要求保留
 * ⇒ 2026-11-17 整段删除（见交接说明与 docs/版本说明）。
 */
object LegalNotices {

    /** 版权行。原版字面量，按 §5(b)「保留版权声明」原样照抄。 */
    const val COPYRIGHT = "© 2025 bandwa, YC-Tan, ningning21 and fingdo 版权所有"

    /** 致谢。原版字面量，原样照抄（属署名与礼貌，不是 GPL 强制项）。 */
    const val THANKS =
        "特别感谢: 逐梦汴梁城 (甲壳虫等详细教程), 卓群 (无偿提供云盘), 戏子 (tasker方控), " +
            "国新 (拨号调试) 和所有间接参与或测试反馈的车友"

    /**
     * 「本软件做过哪些修改」—— GPL-3.0 §5(a)。
     *
     * 只写客观事实：基于谁的哪个项目、什么时候改的、改了哪些部分、仍以 GPL-3.0 发布。
     */
    const val MODIFIED_NOTICE =
        "本软件在 bandwa 开源的 PSA 工具箱（github.com/band-wa/psa-toolbox）基础上继续开发，" +
            "修改日期 2026-11-17。\n" +
            "主要修改：新增「按键映射」「应用管理」两页；在公共服务的注入载荷中加入按键映射补丁" +
            "（灯杆顶端 / 蓝牙电话 / 方向盘语音 / 空调 / 车辆设置 / 手机互联 / 蓝牙电话钢琴键，" +
            "以及会员中心键与车机设置键）；加入在线更新分发。\n" +
            "本软件同样以 GNU GPL-3.0 发布，完整源码与许可证随本软件一并提供。"

    /**
     * 许可证要点 + 查看全文的途径（点「GPL-3.0 许可证」按钮弹出来）—— §5(d)。
     *
     * 这里放**要点 + 指向**而不是 35 KB 全文：§4 要求的「随作品提供许可证副本」由仓库里的
     * LICENSE 文件承担，界面只负责把「你看得到、也找得到」这件事做到。
     */
    const val LICENSE_SUMMARY =
        "GNU General Public License v3.0（GPL-3.0）\n\n" +
            "本软件是自由软件：你可以自由使用、修改和分发它；修改后的作品必须继续以 GPL-3.0 开源，" +
            "并保留版权与许可证声明。\n\n" +
            "本软件按「现状」提供，不附带任何担保。\n\n" +
            "许可证全文：\n" +
            "github.com/C19991221/psa-toolbox-mapping/blob/main/LICENSE\n" +
            "或 GNU 官方页面：https://www.gnu.org/licenses/gpl-3.0.html"
}
