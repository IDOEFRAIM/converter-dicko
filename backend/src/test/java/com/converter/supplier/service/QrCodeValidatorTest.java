package com.converter.supplier.service;

import com.converter.storage.exception.InvalidFileException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure, sans Spring : {@code FileValidator} verifie deja le TYPE de fichier (signature binaire)
 * ailleurs -- cette classe verifie uniquement le CONTENU (retour client : "au niveau du qrcode
 * qu'on upload, on doit verifier qu'il est valide").
 */
class QrCodeValidatorTest {

    private final QrCodeValidator validator = new QrCodeValidator();

    private byte[] realQrCodePng() throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode("https://example.com/pay", BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return out.toByteArray();
    }

    private byte[] plainImagePng() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    @Test
    void validate_withARealQrCode_neverThrows() throws Exception {
        assertThatCode(() -> validator.validate(realQrCodePng())).doesNotThrowAnyException();
    }

    /**
     * Bug reel (retour client) : de nombreux telephones Android enregistrent captures d'ecran
     * et images de galerie en WebP par defaut -- un format qu'ImageIO ne sait PAS decoder de
     * base (JDK sans plugin). {@code ImageIO.read} retournait alors {@code null} sur un code QR
     * pourtant parfaitement valide, rejete a tort comme "illisible". Fixe par l'ajout du plugin
     * TwelveMonkeys (voir pom.xml) qui s'enregistre lui-meme via le SPI ImageIO.
     *
     * <p>Octets reels (pas une simple signature) : QR "https://example.com/pay" encode en WebP
     * sans perte via Pillow, pour verifier un vrai decodage de bout en bout, pas seulement la
     * reconnaissance du format.
     */
    @Test
    void validate_withARealQrCodeInWebpFormat_neverThrows() {
        byte[] webp = Base64.getDecoder().decode(
                "UklGRlIBAABXRUJQVlA4TEYBAAAvx8AxAA8w//M///MfeJDcSJIcSU7UI58UITVpKkagCFCxKk1ShHzOQTBm9r5yr1dE"
                + "/ydA/0kbMOSXpji0AFmKpKnQeitOTanX4uQ0ndqeJE1jr0fRueVXZaazIElTTi5Eyyn1WoBhvuc033MBSvmk6dT2"
                + "FKe+/pdn7IB6bk/RGfKrFHFIisZt0XMqetZCLjg55ZcWnFIsWm5P7PDEzm06VYmkdcgPTYvGULQsJRqPJz7yax2K"
                + "Xoo5DHGt90cPopXzgEPbE/B4ilOlRAN8TwkYipaVyA9tT9G4TZ0h9lLMyQW1nAqtw9RKETu3qQPsuRGnKjEnFzi0"
                + "EA3Q52v4pPmhKUiZk5UYMIxrvU0tp0WrRdK0OLURPWXRa3FymnpK0QEVpGjcFp3bnHrMySn13Ihei6Rp6gxBTulU"
                + "KcAwnZqKlhtqWcl/3A==");
        assertThatCode(() -> validator.validate(webp)).doesNotThrowAnyException();
    }

    @Test
    void validate_withAPlainImageContainingNoQrCode_throwsInvalidFileException() throws Exception {
        assertThatThrownBy(() -> validator.validate(plainImagePng()))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void validate_withNonImageBytes_throwsInvalidFileException() {
        assertThatThrownBy(() -> validator.validate("this is definitely not an image".getBytes()))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void validate_withA1dBarcodeInstedOfAQrCode_isRejected() throws Exception {
        // Un code-barres 1D (ex. EAN produit) est parfaitement decodable par ZXing en general,
        // mais jamais un "code QR" au sens metier -- QrCodeValidator restreint volontairement le
        // decodage au format QR_CODE (voir DecodeHintType.POSSIBLE_FORMATS), donc le rejette
        // meme si son propre contenu est lisible.
        BitMatrix matrix = new Code128Writer().encode("123456789012", BarcodeFormat.CODE_128, 300, 100);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);

        assertThatThrownBy(() -> validator.validate(out.toByteArray()))
                .isInstanceOf(InvalidFileException.class);
    }
}
