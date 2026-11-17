package com.workforcehub.common.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.ServletRequestPathUtils;

public final class RequestPathResolver {

    private RequestPathResolver() {}

    public static String resolve(HttpServletRequest request) {
        RequestPath requestPath = ServletRequestPathUtils.hasParsedRequestPath(request)
            ? ServletRequestPathUtils.getParsedRequestPath(request)
            : ServletRequestPathUtils.parseAndCache(request);
        StringBuilder path = new StringBuilder();
        for (PathContainer.Element element : requestPath.pathWithinApplication().elements()) {
            path.append(element instanceof PathContainer.PathSegment segment
                ? segment.valueToMatch()
                : element.value());
        }
        return path.toString();
    }
}
