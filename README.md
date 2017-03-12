# Botwall4J

This project implements a `ResponseHardening` servlet filter that acts as a botwall for your Java web applications. The filter transparently rewrites all `input` and `form` elements on your web pages to random values which makes them harder to scrape automatically. You do not need to modify your application.

## Instructions 

Make sure that the `botwall4j` jar is available on your classpath. You can then modify the `web.xml` file to include the filter as follows:

```
<filter>
    <filter-name>Botwall4J</filter-name>
    <filter-class>org.lambdasec.botwall4j.ResponseHardening</filter-class>
</filter>
<filter-mapping>
    <filter-name>Botwall4J</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

For properly functioning of your application this filter should be the first in the filter chain. You can ensure that in the `web.xml` by defining it above all other filters.

It is also possible to use it without the `web.xml` configuration. In a Spring Boot application you can add the `botwall4j` library to your application and register the filter using the `filterRegistrationBean`:

```
@Bean
public FilterRegistrationBean responseHardeningFilterRegistrationBean() {
    final FilterRegistrationBean filterRegBean = new FilterRegistrationBean();
    filterRegBean.setFilter(new ResponseHardening());
    filterRegBean.setEnabled(true);
    filterRegBean.setOrder(Integer.MIN_VALUE);
    return filterRegBean;
}
```

Setting the order to `Integer.MIN_VALUE` will ensure that the filter runs first in the filter chain.
