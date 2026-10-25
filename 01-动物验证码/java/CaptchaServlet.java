package demo.course.servlet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 动物验证码。
 *
 * <p>GET /api/captcha 返回一张 PNG：中央是一只随机动物（emoji 绘制），
 * 四周有干扰线、噪点和小号干扰动物。答案（动物中文名）只存在服务端 Session 里，
 * 前端拿不到，登录时再比对。</p>
 *
 * <p>使用方式：登录页把 &lt;img src="api/captcha"&gt; 放上去，用户填写动物名，
 * 登录请求带上 captcha 参数，AuthServlet 调 {@link #verify(HttpSession, String)} 校验。</p>
 */
@WebServlet(urlPatterns = {"/api/captcha"})
public final class CaptchaServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /** Session 里存答案的键。 */
    public static final String SESSION_KEY = "captchaAnswer";
    /** 验证码有效期（毫秒），超过即作废。 */
    private static final long TTL_MILLIS = 3 * 60 * 1000L;
    private static final String TTL_KEY = "captchaIssuedAt";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 动物名 -> emoji；key 就是标准答案。 */
    private static final Map<String, String> ANIMALS = new LinkedHashMap<>();
    static {
        ANIMALS.put("猫", "🐱");
        ANIMALS.put("狗", "🐶");
        ANIMALS.put("熊猫", "🐼");
        ANIMALS.put("兔子", "🐰");
        ANIMALS.put("狐狸", "🦊");
        ANIMALS.put("青蛙", "🐸");
        ANIMALS.put("老虎", "🐯");
        ANIMALS.put("狮子", "🦁");
        ANIMALS.put("考拉", "🐨");
        ANIMALS.put("企鹅", "🐧");
    }

    /** emoji 字体在 Windows 上是 Segoe UI Emoji；找不到就退化到默认字体。 */
    private static final String EMOJI_FONT = pickEmojiFont();

    private static String pickEmojiFont() {
        String[] wanted = {"Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        for (String name : wanted) {
            for (String have : available) {
                if (have.equalsIgnoreCase(name)) {
                    return name;
                }
            }
        }
        return Font.SANS_SERIF;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String answer = pickAnswer();
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_KEY, answer);
        session.setAttribute(TTL_KEY, System.currentTimeMillis());

        BufferedImage image = render(ANIMALS.get(answer));

        // 验证码不能被缓存，否则「换一张」会拿到旧图。
        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        try (OutputStream out = response.getOutputStream()) {
            ImageIO.write(image, "png", out);
            out.flush();
        }
    }

    /**
     * 校验用户填写的动物名。
     *
     * @param session  当前会话
     * @param userInput 用户输入（可以为 null）
     * @return 是否通过；无论通过与否都会作废本次验证码，防止暴力重试
     */
    public static boolean verify(HttpSession session, String userInput) {
        if (session == null) {
            return false;
        }
        Object stored = session.getAttribute(SESSION_KEY);
        Object issuedAt = session.getAttribute(TTL_KEY);

        // 一次性：取出即删，避免同一张图被反复提交
        session.removeAttribute(SESSION_KEY);
        session.removeAttribute(TTL_KEY);

        if (!(stored instanceof String) || !(issuedAt instanceof Long)) {
            return false;
        }
        if (System.currentTimeMillis() - (Long) issuedAt > TTL_MILLIS) {
            return false;
        }
        String expected = ((String) stored).trim();
        String actual = userInput == null ? "" : userInput.trim();
        return !actual.isEmpty() && expected.equalsIgnoreCase(actual);
    }

    private static String pickAnswer() {
        int index = RANDOM.nextInt(ANIMALS.size());
        return ANIMALS.keySet().toArray(new String[0])[index];
    }

    /** 画验证码：渐变底 + 干扰线 + 中央大动物 + 三个半透明干扰动物 + 噪点。 */
    private static BufferedImage render(String emoji) {
        int width = 260;
        int height = 96;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 背景
            g.setPaint(new java.awt.GradientPaint(0, 0, new Color(238, 243, 251),
                    width, height, new Color(226, 234, 247)));
            g.fillRect(0, 0, width, height);

            // 干扰曲线
            for (int i = 0; i < 5; i++) {
                g.setColor(new Color(120 + RANDOM.nextInt(50), 130 + RANDOM.nextInt(40),
                        160 + RANDOM.nextInt(40), 70));
                g.setStroke(new BasicStroke(1f + RANDOM.nextFloat()));
                java.awt.geom.QuadCurve2D curve = new java.awt.geom.QuadCurve2D.Double(
                        RANDOM.nextInt(width), RANDOM.nextInt(height),
                        RANDOM.nextInt(width), RANDOM.nextInt(height),
                        RANDOM.nextInt(width), RANDOM.nextInt(height));
                g.draw(curve);
            }

            // 中央动物：先画一层浅色描影，再画深色主体，保证在浅底上看得清
            double cx = width / 2.0;
            double cy = height / 2.0 + 4;
            double angle = -0.08 + RANDOM.nextDouble() * 0.16;

            g.setFont(new Font(EMOJI_FONT, Font.PLAIN, 66));
            g.setColor(new Color(212, 220, 236));
            drawCentered(g, emoji, cx + 2, cy + 2, angle);

            g.setColor(new Color(30, 40, 64));
            drawCentered(g, emoji, cx, cy, angle);

            // 干扰动物（更少、更淡）
            String[] others = ANIMALS.values().toArray(new String[0]);
            g.setFont(new Font(EMOJI_FONT, Font.PLAIN, 24));
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.16f));
            for (int i = 0; i < 2; i++) {
                String ghost = others[RANDOM.nextInt(others.length)];
                if (ghost.equals(emoji)) {
                    continue;
                }
                drawCentered(g, ghost, 34 + RANDOM.nextInt(width - 68),
                        26 + RANDOM.nextInt(height - 52), RANDOM.nextDouble() - 0.5);
            }
            g.setComposite(java.awt.AlphaComposite.SrcOver);

            // 噪点（减量，别糊住画面）
            for (int i = 0; i < 90; i++) {
                g.setColor(new Color(90, 115, 150, 25 + RANDOM.nextInt(30)));
                int x = RANDOM.nextInt(width);
                int y = RANDOM.nextInt(height);
                g.fillRect(x, y, 1 + RANDOM.nextInt(2), 1 + RANDOM.nextInt(2));
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawCentered(Graphics2D g, String text, double cx, double cy,
            double rotate) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.rotate(rotate);
        int textWidth = g.getFontMetrics().stringWidth(text);
        g.drawString(text, -textWidth / 2, 0);
        g.setTransform(old);
    }
}
