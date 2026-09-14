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
