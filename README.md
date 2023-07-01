# monibag-commons-logger

Simple to use monibag-commons-logger library to generate automatic logs with trace id for (controller, service, and repository)

### add monibag-commons-logger dependency in pom.xml
```xml
<dependency>
	<groupId>com.monibag.commons.logger</groupId>
	<artifactId>monibag-commons-logger</artifactId>
	<version>0.0.1</version>
</dependency>
```

# usage

## integration


### configure filter and enable tracing for each request and response
```java
import java.io.IOException;
import com.monibag.LogTracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LogTracerFilter extends OncePerRequestFilter {
    private final LogTracer logTracer;

    public LogTracerFilter(LogTracer logTracer) {
        this.logTracer = logTracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
    throws ServletException, IOException {
        logTracer.tracingId(request.getMethod());
        filterChain.doFilter(request, response);
    }
}
```


**add secured attribute in application.properties**
```
log.secure-attribute=email,password
```
