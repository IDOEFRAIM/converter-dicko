package com.converter.supplier.service;

import com.converter.storage.exception.InvalidFileException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Verifie qu'une image televersee comme code QR Alipay/WeChat en contient reellement un lisible
 * (retour client : "au niveau du qrcode qu'on upload, on doit verifier qu'il est valide") --
 * {@code FileValidator} ne verifie que le TYPE du fichier (signature binaire image/PDF), jamais
 * son contenu : n'importe quelle photo passait jusqu'ici pour un "code QR".
 *
 * <p>ZXing (bibliotheque de reference du domaine, pas de reimplementation maison) tente un
 * decodage reel restreint au format QR uniquement ({@link DecodeHintType#POSSIBLE_FORMATS}) :
 * un code-barres 1D (ex. EAN produit) ne doit jamais passer pour un code QR valide.
 */
@Component
public class QrCodeValidator {

    private static final Map<DecodeHintType, Object> QR_ONLY_HINTS =
            Map.of(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));

    /** @throws InvalidFileException si le contenu n'est pas une image, ou n'encode aucun code QR lisible */
    public void validate(byte[] content) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new InvalidFileException("Impossible de lire cette image.");
        }
        if (image == null) {
            // Un PDF (type par ailleurs autorise par FileValidator) n'est pas ImageIO-lisible :
            // jamais un code QR valide non plus dans ce cas.
            throw new InvalidFileException("Ce fichier ne contient pas de code QR lisible.");
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        try {
            new MultiFormatReader().decode(bitmap, QR_ONLY_HINTS);
        } catch (NotFoundException e) {
            throw new InvalidFileException("Ce fichier ne contient pas de code QR lisible. "
                    + "Verifiez que la photo cadre bien l'integralite du code QR, nette et sans reflet.");
        }
    }
}
