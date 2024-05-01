package com.generation.tools.codegen.models;

import io.clientcore.core.http.models.HttpMethod;

import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;

public final class HttpRequestContext {

    /* ****************************************************************************************************************
     *
     * Request Configuration
     */
    private String methodName;
    private String methodReturnType;
    private final List<MethodParameter> parameters;
    private HttpMethod httpMethod;

    // This comes from the @Host annotation that is applied to the entire service interface, it will likely have one
    // or more substitutions in it, which will be replaced with the appropriate parameter values annotated with @HostParam.
    private String host;

    // This comes from the @HttpRequestInformation.path annotation that is applied to each method in the service interface.
    // It will likely have one or more substitutions in it, which will be replaced with the appropriate parameter values
    // annotated with @PathParam.
    private String path;

    private final Map<String, String> headers;
    private final Map<String, String> queryParams;

    private final Map<String, Substitution> substitutions;
//    private final List<Substitution> pathSubstitutions;

    private int[] expectedStatusCodes;

    public HttpRequestContext() {
        this.parameters = new ArrayList<>();
        this.headers = new HashMap<>();
        this.queryParams = new HashMap<>();
//        this.hostSubstitutions = new ArrayList<>();
//        this.pathSubstitutions = new ArrayList<>();
        this.substitutions = new HashMap<>();
    }

    private Body body;

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodReturnType() {
        return methodReturnType;
    }

    public void setMethodReturnType(String methodReturnType) {
        this.methodReturnType = methodReturnType;
    }

    public void addParameter(MethodParameter parameter) {
        this.parameters.add(parameter);
    }

    public List<MethodParameter> getParameters() {
        return parameters;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public void addQueryParam(String key, String value) {
        if (queryParams.containsKey(key)) {
            throw new IllegalArgumentException("Cannot add duplicate query parameter '" + key + "'");
        }
        queryParams.put(key, value);
    }

    public void addSubstitution(Substitution substitution) {
        if (substitutions.containsKey(substitution.getParameterName())) {
            throw new IllegalArgumentException("Cannot add duplicate substitution for parameter '" + substitution.getParameterName() + "'");
        }
        substitutions.put(substitution.getParameterName(), substitution);
    }

    public Substitution getSubstitution(String parameterName) {
        return substitutions.get(parameterName);
    }

//    public List<Substitution> getSubstitutions() {
//        return hostSubstitutions;
//    }

//    public void addPathSubstitution(Substitution substitution) {
//        pathSubstitutions.add(substitution);
//    }

//    public List<Substitution> getPathSubstitutions() {
//        return pathSubstitutions;
//    }

    public void setBody(Body body) {
        this.body = body;
    }

    public Body getBody() {
        return body;
    }

    public void setExpectedStatusCodes(int[] expectedStatusCodes) {
        if (expectedStatusCodes != null) {
            Arrays.sort(expectedStatusCodes);
        }
        this.expectedStatusCodes = expectedStatusCodes;
    }

    public List<Integer> getExpectedStatusCodes() {
//        return new ArrexpectedStatusCodes == null ? new int[0] : expectedStatusCodes;
        return Arrays.stream(expectedStatusCodes).boxed().collect(Collectors.toList());
    }

    public static class MethodParameter {
        private final TypeMirror type;
        private final String shortTypeName;
        private final String name;

        public MethodParameter(TypeMirror type, String shortTypeName, String name) {
            this.type = type;
            this.shortTypeName = shortTypeName;
            this.name = name;
        }

        public TypeMirror getTypeMirror() {
            return type;
        }

        public String getShortTypeName() {
            return shortTypeName;
        }

        public String getName() {
            return name;
        }
    }

    public static class Body {
        // This is the content type as specified in the @BodyParam annotation
        private final String contentType;

        // This is the type of the parameter that has been annotated with @BodyParam.
        // This is used to determine which setBody method to call on HttpRequest.
        private final String parameterType;

        // This is the parameter name, so we can refer to it when setting the body on the HttpRequest.
        private final String parameterName;

        public Body(String contentType, String parameterType, String parameterName) {
            this.contentType = contentType;
            this.parameterType = parameterType;
            this.parameterName = parameterName;
        }

        public String getContentType() {
            return contentType;
        }

        public String getParameterType() {
            return parameterType;
        }

        public String getParameterName() {
            return parameterName;
        }
    }
}
