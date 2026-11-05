import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

// Производные Android-ресурсы: только масштабирование и прозрачные поля, исходник не изменяется.
class GenerateIcons {
    static void render(BufferedImage source, int size, int content, File destination) throws Exception {
        var out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int width = content, height = Math.round(content * source.getHeight() / (float) source.getWidth());
        g.drawImage(source, (size-width)/2, (size-height)/2, width, height, null);
        g.dispose();
        destination.getParentFile().mkdirs();
        ImageIO.write(out, "png", destination);
    }
    public static void main(String[] args) throws Exception {
        var image = ImageIO.read(new File(args[0]));
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {48,72,96,144,192};
        for (int i=0;i<sizes.length;i++) render(image, sizes[i], sizes[i], new File("app/src/main/res/mipmap-"+densities[i]+"/ic_launcher.png"));
        // Весь квадрат исходника помещается в безопасный круг adaptive icon (66 из 108 dp).
        render(image, 432, 184, new File("app/src/main/res/drawable-xxxhdpi/catching_launcher_foreground.png"));
    }
}
