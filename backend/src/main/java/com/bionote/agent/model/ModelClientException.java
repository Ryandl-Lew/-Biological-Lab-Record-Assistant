package com.bionote.agent.model;

public class ModelClientException extends RuntimeException {
    private final String code;
    public ModelClientException(String code,String message){super(message);this.code=code;}
    public ModelClientException(String code,String message,Throwable cause){super(message,cause);this.code=code;}
    public String code(){return code;}
}
