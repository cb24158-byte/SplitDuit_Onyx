package com.example.splitDuit;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    public int userId;

    public String username;
    public String password;

    // Constructor
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }
}