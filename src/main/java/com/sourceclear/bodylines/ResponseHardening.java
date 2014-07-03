/*
 * © Copyright 2014 -  SourceClear Inc
 */

package com.sourceclear.bodylines;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 */
public class ResponseHardening implements Filter {
  
  protected FilterConfig config;
  
  protected SecretKey key;
  
  protected Map<String,String> keyStore;
  
  protected Map<String,IvParameterSpec> encryptedStore;

  @Override
  public void init(FilterConfig fc) throws ServletException {
    this.config = fc;
    try {
      this.key = KeyGenerator.getInstance("AES").generateKey();
    } catch (NoSuchAlgorithmException ex) {
      Logger.getLogger(ResponseHardening.class.getName()).log(Level.SEVERE, null, ex);
    }
    this.keyStore = new HashMap<>();
    this.encryptedStore = new HashMap<>();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain fc) 
          throws IOException, ServletException {
    ServletResponse newResponse = response;
    ServletRequest newRequest = request;
    SecureRandom random = new SecureRandom();
    byte iv[] = new byte[16];//generate random 16 byte IV AES is always 16bytes
    random.nextBytes(iv);
    IvParameterSpec ivspec = new IvParameterSpec(iv);
    
    if (request instanceof HttpServletRequest) {
      newRequest = new CharRequestWrapper((HttpServletRequest) request, this.key, keyStore, encryptedStore);
      newResponse = new CharResponseWrapper((HttpServletResponse) response);
    }
    
    fc.doFilter(newRequest, newResponse);

    if (newResponse instanceof CharResponseWrapper) {
      String html = newResponse.toString();
      
      if (html != null) {
        Document doc = Jsoup.parseBodyFragment(html);
        Elements names = doc.select("input[name]");
        for (Element ele : names) {
          String name = ele.attr("name");
          if(encryptedStore.containsKey(name)) {
            String origName = decrypt(name, encryptedStore.get(name));
            encryptedStore.remove(name);
            name = origName;
          }
          if(keyStore.containsKey(name)) {
            String origName = keyStore.get(name);
            keyStore.remove(name);
            name = origName;
          }
          String s;
          if(null != config.getInitParameter("hardeningType")) 
            switch (config.getInitParameter("hardeningType")) {
            case "random":
              s = UUID.randomUUID().toString();
              ele.attr("name",s);
              keyStore.put(s,name);
              break;
            case "encryption":
              s = encrypt(name, ivspec);
              ele.attr("name",s);
              encryptedStore.put(s,ivspec);
              break;
          }
        }
        response.getWriter().write(doc.html());
      }
    }
  }
  
  @Override
  public void destroy() {

  }
  
  private String encrypt(String str, IvParameterSpec ivspec) {
    try {
      Cipher ecipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      ecipher.init(Cipher.ENCRYPT_MODE, this.key, ivspec);
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
  
  private String decrypt(String str, IvParameterSpec ivspec) {
    try {
      Cipher dcipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      dcipher.init(Cipher.DECRYPT_MODE, this.key, ivspec) ;
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
  ///////////////////////////// Class Attributes \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
  
  ////////////////////////////// Class Methods \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
  
  //////////////////////////////// Attributes \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
    
  /////////////////////////////// Constructors \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\  
  
  ////////////////////////////////// Methods \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
  
  //------------------------ Implements:
  
  //------------------------ Overrides:
  
  //---------------------------- Abstract Methods -----------------------------
  
  //---------------------------- Utility Methods ------------------------------
  
  //---------------------------- Property Methods -----------------------------     

}
