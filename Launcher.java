import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第二街区服务器专属游戏启动器 —— UI 框架最小可执行版本。
 * 运行：javac -encoding UTF-8 Launcher.java && java Launcher
 * 目录：Game/ 整合包、data/ 配置、res/ 素材。
 */
public class Launcher {

    // 程序根目录：EXE 模式为 exe 所在目录；开发模式为当前工作目录
    static final Path HOME = resolveHome();

    private static Path resolveHome() {
        try {
            Path src = Paths.get(Launcher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(src)) {           // 以 jar 形式运行（EXE 内嵌 jar）
                Path home = src.getParent();          // jpackage 布局：<应用目录>/app/Launcher.jar
                if (home != null && "app".equalsIgnoreCase(String.valueOf(home.getFileName()))) {
                    home = home.getParent();
                }
                if (home != null) {
                    return home;
                }
            }
        } catch (Exception ignored) {
        }
        return Paths.get("").toAbsolutePath();
    }

    // 解析 Game/、data/ 等程序目录下的路径，避免 CWD 漂移
    static Path appPath(String first, String... more) {
        return HOME.resolve(Paths.get(first, more));
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--pack".equals(args[0])) {
            packGameNow();
            return;
        }
        SwingUtilities.invokeLater(LauncherWindow::new);
    }

    // 打包工具：java Launcher --pack 将 Game/ 打成 game.pack
    private static void packGameNow() {
        Path game = appPath("Game");
        if (!Files.isDirectory(game)) {
            System.out.println("未找到 Game/ 目录");
            return;
        }
        System.out.println("正在将 Game/ 打包为 game.pack …");
        long start = System.currentTimeMillis();
        try {
            GamePack.pack(game, GamePack.PACK, (done, total) -> {
                if (done % 500 == 0 || done == total) {
                    System.out.printf("\r%d/%d (%.1f%%)", done, total, done * 100.0 / total);
                }
            });
            System.out.printf("%n完成：耗时 %ds，game.pack 大小 %d MB%n",
                    (System.currentTimeMillis() - start) / 1000, Files.size(GamePack.PACK) / 1024 / 1024);
        } catch (IOException e) {
            System.out.println("\n打包失败：" + e.getMessage());
        }
    }

    // ================================================= 主题
    static final class Theme {
        static final Color BG = new Color(0x0A0B0D);
        static final Color NAV_BG = new Color(0x0B0C0E);
        static final Color LINE = new Color(0x23262A);
        static final Color TEXT = new Color(0xEDEFF2);
        static final Color MUTED = new Color(0x9AA1A9);
        static final Color ACCENT = new Color(0xF3B03D);
        static final Color ACCENT_HOVER = new Color(0xFFC65C);
        static final Color ACCENT_PRESS = new Color(0xD5962C);
        static final Color ON_ACCENT = new Color(0x17130B);
        static final String FONT = pickFont();

        static Font font(int style, int size) {
            return new Font(FONT, style, size);
        }

        private static String pickFont() {
            String[] prefer = {"Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
                    "Noto Sans CJK SC", "Source Han Sans SC"};
            List<String> available = new ArrayList<>();
            for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                available.add(name);
            }
            for (String name : prefer) {
                if (available.contains(name)) {
                    return name;
                }
            }
            return Font.SANS_SERIF;
        }
    }

    // ================================================= 配置
    static final class Config {
        private final Properties props;

        private Config(Properties props) {
            this.props = props;
        }

        String get(String key, String def) {
            String value = props.getProperty(key);
            return value == null || value.isBlank() ? def : value.trim();
        }

        void set(String key, String value) {
            if (value == null || value.isBlank()) {
                props.remove(key);
            } else {
                props.setProperty(key, value.trim());
            }
        }

        void clearAll() {
            props.clear();
        }

        void save() {
            Path file = appPath("data", "launcher.properties");
            try {
                Files.createDirectories(file.getParent());
                try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                    props.store(writer, "第二街区启动器配置");
                }
            } catch (IOException e) {
                System.err.println("保存 data/launcher.properties 失败：" + e.getMessage());
            }
        }

        static Config load() {
            Properties props = new Properties();
            Path file = appPath("data", "launcher.properties");
            try {
                if (Files.isRegularFile(file)) {
                    try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                        props.load(reader);
                    }
                } else {
                    Files.createDirectories(file.getParent());
                    props.setProperty("launcher.name", "第二街区");
                    props.setProperty("launcher.version", "1.0.0");
                    props.setProperty("server.version", "1.21.11");
                    props.setProperty("server.slogan", "街区、商铺与夜灯都已就位，输入你的游戏ID即可进入第二街区的世界。");
                    try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                        props.store(writer, "第二街区启动器配置");
                    }
                }
            } catch (IOException e) {
                System.err.println("读取 data/launcher.properties 失败：" + e.getMessage());
            }
            return new Config(props);
        }
    }

    // ================================================= 主窗口
    static final class LauncherWindow extends JFrame {
        private final CardLayout cards = new CardLayout();
        private final JPanel pageHost = new JPanel(cards);
        private final NavBar nav;
        private final Config cfg;
        private final StatsPage statsPage;
        private final GamePage gamePage;
        private final ServicePage servicePage;

        LauncherWindow() {
            super("第二街区 · 游戏启动器");
            cfg = Config.load();

            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(1000, 620));
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            setSize(Math.min(1280, screen.width - 120), Math.min(780, screen.height - 140));
            setLocationRelativeTo(null);
            getContentPane().setBackground(Theme.BG);
            setLayout(new BorderLayout());

            nav = new NavBar(this::showPage, this::onAvatarClick);

            pageHost.setBackground(Theme.BG);
            gamePage = new GamePage(cfg);
            pageHost.add(gamePage, "game");
            statsPage = new StatsPage(cfg, this);
            pageHost.add(statsPage, "stats");
            servicePage = new ServicePage(cfg, this);
            pageHost.add(servicePage, "service");
            pageHost.add(new SettingsPage(cfg, gamePage::isRunning), "settings");

            add(nav, BorderLayout.NORTH);
            add(pageHost, BorderLayout.CENTER);

            applyAvatarState();
            refreshAvatarSkin();
            showPage("game");
            setVisible(true);
            maybeInstallGame();
        }

        // 游戏文件缺失时，从 game.pack 自动解压安装
        private void maybeInstallGame() {
            if (!GamePack.needsInstall()) {
                return;
            }
            if (!Files.isRegularFile(GamePack.PACK)) {
                JOptionPane.showMessageDialog(this,
                        "未检测到游戏文件，且启动器目录下缺少安装包 game.pack。\n请将 game.pack 放到启动器目录后重启。",
                        "缺少游戏文件", JOptionPane.WARNING_MESSAGE);
                return;
            }
            new InstallDialog(this).setVisible(true);
        }

        private void showPage(String key) {
            cards.show(pageHost, key);
            nav.setActive(key);
            if ("stats".equals(key)) {
                statsPage.reload();
            }
            if ("service".equals(key)) {
                servicePage.reload();
            }
        }

        private void applyAvatarState() {
            String name = cfg.get("secblk.name", "");
            nav.setAvatarState(name.isEmpty() ? null : name);
        }

        private void onAvatarClick(Component anchor) {
            if (cfg.get("secblk.token", "").isEmpty()) {
                SecblkLoginDialog dialog = new SecblkLoginDialog(this, cfg);
                dialog.setVisible(true);
                if (dialog.loggedIn()) {
                    applyAvatarState();
                    refreshAvatarSkin();
                    statsPage.reload();
                    servicePage.reload();
                }
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            JMenuItem who = new JMenuItem(cfg.get("secblk.name", "已登录"));
            who.setEnabled(false);
            JMenuItem logout = new JMenuItem("退出官网账号");
            logout.addActionListener(e -> {
                cfg.set("secblk.token", "");
                cfg.set("secblk.name", "");
                cfg.save();
                applyAvatarState();
                nav.setAvatarImage(null);
                statsPage.reload();
                servicePage.reload();
            });
            menu.add(who);
            menu.add(logout);
            menu.show(anchor, 0, anchor.getHeight() + 6);
        }

        void setAvatarSkin(BufferedImage image) {
            if (image != null) {
                nav.setAvatarImage(image);
            }
        }

        void onSecblkSessionExpired() {
            applyAvatarState();
            nav.setAvatarImage(null);
        }

        // 启动/登录后后台拉取角色皮肤头像，同步到右上角头像
        private void refreshAvatarSkin() {
            String token = cfg.get("secblk.token", "");
            if (token.isEmpty()) {
                return;
            }
            new Thread(() -> {
                try {
                    Map<String, Object> list = SecblkApi.gameAccounts(token);
                    List<Object> accounts = Json.list(list.get("accounts"));
                    if (accounts.isEmpty()) {
                        return;
                    }
                    Map<String, Object> first = Json.obj(accounts.get(0));
                    Map<String, Object> d = SecblkApi.gameAccountDetails(token, Json.text(first, "id"));
                    BufferedImage head = SecblkApi.fetchSkinHead(Json.text(d, "skin", "textureUrl"));
                    if (head != null) {
                        SwingUtilities.invokeLater(() -> nav.setAvatarImage(head));
                    }
                } catch (IOException ignored) {
                    // 头像加载失败不影响启动
                }
            }, "avatar-skin").start();
        }
    }

    // ================================================= 顶部导航栏
    static final class NavBar extends JPanel {
        static final int HEIGHT = 68;
        private final TabButton[] tabs;
        private final Avatar avatar = new Avatar();

        NavBar(Consumer<String> onSelect, Consumer<Component> onAvatar) {
            setOpaque(true);
            setBackground(Theme.NAV_BG);
            setLayout(null);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
            setPreferredSize(new Dimension(10, HEIGHT));

            tabs = new TabButton[]{
                    new TabButton("游戏", "game"),
                    new TabButton("统计", "stats"),
                    new TabButton("服务", "service"),
                    new TabButton("设置", "settings")};
            for (TabButton tab : tabs) {
                tab.addActionListener(e -> onSelect.accept(tab.key));
                add(tab);
            }
            avatar.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onAvatar.accept(avatar);
                }
            });
            add(avatar);
        }

        void setAvatarState(String name) {
            avatar.setActive(name != null);
            avatar.setToolTipText(name == null ? "登录第二街区官网账号" : name + "（点击管理）");
        }

        void setAvatarImage(BufferedImage image) {
            avatar.setImage(image);
        }

        void setActive(String key) {
            for (TabButton tab : tabs) {
                tab.setActive(tab.key.equals(key));
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int h = getHeight();
            int boxX = 32;
            int box = 30;
            int boxY = (h - box) / 2;
            g.setColor(Theme.ACCENT);
            g.fillRoundRect(boxX, boxY, box, box, 8, 8);
            g.setColor(Theme.ON_ACCENT);
            g.setFont(Theme.font(Font.BOLD, 15));
            FontMetrics markFm = g.getFontMetrics();
            String mark = "Ⅱ";
            g.drawString(mark, boxX + (box - markFm.stringWidth(mark)) / 2,
                    boxY + (box - markFm.getHeight()) / 2 + markFm.getAscent());

            int tx = boxX + box + 12;
            g.setFont(Theme.font(Font.BOLD, 16));
            FontMetrics fm = g.getFontMetrics();
            int blockH = fm.getHeight() + 13;
            int top = (h - blockH) / 2;
            int base = top + fm.getAscent();
            g.setColor(Theme.TEXT);
            g.drawString("第二街区", tx, base);
            g.setFont(Theme.font(Font.PLAIN, 9));
            g.setColor(Theme.MUTED);
            g.drawString("S E C O N D   B L O C K", tx + 1, base + 13);
            g.dispose();
        }

        @Override
        public void doLayout() {
            int h = getHeight();
            int x = 200;
            for (TabButton tab : tabs) {
                Dimension d = tab.getPreferredSize();
                tab.setBounds(x, 0, d.width, h);
                x += d.width + 6;
            }
            int av = 44;
            avatar.setBounds(getWidth() - 32 - av, (h - av) / 2, av, av);
        }
    }

    static final class TabButton extends JButton {
        final String key;
        private boolean active;

        TabButton(String text, String key) {
            super(text);
            this.key = key;
            setFont(Theme.font(Font.PLAIN, 14));
            setForeground(Theme.MUTED);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(0, 14, 0, 14));
        }

        void setActive(boolean active) {
            this.active = active;
            setFont(Theme.font(active ? Font.BOLD : Font.PLAIN, 14));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            boolean hover = getModel().isRollover();
            setForeground(active ? Theme.TEXT : (hover ? new Color(0xD6D9DE) : Theme.MUTED));
            Graphics2D g = (Graphics2D) g0.create();
            super.paintComponent(g);
            if (active) {
                int width = g.getFontMetrics().stringWidth(getText()) + 8;
                g.setColor(Theme.ACCENT);
                g.fillRoundRect((getWidth() - width) / 2, getHeight() - 15, width, 3, 3, 3);
            }
            g.dispose();
        }
    }

    static final class Avatar extends JComponent {
        private boolean active;
        private BufferedImage image;

        Avatar() {
            setToolTipText("登录第二街区官网账号");
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - s) / 2;
            int y = (getHeight() - s) / 2;

            g.setColor(new Color(0x191B1F));
            g.fillOval(x, y, s, s);

            if (image != null) {
                Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                Shape clip = new Ellipse2D.Double(x, y, s, s);
                g.clip(clip);
                g.drawImage(image, x, y, s, s, null);
                g.setClip(null);
                if (interp != null) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
                }
            } else {
                Shape clip = new Ellipse2D.Double(x, y, s, s);
                g.clip(clip);
                g.setColor(new Color(0x878D96));
                int head = (int) (s * 0.26);
                g.fillOval(x + (s - head) / 2, y + (int) (s * 0.24), head, head);
                int sw = (int) (s * 0.62);
                int sh = (int) (s * 0.44);
                g.fillRoundRect(x + (s - sw) / 2, y + (int) (s * 0.58), sw, sh, sw, sw);
                g.setClip(null);
            }

            g.setColor(active ? Theme.ACCENT : new Color(255, 255, 255, 40));
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(x, y, s - 1, s - 1);
            g.dispose();
        }
    }

    // ================================================= 通用按钮与图标
    static final class FlatButton extends JButton {
        private final Color fill;
        private final Color hoverFill;
        private final Color pressFill;
        private final Color borderColor;
        private final int radius;

        private FlatButton(String text, Color fill, Color hoverFill, Color pressFill,
                           Color fg, Color borderColor, int radius) {
            super(text);
            this.fill = fill;
            this.hoverFill = hoverFill;
            this.pressFill = pressFill;
            this.borderColor = borderColor;
            this.radius = radius;
            setForeground(fg);
            setFont(Theme.font(Font.PLAIN, 13));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setIconTextGap(8);
            setBorder(new EmptyBorder(0, 16, 0, 16));
        }

        static FlatButton ghost(String text, Icon icon) {
            FlatButton button = new FlatButton(text,
                    new Color(255, 255, 255, 16), new Color(255, 255, 255, 42),
                    new Color(255, 255, 255, 8), Theme.TEXT, new Color(255, 255, 255, 46), 8);
            button.setIcon(icon);
            return button;
        }

        static FlatButton primary(String text, Icon icon) {
            FlatButton button = new FlatButton(text,
                    Theme.ACCENT, Theme.ACCENT_HOVER, Theme.ACCENT_PRESS, Theme.ON_ACCENT, null, 10);
            button.setFont(Theme.font(Font.BOLD, 16));
            button.setIcon(icon);
            button.setIconTextGap(10);
            button.setBorder(new EmptyBorder(0, 18, 0, 22));
            return button;
        }

        static FlatButton danger(String text, Icon icon) {
            FlatButton button = new FlatButton(text,
                    new Color(0x5A2A2E), new Color(0x70363B), new Color(0x472124),
                    new Color(0xF0B9BE), new Color(0xE06C75), 8);
            button.setIcon(icon);
            return button;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = getModel().isPressed() ? pressFill : (getModel().isRollover() ? hoverFill : fill);
            g.setColor(bg);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius * 2, radius * 2);
            if (borderColor != null) {
                g.setColor(borderColor);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius * 2, radius * 2);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    static final class VectorIcon implements Icon {
        private final String kind;
        private final Color color;
        private final int size;

        VectorIcon(String kind, Color color, int size) {
            this.kind = kind;
            this.color = color;
            this.size = size;
        }

        private int p(double value) {
            return (int) Math.round(value * size / 16.0);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.4f));
            switch (kind) {
                case "globe" -> {
                    g.drawOval(p(1.5), p(1.5), p(13), p(13));
                    g.drawLine(p(1.5), p(8), p(14.5), p(8));
                    g.draw(new Ellipse2D.Double(p(5), p(1.5), p(6), p(13)));
                }
                case "chat" -> {
                    g.draw(new RoundRectangle2D.Double(p(1.5), p(2.5), p(13), p(9.5), p(3), p(3)));
                    g.drawPolygon(new int[]{p(4.5), p(9), p(4.5)},
                            new int[]{p(12), p(12), p(15)}, 3);
                }
                case "link" -> {
                    g.draw(new RoundRectangle2D.Double(p(6), p(2), p(8.5), p(6), p(3), p(3)));
                    g.draw(new RoundRectangle2D.Double(p(1.5), p(8), p(8.5), p(6), p(3), p(3)));
                }
                case "log" -> {
                    g.draw(new RoundRectangle2D.Double(p(3), p(1.5), p(10), p(13), p(2), p(2)));
                    g.drawLine(p(5.5), p(5), p(10.5), p(5));
                    g.drawLine(p(5.5), p(8), p(10.5), p(8));
                    g.drawLine(p(5.5), p(11), p(9), p(11));
                }
                case "camera" -> {
                    g.draw(new RoundRectangle2D.Double(p(1.5), p(4), p(13), p(9), p(2.5), p(2.5)));
                    g.draw(new Ellipse2D.Double(p(5.5), p(6), p(5), p(5)));
                    g.drawLine(p(5), p(4), p(6.5), p(2));
                }
                case "user" -> {
                    g.drawOval(p(5.5), p(2), p(5), p(5));
                    g.drawArc(p(2.5), p(8.5), p(11), p(9), 0, 180);
                }
                case "play" -> g.fillPolygon(new int[]{p(5), p(13), p(5)},
                        new int[]{p(3), p(8), p(13)}, 3);
                default -> {
                }
            }
            g.dispose();
        }
    }

    // ================================================= 游戏主页面
    static final class GamePage extends JPanel {
        private final Config cfg;
        private final JLabel kicker;
        private final JLabel title;
        private final JLabel tagline;
        private final FlatButton siteButton;
        private final FlatButton qqButton;
        private final FlatButton kookButton;
        private final FlatButton logButton;
        private final FlatButton bodycamButton;
        private final FlatButton startButton;
        private final FlatButton accountButton;
        private final JLabel accountLabel;
        private final JLabel statusLabel;
        private Account account;
        private Process process;

        boolean isRunning() {
            return process != null && process.isAlive();
        }

        GamePage(Config cfg) {
            this.cfg = cfg;
            setOpaque(true);
            setBackground(Theme.BG);
            setLayout(null);

            kicker = label("MINECRAFT " + cfg.get("server.version", "1.21.11") + "　·　SURVIVAL SERVER",
                    Theme.ACCENT, Font.BOLD, 12);
            title = label("第二街区", Theme.TEXT, Font.BOLD, 46);
            tagline = label("<html><body style='width:520px;color:#9AA1A9'>"
                    + cfg.get("server.slogan", "街区、商铺与夜灯都已就位，输入你的游戏ID即可进入第二街区的世界。")
                    + "</body></html>", Theme.MUTED, Font.PLAIN, 14);

            siteButton = FlatButton.ghost("访问官网", new VectorIcon("globe", Theme.TEXT, 16));
            qqButton = FlatButton.ghost("QQ社群", new VectorIcon("chat", Theme.TEXT, 16));
            kookButton = FlatButton.ghost("KOOK频道", new VectorIcon("link", Theme.TEXT, 16));
            logButton = FlatButton.ghost("查看游戏日志", new VectorIcon("log", Theme.TEXT, 16));
            bodycamButton = FlatButton.ghost("查看Bodycam录像目录", new VectorIcon("camera", Theme.TEXT, 16));
            startButton = FlatButton.primary("启动游戏", new VectorIcon("play", Theme.ON_ACCENT, 16));
            accountButton = FlatButton.ghost("切换账号", new VectorIcon("user", Theme.TEXT, 16));
            accountLabel = label("", Theme.MUTED, Font.PLAIN, 12);
            accountLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            statusLabel = label("", Theme.ACCENT, Font.PLAIN, 11);
            statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            add(siteButton);
            add(qqButton);
            add(kookButton);
            add(logButton);
            add(bodycamButton);
            add(startButton);
            add(accountButton);
            add(accountLabel);
            add(statusLabel);
            add(kicker);
            add(title);
            add(tagline);

            account = loadAccount();
            refreshAccountLabel();

            accountButton.addActionListener(e -> openAccountDialog());
            startButton.addActionListener(e -> startGame());
            logButton.addActionListener(e -> openGameLog());
            bodycamButton.addActionListener(e -> openBodycamFolder());
            siteButton.addActionListener(e -> openLink("https://secblk.com/"));
            qqButton.addActionListener(e -> copyQQGroup());
            kookButton.addActionListener(e -> openLink("https://www.kookapp.cn/app/channels/9754356030552272"));
        }

        private void openLink(String url) {
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ex) {
                setStatus("无法打开浏览器，请手动访问：" + url);
            }
        }

        private void copyQQGroup() {
            StringSelection selection = new StringSelection("1013247828");
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            setStatus("已复制QQ群号：1013247828");
        }

        private Account loadAccount() {
            String offline = cfg.get("offline.username", "");
            if (offline.matches("^[A-Za-z0-9_]{3,16}$")) {
                return Account.offline(offline);
            }
            return null;
        }

        private void refreshAccountLabel() {
            if (account == null) {
                accountLabel.setText("尚未登录");
                accountLabel.setForeground(Theme.MUTED);
            } else {
                accountLabel.setText(account.name);
                accountLabel.setForeground(Theme.TEXT);
            }
        }

        private void setStatus(String text) {
            statusLabel.setText(text);
        }

        private void openAccountDialog() {
            Window owner = SwingUtilities.getWindowAncestor(this);
            AccountDialog dialog = new AccountDialog(owner, cfg);
            dialog.setVisible(true);
            Account result = dialog.result();
            if (result != null) {
                account = result;
                refreshAccountLabel();
                setStatus("");
            }
        }

        private void startGame() {
            if (process != null && process.isAlive()) {
                setStatus("游戏已在运行");
                return;
            }
            if (account == null) {
                openAccountDialog();
                if (account == null) {
                    return;
                }
            }
            launchWith(account);
        }

        private void launchWith(Account selected) {
            Launch launch;
            try {
                launch = Launch.build(cfg, selected);
            } catch (IOException ex) {
                setStatus("启动失败");
                JOptionPane.showMessageDialog(this, ex.getMessage(), "无法启动游戏", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                Path instance = appPath("Game", "Secblk-第二街区");
                Path logDir = instance.resolve("logs");
                Files.createDirectories(logDir);
                ProcessBuilder builder = new ProcessBuilder(launch.command);
                builder.directory(launch.workingDir);
                builder.redirectErrorStream(true);
                builder.redirectOutput(logDir.resolve("launcher-start.log").toFile());
                process = builder.start();
            } catch (IOException ex) {
                setStatus("启动失败");
                JOptionPane.showMessageDialog(this, "创建游戏进程失败：" + ex.getMessage(),
                        "无法启动游戏", JOptionPane.ERROR_MESSAGE);
                return;
            }
            startButton.setEnabled(false);
            setStatus("游戏已启动，正在加载…");
            Process running = process;
            new Thread(() -> {
                int code = -1;
                try {
                    code = running.waitFor();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                int exit = code;
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    if (exit == 0) {
                        setStatus("游戏已退出");
                    } else {
                        setStatus("游戏异常退出（代码 " + exit + "），请查看日志");
                    }
                });
            }, "game-watch").start();
        }

        private void openGameLog() {
            Path latest = appPath("Game", "Secblk-第二街区", "logs", "latest.log");
            Path fallback = appPath("Game", "Secblk-第二街区", "logs", "launcher-start.log");
            if (Files.isRegularFile(latest)) {
                openFile(latest);
            } else if (Files.isRegularFile(fallback)) {
                openFile(fallback);
            } else {
                JOptionPane.showMessageDialog(this, "还没有游戏日志，请先启动一次游戏。",
                        "游戏日志", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        private void openBodycamFolder() {
            Path dir = appPath("Game", "Secblk-第二街区", "camplus_recordings");
            try {
                Files.createDirectories(dir);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "无法创建录像目录：" + ex.getMessage(),
                        "打开目录", JOptionPane.ERROR_MESSAGE);
                return;
            }
            openFile(dir);
        }

        private void openFile(Path path) {
            try {
                if (!Desktop.isDesktopSupported()) {
                    throw new IOException("当前系统不支持自动打开文件");
                }
                Desktop.getDesktop().open(path.toFile());
            } catch (IOException | RuntimeException ex) {
                JOptionPane.showMessageDialog(this, "无法打开：" + path.toAbsolutePath()
                        + "\n" + ex.getMessage(), "打开失败", JOptionPane.ERROR_MESSAGE);
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth();
            int h = getHeight();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (HERO != null) {
                drawCover(g, HERO, w, h);
            } else {
                g.setPaint(new GradientPaint(0, 0, new Color(0x161A1F), w, h, new Color(0x08090B)));
                g.fillRect(0, 0, w, h);
            }
            g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 150), (float) w * 0.62f, 0,
                    new Color(0, 0, 0, 20)));
            g.fillRect(0, 0, w, h);
            g.setPaint(new GradientPaint(0, h - 280, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 190)));
            g.fillRect(0, h - 280, w, 280);
            g.dispose();
        }

        @Override
        public void doLayout() {
            int w = getWidth();
            int h = getHeight();
            int pad = 32;

            int railW = 176;
            int railH = 40;
            int railGap = 10;
            int railTotal = railH * 3 + railGap * 2;
            int railY = (h - railTotal) / 2 + 40;
            siteButton.setBounds(pad, railY, railW, railH);
            qqButton.setBounds(pad, railY + railH + railGap, railW, railH);
            kookButton.setBounds(pad, railY + (railH + railGap) * 2, railW, railH);

            int textX = pad + railW + 36;
            int textW = w - textX - pad;
            int bottom = h - pad;

            logButton.setBounds(pad, bottom - 44, 156, 44);
            bodycamButton.setBounds(pad + 156 + 12, bottom - 44, 248, 44);

            int startW = 216;
            int startH = 54;
            startButton.setBounds(w - pad - startW, bottom - startH, startW, startH);

            int accountRowW = 420;
            int accountRowH = 34;
            int accountX = w - pad - accountRowW;
            int rowY = bottom - startH - 12 - accountRowH;
            accountLabel.setBounds(accountX, rowY + (accountRowH - 18) / 2, accountRowW - 136, 18);
            accountButton.setBounds(w - pad - 124, rowY, 124, accountRowH);
            statusLabel.setBounds(accountX, rowY - 22, accountRowW, 18);

            int textBottom = rowY - 26;
            tagline.setBounds(textX, textBottom - 44, textW, 44);
            title.setBounds(textX, textBottom - 44 - 10 - 62, textW, 62);
            kicker.setBounds(textX, textBottom - 44 - 10 - 62 - 8 - 22, textW, 22);
        }
    }

    // ================================================= 账号
    static final class Account {
        final String name;
        final String uuid;
        final String token;
        final String userType;

        private Account(String name, String uuid, String token, String userType) {
            this.name = name;
            this.uuid = uuid;
            this.token = token;
            this.userType = userType;
        }

        static Account offline(String name) {
            UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return new Account(name, id.toString(), "0", "legacy");
        }
    }

    // ================================================= 极简 JSON 解析
    static final class Json {
        private final String text;
        private int pos;

        private Json(String text) {
            this.text = text;
        }

        static Object parse(String text) throws IOException {
            if (text == null || text.isBlank()) {
                return Map.of();
            }
            Json parser = new Json(text);
            Object value = parser.value();
            return value == null ? Map.of() : value;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> obj(Object value) {
            return value instanceof Map ? (Map<String, Object>) value : Map.of();
        }

        @SuppressWarnings("unchecked")
        static List<Object> list(Object value) {
            return value instanceof List ? (List<Object>) value : List.of();
        }

        static Object get(Object node, Object... keys) {
            Object current = node;
            for (Object key : keys) {
                if (key instanceof Number number) {
                    List<Object> items = list(current);
                    int index = number.intValue();
                    current = index >= 0 && index < items.size() ? items.get(index) : null;
                } else if (current instanceof Map<?, ?> map) {
                    current = map.get(String.valueOf(key));
                } else {
                    return null;
                }
            }
            return current;
        }

        static String text(Object node, Object... keys) {
            Object value = get(node, keys);
            return value == null ? "" : String.valueOf(value);
        }

        private Object value() throws IOException {
            skip();
            if (pos >= text.length()) {
                throw new IOException("JSON 内容不完整");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skip();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skip();
                String key = string();
                skip();
                expect(':');
                map.put(key, value());
                skip();
                char c = text.charAt(pos++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IOException("JSON 对象格式错误");
                }
            }
        }

        private List<Object> array() throws IOException {
            List<Object> list = new ArrayList<>();
            pos++;
            skip();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skip();
                char c = text.charAt(pos++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IOException("JSON 数组格式错误");
                }
            }
        }

        private String string() throws IOException {
            skip();
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case '"', '\\', '/' -> sb.append(escaped);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IOException("JSON 字符串转义错误");
                }
            }
            throw new IOException("JSON 字符串未闭合");
        }

        private Object number() throws IOException {
            int start = pos;
            while (pos < text.length() && "-+.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            String raw = text.substring(start, pos);
            try {
                if (raw.matches("-?\\d+")) {
                    return Long.parseLong(raw);
                }
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                throw new IOException("JSON 数字格式错误");
            }
        }

        private Object literal(String word, Object value) throws IOException {
            if (!text.startsWith(word, pos)) {
                throw new IOException("JSON 字面量格式错误");
            }
            pos += word.length();
            return value;
        }

        private char peek() throws IOException {
            if (pos >= text.length()) {
                throw new IOException("JSON 内容不完整");
            }
            return text.charAt(pos);
        }

        private void expect(char c) throws IOException {
            if (pos >= text.length() || text.charAt(pos) != c) {
                throw new IOException("JSON 缺少字符 " + c);
            }
            pos++;
        }

        private void skip() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
    }

    // ================================================= 启动命令
    static final class Launch {
        final List<String> command;
        final File workingDir;

        private Launch(List<String> command, File workingDir) {
            this.command = command;
            this.workingDir = workingDir;
        }

        static Launch build(Config cfg, Account account) throws IOException {
            Path game = appPath("Game");
            Path versionDir = game.resolve("Secblk-第二街区");
            Path jar = versionDir.resolve("Secblk-第二街区.jar");
            Path meta = versionDir.resolve("Secblk-第二街区.json");
            Path libraries = game.resolve("libraries");
            Path assets = game.resolve("assets");
            Path natives = versionDir.resolve("Secblk-第二街区-natives");
            if (!Files.isRegularFile(jar) || !Files.isRegularFile(meta)) {
                throw new IOException("Game 目录缺少游戏核心文件：\n" + versionDir);
            }
            if (!Files.isDirectory(libraries) || !Files.isDirectory(assets)) {
                throw new IOException("Game 目录缺少 libraries 或 assets");
            }
            Map<String, Object> version = Json.obj(Json.parse(Files.readString(meta, StandardCharsets.UTF_8)));
            String mainClass = Json.text(version, "mainClass");
            if (mainClass.isBlank()) {
                throw new IOException("版本 JSON 缺少 mainClass");
            }
            String assetIndex = Json.text(version, "assetIndex", "id");
            if (assetIndex.isBlank()) {
                assetIndex = Json.text(version, "assets");
            }
            if (assetIndex.isBlank()) {
                assetIndex = "29";
            }

            List<String> classpath = new ArrayList<>();
            classpath.add(jar.toString());
            List<String> missing = new ArrayList<>();
            for (Object raw : Json.list(version.get("libraries"))) {
                Map<String, Object> lib = Json.obj(raw);
                if (!allowedOnWindows(lib)) {
                    continue;
                }
                String path = Json.text(lib, "downloads", "artifact", "path");
                if (path.isBlank()) {
                    path = derivedPath(Json.text(lib, "name"));
                }
                if (path.isBlank()) {
                    continue;
                }
                Path file = libraries.resolve(path);
                if (Files.isRegularFile(file)) {
                    classpath.add(file.toString());
                } else {
                    missing.add(path);
                }
            }
            if (!missing.isEmpty()) {
                throw new IOException("缺少 " + missing.size() + " 个依赖库，请检查 Game/libraries：\n"
                        + missing.get(0));
            }

            List<String> cmd = new ArrayList<>();
            cmd.add(javaExecutable(cfg).toString());
            cmd.add("-Xms1G");
            cmd.add("-Xmx" + cfg.get("game.memory", "2G"));
            cmd.add("-Djava.library.path=" + natives);
            cmd.add("-Djna.tmpdir=" + natives);
            cmd.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=" + natives);
            cmd.add("-Dio.netty.native.workdir=" + natives);
            cmd.add("-Dminecraft.launcher.brand=SecblkLauncher");
            cmd.add("-Dminecraft.launcher.version=" + cfg.get("launcher.version", "1.0.0"));
            cmd.add("-cp");
            cmd.add(String.join(File.pathSeparator, classpath));
            cmd.add(mainClass);
            cmd.add("--username");
            cmd.add(account.name);
            cmd.add("--version");
            cmd.add(versionDir.getFileName().toString());
            cmd.add("--gameDir");
            cmd.add(versionDir.toString());
            cmd.add("--assetsDir");
            cmd.add(assets.toString());
            cmd.add("--assetIndex");
            cmd.add(assetIndex);
            cmd.add("--uuid");
            cmd.add(account.uuid);
            cmd.add("--accessToken");
            cmd.add(account.token);
            cmd.add("--userType");
            cmd.add(account.userType);
            cmd.add("--versionType");
            cmd.add("release");
            return new Launch(cmd, versionDir.toFile());
        }

        private static boolean allowedOnWindows(Map<String, Object> lib) {
            List<Object> rules = Json.list(lib.get("rules"));
            if (rules.isEmpty()) {
                return true;
            }
            boolean allowed = false;
            for (Object raw : rules) {
                Map<String, Object> rule = Json.obj(raw);
                Map<String, Object> os = Json.obj(Json.get(rule, "os"));
                String osName = Json.text(os, "name");
                String arch = Json.text(os, "arch");
                if (!osName.isBlank() && !"windows".equals(osName)) {
                    continue;
                }
                if (!arch.isBlank() && !"x86_64".equals(arch) && !"amd64".equals(arch)) {
                    continue;
                }
                if (rule.get("features") instanceof Map) {
                    continue;
                }
                if ("allow".equals(Json.text(rule, "action"))) {
                    allowed = true;
                } else if ("disallow".equals(Json.text(rule, "action"))) {
                    return false;
                }
            }
            return allowed;
        }

        private static String derivedPath(String name) {
            String[] parts = name.split(":");
            if (parts.length < 3) {
                return "";
            }
            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            String classifier = parts.length > 3 ? "-" + parts[3] : "";
            return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
        }

        static Path javaExecutable(Config cfg) {
            String custom = cfg.get("java.path", "");
            if (!custom.isBlank()) {
                Path path = Paths.get(custom);
                if (Files.isRegularFile(path)) {
                    return path;
                }
            }
            String home = System.getProperty("java.home");
            if (home != null) {
                Path exe = Paths.get(home, "bin", "java.exe");
                if (Files.isRegularFile(exe)) {
                    return exe;
                }
            }
            return Paths.get("java");
        }
    }

    // ================================================= 账号登录对话框
    static final class AccountDialog extends JDialog {
        private final Config cfg;
        private final JTextField nameField = textField();
        private final JLabel nameError = label("", new Color(0xE0715C), Font.PLAIN, 11);
        private Account result;

        AccountDialog(Window owner, Config cfg) {
            super(owner, "账号登录", Dialog.ModalityType.APPLICATION_MODAL);
            this.cfg = cfg;

            setUndecorated(true);
            setSize(560, 340);
            setLocationRelativeTo(owner);
            JPanel root = new JPanel(null);
            root.setBackground(Theme.BG);
            root.setBorder(BorderFactory.createLineBorder(Theme.LINE));
            setContentPane(root);

            JLabel kicker = label("ACCOUNT", Theme.ACCENT, Font.BOLD, 11);
            kicker.setBounds(32, 22, 200, 18);
            JLabel title = label("账号登录", Theme.TEXT, Font.BOLD, 26);
            title.setBounds(32, 42, 300, 38);
            JLabel caption = label("账号信息仅保存在本机 data/launcher.properties。",
                    Theme.MUTED, Font.PLAIN, 11);
            caption.setBounds(32, 84, 440, 18);
            root.add(kicker);
            root.add(title);
            root.add(caption);

            FlatButton close = FlatButton.ghost("关闭", null);
            close.setBounds(462, 24, 74, 30);
            close.addActionListener(e -> dispose());
            root.add(close);

            JLabel hint = label("输入你的游戏ID", Theme.TEXT, Font.BOLD, 14);
            hint.setBounds(32, 122, 496, 22);
            root.add(hint);

            nameField.setText(cfg.get("offline.username", ""));
            nameField.setBounds(32, 150, 300, 42);
            root.add(nameField);

            nameError.setBounds(32, 200, 496, 18);
            root.add(nameError);

            FlatButton save = FlatButton.primary("使用该ID进入游戏", new VectorIcon("user", Theme.ON_ACCENT, 16));
            save.setBounds(32, 232, 192, 44);
            save.addActionListener(e -> {
                String name = nameField.getText().trim();
                if (!name.matches("^[A-Za-z0-9_]{3,16}$")) {
                    nameError.setText("游戏ID需为 3–16 位字母、数字或下划线");
                    return;
                }
                cfg.set("offline.username", name);
                cfg.save();
                result = Account.offline(name);
                dispose();
            });
            root.add(save);

            JLabel note = label("3–16 位，仅限字母、数字与下划线；正版玩家直接输入自己的正版ID即可。",
                    Theme.MUTED, Font.PLAIN, 11);
            note.setBounds(32, 288, 496, 18);
            root.add(note);
        }

        private static JTextField textField() {
            JTextField field = new JTextField();
            field.setBackground(new Color(0x191B1F));
            field.setForeground(Theme.TEXT);
            field.setCaretColor(Theme.ACCENT);
            field.setFont(Theme.font(Font.PLAIN, 15));
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.LINE, 1),
                    new EmptyBorder(0, 12, 0, 12)));
            return field;
        }

        Account result() {
            return result;
        }
    }

    // ================================================= 工具
    private static final BufferedImage HERO = loadImage("res/hero.jpg");

    private static BufferedImage loadImage(String path) {
        try {
            File file = new File(path);
            if (file.isFile()) {
                return ImageIO.read(file);
            }
            try (InputStream in = Launcher.class.getResourceAsStream("/" + path)) {
                if (in != null) {
                    return ImageIO.read(in);
                }
            }
        } catch (IOException e) {
            System.err.println("加载图片失败：" + path);
        }
        return null;
    }

    private static void drawCover(Graphics2D g, Image image, int w, int h) {
        if (image == null || w <= 0 || h <= 0) {
            return;
        }
        double scale = Math.max(w / (double) image.getWidth(null), h / (double) image.getHeight(null));
        int dw = (int) Math.ceil(image.getWidth(null) * scale);
        int dh = (int) Math.ceil(image.getHeight(null) * scale);
        g.drawImage(image, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
    }

    private static JLabel label(String text, Color color, int style, int size) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    // ================================================= 统计页
    static final class StatsPage extends JPanel {
        private static final Color GREEN = new Color(0x7BD88A);
        private static final Color RED = new Color(0xE06C75);

        private final Config cfg;
        private final LauncherWindow window;
        private final FlatButton refreshButton;

        private Map<String, Object> server;
        private boolean serverFailed;
        private Map<String, Object> details;
        private String boundAt = "";
        private String accountMessage;
        private BufferedImage skinHead;

        StatsPage(Config cfg, LauncherWindow window) {
            this.cfg = cfg;
            this.window = window;
            setOpaque(true);
            setBackground(Theme.BG);
            setLayout(null);
            refreshButton = FlatButton.ghost("刷新数据", new VectorIcon("globe", Theme.TEXT, 16));
            add(refreshButton);
            refreshButton.addActionListener(e -> reload());
            accountMessage = cfg.get("secblk.token", "").isEmpty()
                    ? "未登录官网账号。点击右上角头像登录后，这里会显示你的角色信息。"
                    : "加载中…";
        }

        void reload() {
            server = null;
            serverFailed = false;
            details = null;
            skinHead = null;
            accountMessage = "加载中…";
            repaint();

            new Thread(() -> {
                Map<String, Object> st = null;
                boolean failed = false;
                try {
                    st = SecblkApi.mcStatus();
                } catch (IOException ex) {
                    failed = true;
                }
                Map<String, Object> result = st;
                boolean flag = failed;
                SwingUtilities.invokeLater(() -> {
                    server = result;
                    serverFailed = flag;
                    repaint();
                });
            }, "stats-mc").start();

            String token = cfg.get("secblk.token", "");
            if (token.isEmpty()) {
                accountMessage = "未登录官网账号。点击右上角头像登录后，这里会显示你的角色信息。";
                return;
            }
            new Thread(() -> {
                Map<String, Object> det = null;
                BufferedImage head = null;
                String bound = "";
                String msg = null;
                boolean expired = false;
                try {
                    Map<String, Object> list = SecblkApi.gameAccounts(token);
                    List<Object> accounts = Json.list(list.get("accounts"));
                    if (accounts.isEmpty()) {
                        msg = "当前官网账号还没有绑定游戏角色，请先到官网“我的角色”页完成绑定。";
                    } else {
                        Map<String, Object> first = Json.obj(accounts.get(0));
                        bound = Json.text(first, "boundAt");
                        det = SecblkApi.gameAccountDetails(token, Json.text(first, "id"));
                        head = SecblkApi.fetchSkinHead(Json.text(det, "skin", "textureUrl"));
                    }
                } catch (SecblkApi.ApiException ex) {
                    if (ex.status == 401 || ex.status == 403) {
                        cfg.set("secblk.token", "");
                        cfg.set("secblk.name", "");
                        cfg.save();
                        expired = true;
                        msg = "登录状态已失效，请重新点击右上角头像登录。";
                    } else {
                        msg = "获取角色信息失败（HTTP " + ex.status + "）。";
                    }
                } catch (IOException ex) {
                    msg = "获取角色信息失败，请稍后点“刷新数据”重试。";
                }
                Map<String, Object> rDet = det;
                BufferedImage rHead = head;
                String rBound = bound == null ? "" : bound;
                String rMsg = msg;
                boolean rExpired = expired;
                SwingUtilities.invokeLater(() -> {
                    details = rDet;
                    skinHead = rHead;
                    boundAt = rBound;
                    accountMessage = rMsg;
                    if (rHead != null) {
                        window.setAvatarSkin(rHead);
                    }
                    if (rExpired) {
                        window.onSecblkSessionExpired();
                    }
                    repaint();
                });
            }, "stats-account").start();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Theme.ACCENT);
            g.setFont(Theme.font(Font.BOLD, 12));
            g.drawString("STATISTICS", 32, 46);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(Font.BOLD, 36));
            g.drawString("统计", 32, 88);

            int top = 116;
            int cardH = getHeight() - top - 32;
            int w1 = 380;
            int x2 = 32 + w1 + 20;
            paintServerCard(g, 32, top, w1, cardH);
            paintAccountCard(g, x2, top, getWidth() - 32 - x2, cardH);
            g.dispose();
        }

        private void paintServerCard(Graphics2D g, int x, int y, int w, int h) {
            card(g, x, y, w, h);
            cardTitle(g, x, y, "服务器状态");
            if (server == null) {
                muted(g, x + 24, y + 66, serverFailed ? "获取失败，请点右上角“刷新数据”重试。" : "加载中…");
                return;
            }
            boolean online = Boolean.TRUE.equals(Json.get(server, "online"));
            g.setColor(online ? GREEN : RED);
            g.fillOval(x + 24, y + 54, 10, 10);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(Font.BOLD, 14));
            g.drawString(online ? "在线" : "离线", x + 42, y + 64);

            String players = Json.text(server, "onlinePlayers");
            g.setFont(Theme.font(Font.BOLD, 44));
            g.setColor(Theme.TEXT);
            g.drawString(players, x + 24, y + 122);
            int pw = g.getFontMetrics().stringWidth(players);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 14));
            g.drawString("/ " + Json.text(server, "maxPlayers") + " 人在线", x + 30 + pw, y + 116);

            g.setColor(Theme.LINE);
            g.drawLine(x + 24, y + 144, x + w - 24, y + 144);
            int ry = y + 172;
            ry = detailRow(g, x, w, ry, "延迟", Json.text(server, "latencyMs") + " ms");
            ry = detailRow(g, x, w, ry, "版本", Json.text(server, "version"));
            detailRow(g, x, w, ry, "MOTD", ellipsize(g, Json.text(server, "motd"), w - 138));
        }

        private void paintAccountCard(Graphics2D g, int x, int y, int w, int h) {
            card(g, x, y, w, h);
            cardTitle(g, x, y, "我的角色");
            if (details == null) {
                muted(g, x + 24, y + 66, accountMessage == null ? "" : accountMessage);
                return;
            }
            int tx = x + 24;
            if (skinHead != null) {
                Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(skinHead, x + 24, y + 46, 56, 56, null);
                if (hint != null) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
                }
                g.setColor(Theme.LINE);
                g.drawRoundRect(x + 24, y + 46, 55, 55, 10, 10);
                tx += 68;
            }
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(Font.BOLD, 18));
            g.drawString(Json.text(details, "minecraftUsername"), tx, y + 66);
            String role = Json.text(details, "roleplay", "fullName");
            if (!role.isEmpty()) {
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(Font.PLAIN, 13));
                g.drawString("角色　" + role, tx, y + 90);
            }

            String org = Json.text(details, "organizations", "current", "shortName");
            if (org.isEmpty()) {
                org = Json.text(details, "organizations", "current", "name");
            }
            String pos = Json.text(details, "organizations", "current", "positionDisplayName");
            if (!org.isEmpty()) {
                String pill = org + (pos.isEmpty() ? "" : " · " + pos);
                drawPill(g, x + w - 24 - pillWidth(g, pill), y + 52, pill,
                        Theme.ACCENT, new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(), 30));
            }

            int by = y + 128;
            int col = (w - 48) / 3;
            balanceCell(g, x + 24, by, "金币",
                    money(Json.get(details, "economy", "vault", "formattedBalance"),
                            Json.get(details, "economy", "vault", "balance")));
            balanceCell(g, x + 24 + col, by, "银行",
                    money(null, Json.get(details, "economy", "bank", "balance")));
            balanceCell(g, x + 24 + 2 * col, by, "点券",
                    money(null, Json.get(details, "economy", "points", "balance")));

            g.setColor(Theme.LINE);
            g.drawLine(x + 24, by + 56, x + w - 24, by + 56);

            Map<String, Object> lic = Json.obj(Json.get(details, "roleplay", "licenses"));
            int px = x + 24;
            int py = by + 76;
            px = licensePill(g, px, py, "驾驶", lic.get("driving")) + 10;
            px = licensePill(g, px, py, "枪械", lic.get("firearms")) + 10;
            licensePill(g, px, py, "商业", lic.get("business"));

            if (!boundAt.isEmpty()) {
                muted(g, x + 24, py + 48, "绑定于 " + boundAt.substring(0, Math.min(10, boundAt.length())));
            }
        }

        private int licensePill(Graphics2D g, int x, int y, String name, Object owned) {
            boolean ok = Boolean.TRUE.equals(owned);
            String text = name + (ok ? "  ✓" : "  ✗");
            drawPill(g, x, y, text,
                    ok ? GREEN : new Color(0x8A9099),
                    ok ? new Color(123, 216, 138, 30) : new Color(138, 144, 153, 24));
            return x + pillWidth(g, text);
        }

        private int pillWidth(Graphics2D g, String text) {
            g.setFont(Theme.font(Font.BOLD, 12));
            return g.getFontMetrics().stringWidth(text) + 24;
        }

        private void drawPill(Graphics2D g, int x, int y, String text, Color fg, Color bg) {
            g.setFont(Theme.font(Font.BOLD, 12));
            int w = pillWidth(g, text);
            g.setColor(bg);
            g.fillRoundRect(x, y, w, 24, 12, 12);
            g.setColor(fg);
            g.drawString(text, x + 12, y + 16);
        }

        private void balanceCell(Graphics2D g, int x, int y, String label, String value) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 12));
            g.drawString(label, x, y);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(Font.BOLD, 20));
            g.drawString(value, x, y + 28);
        }

        private int detailRow(Graphics2D g, int x, int w, int y, String k, String v) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 13));
            g.drawString(k, x + 24, y);
            g.setColor(Theme.TEXT);
            g.drawString(v, x + 114, y);
            return y + 26;
        }

        private void card(Graphics2D g, int x, int y, int w, int h) {
            g.setColor(Theme.NAV_BG);
            g.fillRoundRect(x, y, w, h, 14, 14);
            g.setColor(Theme.LINE);
            g.drawRoundRect(x, y, w - 1, h - 1, 14, 14);
        }

        private void cardTitle(Graphics2D g, int x, int y, String title) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.BOLD, 12));
            g.drawString(title, x + 24, y + 34);
        }

        private void muted(Graphics2D g, int x, int y, String text) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 13));
            g.drawString(text, x, y);
        }

        private String ellipsize(Graphics2D g, String text, int maxWidth) {
            if (text == null || text.isEmpty()) {
                return "—";
            }
            FontMetrics fm = g.getFontMetrics();
            if (fm.stringWidth(text) <= maxWidth) {
                return text;
            }
            String tail = "…";
            int end = text.length();
            while (end > 0 && fm.stringWidth(text.substring(0, end) + tail) > maxWidth) {
                end--;
            }
            return text.substring(0, end) + tail;
        }

        private static String money(Object formatted, Object raw) {
            if (formatted != null && !String.valueOf(formatted).isBlank()) {
                return String.valueOf(formatted);
            }
            if (raw instanceof Number number) {
                return String.format("%,.2f", number.doubleValue());
            }
            return "—";
        }

        @Override
        public void doLayout() {
            refreshButton.setBounds(getWidth() - 182, 40, 150, 40);
        }
    }

    // ================================================= 官网账号登录对话框
    static final class SecblkLoginDialog extends JDialog {
        private boolean loggedIn;

        SecblkLoginDialog(Window owner, Config cfg) {
            super(owner, ModalityType.APPLICATION_MODAL);
            setUndecorated(true);
            setBackground(new Color(0, 0, 0, 0));

            JPanel panel = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(Theme.BG);
                    g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                    g.setColor(Theme.LINE);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                    // 顶部点缀条
                    g.setColor(Theme.ACCENT);
                    g.fillRoundRect(32, 22, 34, 3, 2, 2);
                    g.dispose();
                }
            };
            panel.setOpaque(false);

            // 拖动窗口
            MouseAdapter drag = new MouseAdapter() {
                private Point origin;

                @Override
                public void mousePressed(MouseEvent e) {
                    origin = e.getPoint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (origin == null) {
                        return;
                    }
                    Point p = e.getLocationOnScreen();
                    setLocation(p.x - origin.x, p.y - origin.y);
                }
            };
            panel.addMouseListener(drag);
            panel.addMouseMotionListener(drag);

            JLabel close = label("✕", Theme.MUTED, Font.PLAIN, 14);
            close.setHorizontalAlignment(SwingConstants.CENTER);
            close.setCursor(new Cursor(Cursor.HAND_CURSOR));
            close.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    dispose();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    close.setForeground(Theme.TEXT);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    close.setForeground(Theme.MUTED);
                }
            });

            JLabel kicker = label("SECBLK ACCOUNT", Theme.ACCENT, Font.BOLD, 11);
            JLabel title = label("登录官网账号", Theme.TEXT, Font.BOLD, 22);
            JLabel hint = label("使用 secblk.com 账号登录，同步你的角色数据。", Theme.MUTED, Font.PLAIN, 12);

            JLabel userLabel = label("用户名 / 邮箱", Theme.MUTED, Font.BOLD, 11);
            JLabel passLabel = label("密码", Theme.MUTED, Font.BOLD, 11);
            JTextField userField = field(false);
            JPasswordField passField = (JPasswordField) field(true);
            char defaultEcho = passField.getEchoChar();

            JCheckBox showPass = new JCheckBox("显示密码");
            showPass.setOpaque(false);
            showPass.setForeground(Theme.MUTED);
            showPass.setFont(Theme.font(Font.PLAIN, 12));
            showPass.setFocusPainted(false);
            showPass.addActionListener(e ->
                    passField.setEchoChar(showPass.isSelected() ? (char) 0 : defaultEcho));

            JLabel status = label("", Theme.MUTED, Font.PLAIN, 12);
            FlatButton loginButton = FlatButton.primary("登 录", new VectorIcon("user", Theme.ON_ACCENT, 15));
            loginButton.setHorizontalAlignment(SwingConstants.CENTER);

            JLabel register = label("<html><u>没有账号？前往 secblk.com 注册</u></html>",
                    Theme.MUTED, Font.PLAIN, 12);
            register.setHorizontalAlignment(SwingConstants.CENTER);
            register.setCursor(new Cursor(Cursor.HAND_CURSOR));
            register.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        Desktop.getDesktop().browse(URI.create("https://secblk.com/"));
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    register.setForeground(Theme.ACCENT);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    register.setForeground(Theme.MUTED);
                }
            });

            close.setBounds(368, 14, 28, 28);
            kicker.setBounds(32, 36, 300, 14);
            title.setBounds(32, 56, 340, 30);
            hint.setBounds(32, 92, 356, 16);
            userLabel.setBounds(32, 132, 200, 14);
            userField.setBounds(32, 152, 356, 42);
            passLabel.setBounds(32, 210, 200, 14);
            passField.setBounds(32, 230, 356, 42);
            showPass.setBounds(30, 282, 120, 18);
            status.setBounds(32, 310, 356, 16);
            loginButton.setBounds(32, 336, 356, 44);
            register.setBounds(32, 396, 356, 16);

            for (Component c : new Component[]{close, kicker, title, hint, userLabel, userField,
                    passLabel, passField, showPass, status, loginButton, register}) {
                panel.add(c);
            }

            Runnable submit = () -> doLogin(cfg, userField.getText().trim(),
                    new String(passField.getPassword()), status, loginButton);
            loginButton.addActionListener(e -> submit.run());
            passField.addActionListener(e -> submit.run());
            userField.addActionListener(e -> passField.requestFocusInWindow());

            setContentPane(panel);
            setSize(420, 442);
            setResizable(false);
            setLocationRelativeTo(owner);
        }

        boolean loggedIn() {
            return loggedIn;
        }

        private void doLogin(Config cfg, String username, String password,
                             JLabel status, FlatButton loginButton) {
            if (username.isEmpty() || password.isEmpty()) {
                status.setForeground(new Color(0xE06C75));
                status.setText("请输入用户名和密码。");
                return;
            }
            loginButton.setEnabled(false);
            status.setForeground(Theme.MUTED);
            status.setText("正在进行安全验证…");
            new Thread(() -> {
                try {
                    Map<String, Object> res = SecblkApi.login(username, password);
                    String token = Json.text(res, "accessToken");
                    String name = Json.text(res, "displayName");
                    if (name.isEmpty()) {
                        name = Json.text(res, "username");
                    }
                    if (token.isEmpty()) {
                        throw new IOException("服务器未返回登录凭证");
                    }
                    cfg.set("secblk.token", token);
                    cfg.set("secblk.name", name.isEmpty() ? username : name);
                    cfg.save();
                    SwingUtilities.invokeLater(() -> {
                        loggedIn = true;
                        dispose();
                    });
                } catch (SecblkApi.ApiException ex) {
                    String msg = ex.status == 401 ? "用户名或密码错误。"
                            : ex.status == 400 ? "安全验证失败，请重试。"
                            : "登录失败（HTTP " + ex.status + "）。";
                    fail(status, loginButton, msg);
                } catch (IOException ex) {
                    fail(status, loginButton, "登录失败：" + ex.getMessage());
                }
            }, "secblk-login").start();
        }

        private void fail(JLabel status, FlatButton loginButton, String msg) {
            SwingUtilities.invokeLater(() -> {
                status.setForeground(new Color(0xE06C75));
                status.setText(msg);
                loginButton.setEnabled(true);
            });
        }

        private static JTextField field(boolean password) {
            JTextField f = password ? new JPasswordField() : new JTextField();
            f.setBackground(new Color(0x14161A));
            f.setForeground(Theme.TEXT);
            f.setCaretColor(Theme.TEXT);
            f.setFont(Theme.font(Font.PLAIN, 13));
            setFieldBorder(f, Theme.LINE);
            // 聚焦时描边变主题色
            f.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    setFieldBorder(f, Theme.ACCENT);
                }

                @Override
                public void focusLost(FocusEvent e) {
                    setFieldBorder(f, Theme.LINE);
                }
            });
            return f;
        }

        private static void setFieldBorder(JTextField f, Color color) {
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(color, 1, true),
                    new EmptyBorder(8, 12, 8, 12)));
        }
    }

    // ================================================= Secblk 官网 API
    static final class SecblkApi {
        private static final String BASE = "https://secblk.com/v1";

        static final class ApiException extends IOException {
            final int status;

            ApiException(int status, String message) {
                super(message);
                this.status = status;
            }
        }

        static Map<String, Object> mcStatus() throws IOException {
            return Json.obj(Json.parse(get("/api/mc-status", null)));
        }

        static Map<String, Object> gameAccounts(String token) throws IOException {
            return Json.obj(Json.parse(get("/api/users/game-accounts", token)));
        }

        static Map<String, Object> gameAccountDetails(String token, String id) throws IOException {
            return Json.obj(Json.parse(get("/api/users/game-accounts/" + id + "/details", token)));
        }

        static List<Object> whitelistRequests(String token) throws IOException {
            return listOf(Json.parse(get("/api/users/whitelist-requests", token)), "requests");
        }

        static List<Object> tickets(String token) throws IOException {
            return listOf(Json.parse(get("/api/users/tickets", token)), "tickets");
        }

        static void applyWhitelist(String token, String mcUsername, String reason) throws IOException {
            String body = "{\"minecraftUsername\":" + quote(mcUsername)
                    + ",\"reason\":" + quote(reason) + ",\"answers\":[]}";
            post("/api/users/whitelist-requests", body, token);
        }

        static void deleteWhitelistRequest(String token, String id) throws IOException {
            request("DELETE", "/api/users/whitelist-requests/"
                    + java.net.URLEncoder.encode(id, StandardCharsets.UTF_8), null, token);
        }

        static void createTicket(String token, String subject, String content) throws IOException {
            String body = "{\"subject\":" + quote(subject) + ",\"content\":" + quote(content) + "}";
            post("/api/users/tickets", body, token);
        }

        // 兼容裸数组与 {requests:[...]} 等包装形式
        private static List<Object> listOf(Object parsed, String key) {
            List<Object> direct = Json.list(parsed);
            if (!direct.isEmpty()) {
                return direct;
            }
            Map<String, Object> m = Json.obj(parsed);
            for (String k : new String[]{key, "items", "data", "records", "content", "list"}) {
                List<Object> l = Json.list(m.get(k));
                if (!l.isEmpty()) {
                    return l;
                }
            }
            return List.of();
        }

        // 从错误响应体提取服务端 message 字段
        static String serverMessage(ApiException ex) {
            try {
                String m = Json.text(Json.obj(Json.parse(ex.getMessage())), "message");
                if (!m.isEmpty()) {
                    return m;
                }
            } catch (Exception ignored) {
            }
            return "请求失败（HTTP " + ex.status + "）";
        }

        // 从 64x64 皮肤贴图裁出头部（基层 + 帽子层）
        static BufferedImage fetchSkinHead(String textureUrl) {
            if (textureUrl == null || textureUrl.isEmpty()) {
                return null;
            }
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(textureUrl).toURL().openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "SecblkLauncher/1.0");
                BufferedImage skin;
                try (InputStream in = conn.getInputStream()) {
                    skin = ImageIO.read(in);
                } finally {
                    conn.disconnect();
                }
                if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 16) {
                    return null;
                }
                BufferedImage head = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = head.createGraphics();
                g.drawImage(skin, 0, 0, 8, 8, 8, 8, 16, 16, null);
                g.drawImage(skin, 0, 0, 8, 8, 40, 8, 48, 16, null);
                g.dispose();
                return head;
            } catch (IOException | IllegalArgumentException ex) {
                return null;
            }
        }

        static Map<String, Object> login(String username, String password) throws IOException {
            Map<String, Object> pow = Json.obj(Json.parse(get("/api/auth/login/pow", null)));
            String token = Json.text(pow, "token");
            String challenge = Json.text(pow, "challenge");
            if (token.isEmpty() || challenge.isEmpty()) {
                throw new IOException("proof_unavailable");
            }
            Number diff = (Number) Json.get(pow, "difficulty");
            Number exp = (Number) Json.get(pow, "expiresInSeconds");
            int difficulty = diff == null ? 16 : diff.intValue();
            long deadline = System.currentTimeMillis() + (exp == null ? 300 : exp.longValue()) * 1000L;
            String proofToken = solvePow(token, challenge, difficulty, deadline);
            String body = "{\"username\":" + quote(username)
                    + ",\"password\":" + quote(password)
                    + ",\"proofToken\":" + quote(proofToken) + "}";
            return Json.obj(Json.parse(post("/api/auth/login", body, null)));
        }

        // 官网 PoW：SHA-256(token:challenge:nonce) 前 difficulty 位为零，proofToken = token:nonce
        private static String solvePow(String token, String challenge, int difficulty, long deadline)
                throws IOException {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String prefix = token + ":" + challenge + ":";
                int whole = difficulty / 8;
                int remaining = difficulty % 8;
                for (long nonce = 0; ; nonce++) {
                    if ((nonce & 1023) == 0 && System.currentTimeMillis() >= deadline) {
                        throw new IOException("安全验证超时，请重试");
                    }
                    byte[] hash = digest.digest((prefix + nonce).getBytes(StandardCharsets.UTF_8));
                    boolean ok = true;
                    for (int i = 0; i < whole; i++) {
                        if (hash[i] != 0) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok && (remaining == 0 || ((hash[whole] & 0xFF) >>> (8 - remaining)) == 0)) {
                        return token + ":" + nonce;
                    }
                }
            } catch (NoSuchAlgorithmException e) {
                throw new IOException("当前 Java 环境不支持 SHA-256", e);
            }
        }

        private static String get(String path, String token) throws IOException {
            return request("GET", path, null, token);
        }

        private static String post(String path, String jsonBody, String token) throws IOException {
            return request("POST", path, jsonBody, token);
        }

        private static String request(String method, String path, String body, String token) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) URI.create(BASE + path).toURL().openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "SecblkLauncher/1.0");
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (Writer writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(body);
                }
            }
            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            conn.disconnect();
            if (status >= 400) {
                throw new ApiException(status, text.isBlank() ? "HTTP " + status : text);
            }
            return text;
        }

        private static String quote(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.append('"').toString();
        }
    }

    // ================================================= 服务页（白名单 + 工单）
    static final class ServicePage extends JPanel {
        private static final Color GREEN = new Color(0x7BD88A);
        private static final Color RED = new Color(0xE06C75);
        private static final Color YELLOW = new Color(0xE5C07B);
        private static final int MAX_ROWS = 6;
        private static final int ROW_H = 64;
        private static final int TOP = 116;

        private final Config cfg;
        private final LauncherWindow window;
        private final FlatButton refreshButton;
        private final FlatButton applyWlButton;
        private final FlatButton newTicketButton;
        private final java.util.List<FlatButton> withdrawButtons = new java.util.ArrayList<>();

        private List<Object> requests;
        private List<Object> tickets;
        private String wlMessage;
        private String tkMessage;

        ServicePage(Config cfg, LauncherWindow window) {
            this.cfg = cfg;
            this.window = window;
            setOpaque(true);
            setBackground(Theme.BG);
            setLayout(null);

            refreshButton = FlatButton.ghost("刷新数据", new VectorIcon("globe", Theme.TEXT, 16));
            applyWlButton = FlatButton.primary("申请白名单", new VectorIcon("user", Theme.ON_ACCENT, 15));
            newTicketButton = FlatButton.primary("新建工单", null);
            add(refreshButton);
            add(applyWlButton);
            add(newTicketButton);

            refreshButton.addActionListener(e -> reload());
            applyWlButton.addActionListener(e ->
                    new WhitelistApplyDialog(window, cfg, this::reload).setVisible(true));
            newTicketButton.addActionListener(e ->
                    new TicketDialog(window, cfg, this::reload).setVisible(true));
        }

        void reload() {
            requests = null;
            tickets = null;
            wlMessage = null;
            tkMessage = null;
            clearWithdrawButtons();

            String token = cfg.get("secblk.token", "");
            if (token.isEmpty()) {
                wlMessage = "未登录官网账号。点击右上角头像登录后可申请白名单。";
                tkMessage = "未登录官网账号。点击右上角头像登录后可提交工单。";
                applyWlButton.setVisible(false);
                newTicketButton.setVisible(false);
                repaint();
                return;
            }
            applyWlButton.setVisible(true);
            newTicketButton.setVisible(true);
            repaint();

            new Thread(() -> {
                List<Object> req = null;
                List<Object> tks = null;
                String wlMsg = null;
                String tkMsg = null;
                boolean expired = false;
                try {
                    req = SecblkApi.whitelistRequests(token);
                } catch (SecblkApi.ApiException ex) {
                    if (ex.status == 401 || ex.status == 403) {
                        expired = true;
                    } else {
                        wlMsg = "获取白名单申请失败（HTTP " + ex.status + "）。";
                    }
                } catch (IOException ex) {
                    wlMsg = "获取白名单申请失败，请检查网络后重试。";
                }
                try {
                    tks = SecblkApi.tickets(token);
                } catch (SecblkApi.ApiException ex) {
                    if (ex.status == 401 || ex.status == 403) {
                        expired = true;
                    } else {
                        tkMsg = "获取工单列表失败（HTTP " + ex.status + "）。";
                    }
                } catch (IOException ex) {
                    tkMsg = "获取工单列表失败，请检查网络后重试。";
                }
                if (expired) {
                    cfg.set("secblk.token", "");
                    cfg.set("secblk.name", "");
                    cfg.save();
                    wlMsg = "登录状态已失效，请重新点击右上角头像登录。";
                    tkMsg = wlMsg;
                }
                List<Object> rReq = req;
                List<Object> rTks = tks;
                String rWl = wlMsg;
                String rTk = tkMsg;
                boolean rExpired = expired;
                SwingUtilities.invokeLater(() -> {
                    requests = rReq;
                    tickets = rTks;
                    wlMessage = rWl;
                    tkMessage = rTk;
                    applyWlButton.setVisible(!rExpired);
                    newTicketButton.setVisible(!rExpired);
                    if (rExpired) {
                        window.onSecblkSessionExpired();
                    }
                    rebuildWithdrawButtons();
                    repaint();
                });
            }, "service-load").start();
        }

        private void clearWithdrawButtons() {
            for (FlatButton b : withdrawButtons) {
                remove(b);
            }
            withdrawButtons.clear();
        }

        // 为可见的待审核申请生成"撤回"按钮
        private void rebuildWithdrawButtons() {
            clearWithdrawButtons();
            if (requests == null) {
                return;
            }
            int rows = Math.min(MAX_ROWS, requests.size());
            for (int i = 0; i < rows; i++) {
                Map<String, Object> r = Json.obj(requests.get(i));
                if (!"PENDING".equalsIgnoreCase(Json.text(r, "status"))) {
                    continue;
                }
                String id = Json.text(r, "id");
                FlatButton b = FlatButton.ghost("撤回", null);
                b.addActionListener(e -> withdraw(id));
                withdrawButtons.add(b);
                add(b);
            }
            revalidate();
            doLayout();
        }

        private void withdraw(String id) {
            if (JOptionPane.showConfirmDialog(this, "确定撤回这条白名单申请？", "撤回申请",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
            String token = cfg.get("secblk.token", "");
            new Thread(() -> {
                String err = null;
                try {
                    SecblkApi.deleteWhitelistRequest(token, id);
                } catch (SecblkApi.ApiException ex) {
                    err = SecblkApi.serverMessage(ex);
                } catch (IOException ex) {
                    err = "撤回失败，请检查网络后重试。";
                }
                String rErr = err;
                SwingUtilities.invokeLater(() -> {
                    if (rErr != null) {
                        JOptionPane.showMessageDialog(this, rErr, "撤回失败", JOptionPane.ERROR_MESSAGE);
                    }
                    reload();
                });
            }, "service-withdraw").start();
        }

        @Override
        public void doLayout() {
            int w = getWidth();
            int h = getHeight();
            refreshButton.setBounds(w - 32 - 118, 30, 118, 36);
            int cardH = h - TOP - 32;
            int wlW = 420;
            int x2 = 32 + wlW + 20;
            int tkW = w - 32 - x2;
            applyWlButton.setBounds(32 + 24, TOP + cardH - 24 - 42, wlW - 48, 42);
            newTicketButton.setBounds(x2 + 24, TOP + cardH - 24 - 42, Math.max(120, tkW - 48), 42);
            // 撤回按钮与各自的待审核行对齐
            if (requests != null) {
                int bi = 0;
                int rows = Math.min(MAX_ROWS, requests.size());
                for (int i = 0; i < rows && bi < withdrawButtons.size(); i++) {
                    Map<String, Object> r = Json.obj(requests.get(i));
                    if (!"PENDING".equalsIgnoreCase(Json.text(r, "status"))) {
                        continue;
                    }
                    int rowY = TOP + 52 + i * ROW_H;
                    withdrawButtons.get(bi).setBounds(32 + wlW - 24 - 60, rowY + 16, 60, 32);
                    bi++;
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Theme.ACCENT);
            g.setFont(Theme.font(Font.BOLD, 12));
            g.drawString("SERVICE", 32, 46);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(Font.BOLD, 36));
            g.drawString("服务", 32, 88);

            int cardH = getHeight() - TOP - 32;
            int wlW = 420;
            int x2 = 32 + wlW + 20;
            int tkW = getWidth() - 32 - x2;
            paintWlCard(g, 32, TOP, wlW, cardH);
            paintTkCard(g, x2, TOP, tkW, cardH);
            g.dispose();
        }

        private void paintWlCard(Graphics2D g, int x, int y, int w, int h) {
            card(g, x, y, w, h);
            cardTitle(g, x, y, "白名单申请");
            if (wlMessage != null) {
                muted(g, x + 24, y + 66, wlMessage);
                return;
            }
            if (requests == null) {
                muted(g, x + 24, y + 66, "加载中…");
                return;
            }
            if (requests.isEmpty()) {
                muted(g, x + 24, y + 66, "暂无申请记录，点击下方按钮提交。");
                return;
            }
            int rows = Math.min(MAX_ROWS, requests.size());
            for (int i = 0; i < rows; i++) {
                Map<String, Object> r = Json.obj(requests.get(i));
                int rowY = y + 52 + i * ROW_H;
                if (i > 0) {
                    g.setColor(Theme.LINE);
                    g.drawLine(x + 24, rowY, x + w - 24, rowY);
                }
                String status = Json.text(r, "status");
                g.setColor(Theme.TEXT);
                g.setFont(Theme.font(Font.BOLD, 14));
                g.drawString(Json.text(r, "minecraftUsername"), x + 24, rowY + 24);
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(Font.PLAIN, 12));
                String meta = "提交于 " + fmtTime(Json.text(r, "createdAt"));
                String reviewer = Json.text(r, "reviewedBy");
                if (!reviewer.isEmpty()) {
                    meta += "　审核人 " + reviewer;
                }
                g.drawString(meta, x + 24, rowY + 46);
                int pw = pillWidth(g, statusLabel(status));
                int pillX = x + w - 24 - pw - ("PENDING".equalsIgnoreCase(status) ? 68 : 0);
                drawStatusPill(g, pillX, rowY + 18, status);
            }
            if (requests.size() > MAX_ROWS) {
                muted(g, x + 24, y + h - 80, "共 " + requests.size() + " 条，仅显示前 " + MAX_ROWS + " 条");
            }
        }

        private void paintTkCard(Graphics2D g, int x, int y, int w, int h) {
            card(g, x, y, w, h);
            cardTitle(g, x, y, "我的工单");
            if (tkMessage != null) {
                muted(g, x + 24, y + 66, tkMessage);
                return;
            }
            if (tickets == null) {
                muted(g, x + 24, y + 66, "加载中…");
                return;
            }
            if (tickets.isEmpty()) {
                muted(g, x + 24, y + 66, "暂无工单，点击下方按钮提交。");
                return;
            }
            int rows = Math.min(MAX_ROWS, tickets.size());
            for (int i = 0; i < rows; i++) {
                Map<String, Object> t = Json.obj(tickets.get(i));
                int rowY = y + 52 + i * ROW_H;
                if (i > 0) {
                    g.setColor(Theme.LINE);
                    g.drawLine(x + 24, rowY, x + w - 24, rowY);
                }
                String status = Json.text(t, "status");
                int pw = pillWidth(g, statusLabel(status));
                String subject = Json.text(t, "subject");
                if (subject.isEmpty()) {
                    subject = Json.text(t, "title");
                }
                g.setColor(Theme.TEXT);
                g.setFont(Theme.font(Font.BOLD, 14));
                g.drawString(ellipsize(g, subject, w - 48 - pw - 16), x + 24, rowY + 24);
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(Font.PLAIN, 12));
                g.drawString("提交于 " + fmtTime(Json.text(t, "createdAt")), x + 24, rowY + 46);
                drawStatusPill(g, x + w - 24 - pw, rowY + 18, status);
            }
            if (tickets.size() > MAX_ROWS) {
                muted(g, x + 24, y + h - 80, "共 " + tickets.size() + " 条，仅显示前 " + MAX_ROWS + " 条");
            }
        }

        private static String statusLabel(String status) {
            if (status == null || status.isEmpty()) {
                return "未知";
            }
            return switch (status.toUpperCase()) {
                case "PENDING" -> "待审核";
                case "APPROVED" -> "已通过";
                case "REJECTED" -> "已拒绝";
                case "OPEN" -> "待处理";
                case "IN_PROGRESS", "ANSWERED" -> "处理中";
                case "RESOLVED" -> "已解决";
                case "CLOSED" -> "已关闭";
                default -> status;
            };
        }

        private void drawStatusPill(Graphics2D g, int x, int y, String status) {
            String s = status == null ? "" : status.toUpperCase();
            Color fg;
            Color bg;
            switch (s) {
                case "APPROVED", "RESOLVED" -> {
                    fg = GREEN;
                    bg = new Color(123, 216, 138, 28);
                }
                case "REJECTED" -> {
                    fg = RED;
                    bg = new Color(224, 108, 117, 28);
                }
                case "PENDING", "OPEN" -> {
                    fg = YELLOW;
                    bg = new Color(229, 192, 123, 28);
                }
                case "IN_PROGRESS", "ANSWERED" -> {
                    fg = Theme.ACCENT;
                    bg = new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(), 28);
                }
                default -> {
                    fg = new Color(0x8A9099);
                    bg = new Color(138, 144, 153, 22);
                }
            }
            String label = statusLabel(status);
            int w = pillWidth(g, label);
            g.setColor(bg);
            g.fillRoundRect(x, y, w, 24, 12, 12);
            g.setColor(fg);
            g.setFont(Theme.font(Font.BOLD, 12));
            g.drawString(label, x + 12, y + 16);
        }

        private int pillWidth(Graphics2D g, String text) {
            g.setFont(Theme.font(Font.BOLD, 12));
            return g.getFontMetrics().stringWidth(text) + 24;
        }

        private static String fmtTime(String iso) {
            if (iso == null || iso.isEmpty()) {
                return "—";
            }
            if (iso.length() < 10) {
                return iso;
            }
            return iso.length() >= 16 ? iso.substring(0, 10) + " " + iso.substring(11, 16) : iso.substring(0, 10);
        }

        private void card(Graphics2D g, int x, int y, int w, int h) {
            g.setColor(Theme.NAV_BG);
            g.fillRoundRect(x, y, w, h, 14, 14);
            g.setColor(Theme.LINE);
            g.drawRoundRect(x, y, w - 1, h - 1, 14, 14);
        }

        private void cardTitle(Graphics2D g, int x, int y, String title) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.BOLD, 12));
            g.drawString(title, x + 24, y + 34);
        }

        private void muted(Graphics2D g, int x, int y, String text) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 13));
            g.drawString(text, x, y);
        }

        private String ellipsize(Graphics2D g, String text, int maxWidth) {
            if (text == null || text.isEmpty()) {
                return "—";
            }
            FontMetrics fm = g.getFontMetrics();
            if (fm.stringWidth(text) <= maxWidth) {
                return text;
            }
            String tail = "…";
            int end = text.length();
            while (end > 0 && fm.stringWidth(text.substring(0, end) + tail) > maxWidth) {
                end--;
            }
            return text.substring(0, end) + tail;
        }
    }

    // ================================================= 深色圆角模态对话框基类
    static class DarkDialog extends JDialog {
        private Point dragOffset;

        DarkDialog(LauncherWindow owner, String title, int w, int h) {
            super(owner);
            setUndecorated(true);
            setModal(true);
            setBackground(new Color(0, 0, 0, 0));
            JPanel content = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setColor(Theme.NAV_BG);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                    g.setColor(Theme.LINE);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                    g.setColor(Theme.ACCENT);
                    g.fillRoundRect(22, 22, 4, 18, 2, 2);
                    g.setColor(Theme.TEXT);
                    g.setFont(Theme.font(Font.BOLD, 18));
                    g.drawString(title, 36, 37);
                    g.dispose();
                }
            };
            content.setOpaque(false);
            setContentPane(content);
            setSize(w, h);
            setLocationRelativeTo(owner);

            MouseAdapter drag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragOffset = e.getPoint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragOffset != null) {
                        setLocation(getX() + e.getX() - dragOffset.x, getY() + e.getY() - dragOffset.y);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragOffset = null;
                }
            };
            content.addMouseListener(drag);
            content.addMouseMotionListener(drag);

            JLabel close = new JLabel("✕", SwingConstants.CENTER);
            close.setFont(Theme.font(Font.PLAIN, 15));
            close.setForeground(Theme.MUTED);
            close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            close.setBounds(w - 46, 12, 32, 32);
            close.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    dispose();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    close.setForeground(Theme.TEXT);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    close.setForeground(Theme.MUTED);
                }
            });
            content.add(close);
        }

        static JLabel fieldLabel(String text, int x, int y) {
            JLabel l = new JLabel(text);
            l.setFont(Theme.font(Font.PLAIN, 12));
            l.setForeground(Theme.MUTED);
            l.setBounds(x, y, 380, 18);
            return l;
        }

        static JTextField textField(String value, int x, int y, int w) {
            JTextField f = new JTextField(value);
            f.setBackground(new Color(0x14161A));
            f.setForeground(Theme.TEXT);
            f.setCaretColor(Theme.TEXT);
            f.setFont(Theme.font(Font.PLAIN, 14));
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.LINE, 1, true),
                    new EmptyBorder(8, 12, 8, 12)));
            f.setBounds(x, y, w, 42);
            return f;
        }

        static JScrollPane textArea(JTextArea area, int x, int y, int w, int h) {
            area.setBackground(new Color(0x14161A));
            area.setForeground(Theme.TEXT);
            area.setCaretColor(Theme.TEXT);
            area.setFont(Theme.font(Font.PLAIN, 14));
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setBorder(new EmptyBorder(10, 12, 10, 12));
            JScrollPane sp = new JScrollPane(area);
            sp.setBorder(BorderFactory.createLineBorder(Theme.LINE, 1, true));
            sp.setBounds(x, y, w, h);
            return sp;
        }
    }

    // ================================================= 白名单申请对话框
    static final class WhitelistApplyDialog extends DarkDialog {
        WhitelistApplyDialog(LauncherWindow owner, Config cfg, Runnable onDone) {
            super(owner, "申请白名单", 460, 430);
            JPanel c = (JPanel) getContentPane();
            c.add(fieldLabel("游戏ID", 28, 62));
            JTextField mc = textField(cfg.get("offline.username", ""), 28, 84, 404);
            c.add(mc);
            c.add(fieldLabel("申请理由", 28, 140));
            JTextArea reason = new JTextArea();
            c.add(textArea(reason, 28, 162, 404, 150));
            JLabel status = new JLabel();
            status.setFont(Theme.font(Font.PLAIN, 12));
            status.setBounds(28, 322, 404, 18);
            c.add(status);
            FlatButton submit = FlatButton.primary("提交申请", null);
            submit.setBounds(28, 350, 404, 44);
            c.add(submit);

            submit.addActionListener(e -> {
                String name = mc.getText().trim();
                String text = reason.getText().trim();
                if (name.isEmpty() || text.isEmpty()) {
                    status.setForeground(new Color(0xE06C75));
                    status.setText(name.isEmpty() ? "请填写游戏ID" : "请填写申请理由");
                    return;
                }
                submit.setEnabled(false);
                status.setForeground(Theme.MUTED);
                status.setText("提交中…");
                String token = cfg.get("secblk.token", "");
                new Thread(() -> {
                    String err = null;
                    try {
                        SecblkApi.applyWhitelist(token, name, text);
                    } catch (SecblkApi.ApiException ex) {
                        err = SecblkApi.serverMessage(ex);
                    } catch (IOException ex) {
                        err = "网络错误，请稍后重试";
                    }
                    String rErr = err;
                    SwingUtilities.invokeLater(() -> {
                        if (rErr == null) {
                            dispose();
                            onDone.run();
                        } else {
                            status.setForeground(new Color(0xE06C75));
                            status.setText(rErr);
                            submit.setEnabled(true);
                        }
                    });
                }, "whitelist-apply").start();
            });
        }
    }

    // ================================================= 新建工单对话框
    static final class TicketDialog extends DarkDialog {
        TicketDialog(LauncherWindow owner, Config cfg, Runnable onDone) {
            super(owner, "新建工单", 460, 430);
            JPanel c = (JPanel) getContentPane();
            c.add(fieldLabel("标题", 28, 62));
            JTextField subject = textField("", 28, 84, 404);
            c.add(subject);
            c.add(fieldLabel("问题描述", 28, 140));
            JTextArea content = new JTextArea();
            c.add(textArea(content, 28, 162, 404, 150));
            JLabel status = new JLabel();
            status.setFont(Theme.font(Font.PLAIN, 12));
            status.setBounds(28, 322, 404, 18);
            c.add(status);
            FlatButton submit = FlatButton.primary("提交工单", null);
            submit.setBounds(28, 350, 404, 44);
            c.add(submit);

            submit.addActionListener(e -> {
                String s = subject.getText().trim();
                String text = content.getText().trim();
                if (s.isEmpty() || text.isEmpty()) {
                    status.setForeground(new Color(0xE06C75));
                    status.setText(s.isEmpty() ? "请填写标题" : "请填写问题描述");
                    return;
                }
                submit.setEnabled(false);
                status.setForeground(Theme.MUTED);
                status.setText("提交中…");
                String token = cfg.get("secblk.token", "");
                new Thread(() -> {
                    String err = null;
                    try {
                        SecblkApi.createTicket(token, s, text);
                    } catch (SecblkApi.ApiException ex) {
                        err = SecblkApi.serverMessage(ex);
                    } catch (IOException ex) {
                        err = "网络错误，请稍后重试";
                    }
                    String rErr = err;
                    SwingUtilities.invokeLater(() -> {
                        if (rErr == null) {
                            dispose();
                            onDone.run();
                        } else {
                            status.setForeground(new Color(0xE06C75));
                            status.setText(rErr);
                            submit.setEnabled(true);
                        }
                    });
                }, "ticket-create").start();
            });
        }
    }

    // ================================================= 设置页
    static final class SettingsPage extends JPanel {
        private static final Color DANGER_RED = new Color(0xE06C75);
        private static final int CARD1_Y = 116;
        private static final int CARD1_H = 150;
        private static final int CARD2_Y = CARD1_Y + CARD1_H + 16;
        private static final int CARD2_H = 76;
        private static final int CARD3_Y = CARD2_Y + CARD2_H + 16;
        private static final int CARD3_H = 120;

        private final Config cfg;
        private final java.util.function.BooleanSupplier gameRunning;
        private final JTextField javaField;
        private final JLabel javaStatus;
        private final FlatButton applyJava;
        private final FlatButton browseJava;
        private final FlatButton resetJava;
        private final FlatButton closeButton;
        private final FlatButton deleteGameButton;
        private final FlatButton deleteAllButton;

        SettingsPage(Config cfg, java.util.function.BooleanSupplier gameRunning) {
            this.cfg = cfg;
            this.gameRunning = gameRunning;
            setOpaque(true);
            setBackground(Theme.BG);
            setLayout(null);

            javaField = new JTextField(cfg.get("java.path", ""));
            javaField.setBackground(new Color(0x14161A));
            javaField.setForeground(Theme.TEXT);
            javaField.setCaretColor(Theme.TEXT);
            javaField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.LINE, 1, true),
                    new EmptyBorder(8, 10, 8, 10)));
            javaField.setFont(Theme.font(Font.PLAIN, 13));

            javaStatus = new JLabel();
            javaStatus.setFont(Theme.font(Font.PLAIN, 12));
            updateJavaEffective();

            applyJava = FlatButton.ghost("应用", null);
            browseJava = FlatButton.ghost("浏览…", null);
            resetJava = FlatButton.ghost("恢复默认", null);
            closeButton = FlatButton.danger("关闭启动器", null);
            deleteGameButton = FlatButton.danger("删除游戏", null);
            deleteAllButton = FlatButton.danger("删除所有数据", null);

            for (Component c : new Component[]{javaField, javaStatus, applyJava, browseJava, resetJava,
                    closeButton, deleteGameButton, deleteAllButton}) {
                add(c);
            }

            applyJava.addActionListener(e -> applyJavaPath());
            javaField.addActionListener(e -> applyJavaPath());
            browseJava.addActionListener(e -> browseJava());
            resetJava.addActionListener(e -> {
                javaField.setText("");
                applyJavaPath();
            });
            closeButton.addActionListener(e -> System.exit(0));
            deleteGameButton.addActionListener(e -> deleteGame());
            deleteAllButton.addActionListener(e -> deleteAllData());
        }

        private void applyJavaPath() {
            String path = javaField.getText().trim();
            if (!path.isEmpty() && !Files.isRegularFile(Paths.get(path))) {
                javaFeedback("文件不存在：" + path, true);
                return;
            }
            cfg.set("java.path", path);
            cfg.save();
            updateJavaEffective();
        }

        private void updateJavaEffective() {
            javaFeedback("当前生效：" + Launch.javaExecutable(cfg)
                    + (cfg.get("java.path", "").isEmpty() ? "（自动检测）" : ""), false);
        }

        private void javaFeedback(String msg, boolean error) {
            javaStatus.setForeground(error ? DANGER_RED : Theme.MUTED);
            javaStatus.setText(msg);
        }

        private void browseJava() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("选择 java.exe");
            chooser.setFileFilter(new FileNameExtensionFilter("Java 可执行文件 (java.exe)", "exe"));
            String current = javaField.getText().trim();
            if (!current.isEmpty()) {
                chooser.setSelectedFile(new File(current));
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                javaField.setText(chooser.getSelectedFile().getAbsolutePath());
                applyJavaPath();
            }
        }

        private void deleteGame() {
            if (gameRunning.getAsBoolean()) {
                JOptionPane.showMessageDialog(this, "游戏正在运行，请先关闭游戏再执行删除。",
                        "无法删除", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (externalGameRunning() != null) {
                JOptionPane.showMessageDialog(this,
                        "检测到游戏正在运行（进程 " + externalGameRunning() + "），请先关闭游戏再执行删除。",
                        "无法删除", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!confirm("删除游戏", "将删除 Game 目录下的全部游戏文件（包含存档、模组与配置），不可恢复。\n安装包 game.pack 会保留，下次启动可自动重装。\n确定继续？")) {
                return;
            }
            runDelete("游戏文件", new Path[]{appPath("Game")}, false);
        }

        private void deleteAllData() {
            if (gameRunning.getAsBoolean()) {
                JOptionPane.showMessageDialog(this, "游戏正在运行，请先关闭游戏再执行删除。",
                        "无法删除", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (externalGameRunning() != null) {
                JOptionPane.showMessageDialog(this,
                        "检测到游戏正在运行（进程 " + externalGameRunning() + "），请先关闭游戏再执行删除。",
                        "无法删除", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!confirm("删除所有数据", "将删除游戏文件与启动器全部数据（官网登录状态、游戏ID、设置），不可恢复。\n确定继续？")) {
                return;
            }
            if (!confirm("再次确认", "此操作不可撤销。再次确认删除所有数据？")) {
                return;
            }
            // 先清空内存中的配置，防止删除后任何 save 把 ID 写回
            cfg.clearAll();
            // 先删 data/（快、无占用），确保 ID 必定被移除；再删 Game/
            runDelete("所有数据", new Path[]{appPath("data"), appPath("Game")}, true);
        }

        // 检测其他进程（如直接启动的游戏）是否占用了游戏目录
        private static String externalGameRunning() {
            long self = ProcessHandle.current().pid();
            return ProcessHandle.allProcesses()
                    .filter(ph -> ph.pid() != self)
                    .map(ph -> ph.info().commandLine().orElse(""))
                    .filter(cmd -> cmd.contains("Secblk-第二街区"))
                    .findFirst()
                    .orElse(null);
        }

        private boolean confirm(String title, String message) {
            return JOptionPane.showConfirmDialog(this, message, title,
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
        }

        private void runDelete(String what, Path[] roots, boolean exitAfter) {
            setDangerButtonsEnabled(false);
            new Thread(() -> {
                String error = null;
                try {
                    for (Path root : roots) {
                        deleteChildren(root);
                    }
                } catch (IOException ex) {
                    error = ex.getMessage();
                }
                String result = error;
                SwingUtilities.invokeLater(() -> {
                    setDangerButtonsEnabled(true);
                    if (result != null) {
                        JOptionPane.showMessageDialog(this,
                                "删除失败：" + result + "\n可能有文件被占用，请关闭相关程序后重试。",
                                "删除失败", JOptionPane.ERROR_MESSAGE);
                    } else if (exitAfter) {
                        JOptionPane.showMessageDialog(this, what + "已删除，启动器即将关闭。",
                                "完成", JOptionPane.INFORMATION_MESSAGE);
                        System.exit(0);
                    } else {
                        JOptionPane.showMessageDialog(this, what + "已删除。",
                                "完成", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            }, "settings-delete").start();
        }

        private static void deleteChildren(Path root) throws IOException {
            if (!Files.exists(root)) {
                return;
            }
            try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
                for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    if (!p.equals(root)) {
                        Files.deleteIfExists(p);
                    }
                }
            }
        }

        private void setDangerButtonsEnabled(boolean enabled) {
            closeButton.setEnabled(enabled);
            deleteGameButton.setEnabled(enabled);
            deleteAllButton.setEnabled(enabled);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Theme.ACCENT);
            g.setFont(Theme.font(Font.BOLD, 12));
            g.drawString("SETTINGS", 32, 46);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(Font.BOLD, 36));
            g.drawString("设置", 32, 88);

            int w = getWidth();
            card(g, 32, CARD1_Y, w - 64, CARD1_H, false);
            cardTitle(g, CARD1_Y, "Java 运行环境", Theme.MUTED);
            muted(g, CARD1_Y + 64, "自定义 java.exe 路径，留空则自动检测（优先启动器目录下的 runtime，其次系统 PATH）。");

            card(g, 32, CARD2_Y, w - 64, CARD2_H, false);
            cardTitle(g, CARD2_Y, "关于", Theme.MUTED);
            muted(g, CARD2_Y + 52, "启动器版本 v" + cfg.get("launcher.version", "1.0.0")
                    + "　·　游戏目录 Game/　·　配置目录 data/");

            card(g, 32, CARD3_Y, w - 64, CARD3_H, true);
            cardTitle(g, CARD3_Y, "危险操作", DANGER_RED);
            muted(g, CARD3_Y + 50, "以下操作不可恢复，执行前请先关闭游戏。");
            g.dispose();
        }

        private void card(Graphics2D g, int x, int y, int w, int h, boolean danger) {
            g.setColor(Theme.NAV_BG);
            g.fillRoundRect(x, y, w, h, 14, 14);
            g.setColor(danger ? new Color(0xE06C75 & 0xFFFFFF | 0x55000000, true) : Theme.LINE);
            g.drawRoundRect(x, y, w - 1, h - 1, 14, 14);
        }

        private void cardTitle(Graphics2D g, int cardY, String title, Color color) {
            g.setColor(color);
            g.setFont(Theme.font(Font.BOLD, 12));
            g.drawString(title, 56, cardY + 34);
        }

        private void muted(Graphics2D g, int y, String text) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 12));
            g.drawString(text, 56, y);
        }

        @Override
        public void doLayout() {
            int w = getWidth();
            int btnW = 96;
            int gap = 8;
            int right = w - 32 - 24;
            resetJava.setBounds(right - btnW, CARD1_Y + 84, btnW, 36);
            browseJava.setBounds(right - btnW * 2 - gap, CARD1_Y + 84, btnW, 36);
            applyJava.setBounds(right - btnW * 3 - gap * 2, CARD1_Y + 84, btnW, 36);
            javaField.setBounds(56, CARD1_Y + 84, right - btnW * 3 - gap * 2 - 56 - gap, 36);
            javaStatus.setBounds(56, CARD1_Y + 128, w - 112, 16);

            closeButton.setBounds(56, CARD3_Y + 64, 150, 40);
            deleteGameButton.setBounds(56 + 166, CARD3_Y + 64, 150, 40);
            deleteAllButton.setBounds(56 + 332, CARD3_Y + 64, 170, 40);
        }
    }

    // ================================================= 游戏文件压缩包
    static final class GamePack {
        static final Path PACK = appPath("game.pack");

        interface Progress {
            void onProgress(long done, long total);
        }

        // 以安装完成标记为准：中途被杀的半成品会触发重新解压
        static boolean needsInstall() {
            return !Files.isRegularFile(appPath("Game", ".install-ok"));
        }

        static void unpack(Path pack, Path destDir, Progress progress) throws IOException {
            try (ZipFile zip = new ZipFile(pack.toFile())) {
                long total = zip.size();
                long done = 0;
                java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    Path out = destDir.resolve(entry.getName()).normalize();
                    if (!out.startsWith(destDir)) {
                        throw new IOException("压缩包包含非法条目：" + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        if (out.getParent() != null) {
                            Files.createDirectories(out.getParent());
                        }
                        try (InputStream in = zip.getInputStream(entry)) {
                            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    if (progress != null) {
                        progress.onProgress(++done, total);
                    }
                }
                // 全部条目解压成功后才写入完成标记
                Files.writeString(destDir.resolve(".install-ok"), String.valueOf(System.currentTimeMillis()));
            }
        }

        // 打包排除：运行日志与 BodyCam 录像
        private static boolean isExcluded(Path rel) {
            String s = rel.toString().replace('\\', '/');
            return s.equals("Secblk-第二街区/logs")
                    || s.startsWith("Secblk-第二街区/logs/")
                    || s.equals("Secblk-第二街区/camplus_recordings")
                    || s.startsWith("Secblk-第二街区/camplus_recordings/");
        }

        static void pack(Path sourceDir, Path out, Progress progress) throws IOException {
            List<Path> files;
            try (java.util.stream.Stream<Path> s = Files.walk(sourceDir)) {
                files = s.filter(p -> !p.equals(sourceDir))
                        .filter(p -> !isExcluded(sourceDir.relativize(p)))
                        .toList();
            }
            long total = files.size();
            long done = 0;
            try (OutputStream fos = Files.newOutputStream(out);
                 ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos, 65536))) {
                for (Path p : files) {
                    String name = sourceDir.relativize(p).toString().replace('\\', '/');
                    if (Files.isDirectory(p)) {
                        zos.putNextEntry(new ZipEntry(name + "/"));
                        zos.closeEntry();
                    } else {
                        zos.putNextEntry(new ZipEntry(name));
                        Files.copy(p, zos);
                        zos.closeEntry();
                    }
                    if (progress != null) {
                        progress.onProgress(++done, total);
                    }
                }
            }
        }
    }

    // ================================================= 游戏安装进度对话框
    static final class InstallDialog extends JDialog {
        InstallDialog(Window owner) {
            super(owner, "安装游戏文件", ModalityType.APPLICATION_MODAL);
            JPanel panel = new JPanel(null);
            panel.setBackground(Theme.BG);

            JLabel title = label("正在解压游戏文件", Theme.TEXT, Font.BOLD, 16);
            JLabel hint = label("首次运行需要从 game.pack 安装，请稍候…", Theme.MUTED, Font.PLAIN, 12);
            JProgressBar bar = new JProgressBar(0, 1000);
            bar.setValue(0);
            bar.setStringPainted(true);
            JLabel status = label("准备中…", Theme.MUTED, Font.PLAIN, 12);

            title.setBounds(24, 20, 360, 22);
            hint.setBounds(24, 46, 360, 16);
            bar.setBounds(24, 76, 372, 22);
            status.setBounds(24, 106, 372, 16);
            for (Component c : new Component[]{title, hint, bar, status}) {
                panel.add(c);
            }

            setContentPane(panel);
            setSize(424, 176);
            setResizable(false);
            setLocationRelativeTo(owner);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

            new Thread(() -> {
                try {
                    GamePack.unpack(GamePack.PACK, appPath("Game"), (done, total) ->
                            SwingUtilities.invokeLater(() -> {
                                bar.setValue((int) (done * 1000 / Math.max(1, total)));
                                status.setText(done + " / " + total + " 个文件");
                            }));
                    SwingUtilities.invokeLater(() -> {
                        dispose();
                        JOptionPane.showMessageDialog(owner, "游戏文件安装完成，可以开始游戏了。",
                                "安装完成", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        dispose();
                        JOptionPane.showMessageDialog(owner, "解压失败：" + ex.getMessage(),
                                "安装失败", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }, "game-install").start();
        }
    }
}
