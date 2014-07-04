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
import javax.servlet.http.HttpSession;
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
  
  ///////////////////////////// Class Attributes \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
  
  ////////////////////////////// Class Methods \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
  
  //////////////////////////////// Attributes \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
  
  protected FilterConfig config;
    
  /////////////////////////////// Constructors \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\  
  
  ////////////////////////////////// Methods \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
  
  //------------------------ Implements:
  
  //------------------------ Overrides: init, doFilter and destroy
  
  @Override
  public void init(FilterConfig fc) throws ServletException {
    this.config = fc;   
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain fc) 
          throws IOException, ServletException {
    ServletResponse newResponse = response;
    ServletRequest newRequest = request;
    SecretKey key;
    Map<String,String> keyStore;
    Map<String,IvParameterSpec> encryptedStore;
    
    SecureRandom random = new SecureRandom();
    byte iv[] = new byte[16];//generate random 16 byte IV AES is always 16bytes
    random.nextBytes(iv);
    IvParameterSpec ivspec = new IvParameterSpec(iv);
    
    try {
      if (request instanceof HttpServletRequest) {
        HttpSession st = ((HttpServletRequest) request).getSession();
        key = (SecretKey) st.getAttribute("key");
        keyStore = (Map<String, String>) st.getAttribute("keyStore");
        encryptedStore = (Map<String, IvParameterSpec>) st.getAttribute("encryptedStore");
        if(key == null) {
          try {
            key = KeyGenerator.getInstance("AES").generateKey();
            } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(ResponseHardening.class.getName()).log(Level.SEVERE, null, ex);
          }
          keyStore = new HashMap<>();
          encryptedStore = new HashMap<>();  
        }
        newRequest = new CharRequestWrapper((HttpServletRequest) request, key, keyStore, encryptedStore);
        newResponse = new CharResponseWrapper((HttpServletResponse) response);

        fc.doFilter(newRequest, newResponse);

        if (newResponse instanceof CharResponseWrapper) {
          String html = newResponse.toString();

          if (html != null) {
            Document doc = Jsoup.parseBodyFragment(html);
            harden(doc, "input[name]", "name", ivspec, keyStore, encryptedStore, key);
            harden(doc, "input[id]", "id", ivspec, keyStore, encryptedStore, key);
            harden(doc, "form[id]", "id", ivspec, keyStore, encryptedStore, key);
            response.getWriter().write(doc.html());
          }
        }
        st.setAttribute("key", key);
        st.setAttribute("keyStore", keyStore);
        st.setAttribute("encryptedStore", encryptedStore);
      }
    }
    catch (ServletException se) {
      if(response instanceof HttpServletResponse) {
        String str = "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n" +
                    "<html><head>\n" +
                    "<title>403 Forbidden</title>\n" +
                    "</head><body>\n" +
                    "<h1>Forbidden</h1>\n" +
                    "<hr>\n" +
                    "<address>Response Hardening by SourceClear Bodylines</address>\n" +
                    "</body></html>";
        response.getWriter().write(str);
        ((HttpServletResponse) response).setStatus(403);
      }
      else Logger.getLogger(ResponseHardening.class.getName()).log(Level.SEVERE, null, se);
    }
  }
  
  @Override
  public void destroy() {
    // do clean up here
  }
  
  //---------------------------- Abstract Methods -----------------------------
  
  //---------------------------- Utility Methods ------------------------------
  
  private void harden(Document doc, String selector, String attribute, IvParameterSpec ivspec, Map<String, String> keyStore,
          Map<String, IvParameterSpec> encryptedStore, SecretKey key) {
    Elements names = doc.select(selector);
    for (Element ele : names) {
      String name = ele.attr(attribute);
      if(encryptedStore.containsKey(name)) {
        String origName = decrypt(name, encryptedStore.get(name), key);
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
          ele.attr(attribute,s);
          keyStore.put(s,name);
          break;
        case "encryption":
          s = encrypt(name, ivspec, key);
          ele.attr(attribute,s);
          encryptedStore.put(s,ivspec);
          break;
      }
    }
  }
  
  private String encrypt(String str, IvParameterSpec ivspec, SecretKey key) {
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
  
  private String decrypt(String str, IvParameterSpec ivspec, SecretKey key) {
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
  
  //---------------------------- Property Methods -----------------------------     

}
