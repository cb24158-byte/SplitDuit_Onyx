package com.example.splitDuit;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FriendDao {
    @Insert
    long insertFriend(Friend friend);

    @Update
    void updateFriend(Friend friend);
    @Query("SELECT * FROM friend ORDER BY friendName ASC")
    List<Friend> getAllFriends();

    @Query("SELECT * FROM friend WHERE friendId = :friendId LIMIT 1")
    Friend getFriendById(int friendId);

    @Query("SELECT * FROM friend WHERE friendName = :name LIMIT 1")
    Friend getFriendByName(String name);
    @Delete
    void deleteFriend(Friend friend);

}