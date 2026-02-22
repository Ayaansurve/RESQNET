package com.example.myapplication;

public class Message {
    private String text;
    private boolean isMe;

    public Message(String text, boolean isMe) {
        this.text = text;
        this.isMe = isMe;
    }

    public String getText() { return text; }
    public boolean isMe() { return isMe; }
}