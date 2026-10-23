import openize.heic.decoder.*;
import openize.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Decodes each input HEIC (argument pairs: input, output PNG) to an RGB PNG. */
public class Decode {
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i += 2) {
            long start = System.nanoTime();
            try (IOFileStream fs = new IOFileStream(args[i], IOMode.READ)) {
                HeicImage image = HeicImage.load(fs);
                int width = (int) image.getWidth();
                int height = (int) image.getHeight();
                int[] pixels = image.getInt32Array(PixelFormat.Argb32);
                BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                out.setRGB(0, 0, width, height, pixels, 0, width);
                ImageIO.write(out, "png", new File(args[i + 1]));
                System.out.printf("%s %dx%d in %d ms%n", args[i], width, height, (System.nanoTime() - start) / 1_000_000);
            } catch (Throwable t) {
                System.out.println(args[i] + " FAILED: " + t);
                t.printStackTrace();
            }
        }
    }
}
