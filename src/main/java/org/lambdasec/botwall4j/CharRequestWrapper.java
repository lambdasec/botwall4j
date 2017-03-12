
package org.lambdasec.botwall4j;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 *
 */
public class CharRequestWrapper extends HttpServletRequestWrapper {

  private String newRequestBody;

  private BufferedReader modifiedReader;

  private boolean getInputStreamCalled;

  private boolean getReaderCalled;

  CharRequestWrapper(HttpServletRequest request, Map<String,String> keyStore) throws ServletException {
    super(request);
    Map<String, String[]> paramMap = request.getParameterMap();
    if(paramMap == null) {
      StringBuilder sb = new StringBuilder();
      try {
        BufferedReader reader = request.getReader();
        if (reader != null) {
          String s;
          while ((s = reader.readLine()) != null) {
            sb.append(s);
            //sb.append('\n'); //if you want the newline
          }
        }
      } catch (IOException ex) {
        Logger.getLogger(CharRequestWrapper.class.getName()).log(Level.SEVERE, null, ex);
      }
      paramMap = getQueryMap(sb.toString());
    }
    if(!paramMap.isEmpty()) {
      StringBuilder newSb = new StringBuilder();
      for (String s : paramMap.keySet()) {
        for (String param: paramMap.get(s)) {
          if(keyStore.containsKey(s)) {
            String randomStr = keyStore.get(s);
            newSb.append(randomStr).append("=").append(param);
          }
          else {
            request.getSession().invalidate();
            throw new ServletException();
          }
          newSb.append("&");
        }
      }
      newRequestBody = newSb.toString();
    }
    else newRequestBody = "";
  }  

  @Override
  public ServletInputStream getInputStream() throws IOException {
    if (getReaderCalled) {
      throw new IllegalStateException("getReader already called");
    }

    getInputStreamCalled = true;
    final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.newRequestBody.getBytes());

    return new ServletInputStream() {
      @Override
      public int read() throws IOException {
        return byteArrayInputStream.read();
      }
    };
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
  public String getQueryString() {
    return this.newRequestBody;
  }

  @Override
  public String getParameter(String name) {
    Map<String, String[]> paramMap = getQueryMap(this.newRequestBody);
    String value = null;
    if(paramMap.containsKey(name)) {
      value = paramMap.get(name)[0];
    }
    return value; 
  }
  
  @Override
  public Enumeration<String> getParameterNames() {
    Map<String,String[]> paramMap = getQueryMap(this.newRequestBody);
    return Collections.enumeration(paramMap.keySet()); 
  }

  @Override
  public String[] getParameterValues(String name) {
    Map<String,String[]> paramMap = getQueryMap(this.newRequestBody);
    String[] values = paramMap.get(name);
    return values;
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    return getQueryMap(this.newRequestBody);
  }  

  private Map<String, String[]> getQueryMap(String query)
  {  
     String[] params = query.split("&");  
     Map<String, String[]> map = new HashMap<>();
     if(params.length > 0) {
        for (String param : params) {
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
              List<String> values = Arrays.asList(map.get(name));
              values.add(value);
              map.remove(name);
              map.put(name, (String []) values.toArray());
            }
            else {
              String[] values = new String[1];
              values[0] = value;
              map.put(name, values);
            }
          }
        }
     }
     return map;  
  }

}
