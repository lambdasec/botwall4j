
package org.lambdasec.botwall4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 */
public class ResponseHardening implements Filter {

 protected FilterConfig config;

 @Override
  public void init(FilterConfig fc) throws ServletException {
    this.config = fc;
 }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain fc) 
          throws IOException, ServletException {
    Map<String,String> keyStore;

    try {
      if (request instanceof HttpServletRequest) {
        HttpSession st = ((HttpServletRequest) request).getSession();
        keyStore = (Map<String, String>) st.getAttribute("keyStore");
        if(keyStore == null) {
          keyStore = new HashMap<>();
        }
        ServletRequest newRequest = new CharRequestWrapper((HttpServletRequest) request, keyStore);
        ServletResponse newResponse = new CharResponseWrapper((HttpServletResponse) response);

        fc.doFilter(newRequest, newResponse);

        String html = newResponse.toString();

        if (html != null) {
          Document doc = Jsoup.parseBodyFragment(html);
          randomize(doc, "input[name]", "name", keyStore, true);
          randomize(doc, "input[id]", "id", keyStore, false);
          randomize(doc, "form[id]", "id", keyStore, false);
          response.getWriter().write(doc.html());
        }
        st.setAttribute("keyStore", keyStore);
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
                    "<address>Protected by <a href=https://github.com/lambdasec/botwall4j>Botwall4J</a></address>\n" +
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

  private void randomize(Document doc, String selector, String attribute, Map<String, String> keyStore,
                         boolean saveInStore) {
    Elements names = doc.select(selector);
    for (Element ele : names) {
      String name = ele.attr(attribute);
      if(keyStore.containsKey(name)) {
        String origName = keyStore.get(name);
        keyStore.remove(name);
        name = origName;
      }
      String s = UUID.randomUUID().toString();
      ele.attr(attribute, s);
      if(saveInStore) keyStore.put(s, name);
    }
  }

}
