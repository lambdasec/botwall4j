package org.lambdasec.botwall4j;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by asankhaya on 2/21/17.
 */
public class Util {

  public static String encrypt(String str, IvParameterSpec ivspec, SecretKey key) {
    try {
      Cipher ecipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      ecipher.init(Cipher.ENCRYPT_MODE, key, ivspec);
      byte[] utf8 = str.getBytes("UTF8");
      byte[] enc = ecipher.doFinal(utf8);
      return Hex.encodeHexString(enc);
    } catch (UnsupportedEncodingException | IllegalBlockSizeException | BadPaddingException |
            NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
            InvalidAlgorithmParameterException ex) {
      Logger.getLogger(ResponseHardening.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  public static String decrypt(String str, IvParameterSpec ivspec, SecretKey key) {
    try {
      Cipher dcipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      dcipher.init(Cipher.DECRYPT_MODE, key, ivspec) ;
      byte[] dec = Hex.decodeHex(str.toCharArray());
      byte[] utf8 = dcipher.doFinal(dec);
      return new String(utf8,"UTF8");
    } catch (IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException |
            DecoderException | InvalidKeyException | InvalidAlgorithmParameterException |
            NoSuchAlgorithmException | NoSuchPaddingException ex) {
      Logger.getLogger(ResponseHardening.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }
}
