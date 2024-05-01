package com.generation.tools.codegen.models;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TemplateInput {
    // a map of fully-qualified class names to their short names
    private final Map<String, String> imports = new TreeMap<>();

    private String packageName;

    private String serviceInterfaceFQN;

    private String serviceInterfaceShortName;

    private String serviceInterfaceImplShortName;

    private String host;

    private List<HttpRequestContext> httpRequestContexts;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Map<String, String> getImports() {
        return imports;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getServiceInterfaceShortName() {
        return serviceInterfaceShortName;
    }

    public void setServiceInterfaceShortName(String serviceInterfaceShortName) {
        this.serviceInterfaceShortName = serviceInterfaceShortName;
    }

    public String getServiceInterfaceImplShortName() {
        return serviceInterfaceImplShortName;
    }

    public void setServiceInterfaceImplShortName(String serviceInterfaceImplShortName) {
        this.serviceInterfaceImplShortName = serviceInterfaceImplShortName;
    }

    //    public void addImport(String fqcn) {
//        imports.put(fqcn, fqcn.substring(fqcn.lastIndexOf('.') + 1));
//    }

    private static String toShortName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        if (lastDot > 0) {
            return fqcn.substring(lastDot + 1);
        }
        return fqcn;
    }

    // returns the short name of the class
    public String addImport(String importFQN) {
        if (importFQN != null && !importFQN.isEmpty()) {
            String shortName = toShortName(importFQN);
            imports.put(importFQN, shortName);
            return shortName;
        }
        return null;
    }

    // returns the short name of the class
    public String addImport(TypeMirror type) {
        String longName = type.toString();
        String shortName = null;

        if (type.getKind().isPrimitive()) {
            shortName = toShortName(longName);
            imports.put(longName, shortName);
        } else if (imports.containsKey(type.toString())) {
            shortName = imports.get(longName);
        } else if (type.getKind() == TypeKind.DECLARED) {
            // now we need to check if this type is a generic type, and if it is, we need to recursively check
            // the type arguments
            TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
            List<? extends TypeMirror> typeArguments = ((DeclaredType) type).getTypeArguments();
            if (typeArguments != null && !typeArguments.isEmpty()) {
                longName = typeElement.getQualifiedName().toString();
                shortName = toShortName(typeElement.getQualifiedName().toString());
                imports.put(longName, shortName);
            } else {
                shortName = toShortName(longName);
                imports.put(longName, shortName);
            }
        }

        return shortName;
    }

    public void setHttpRequestContexts(List<HttpRequestContext> httpRequestContexts) {
        this.httpRequestContexts = httpRequestContexts;
    }

    public List<HttpRequestContext> getHttpRequestContexts() {
        return httpRequestContexts;
    }

    public void setServiceInterfaceFQN(String serviceInterfaceFQN) {
        this.serviceInterfaceFQN = serviceInterfaceFQN;
    }

    public String getServiceInterfaceFQN() {
        return serviceInterfaceFQN;
    }
}
