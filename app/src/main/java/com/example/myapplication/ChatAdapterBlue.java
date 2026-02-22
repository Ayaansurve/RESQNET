package com.example.wordwave;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.Message;

import java.util.List;

public class ChatAdapterBlue extends RecyclerView.Adapter<ChatAdapterBlue.ViewHolder> {
    private List<Message> messageList;

    public ChatAdapterBlue(List<Message> messageList) {
        this.messageList = messageList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = messageList.get(position);
        holder.text.setText(message.getText());

        // Basic styling to distinguish sender from receiver
        if (message.isMe()) {
            holder.text.setGravity(Gravity.END);
            holder.text.setTextColor(0xFF0000FF); // Blue for "Me"
        } else {
            holder.text.setGravity(Gravity.START);
            holder.text.setTextColor(0xFF000000); // Black for "Them"
        }
    }

    @Override
    public int getItemCount() { return messageList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;
        ViewHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(android.R.id.text1);
        }
    }
}