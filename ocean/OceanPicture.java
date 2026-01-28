package ocean;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HexFormat;

import javax.imageio.ImageIO;

public class OceanPicture {

	// Wandelt einen Hex-codierten String mit Bildinformationen in ein BufferesImage um
	public static BufferedImage convertHexString2Image(String hexValues) {
		HexFormat hexFormat = HexFormat.of();
		byte[] bytes = hexFormat.parseHex(hexValues);
    	ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
    	try {
			return ImageIO.read(bin);
		} catch (IOException e) {
		}
		return null;
	}
	// Wandelt ein BufferedImage in einen Hex-codierten String
	public static String convertImage2HexString(BufferedImage bimg) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		String pngStr = null;
    	try {
			if(ImageIO.write(bimg, "png", bos)) {
				byte[] bytes = bos.toByteArray();
				HexFormat hexFormat = HexFormat.of();
				pngStr =  hexFormat.formatHex(bytes);
			}
		} catch (IOException e) {
		}
		return pngStr;
	}
	
	// speichert ein BufferedImage als PNG-Datei ab
	public static boolean saveAsPNG(BufferedImage bimg, String filename) {
		if (bimg != null && filename !=null) {
			if (!filename.endsWith(".png")) {
				filename += ".png";
			}
			File imageFile = new File(filename);
			try {
				boolean ok = ImageIO.write(bimg, "png", imageFile);
				return ok;
			} catch (IOException e) {
			}
		}
		return false;
	}
	
	// Laedt eine PNG-Datei in ein BufferdImage
	public static BufferedImage loadPNG(String filename) {
		if (filename !=null) {
			if (!filename.endsWith(".png")) {
				filename += ".png";
			}
			File imageFile = new File(filename);
			try {
				BufferedImage bimg = ImageIO.read(imageFile);
				return bimg;
			} catch (IOException e) {
			}
		}
		return null;
	}
	
}
