package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 开局两本书(m122):每个玩家首次进入(每人仅一次)发两本成书——
 *   ①《永夜·缘起》:世界观/剧情介绍(给新出生的玩家看背景)。
 *   ②《幸存者手册》:玩法介绍(怎么变强、六职业怎么玩、别怎么苟、战利品规则等)。
 *
 * 实现照 {@link StartingKitHandler} 的范式:JOIN 事件 + 持久附件 GOT_WELCOME_BOOKS 防重发(死亡保留)。
 * 书用原版 written_book 物品 + WRITTEN_BOOK_CONTENT 组件(1.21.1 数据组件方式)构造,纯成品书,玩家右键即可翻阅。
 */
public final class WelcomeBookHandler {
    private WelcomeBookHandler() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.giveWelcomeBooks) return;
            if (p.getAttachedOrElse(ModAttachments.GOT_WELCOME_BOOKS, false)) return; // 每人只发一次

            // 先发剧情书、再发手册(背包里手册在上、剧情在下,先看手册也行)
            p.giveItemStack(buildLoreBook());
            p.giveItemStack(buildGuideBook());
            p.setAttached(ModAttachments.GOT_WELCOME_BOOKS, true);
        });
        Yongye.LOGGER.info("[永夜] 开局两本书系统已挂载");
    }

    // ====================== 成书构造工具 ======================

    /**
     * 构造一页:首行标题(暗红粗体)+ 空行 + 正文(默认黑字)。
     * 父节点用空样式,两个子节点各自带样式(兄弟之间不相互继承),所以标题红、正文黑互不串色。
     */
    private static RawFilteredPair<Text> page(String title, String body) {
        MutableText heading = Text.literal(title + "\n\n").formatted(Formatting.DARK_RED, Formatting.BOLD);
        MutableText full = Text.empty().append(heading).append(Text.literal(body));
        return new RawFilteredPair<>(full, Optional.<Text>empty());
    }

    /** 用书名 + 页列表组出一本成品 written_book。 */
    private static ItemStack writtenBook(String title, List<RawFilteredPair<Text>> pages) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        WrittenBookContentComponent content = new WrittenBookContentComponent(
                new RawFilteredPair<>(title, Optional.<String>empty()), // 书名
                "永夜",                                                  // 作者
                0,                                                       // generation:0=原作
                pages,                                                   // 页内容
                true                                                     // resolved:纯文本已就绪,无需再解析选择器
        );
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    // ====================== 书①《永夜·缘起》(剧情) ======================

    private static ItemStack buildLoreBook() {
        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        pages.add(page("永夜·缘起",
                "太阳沉下地平线后,就再也没有升起来。星辰熄了,只剩一轮会渗血的红盘挂在天上。被吞掉的不只是光,是「白昼」这件事本身。从那一刻起,世界进入了永夜。"));
        pages.add(page("脉动黑暗",
                "这片黑暗不是空的——它有脉搏。夜深时把手贴在地上,能感到一种像心跳一样的搏动从地底传上来。黑暗不再是「没有光」,它是一种活着的、有意志的东西。"));
        pages.add(page("世界换了主人",
                "黑暗渗进每一只生物的骨血。温顺的走兽长出獠牙,零散的怪物开始结群、进化、思考。从前一刀解决的骷髅,如今血厚数倍、出手凶狠。世界没有毁灭,只是换了主人。"));
        pages.add(page("永夜会加深",
                "永夜不是固定的,它会一寸寸往下沉。每深一层,天更黑、视野更窄、怪更强、红月更频繁。幸存者按深浅分了几层:灾变、灭世、深渊——最后,是没人回来过的终焉。"));
        pages.add(page("赎夜",
                "摧毁黑暗的节点、完成那些近乎自杀的试炼,能把永夜逼退一层——天会亮回去一点,怪会弱回去一点。但黑暗有耐心,它从不真正退场。赎夜买的是时间,不是黎明。"));
        pages.add(page("长门 · 佩恩",
                "走在「下沉」最前面的,是一个降临者。传说它曾是第一个在永夜里没死、却也没活下来的人——黑暗没杀他,而是把他变成了自己的喉舌。哪里燃起反抗的火,那股脉动就靠近哪里。"));
        pages.add(page("黑暗恨强者",
                "这是永夜最核心的法则:黑暗不怕弱者,它吃弱者;它怕的、恨的,是强到敢反抗的人。所以你越强,永夜越盯着你。这不是难度数值,是世界对你的恶意。"));
        pages.add(page("三条规矩",
                "一、它缝死你的伤口,让你回不了血。二、它当面扒走你的武器盔甲,穿着你的东西耀武扬威。三、它不让你逃、不让你躲——躲水里、垒高塔、封进盒子,它都有办法把你逼出来。"));
        pages.add(page("六大本命",
                "黑暗灌进身体的瞬间,极少数人体内有什么反过来咬住了黑暗。他们醒来时,多了一条只属于自己的、对抗永夜的路——本命。共六途,决定你以什么姿态站在这片夜里。"));
        pages.add(page("本命 · 其一",
                "铁壁,以身为墙,越站定护盾越厚。巨阙,纯粹的怒火,受伤越重战意越炽。噬魂杖,以命为薪,焚烧自己的生命去引动灵火。"));
        pages.add(page("本命 · 其二",
                "流光,把抽走的光淬成剑,每次命中都积起剑气。武僧,什么武器都不要,生吞世界的造物越吃越壮。影刺,与影子讲和,借黑暗自己的工具反割黑暗的喉。"));
        pages.add(page("两种余烬",
                "世界破碎时散下两种物质。生命系——旧世界的余烬,拾起汇拢能把流失的生命夺回身上。永夜系——深渊的凝结,本身有毒;可想锻造对抗黑暗的力量,你就得亲手握住黑暗。用夜打夜。"));
        pages.add(page("你的处境",
                "没有人向你许诺过黎明。你会变厚、会变强,明知每多一分力量,黑暗就多盯你一眼。永夜问你的从来不是「能不能赢」,而是——在这片再也不会天亮的夜里,你能站多久,又以什么姿态站着。"));
        return writtenBook("永夜·缘起", pages);
    }

    // ====================== 书②《幸存者手册》(玩法) ======================

    private static ItemStack buildGuideBook() {
        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        pages.add(page("幸存者手册",
                "这本册子记着前人用命换来的经验。读完它,未必能让你活下来——但至少,你会死得明白一点。"));
        pages.add(page("先让自己变厚",
                "打怪会掉「生命碎片」。十枚碎片凝一枚结晶,层层向上(结晶→核心→灾变血核)。开背包用「兑换」按钮合并,或直接强化进身上。你身上每一点多出的血,都是从黑暗嘴边抢来的。"));
        pages.add(page("选你的本命",
                "出生时会让你选一条本命。铁壁站着扛、巨阙越痛越猛、噬魂杖耗血放法术。法师别拿杖去砍——按住右键蓄力吟唱,松手放魔法弹,蓄得越久伤害越高(满蓄力可达攻击力数倍)。"));
        pages.add(page("本命 · 续",
                "流光攒剑气、影刺借暗能潜杀。武僧不用武器:右键直接「吃」生命碎片、结晶这些材料,越吃拳头越痛、命越厚——它是单独一套成长,空手就是它的兵器。"));
        pages.add(page("第二本命",
                "练到一定等级,可再学一门本命,最多容两条(多了灵魂承不住)。若已满两条,再右键新职业书会弹界面,让你任选丢弃旧的一条换上新的。被丢的天赋点不退还,想清楚再换。"));
        pages.add(page("技能书",
                "攻击、护甲、回复、闪避、荆棘……八种属性技能书,吃下永久加成。后期攒一堆懒得一本本吃?开背包点「学书」,一键学完背包里所有技能书。"));
        pages.add(page("强化与守护",
                "开背包点「强化」,选一件装备,用背包里全部材料一键升级。怕被精英扒走?用「守护附魔书」——右键它弹界面,点要保护的装备(武器/盔甲/盾都行),从此那件夺不走。"));
        pages.add(page("神器",
                "十种背包神器,放饰品栏即生效:守护书钉住装备、不灭余烬替你烧掉一次死亡。各有奇效,凑齐了能在永夜里多撑很久。"));
        pages.add(page("夜不许你躲(一)",
                "永夜最恨懦弱地苟着,立下了规矩。泡在水里赖着不出来?水底的守护者会浮上来咬你。垒一座孤塔站顶上放冷箭?天上的幻翼会俯冲下来把你掀翻。"));
        pages.add(page("夜不许你躲(二)",
                "在头顶盖满方块把自己封进盒子?末影人会一块块拆你的墙,夜会重新灌进来。而只要你龟缩太久,黑暗就直接抽干你的血,逼你出来面对。站着面对,或者死。"));
        pages.add(page("战利品",
                "精英怪必掉一份保底:10 枚生命碎片、1 枚结晶、1 本随机技能书。地上属于本族的造物会自动飞进你背包,不必弯腰捡;但杂物太多会被定时清理扫掉,别囤垃圾。"));
        pages.add(page("清理与天赋",
                "地上掉落物每隔一段会全服清理一次,清理前有倒计时,听到就赶紧捡走想要的。等级攒下的天赋点,开背包点「天赋」加点;点满了还有「精通」节点能一直吸。"));
        pages.add(page("你能撑多久",
                "幸存者之间没有排名,只有一个数字被记下——你撑了多久。永夜不会天亮。握紧武器,变厚,逼退它一层,再被它追上。它问的只有一件事:你,能站多久。"));
        return writtenBook("幸存者手册", pages);
    }
}
