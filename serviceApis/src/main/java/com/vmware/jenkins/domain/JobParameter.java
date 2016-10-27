package com.vmware.jenkins.domain;

import com.vmware.http.request.UrlParam;
import com.vmware.util.exception.RuntimeIOException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class JobParameter {
    public String name;
    public String value;

    public JobParameter(final String name, final String value) {
        this.name = name;
        this.value = value;
    }

    public UrlParam toUrlParam() {
        try {
            return new UrlParam(name, URLEncoder.encode(value, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeIOException(e);
        }
    }
}
