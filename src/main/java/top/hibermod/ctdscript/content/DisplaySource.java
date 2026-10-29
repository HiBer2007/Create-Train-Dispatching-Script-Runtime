package top.hibermod.ctdscript.content;

import java.util.List;

/**
 * 显示板的**数据源**：给定一块板子的位置，返回它该显示什么。
 *
 * <h2>为什么是「拉」而不是「推」</h2>
 * 推模型要求写入方知道所有板子的坐标，还得自己维护刷新节奏——
 * 车一开动坐标就变，脚本就得满地图追。拉模型下板子自己知道
 * 「我是谁、我在哪、我该看什么」，写数据的人只要提供一份数据。
 *
 * <p>这也解决了「新放出来的板子是空的」：板子只要绑了源，
 * 第一次 tick 就会自己把内容拉下来，不需要任何人记得来写它。
 *
 * <h2>约定</h2>
 * <ul>
 *   <li>必须便宜：每块板子每 20 刻问一次，一个世界里可能有很多块；</li>
 *   <li>抛异常会被吞掉并记一次，不会让方块实体 tick 炸掉整个区块；</li>
 *   <li>返回 {@code null} 或空列表 = 这次没有内容，保留上一次（不闪空）。</li>
 * </ul>
 */
public interface DisplaySource {

    /**
     * @param ctx 板子所在的世界、位置与绑定参数（源 id 里冒号后面那段）
     * @return 要显示的行；{@code null}/空表示本次不更新
     */
    List<String> lines(DisplayContext ctx);

    /** 人读的名字，用于 {@code /ctd display} 自检。 */
    default String describe() {
        return getClass().getSimpleName();
    }
}
