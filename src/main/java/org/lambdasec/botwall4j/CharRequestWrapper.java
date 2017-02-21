
package org.lambdasec.botwall4j;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 *
 */
public class CharRequestWrapper extends HttpServletRequestWrapper {

  protected String newRequestBody;
  
  protected BufferedReader modifiedReader;
  
  protected boolean getInputStreamCalled;

  protected boolean getReaderCalled;
  
  protected Map<String,String> keyStore;   

  public CharRequestWrapper(HttpServletRequest request, SecretKey key, Map<String,String> keyStore,
          Map<String,IvParameterSpec> encryptedStore) throws ServletException {
    super(request);
    StringBuilder sb = new StringBuilder();
    try {
    BufferedReader reader = request.getReader();
    if(reader != null) {
        String s;
        while ((s=reader.readLine())!=null) {
          sb.append(s);
          //sb.append('\n'); //if you want the newline
        }
      }
    }
    catch (IOException ex) {
      Logger.getLogger(CharRequestWrapper.class.getName()).log(Level.SEVERE, null, ex);
    }
    String originalRequestBody = sb.toString();
    if(!originalRequestBody.isEmpty()) {
      Map<String, List<String>> paramMap = getQueryMap(originalRequestBody);
      StringBuilder newSb = new StringBuilder();
      for (String s : paramMap.keySet()) {
        Iterator<String> itr = paramMap.get(s).iterator();
        while(itr.hasNext()) {
          String param = itr.next();
          if(keyStore.containsKey(s)) {
            String randomStr = keyStore.get(s);
            newSb.append(randomStr).append("=").append(param);
          }
          else if(encryptedStore.containsKey(s)) {
            String plainTxt = Util.decrypt(s,encryptedStore.get(s),key);
            newSb.append(plainTxt).append("=").append(param);
          }
          else {
            if(keyStore.isEmpty() && encryptedStore.isEmpty())
              newSb.append(s).append("=").append(param);
            else {
              request.getSession().invalidate();
              throw new ServletException();
            }
          }
          newSb.append("&");
        }
      }
      newRequestBody = newSb.toString();
    }
    else newRequestBody = originalRequestBody;
    this.keyStore = keyStore;
  }  

  @Override
  public ServletInputStream getInputStream() throws IOException {
    if (getReaderCalled) {
      throw new IllegalStateException("getReader already called");
    }

    getInputStreamCalled = true;
    final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(newRequestBody.getBytes());
    ServletInputStream istream = new ServletInputStream() {
      
      @Override
      public int read() throws IOException {
        return byteArrayInputStream.read();
      }
    /*
      @Override
      public boolean isFinished() {
        return (byteArrayInputStream.available() == 0);
      }

      @Override
      public boolean isReady() {
        return (byteArrayInputStream.available() > 0);
      }

      @Override
      public void setReadListener(ReadListener rl) {
        rl = null;
      }
    */
    };
    
    return istream;
  }

  @Override
  public BufferedReader getReader() throws IOException {
    if (modifiedReader != null) {
      return modifiedReader;
    }
    if (getInputStreamCalled) {
      throw new IllegalStateException("getInputStreamCalled already called");
    }
    getReaderCalled = true;
    modifiedReader = new BufferedReader(new InputStreamReader(this.getInputStream(), this.getRequest().getCharacterEncoding()));
    return modifiedReader;
  }
  
  @Override
  public String getParameter(String name) {
    Map<String, List<String>> paramMap = getQueryMap(this.newRequestBody);
    String value = null;
    if(paramMap.containsKey(name)) {
      value = paramMap.get(name).get(0);
    }
    return value; 
  }
  
  @Override
  public Enumeration<String> getParameterNames() {
    Map<String,List<String>> paramMap = getQueryMap(this.newRequestBody);
    return Collections.enumeration(paramMap.keySet()); 
  }

  @Override
  public String[] getParameterValues(String name) {
    Map<String,List<String>> paramMap = getQueryMap(this.newRequestBody);
    List<String> values = paramMap.get(name);
    return values.toArray(new String[values.size()]); 
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    Map<String,List<String>> paramMap = getQueryMap(this.newRequestBody);
    Map<String,String[]> retMap = new HashMap<>();
    for(String s : paramMap.keySet()) {
      List<String> values = paramMap.get(s);
      retMap.put(s, values.toArray(new String[values.size()]));
    }
    return retMap;
  }  
  
  private Map<String, List<String>> getQueryMap(String query)
  {  
     String[] params = query.split("&");  
     Map<String, List<String>> map = new HashMap<>(); 
     if(params.length > 1) {
      for (String param : params)  
      { 
        if(param.split("=").length>1) {
          String name = param.split("=")[0];  
          String value = param.split("=")[1];
         try {
          //String namePath = this.getRequest().getServletContext().getContextPath()+s;
          name=URLDecoder.decode(name, "UTF-8");
          value=URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
          Logger.getLogger(CharRequestWrapper.class.getName()).log(Level.SEVERE, null, ex);
        }
         if(map.containsKey(name)) {
           List<String> values = map.get(name);
           values.add(value);
           map.remove(name);
           map.put(name, values);
         }
         else {
          List<String> values = new LinkedList<>(); 
          values.add(value);
          map.put(name,values);
         }
        }
      }  
     }
     return map;  
  }

}
