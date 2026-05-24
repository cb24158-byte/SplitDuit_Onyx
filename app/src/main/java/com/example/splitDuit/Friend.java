package com.example.splitDuit;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "friend")
public class Friend {
    @PrimaryKey(autoGenerate = true)
    public int friendId;
    public Date date;
    public String friendName;
    public String friendPhoneNumber;
    public String note;

    public Friend(String friendName, String friendPhoneNumber, Date date) {
        this.date = date;
        this.friendName = friendName;
        this.friendPhoneNumber = friendPhoneNumber;
    }
    public String getName() {
        return this.friendName;
    }
}