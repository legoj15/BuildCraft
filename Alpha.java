import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class Alpha {
    public static void main(String[] args) throws Exception {
        File file = new File("e:/GitHub/BuildCraft/src/main/resources/assets/buildcraftunofficial/textures/gui/nothing_filtered_buffer_slot.png");
        BufferedImage img = ImageIO.read(file);
        for(int y=0; y<img.getHeight(); y++) {
            for(int x=0; x<img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                int r = (argb >> 16) & 0xff;
                int g = (argb >> 8) & 0xff;
                int b = argb & 0xff;
                a = (int)(a * 0.45); // Originally 0.7, now 0.7 * 0.428 = ~0.3
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", file);
    }
}
